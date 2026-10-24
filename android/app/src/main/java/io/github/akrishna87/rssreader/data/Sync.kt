package io.github.akrishna87.rssreader.data

import org.json.JSONArray
import org.json.JSONObject

/*
 * Cross-device sync. Every device keeps its own copy of the feeds and fetches its own articles; what syncs is
 * small: subscriptions (with folders, names, priority), per-article state (read, Read Later, boards,
 * highlights and notes), boards, and preferences. Each entry carries the time of its last change and the newest
 * change wins, so edits made offline on two devices merge cleanly.
 */

data class DFeed(
    val url: String, val title: String, val customTitle: String, val folder: String, val priority: Boolean,
    val ts: Long, val deleted: Boolean = false, val fullText: Boolean = false,
)

/** Enough about an article to show it on a device that hasn't fetched it. */
data class DMeta(val feedId: String, val title: String, val link: String, val date: Long, val author: String, val snippet: String, val thumb: String)

data class DArt(
    val read: Boolean, val starred: Boolean, val boards: List<String>, val highlights: List<Highlight>,
    val ts: Long, val meta: DMeta?,
)

data class DPrefs(val layout: String, val textSize: Int, val theme: String, val mute: List<String>, val highlight: List<String>, val ts: Long)

data class Doc(
    val feeds: Map<String, DFeed> = emptyMap(),
    val art: Map<String, DArt> = emptyMap(),
    /** Board name → time of last change; negative means deleted at that time. */
    val boards: Map<String, Long> = emptyMap(),
    val prefs: DPrefs? = null,
)

object SyncMerge {
    const val ARTICLE_MAX_AGE = 60L * 86_400_000
    const val ARTICLE_MAX_ENTRIES = 5000
    const val TOMBSTONE_MAX_AGE = 120L * 86_400_000

    class Result(val snapshot: Snapshot, val json: String, val needsPush: Boolean, val changedLocal: Boolean)

    /** Fold [remoteJson] (null = nothing stored yet) into [local]. */
    fun merge(local: Snapshot, remoteJson: String?, now: Long): Result {
        val remote = parse(remoteJson)

        // ---- feeds ----
        val feeds = local.feeds.associateBy { it.id }.toMutableMap()
        val tombs = local.deletedFeeds.toMutableMap()
        for ((id, r) in remote.feeds) {
            val lf = feeds[id]
            if (r.deleted) {
                if (lf != null && lf.ts <= r.ts) feeds.remove(id)
                if ((tombs[id] ?: 0) < r.ts && feeds[id] == null) tombs[id] = r.ts
            } else if (lf == null) {
                if ((tombs[id] ?: 0) >= r.ts) continue
                tombs.remove(id)
                feeds[id] = Feed(id, r.url, r.title, customTitle = r.customTitle, folder = r.folder, priority = r.priority, fullText = r.fullText, ts = r.ts)
            } else if (r.ts > lf.ts) {
                feeds[id] = lf.copy(
                    customTitle = r.customTitle, folder = r.folder, priority = r.priority, fullText = r.fullText, ts = r.ts,
                    title = r.title.ifEmpty { lf.title },
                )
            }
        }
        tombs.keys.removeAll { it in feeds || now - tombs.getValue(it) > TOMBSTONE_MAX_AGE }
        val articlesIn = local.articles.filter { it.feedId in feeds }

        // ---- boards ----
        val boardTs = local.boardTs.toMutableMap()
        val boardDel = local.deletedBoards.toMutableMap()
        val boardNames = local.boards.toMutableList()
        for ((name, signed) in remote.boards) {
            val rts = Math.abs(signed)
            val have = name in boardNames
            val lts = if (have) boardTs[name] ?: 1L else boardDel[name] ?: 0L
            if (rts <= lts) continue
            if (signed < 0) {
                boardNames.remove(name); boardTs.remove(name); boardDel[name] = rts
            } else {
                if (!have) boardNames.add(name)
                boardTs[name] = rts; boardDel.remove(name)
            }
        }
        boardDel.keys.removeAll { now - boardDel.getValue(it) > TOMBSTONE_MAX_AGE }

        // ---- articles ----
        val articles = articlesIn.associateBy { it.id }.toMutableMap()
        for ((id, r) in remote.art) {
            val la = articles[id]
            if (la != null) {
                if (r.ts > la.mod) {
                    articles[id] = la.copy(read = r.read, starred = r.starred, boards = r.boards, highlights = r.highlights, mod = r.ts)
                }
            } else if (r.meta != null && r.meta.feedId in feeds && (r.starred || r.boards.isNotEmpty() || r.highlights.isNotEmpty())) {
                val m = r.meta
                articles[id] = Article(
                    id, m.feedId, m.title, m.link, m.date, m.author, "<p>" + escape(m.snippet) + "</p>", m.snippet, m.thumb.ifEmpty { null },
                    read = r.read, starred = r.starred, boards = r.boards, highlights = r.highlights, mod = r.ts,
                )
            }
        }

        // ---- preferences ----
        var prefsTs = local.prefsTs
        var mute = local.mute
        var highlight = local.highlight
        var settings = local.settings
        remote.prefs?.let { p ->
            if (p.ts > prefsTs) {
                mute = p.mute; highlight = p.highlight; prefsTs = p.ts
                // Refresh, notification and offline choices belong to each device, so they stay as they are.
                settings = local.settings.copy(layout = p.layout, textSize = p.textSize.coerceIn(14, 26), theme = p.theme)
            }
        }

        val snapshot = local.copy(
            feeds = feeds.values.toList(), articles = articles.values.toList(), boards = boardNames,
            mute = mute, highlight = highlight, settings = settings,
            deletedFeeds = tombs, boardTs = boardTs, deletedBoards = boardDel, prefsTs = prefsTs,
        )

        // ---- what the server should hold: remote entries we have nothing newer for, plus ours ----
        val docFeeds = HashMap<String, DFeed>()
        feeds.values.forEach { f -> docFeeds[f.id] = DFeed(f.url, f.title, f.customTitle, f.folder, f.priority, f.ts, fullText = f.fullText) }
        tombs.forEach { (id, ts) -> docFeeds[id] = DFeed("", "", "", "", false, ts, deleted = true) }
        remote.feeds.forEach { (id, r) -> if (r.deleted && id !in docFeeds && now - r.ts <= TOMBSTONE_MAX_AGE) docFeeds[id] = r }

        val docArt = HashMap<String, DArt>()
        remote.art.forEach { (id, r) -> if (id !in articles && now - r.ts <= ARTICLE_MAX_AGE) docArt[id] = r }
        articles.values.filter { it.mod > 0 && (it.kept || now - it.mod <= ARTICLE_MAX_AGE) }.forEach { a ->
            docArt[a.id] = DArt(
                a.read, a.starred, a.boards, a.highlights.sortedBy { it.time }, a.mod,
                if (a.kept) DMeta(a.feedId, a.title, a.link, a.date, a.author, a.snippet, a.thumbnail ?: "") else null,
            )
        }
        val pruned = if (docArt.size > ARTICLE_MAX_ENTRIES) {
            docArt.entries.sortedWith(compareByDescending<Map.Entry<String, DArt>> { it.value.meta != null }.thenByDescending { it.value.ts })
                .take(ARTICLE_MAX_ENTRIES).associate { it.key to it.value }
        } else docArt

        val docBoards = HashMap<String, Long>()
        boardNames.forEach { docBoards[it] = boardTs[it] ?: 1L }
        boardDel.forEach { (n, ts) -> docBoards[n] = -ts }

        val doc = Doc(
            docFeeds, pruned, docBoards,
            if (prefsTs > 0) DPrefs(settings.layout, settings.textSize, settings.theme, mute, highlight, prefsTs) else null,
        )
        val json = encode(doc)
        return Result(snapshot, json, needsPush = json != encode(remote), changedLocal = snapshot != local)
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;")

    // ---- JSON (deterministic: keys are sorted so equal docs encode equally) ----

    fun encode(d: Doc): String {
        val o = JSONObject()
        o.put("v", 1)
        o.put("feeds", JSONObject().also { m ->
            d.feeds.toSortedMap().forEach { (id, f) ->
                m.put(id, if (f.deleted) JSONObject().put("deleted", true).put("ts", f.ts)
                else JSONObject().put("url", f.url).put("title", f.title).put("customTitle", f.customTitle)
                    .put("folder", f.folder).put("priority", f.priority).put("fullText", f.fullText).put("ts", f.ts))
            }
        })
        o.put("art", JSONObject().also { m ->
            d.art.toSortedMap().forEach { (id, a) ->
                val e = JSONObject().put("r", a.read).put("s", a.starred).put("ts", a.ts)
                    .put("b", JSONArray(a.boards.sorted()))
                    .put("h", JSONArray().also { h -> a.highlights.forEach { h.put(JSONObject().put("t", it.text).put("n", it.note).put("ts", it.time)) } })
                a.meta?.let { m2 ->
                    e.put("m", JSONObject().put("f", m2.feedId).put("t", m2.title).put("l", m2.link).put("d", m2.date)
                        .put("a", m2.author).put("sn", m2.snippet.take(240)).put("th", m2.thumb))
                }
                m.put(id, e)
            }
        })
        o.put("boards", JSONObject().also { m -> d.boards.toSortedMap().forEach { (n, ts) -> m.put(n, ts) } })
        d.prefs?.let { p ->
            o.put("prefs", JSONObject().put("layout", p.layout).put("textSize", p.textSize).put("theme", p.theme)
                .put("mute", JSONArray(p.mute)).put("highlight", JSONArray(p.highlight)).put("ts", p.ts))
        }
        return o.toString()
    }

    fun parse(json: String?): Doc {
        if (json.isNullOrBlank()) return Doc()
        return try {
            val o = JSONObject(json)
            val feeds = HashMap<String, DFeed>()
            o.optJSONObject("feeds")?.let { m ->
                m.keys().forEach { id ->
                    val f = m.optJSONObject(id) ?: return@forEach
                    val ts = f.optLong("ts")
                    if (ts <= 0) return@forEach
                    if (f.optBoolean("deleted")) feeds[id] = DFeed("", "", "", "", false, ts, deleted = true)
                    else if (f.optString("url").isNotEmpty()) feeds[id] = DFeed(
                        f.getString("url"), f.optString("title"), f.optString("customTitle"), f.optString("folder"), f.optBoolean("priority"), ts, fullText = f.optBoolean("fullText"),
                    )
                }
            }
            val art = HashMap<String, DArt>()
            o.optJSONObject("art")?.let { m ->
                m.keys().forEach { id ->
                    val a = m.optJSONObject(id) ?: return@forEach
                    val ts = a.optLong("ts")
                    if (ts <= 0) return@forEach
                    val boards = a.optJSONArray("b")?.let { b -> (0 until b.length()).mapNotNull { b.optString(it).ifEmpty { null } } } ?: emptyList()
                    val hl = a.optJSONArray("h")?.let { h ->
                        (0 until h.length()).mapNotNull { i ->
                            h.optJSONObject(i)?.let { x -> Highlight(x.optString("t"), x.optString("n"), x.optLong("ts")) }?.takeIf { it.text.isNotEmpty() }
                        }
                    } ?: emptyList()
                    val meta = a.optJSONObject("m")?.let { x ->
                        DMeta(x.optString("f"), x.optString("t"), x.optString("l"), x.optLong("d"), x.optString("a"), x.optString("sn"), x.optString("th"))
                    }
                    art[id] = DArt(a.optBoolean("r"), a.optBoolean("s"), boards, hl, ts, meta)
                }
            }
            val boards = HashMap<String, Long>()
            o.optJSONObject("boards")?.let { m -> m.keys().forEach { n -> m.optLong(n).takeIf { it != 0L }?.let { boards[n] = it } } }
            val prefs = o.optJSONObject("prefs")?.let { p ->
                fun list(k: String) = p.optJSONArray(k)?.let { a -> (0 until a.length()).mapNotNull { a.optString(it).ifEmpty { null } } } ?: emptyList()
                DPrefs(p.optString("layout", "magazine"), p.optInt("textSize", 18), p.optString("theme", "system"), list("mute"), list("highlight"), p.optLong("ts"))
            }?.takeIf { it.ts > 0 }
            Doc(feeds, art, boards, prefs)
        } catch (e: Exception) {
            Doc()
        }
    }
}
