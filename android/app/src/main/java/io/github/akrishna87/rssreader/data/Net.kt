package io.github.akrishna87.rssreader.data

import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL
import java.nio.charset.Charset

object Net {
    private const val MAX_BYTES = 8 * 1024 * 1024
    private const val USER_AGENT = "Mozilla/5.0 (Linux; Android) RSSReader/2.0 (+https://github.com/Akrishna87/Akrishna87)"

    /** GET [url] as text, following up to 6 redirects (including http↔https). */
    fun fetch(url: String): String {
        var current = url
        repeat(7) {
            val conn = URL(current).openConnection() as HttpURLConnection
            try {
                conn.instanceFollowRedirects = false
                conn.connectTimeout = 15_000
                conn.readTimeout = 20_000
                conn.setRequestProperty("User-Agent", USER_AGENT)
                conn.setRequestProperty(
                    "Accept",
                    "application/rss+xml, application/atom+xml, application/xml;q=0.9, text/xml;q=0.9, text/html;q=0.8, */*;q=0.5",
                )
                val code = conn.responseCode
                if (code in 300..399) {
                    val location = conn.getHeaderField("Location") ?: throw IOException("Redirect without a location")
                    current = URL(URL(current), location).toString()
                    return@repeat
                }
                if (code !in 200..299) throw IOException("The site answered HTTP $code")
                val bytes = conn.inputStream.use { input ->
                    val out = java.io.ByteArrayOutputStream()
                    val buf = ByteArray(16 * 1024)
                    while (true) {
                        val n = input.read(buf)
                        if (n < 0) break
                        out.write(buf, 0, n)
                        if (out.size() > MAX_BYTES) throw IOException("Response too large")
                    }
                    out.toByteArray()
                }
                return String(bytes, charsetOf(conn.contentType, bytes))
            } finally {
                conn.disconnect()
            }
        }
        throw IOException("Too many redirects")
    }

    private fun charsetOf(contentType: String?, bytes: ByteArray): Charset {
        val declared = contentType?.let { Regex("charset=\"?([\\w-]+)", RegexOption.IGNORE_CASE).find(it)?.groupValues?.get(1) }
            ?: Regex("encoding=[\"']([\\w-]+)[\"']").find(String(bytes, 0, minOf(bytes.size, 300), Charsets.ISO_8859_1))?.groupValues?.get(1)
            ?: Regex("<meta[^>]+charset=[\"']?([\\w-]+)", RegexOption.IGNORE_CASE)
                .find(String(bytes, 0, minOf(bytes.size, 4000), Charsets.ISO_8859_1))?.groupValues?.get(1)
        return try {
            if (declared != null) Charset.forName(declared) else Charsets.UTF_8
        } catch (e: Exception) {
            Charsets.UTF_8
        }
    }

    /** Find the feed a web page advertises with <link rel="alternate" type="application/rss+xml">. */
    fun discoverFeed(html: String, pageUrl: String): String? {
        val head = html.take(200_000)
        for (m in Regex("<link\\b[^>]*>", RegexOption.IGNORE_CASE).findAll(head)) {
            val tag = m.value
            val rel = attr(tag, "rel")?.lowercase() ?: continue
            val type = attr(tag, "type")?.lowercase() ?: ""
            if ("alternate" in rel && (type.contains("rss") || type.contains("atom") || type.contains("feed+") || type == "application/xml" || type == "text/xml")) {
                resolveUrl(pageUrl, attr(tag, "href")?.let(::decodeEntities))?.let { return it }
            }
        }
        return null
    }

    private fun attr(tag: String, name: String): String? =
        Regex("\\b$name\\s*=\\s*(\"([^\"]*)\"|'([^']*)'|([^\\s>]+))", RegexOption.IGNORE_CASE).find(tag)?.let { m ->
            m.groupValues[2].ifEmpty { m.groupValues[3].ifEmpty { m.groupValues[4] } }
        }

    /** Fetch [url]; if it's a web page rather than a feed, follow the feed it advertises. */
    fun loadFeed(url: String): Pair<String, ParsedFeed> {
        val text = fetch(url)
        try {
            return url to FeedParser.parse(text, url)
        } catch (e: Exception) {
            val discovered = discoverFeed(text, url)
            if (discovered != null && discovered != url) {
                return discovered to FeedParser.parse(fetch(discovered), discovered)
            }
            throw if (e is NotAFeedException) e else NotAFeedException()
        }
    }
}
