package io.github.akrishna87.rssreader

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import io.github.akrishna87.rssreader.data.Net
import io.github.akrishna87.rssreader.data.Readability
import io.github.akrishna87.rssreader.data.Refresher
import io.github.akrishna87.rssreader.data.Store
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.TimeUnit

/** Refreshes every feed while the app is closed, then notifies about new articles. */
class RefreshWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        // The open app refreshes on its own.
        if (AppState.foreground) return Result.success()
        val store = Store(File(applicationContext.filesDir, "reader.json"))
        val start = store.load()
        if (start.feeds.isEmpty()) return Result.success()

        val fetched = withContext(Dispatchers.IO) { Refresher.fetchAll(start.feeds) }
        var outcome: Refresher.Outcome? = null
        // Merge into whatever is on disk now, in case something else wrote while we were downloading.
        store.update { disk ->
            Refresher.apply(disk, fetched, System.currentTimeMillis()).also { outcome = it }.snapshot
        }
        val done = outcome ?: return Result.success()
        val settings = done.snapshot.settings

        val toNotify = Refresher.notifiable(done.newArticles, done.snapshot, settings.notify, System.currentTimeMillis())
        Notifier.show(applicationContext, toNotify, done.snapshot.feeds)

        if (settings.offline && done.newArticles.isNotEmpty()) {
            val fullTextFeeds = done.snapshot.feeds.filter { it.fullText }.map { it.id }.toSet()
            val wanted = done.newArticles.filter { it.feedId in fullTextFeeds && it.link.isNotEmpty() }.take(15)
            val pages = withContext(Dispatchers.IO) {
                wanted.mapNotNull { a -> runCatching { Readability.extract(Net.fetch(a.link), a.link) }.getOrNull()?.let { a.id to it } }
            }.toMap()
            if (pages.isNotEmpty()) store.update { s -> s.copy(articles = s.articles.map { a -> pages[a.id]?.let { a.copy(fullHtml = it) } ?: a }) }
            Prefetch.images(applicationContext, store.load().articles.filter { a -> done.newArticles.any { it.id == a.id } })
        }
        HeadlinesWidget.updateAll(applicationContext)
        return Result.success()
    }

    companion object {
        private const val NAME = "background-refresh"

        /** Run every [hours] hours (0 = never) while on a network. */
        fun schedule(context: Context, hours: Int) {
            val work = WorkManager.getInstance(context)
            if (hours <= 0) {
                work.cancelUniqueWork(NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<RefreshWorker>(hours.toLong(), TimeUnit.HOURS)
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .build()
            work.enqueueUniquePeriodicWork(NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
