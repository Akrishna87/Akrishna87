package io.github.akrishna87.rssreader.data

import java.util.concurrent.Callable
import java.util.concurrent.Executors

/** Fetches every feed and merges the results, without any UI. Used by the background worker. */
object Refresher {
    class Outcome(val snapshot: Snapshot, val newArticles: List<Article>, val failed: Int)

    /** Download every feed (four at a time). Does no merging, so it can run without holding any lock. */
    fun fetchAll(feeds: List<Feed>, fetch: (String) -> ParsedFeed = { Net.loadFeed(it).second }): Map<String, Result<ParsedFeed>> {
        if (feeds.isEmpty()) return emptyMap()
        val pool = Executors.newFixedThreadPool(4)
        return try {
            feeds.map { f -> f.id to pool.submit(Callable { runCatching { fetch(f.url) } }) }.associate { (id, job) -> id to job.get() }
        } finally {
            pool.shutdown()
        }
    }

    /** Fold downloaded feeds into [snapshot]. */
    fun apply(snapshot: Snapshot, fetched: Map<String, Result<ParsedFeed>>, now: Long): Outcome {
        var articles = snapshot.articles
        val before = articles.map { it.id }.toHashSet()
        var failed = 0
        val feeds = snapshot.feeds.map { f ->
            val result = fetched[f.id] ?: return@map f
            result.fold(
                onSuccess = {
                    articles = mergeArticles(articles, f.id, it, now).first
                    f.copy(lastFetched = now, error = null, title = it.title.ifBlank { f.title })
                },
                onFailure = {
                    failed++
                    f.copy(error = it.message ?: "Update failed")
                },
            )
        }
        // A feed fetched for the first time isn't "new articles"; only feeds we'd already read from are.
        val known = snapshot.feeds.filter { it.lastFetched > 0 }.map { it.id }.toHashSet()
        val fresh = articles.filter { it.id !in before && it.feedId in known }.sortedByDescending { it.date }
        return Outcome(snapshot.copy(feeds = feeds, articles = articles.sortedByDescending { it.date }), fresh, failed)
    }

    fun refresh(snapshot: Snapshot, now: Long, fetch: (String) -> ParsedFeed = { Net.loadFeed(it).second }): Outcome =
        apply(snapshot, fetchAll(snapshot.feeds, fetch), now)

    /** Which of [fresh] deserve a notification under [mode] ("off", "priority" or "all"). */
    fun notifiable(fresh: List<Article>, snapshot: Snapshot, mode: String, now: Long): List<Article> {
        if (mode == "off") return emptyList()
        val filters = Filters(snapshot.mute, snapshot.highlight)
        val priority = snapshot.feeds.filter { it.priority }.map { it.id }.toHashSet()
        return fresh.filter { a ->
            a.date >= now - 2 * 86_400_000L && filters.mutedBy(a) == null &&
                (mode == "all" || a.feedId in priority || filters.highlightedBy(a) != null)
        }
    }
}
