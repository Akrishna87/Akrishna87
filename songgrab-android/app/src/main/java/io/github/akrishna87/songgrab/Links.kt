package io.github.akrishna87.songgrab

import java.net.URI

/** Finds the link to save in whatever was pasted or shared, and tidies YouTube links. */
object Links {
    private val urlPattern = Regex("""https?://[^\s<>"']+""", RegexOption.IGNORE_CASE)
    private val videoId = Regex("^[A-Za-z0-9_-]{11}$")

    /**
     * The first YouTube video link in [text] as a plain watch link, or else the first web link
     * at all (yt-dlp handles many other sites too). Null if there's no link.
     */
    fun find(text: String?): String? {
        if (text.isNullOrBlank()) return null
        val urls = urlPattern.findAll(text).map { it.value.trimEnd('.', ',', ')', ']', '!', '?', ';') }.toList()
        return urls.firstNotNullOfOrNull { youtube(it) } ?: urls.firstOrNull()
    }

    /**
     * `https://www.youtube.com/watch?v=<id>` for any link to a single YouTube video (watch,
     * youtu.be, Shorts, YouTube Music, live, embed), else null. Playlist and timestamp parts are
     * dropped so only the one song is saved.
     */
    fun youtube(url: String): String? {
        val uri = try {
            URI(url.trim())
        } catch (e: Exception) {
            return null
        }
        val host = uri.host?.lowercase()?.removePrefix("www.")?.removePrefix("m.") ?: return null
        val path = uri.rawPath.orEmpty()
        val id = when (host) {
            "youtu.be" -> path.removePrefix("/").substringBefore('/')
            "youtube.com", "music.youtube.com", "youtube-nocookie.com" -> when {
                path == "/watch" || path == "/watch/" -> queryParam(uri.rawQuery, "v")
                path.startsWith("/shorts/") || path.startsWith("/live/") ||
                    path.startsWith("/embed/") || path.startsWith("/v/") -> path.split('/').getOrNull(2)
                else -> null
            }
            else -> null
        }
        return id?.takeIf { videoId.matches(it) }?.let { "https://www.youtube.com/watch?v=$it" }
    }

    private fun queryParam(query: String?, name: String): String? =
        query?.split('&')?.firstNotNullOfOrNull { part ->
            val (key, value) = part.split('=', limit = 2).let { it[0] to it.getOrNull(1) }
            value.takeIf { key == name }
        }
}
