package io.github.akrishna87.rssreader.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object Opml {
    data class Entry(val url: String, val title: String)

    fun parse(xml: String): List<Entry> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t')))
        val out = ArrayList<Entry>()
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event != XmlPullParser.START_TAG || !parser.name.equals("outline", true)) continue
            var url: String? = null
            var title = ""
            for (i in 0 until parser.attributeCount) {
                when (parser.getAttributeName(i).lowercase()) {
                    "xmlurl" -> url = parser.getAttributeValue(i)
                    "title" -> title = parser.getAttributeValue(i)
                    "text" -> if (title.isEmpty()) title = parser.getAttributeValue(i)
                }
            }
            normalizeUrl(url ?: continue)?.let { out.add(Entry(it, title)) }
        }
        return out
    }

    fun export(feeds: List<Feed>): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        val body = feeds.joinToString("\n") { f ->
            "    <outline type=\"rss\" text=\"${esc(f.title)}\" title=\"${esc(f.title)}\" xmlUrl=\"${esc(f.url)}\"" +
                (if (f.siteUrl.isNotEmpty()) " htmlUrl=\"${esc(f.siteUrl)}\"" else "") + "/>"
        }
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n  <head><title>Reader subscriptions</title></head>\n  <body>\n$body\n  </body>\n</opml>\n"
    }
}
