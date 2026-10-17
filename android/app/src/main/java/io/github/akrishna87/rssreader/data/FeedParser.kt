package io.github.akrishna87.rssreader.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader
import java.time.LocalDate
import java.time.OffsetDateTime
import java.time.ZoneOffset
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter

class NotAFeedException : Exception("Not an RSS or Atom feed")

/** Parses RSS 2.0, RSS 1.0 (RDF) and Atom. Throws [NotAFeedException] for anything else. */
object FeedParser {
    private const val NS_ATOM = "http://www.w3.org/2005/Atom"
    private const val NS_CONTENT = "http://purl.org/rss/1.0/modules/content/"
    private const val NS_DC = "http://purl.org/dc/elements/1.1/"
    private const val NS_MEDIA = "http://search.yahoo.com/mrss/"
    private const val NS_ITUNES = "http://www.itunes.com/dtds/podcast-1.0.dtd"
    private val IMG_SRC = Regex("<img[^>]+?src\\s*=\\s*[\"']([^\"']+)[\"']", RegexOption.IGNORE_CASE)

    fun parse(xml: String, feedUrl: String): ParsedFeed {
        val parser = XmlPullParserFactory.newInstance().apply { isNamespaceAware = true }.newPullParser()
        parser.setInput(StringReader(xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t')))

        // Anything that isn't well-formed XML up to its first element (e.g. an HTML page) isn't a feed.
        var event = try {
            var e = parser.eventType
            while (e != XmlPullParser.START_TAG && e != XmlPullParser.END_DOCUMENT) e = parser.next()
            e
        } catch (e: Exception) {
            throw NotAFeedException()
        }
        if (event != XmlPullParser.START_TAG) throw NotAFeedException()
        val root = parser.name.lowercase()
        if (root != "rss" && root != "rdf" && root != "feed") throw NotAFeedException()
        val atom = root == "feed"

        var feedTitle = ""
        var siteUrl = ""
        val items = ArrayList<ParsedItem>()
        var item: ItemBuilder? = null
        var itemDepth = -1

        while (true) {
            event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG) {
                if (item != null && parser.depth == itemDepth) {
                    items.add(item.build(feedUrl))
                    item = null
                }
                continue
            }
            if (event != XmlPullParser.START_TAG) continue

            val name = parser.name
            val ns = parser.namespace ?: ""
            val plain = ns.isEmpty() || ns == NS_ATOM || ns.startsWith("http://purl.org/rss/1.0") && ns != NS_CONTENT

            if (item == null) {
                when {
                    (name == "item" || name == "entry") && plain -> { item = ItemBuilder(); itemDepth = parser.depth }
                    name == "title" && plain && feedTitle.isEmpty() -> feedTitle = htmlToText(readContent(parser))
                    name == "link" && plain && siteUrl.isEmpty() -> {
                        val href = parser.getAttributeValue(null, "href")
                        if (href != null) {
                            val rel = parser.getAttributeValue(null, "rel")
                            if (rel == null || rel == "alternate") siteUrl = resolveUrl(feedUrl, href) ?: ""
                        } else if (!atom) {
                            siteUrl = resolveUrl(feedUrl, readContent(parser)) ?: ""
                        }
                    }
                }
                continue
            }

            val it = item
            when {
                ns == NS_MEDIA && name == "thumbnail" -> it.thumb = it.thumb ?: parser.getAttributeValue(null, "url")
                ns == NS_MEDIA && name == "content" -> {
                    val type = parser.getAttributeValue(null, "type") ?: ""
                    val medium = parser.getAttributeValue(null, "medium") ?: ""
                    if (medium == "image" || type.startsWith("image/")) it.thumb = it.thumb ?: parser.getAttributeValue(null, "url")
                }
                ns == NS_MEDIA && name == "description" -> if (it.content.isEmpty()) {
                    it.content = escape(readContent(parser)).replace("\n", "<br>")
                }
                ns == NS_ITUNES && name == "image" -> it.thumb = it.thumb ?: parser.getAttributeValue(null, "href")
                name == "enclosure" -> {
                    val type = parser.getAttributeValue(null, "type") ?: ""
                    if (type.startsWith("image/")) it.thumb = it.thumb ?: parser.getAttributeValue(null, "url")
                }
                ns == NS_CONTENT && name == "encoded" -> it.content = readContent(parser)
                ns == NS_DC && name == "creator" -> it.author = it.author.ifEmpty { htmlToText(readContent(parser)) }
                ns == NS_DC && name == "date" -> it.date = it.date.ifEmpty { readContent(parser) }
                !plain -> {}
                name == "title" -> it.title = htmlToText(readContent(parser))
                name == "link" -> {
                    val href = parser.getAttributeValue(null, "href")
                    if (href != null) {
                        val rel = parser.getAttributeValue(null, "rel")
                        if ((rel == null || rel == "alternate") && it.link.isEmpty()) it.link = href
                    } else if (it.link.isEmpty()) {
                        it.link = readContent(parser).trim()
                    }
                }
                name == "guid" -> {
                    val permalink = parser.getAttributeValue(null, "isPermaLink") != "false"
                    it.guid = readContent(parser).trim()
                    if (permalink && it.link.isEmpty() && it.guid.startsWith("http")) it.link = it.guid
                }
                name == "id" -> it.guid = readContent(parser).trim()
                name == "pubDate" || name == "published" -> it.date = readContent(parser)
                name == "updated" -> it.date = it.date.ifEmpty { readContent(parser) }
                name == "description" || name == "summary" -> {
                    val c = readContent(parser)
                    if (it.content.isEmpty()) it.content = c
                }
                name == "content" -> it.content = readContent(parser)
                name == "name" && it.author.isEmpty() -> it.author = htmlToText(readContent(parser))
                name == "author" && !atom -> it.author = it.author.ifEmpty { htmlToText(readContent(parser)) }
            }
        }
        return ParsedFeed(feedTitle, siteUrl, items)
    }

    /**
     * Reads everything inside the current element. Text at the top level is
     * returned as-is (RSS/Atom carry escaped HTML there); nested elements
     * (Atom type="xhtml") are re-serialised as HTML.
     */
    private fun readContent(parser: XmlPullParser): String {
        val startDepth = parser.depth
        val sb = StringBuilder()
        while (true) {
            when (parser.next()) {
                XmlPullParser.TEXT -> sb.append(if (parser.depth > startDepth) escape(parser.text) else parser.text)
                XmlPullParser.START_TAG -> {
                    sb.append('<').append(parser.name)
                    for (i in 0 until parser.attributeCount) {
                        sb.append(' ').append(parser.getAttributeName(i)).append("=\"")
                            .append(escape(parser.getAttributeValue(i))).append('"')
                    }
                    sb.append('>')
                }
                XmlPullParser.END_TAG -> {
                    if (parser.depth == startDepth) return sb.toString()
                    sb.append("</").append(parser.name).append('>')
                }
                XmlPullParser.END_DOCUMENT -> return sb.toString()
            }
        }
    }

    private fun escape(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")

    private class ItemBuilder {
        var guid = ""; var title = ""; var link = ""; var date = ""; var author = ""; var content = ""
        var thumb: String? = null

        fun build(feedUrl: String): ParsedItem {
            val base = resolveUrl(feedUrl, link) ?: feedUrl
            val img = thumb ?: IMG_SRC.find(content)?.groupValues?.get(1)?.replace("&amp;", "&")
            return ParsedItem(
                guid = guid,
                title = title.ifEmpty { htmlToText(content).take(90).ifEmpty { "(untitled)" } },
                link = resolveUrl(feedUrl, link) ?: "",
                date = parseDate(date),
                author = author,
                contentHtml = content,
                thumbnail = resolveUrl(base, img),
            )
        }
    }

    private val ZONES = mapOf(
        "UT" to "+0000", "UTC" to "+0000", "Z" to "+0000", "EST" to "-0500", "EDT" to "-0400",
        "CST" to "-0600", "CDT" to "-0500", "MST" to "-0700", "MDT" to "-0600", "PST" to "-0800",
        "PDT" to "-0700", "IST" to "+0530", "BST" to "+0100", "CET" to "+0100", "CEST" to "+0200",
    )

    /** RFC 822/1123 (RSS) and ISO 8601 (Atom) dates → epoch millis; 0 if unparseable. */
    fun parseDate(raw: String): Long {
        val s = raw.trim().replace(Regex("\\s+"), " ")
        if (s.isEmpty()) return 0
        try { return OffsetDateTime.parse(s).toInstant().toEpochMilli() } catch (_: Exception) {}
        try { return ZonedDateTime.parse(s).toInstant().toEpochMilli() } catch (_: Exception) {}
        var r = s
        Regex(" ([A-Z]{1,4})$").find(r)?.let { m -> ZONES[m.groupValues[1]]?.let { r = r.substring(0, m.range.first) + " " + it } }
        try { return ZonedDateTime.parse(r, DateTimeFormatter.RFC_1123_DATE_TIME).toInstant().toEpochMilli() } catch (_: Exception) {}
        // Some feeds use a full weekday name or omit the seconds.
        for (pattern in listOf("EEEE, d MMM yyyy HH:mm:ss Z", "EEE, d MMM yyyy HH:mm Z", "d MMM yyyy HH:mm:ss Z")) {
            try {
                return ZonedDateTime.parse(r, DateTimeFormatter.ofPattern(pattern, java.util.Locale.US)).toInstant().toEpochMilli()
            } catch (_: Exception) {}
        }
        try { return LocalDate.parse(s.take(10)).atStartOfDay().toInstant(ZoneOffset.UTC).toEpochMilli() } catch (_: Exception) {}
        return 0
    }
}
