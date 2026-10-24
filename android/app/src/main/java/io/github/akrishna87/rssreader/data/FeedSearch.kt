package io.github.akrishna87.rssreader.data

import org.json.JSONObject
import java.net.URLEncoder

/** A feed found by searching a topic or name. */
data class FoundFeed(val name: String, val url: String, val description: String, val followers: Long)

/** Looks up feeds for a topic ("football", "rust programming") in Feedly's public feed directory. */
object FeedSearch {
    private const val ENDPOINT = "https://cloud.feedly.com/v3/search/feeds?count=25&query="

    fun search(query: String): List<FoundFeed> =
        parse(Net.fetch(ENDPOINT + URLEncoder.encode(query.trim(), "UTF-8")))

    fun parse(json: String): List<FoundFeed> {
        val results = try { JSONObject(json).optJSONArray("results") } catch (e: Exception) { null } ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val r = results.optJSONObject(i) ?: return@mapNotNull null
            val url = r.optString("feedId").removePrefix("feed/").trim()
            if (!url.startsWith("http", ignoreCase = true)) return@mapNotNull null
            val title = r.optString("title").trim().ifEmpty { r.optString("website").trim() }
            if (title.isEmpty()) return@mapNotNull null
            FoundFeed(title, url, r.optString("description").trim(), r.optLong("subscribers"))
        }.distinctBy { it.url }.sortedByDescending { it.followers }
    }

    /** "12K followers" style label. */
    fun followersLabel(n: Long): String = when {
        n >= 1_000_000 -> "%.1fM followers".format(n / 1_000_000.0)
        n >= 1_000 -> "${n / 1_000}K followers"
        n > 0 -> "$n followers"
        else -> ""
    }
}
