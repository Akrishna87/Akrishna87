package io.github.akrishna87.rssreader.data

import org.json.JSONArray
import org.json.JSONObject
import java.io.File

data class Snapshot(
    val feeds: List<Feed> = emptyList(),
    val articles: List<Article> = emptyList(),
    val unreadOnly: Boolean = false,
    val mute: List<String> = emptyList(),
    val highlight: List<String> = emptyList(),
    val collapsed: Set<String> = emptySet(),
    val boards: List<String> = emptyList(),
    val settings: Settings = Settings(),
)

/** Everything lives in one JSON file in the app's private storage. */
class Store(private val file: File) {

    fun load(): Snapshot {
        if (!file.exists()) return Snapshot()
        return try {
            val o = JSONObject(file.readText())
            val feeds = o.optJSONArray("feeds").objects().map {
                Feed(
                    id = it.getString("id"),
                    url = it.getString("url"),
                    title = it.optString("title"),
                    siteUrl = it.optString("siteUrl"),
                    lastFetched = it.optLong("lastFetched"),
                    error = it.optString("error").ifEmpty { null },
                    folder = it.optString("folder"),
                    customTitle = it.optString("customTitle"),
                    priority = it.optBoolean("priority"),
                )
            }
            val articles = o.optJSONArray("articles").objects().map {
                Article(
                    id = it.getString("id"),
                    feedId = it.getString("feedId"),
                    title = it.optString("title"),
                    link = it.optString("link"),
                    date = it.optLong("date"),
                    author = it.optString("author"),
                    contentHtml = it.optString("content"),
                    snippet = it.optString("snippet"),
                    thumbnail = it.optString("thumb").ifEmpty { null },
                    read = it.optBoolean("read"),
                    starred = it.optBoolean("starred"),
                    boards = it.optJSONArray("boards").strings(),
                    highlights = it.optJSONArray("highlights").objects().map { h ->
                        Highlight(h.optString("text"), h.optString("note"), h.optLong("time"))
                    }.filter { h -> h.text.isNotEmpty() },
                )
            }
            Snapshot(
                feeds, articles, o.optBoolean("unreadOnly"),
                mute = o.optJSONArray("mute").strings(),
                highlight = o.optJSONArray("highlight").strings(),
                collapsed = o.optJSONArray("collapsed").strings().toSet(),
                boards = o.optJSONArray("boards").strings(),
                settings = o.optJSONObject("settings")?.let {
                    Settings(it.optString("layout", "magazine"), it.optInt("textSize", 18).coerceIn(14, 26), it.optString("theme", "system"))
                } ?: Settings(),
            )
        } catch (e: Exception) {
            Snapshot()
        }
    }

    @Synchronized
    fun save(s: Snapshot) {
        val o = JSONObject()
        o.put("version", 1)
        o.put("unreadOnly", s.unreadOnly)
        o.put("mute", JSONArray(s.mute))
        o.put("highlight", JSONArray(s.highlight))
        o.put("collapsed", JSONArray(s.collapsed.sorted()))
        o.put("boards", JSONArray(s.boards))
        o.put("settings", JSONObject().put("layout", s.settings.layout).put("textSize", s.settings.textSize).put("theme", s.settings.theme))
        o.put("feeds", JSONArray().apply {
            s.feeds.forEach { f ->
                put(JSONObject().apply {
                    put("id", f.id); put("url", f.url); put("title", f.title); put("siteUrl", f.siteUrl)
                    put("lastFetched", f.lastFetched); put("error", f.error ?: "")
                    put("folder", f.folder); put("customTitle", f.customTitle); put("priority", f.priority)
                })
            }
        })
        o.put("articles", JSONArray().apply {
            s.articles.forEach { a ->
                put(JSONObject().apply {
                    put("id", a.id); put("feedId", a.feedId); put("title", a.title); put("link", a.link)
                    put("date", a.date); put("author", a.author); put("content", a.contentHtml)
                    put("snippet", a.snippet); put("thumb", a.thumbnail ?: ""); put("read", a.read); put("starred", a.starred)
                    put("boards", JSONArray(a.boards))
                    put("highlights", JSONArray().apply {
                        a.highlights.forEach { h -> put(JSONObject().put("text", h.text).put("note", h.note).put("time", h.time)) }
                    })
                })
            }
        })
        // Write to a temp file then swap, so a crash mid-write can't corrupt the data.
        val tmp = File(file.parentFile, file.name + ".tmp")
        tmp.writeText(o.toString())
        if (!tmp.renameTo(file)) {
            file.delete()
            tmp.renameTo(file)
        }
    }

    private fun JSONArray?.strings(): List<String> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optString(it).ifEmpty { null } }

    private fun JSONArray?.objects(): List<JSONObject> =
        if (this == null) emptyList() else (0 until length()).mapNotNull { optJSONObject(it) }
}

/** Merge freshly fetched items into the stored articles for one feed. Returns (all articles, new count). */
fun mergeArticles(existing: List<Article>, feedId: String, parsed: ParsedFeed, now: Long, maxPerFeed: Int = 300): Pair<List<Article>, Int> {
    val byId = existing.associateBy { it.id }.toMutableMap()
    var added = 0
    parsed.items.forEachIndexed { index, p ->
        val key = p.guid.ifEmpty { p.link.ifEmpty { p.title + "|" + p.date } }
        val id = feedId + "-" + stableId(key)
        val content = p.contentHtml.take(200_000)
        val old = byId[id]
        val fresh = Article(
            id = id,
            feedId = feedId,
            title = p.title,
            link = p.link,
            // Undated items keep feed order by stepping back a second each.
            date = if (p.date > 0) p.date else old?.date ?: (now - index * 1000L),
            author = p.author,
            contentHtml = content,
            snippet = htmlToText(content).take(240),
            thumbnail = p.thumbnail,
            read = old?.read ?: false,
            starred = old?.starred ?: false,
            boards = old?.boards ?: emptyList(),
            highlights = old?.highlights ?: emptyList(),
        )
        if (old == null) added++
        byId[id] = fresh
    }
    // Keep the newest [maxPerFeed] per feed, but never drop unread or saved ones.
    val mine = byId.values.filter { it.feedId == feedId }.sortedByDescending { it.date }
    mine.drop(maxPerFeed).forEach { if (it.read && !it.kept) byId.remove(it.id) }
    return byId.values.toList() to added
}
