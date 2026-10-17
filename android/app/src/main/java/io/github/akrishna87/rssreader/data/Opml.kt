package io.github.akrishna87.rssreader.data

import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.StringReader

object Opml {
    data class Entry(val url: String, val title: String, val folder: String = "")

    fun parse(xml: String): List<Entry> {
        val parser = XmlPullParserFactory.newInstance().newPullParser()
        parser.setInput(StringReader(xml.trimStart('\uFEFF', ' ', '\n', '\r', '\t')))
        val out = ArrayList<Entry>()
        // Outlines without an xmlUrl that contain feeds are folders.
        val stack = ArrayList<String?>()
        while (true) {
            val event = parser.next()
            if (event == XmlPullParser.END_DOCUMENT) break
            if (event == XmlPullParser.END_TAG && parser.name.equals("outline", true)) {
                if (stack.isNotEmpty()) stack.removeAt(stack.size - 1)
                continue
            }
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
            if (url == null) {
                stack.add(title.trim())
                continue
            }
            stack.add(null)
            val folder = stack.dropLast(1).lastOrNull { !it.isNullOrEmpty() } ?: ""
            normalizeUrl(url)?.let { out.add(Entry(it, title, folder)) }
        }
        return out
    }

    fun export(feeds: List<Feed>): String {
        fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
        fun line(f: Feed, pad: String) =
            "$pad<outline type=\"rss\" text=\"${esc(f.name)}\" title=\"${esc(f.name)}\" xmlUrl=\"${esc(f.url)}\"" +
                (if (f.siteUrl.isNotEmpty()) " htmlUrl=\"${esc(f.siteUrl)}\"" else "") + "/>"
        val folders = feeds.map { it.folder }.filter { it.isNotEmpty() }.distinct().sorted()
        val body = (folders.map { name ->
            "    <outline text=\"${esc(name)}\" title=\"${esc(name)}\">\n" +
                feeds.filter { it.folder == name }.joinToString("\n") { line(it, "      ") } + "\n    </outline>"
        } + feeds.filter { it.folder.isEmpty() }.map { line(it, "    ") }).joinToString("\n")
        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<opml version=\"2.0\">\n  <head><title>Reader subscriptions</title></head>\n  <body>\n$body\n  </body>\n</opml>\n"
    }
}
