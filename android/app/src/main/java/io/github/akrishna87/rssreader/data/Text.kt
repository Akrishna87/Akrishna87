package io.github.akrishna87.rssreader.data

import java.net.URI

private val TAG = Regex("<[^>]*>")
private val SCRIPT_STYLE = Regex("(?is)<(script|style)\\b.*?</\\1>")
private val SPACES = Regex("\\s+")
private val ENTITY = Regex("&(#x[0-9a-fA-F]+|#[0-9]+|[a-zA-Z]+);")
private val NAMED = mapOf(
    "amp" to "&", "lt" to "<", "gt" to ">", "quot" to "\"", "apos" to "'", "nbsp" to " ",
    "hellip" to "…", "mdash" to "—", "ndash" to "–", "rsquo" to "’", "lsquo" to "‘",
    "rdquo" to "”", "ldquo" to "“", "copy" to "©", "reg" to "®", "trade" to "™",
)

fun decodeEntities(s: String): String = ENTITY.replace(s) { m ->
    val e = m.groupValues[1]
    when {
        e.startsWith("#x") || e.startsWith("#X") -> e.substring(2).toIntOrNull(16)?.let { String(Character.toChars(it)) }
        e.startsWith("#") -> e.substring(1).toIntOrNull()?.let { String(Character.toChars(it)) }
        else -> NAMED[e]
    } ?: m.value
}

/** HTML → plain text, collapsed to one line. */
fun htmlToText(html: String): String =
    decodeEntities(TAG.replace(SCRIPT_STYLE.replace(html, " "), " ")).replace(SPACES, " ").trim()

/** Resolve [href] against [base]; null if it isn't an http(s) URL. */
fun resolveUrl(base: String, href: String?): String? {
    val h = href?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return try {
        val u = if (base.isNotEmpty()) URI(base).resolve(URI(h.replace(" ", "%20"))) else URI(h)
        val s = u.toString()
        if (s.startsWith("http://") || s.startsWith("https://")) s else null
    } catch (e: Exception) {
        null
    }
}

/** Accepts "example.com", "feed://…", etc. Returns a full https URL or null. */
fun normalizeUrl(raw: String): String? {
    var s = raw.trim()
    if (s.isEmpty()) return null
    if (s.startsWith("feed://", true)) s = "https://" + s.substring(7)
    else if (s.startsWith("feed:", true)) s = s.substring(5)
    if (!s.startsWith("http://", true) && !s.startsWith("https://", true)) s = "https://$s"
    return try {
        val u = URI(s)
        if (u.host.isNullOrEmpty()) null else u.toString()
    } catch (e: Exception) {
        null
    }
}

/** Turn a YouTube / Reddit / GitHub page link into its feed URL, if it's one of those. */
fun rewriteKnownUrl(url: String): String {
    val u = try { URI(url) } catch (e: Exception) { return url }
    val host = (u.host ?: return url).removePrefix("www.").removePrefix("old.").removePrefix("m.")
    val path = (u.path ?: "").trimEnd('/')
    Regex("^/(r|u|user)/([\\w-]+)$", RegexOption.IGNORE_CASE).find(path)?.takeIf { host == "reddit.com" }?.let {
        val kind = if (it.groupValues[1].equals("r", true)) "r" else "user"
        return "https://www.reddit.com/$kind/${it.groupValues[2]}/.rss"
    }
    if (host == "github.com" && Regex("^/[\\w.-]+/[\\w.-]+$").matches(path)) return "https://github.com$path/releases.atom"
    if (host == "youtube.com") {
        Regex("^/channel/(UC[\\w-]{22})$").find(path)?.let {
            return "https://www.youtube.com/feeds/videos.xml?channel_id=${it.groupValues[1]}"
        }
        if (path == "/playlist") Regex("(?:^|&)list=([\\w-]+)").find(u.rawQuery ?: "")?.let {
            return "https://www.youtube.com/feeds/videos.xml?playlist_id=${it.groupValues[1]}"
        }
    }
    if (host == "medium.com" && Regex("^/@[\\w.-]+$").matches(path)) return "https://medium.com/feed$path"
    if (host.endsWith(".substack.com") && path.isEmpty()) return "https://$host/feed"
    return url
}
