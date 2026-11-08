package io.github.akrishna87.saveit

import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Threads posts. yt-dlp can't read Threads, so SaveIt opens the post's public page once, the way
 * a phone browser does, and looks for a video on it. If Threads shows one to signed-out visitors,
 * SaveIt hands yt-dlp an info file pointing at it, and yt-dlp downloads, converts and tags it as
 * for any other site. If not (Threads often asks visitors to sign in), the post can't be saved.
 */
object Threads {

    class NoVideoException(message: String) : IOException(message)

    /** What a post's page says about its video. */
    data class Post(val videoUrl: String, val caption: String?, val image: String?)

    private val postLink = Regex("""^https?://(?:www\.)?threads\.(?:net|com)/(@[\w.]+)/post/([\w-]+)""", RegexOption.IGNORE_CASE)

    /** The short links the Threads app's Share button gives, e.g. threads.com/share/BBOvTai8tl. */
    private val shareLink = Regex("""^https?://(?:www\.)?threads\.(?:net|com)/share/([\w-]+)""", RegexOption.IGNORE_CASE)

    /** A post link anywhere in a page (its canonical link, or JSON with escaped slashes). */
    private val postInPage = Regex("""threads\.(?:net|com)\\?/(@[\w.]+)\\?/post\\?/([\w-]+)""", RegexOption.IGNORE_CASE)

    private const val BROWSER =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/129.0 Mobile Safari/537.36"

    fun isPost(url: String): Boolean = postLink.containsMatchIn(url) || shareLink.containsMatchIn(url)

    /** "@user" and the post's code named in a page, for a share link's page. */
    fun postNamedIn(html: String): Pair<String, String>? = postInPage.find(html)?.let { it.groupValues[1] to it.groupValues[2] }

    /** "@user" and the post's code, from a post link. */
    fun parts(url: String): Pair<String, String>? = postLink.find(url)?.let { it.groupValues[1] to it.groupValues[2] }

    /**
     * Writes a yt-dlp info file for the post's video into [dir] and returns it. A short share link
     * is opened first to find the post it leads to: Threads redirects it to the post, or names
     * the post in the page it returns.
     */
    fun infoFile(url: String, dir: File): File {
        var sharePage: String? = null
        val (user, code) = parts(url) ?: run {
            if (!shareLink.containsMatchIn(url)) throw NoVideoException("That isn't a link to a Threads post.")
            val (finalUrl, html) = open(url)
            sharePage = html
            parts(finalUrl) ?: postNamedIn(html) ?: throw NoVideoException(
                "Couldn't find the post this Threads link leads to. In Threads, open the post, tap ⋯ → Copy link, and paste that link here.",
            )
        }
        val page = "https://www.threads.com/$user/post/$code"
        // The share link may have landed on the post already; otherwise open the post itself.
        val post = sharePage?.let(::parse) ?: parse(open(page).second) ?: throw NoVideoException(
            "Couldn't find a video in this Threads post. It may be a photo or text post, or Threads may only show it to people who are signed in.",
        )
        return File(dir, "threads-$code.info.json").apply { writeText(infoJson(post, user, code, page)) }
    }

    /** Opens a page as a phone browser does, following redirects: where it ended up, and the page. */
    private fun open(address: String): Pair<String, String> = try {
        fetch(address)
    } catch (e: IOException) {
        throw IOException("Couldn't open the Threads post. Check your connection and try again.", e)
    }

    private fun fetch(address: String): Pair<String, String> {
        val connection = URL(address).openConnection() as HttpURLConnection
        try {
            connection.instanceFollowRedirects = true
            connection.connectTimeout = 15_000
            connection.readTimeout = 20_000
            connection.setRequestProperty("User-Agent", BROWSER)
            if (connection.responseCode !in 200..299) throw IOException("HTTP ${connection.responseCode}")
            val html = connection.inputStream.bufferedReader().use { it.readText() }
            return connection.url.toString() to html
        } finally {
            connection.disconnect()
        }
    }

    private val videoVersions = Regex(""""video_versions"\s*:\s*\[\s*\{[^}]*?"url"\s*:\s*"((?:[^"\\]|\\.)+)"""")
    private val metaVideo = Regex("""<meta[^>]+property="og:video(?::secure_url|:url)?"[^>]+content="([^"]+)"""", RegexOption.IGNORE_CASE)
    private val videoTag = Regex("""<(?:video|source)\b[^>]*\ssrc="(https?://[^"]+)"""", RegexOption.IGNORE_CASE)
    private val caption = Regex(""""caption"\s*:\s*\{[^{}]*?"text"\s*:\s*"((?:[^"\\]|\\.)*)"""")
    private val metaDescription = Regex("""<meta[^>]+property="og:description"[^>]+content="([^"]*)"""", RegexOption.IGNORE_CASE)
    private val metaImage = Regex("""<meta[^>]+property="og:image"[^>]+content="([^"]+)"""", RegexOption.IGNORE_CASE)

    /** Finds the video, caption and preview image in a Threads page. Null if it shows no video. */
    fun parse(html: String): Post? {
        val video = videoVersions.find(html)?.groupValues?.get(1)?.let(::unescapeJson)
            ?: metaVideo.find(html)?.groupValues?.get(1)?.let(::unescapeHtml)
            ?: videoTag.find(html)?.groupValues?.get(1)?.let(::unescapeHtml)
            ?: return null
        if (!video.startsWith("https://")) return null
        val text = caption.find(html)?.groupValues?.get(1)?.let(::unescapeJson)
            ?: metaDescription.find(html)?.groupValues?.get(1)?.let(::unescapeHtml)
        val image = metaImage.find(html)?.groupValues?.get(1)?.let(::unescapeHtml)
        return Post(video, text?.trim()?.takeIf { it.isNotEmpty() }, image)
    }

    /** The song or video title: the caption's first line, shortened. */
    fun title(caption: String?, user: String): String {
        val line = caption?.lineSequence()?.map { it.trim() }?.firstOrNull { it.isNotEmpty() }
        if (line.isNullOrEmpty()) return "Threads video by $user"
        return if (line.length <= 80) line else line.take(79).trimEnd() + "…"
    }

    /** A yt-dlp info file: one MP4 at [Post.videoUrl], titled from the caption, by the user. */
    fun infoJson(post: Post, user: String, code: String, page: String): String = JSONObject()
        .put("id", code)
        .put("title", title(post.caption, user))
        .put("uploader", user)
        .put("url", post.videoUrl)
        .put("ext", "mp4")
        .put("webpage_url", page)
        .put("extractor", "generic")
        .put("extractor_key", "Generic")
        .put("_type", "video")
        .apply { post.image?.let { put("thumbnails", JSONArray().put(JSONObject().put("url", it).put("id", "0"))) } }
        .toString()

    private val unicodeEscape = Regex("""\\u([0-9a-fA-F]{4})""")

    fun unescapeJson(text: String): String = runCatching { JSONObject("{\"v\":\"$text\"}").getString("v") }
        .getOrElse { unicodeEscape.replace(text.replace("\\/", "/")) { it.groupValues[1].toInt(16).toChar().toString() } }

    fun unescapeHtml(text: String): String = text
        .replace("&amp;", "&").replace("&quot;", "\"").replace("&#039;", "'").replace("&#x27;", "'")
        .replace("&lt;", "<").replace("&gt;", ">")
}
