package io.github.akrishna87.rssreader.data

data class Feed(
    val id: String,
    val url: String,
    val title: String,
    val siteUrl: String = "",
    val lastFetched: Long = 0,
    val error: String? = null,
    /** Folder name; empty = not in a folder. */
    val folder: String = "",
    /** Name the user gave it; empty = use the feed's own title. */
    val customTitle: String = "",
) {
    val name: String get() = customTitle.ifBlank { title }
}

data class Article(
    val id: String,
    val feedId: String,
    val title: String,
    val link: String,
    val date: Long,
    val author: String,
    val contentHtml: String,
    val snippet: String,
    val thumbnail: String?,
    val read: Boolean = false,
    val starred: Boolean = false,
)

/** What a feed fetch produced, before it's merged into stored articles. */
data class ParsedFeed(val title: String, val siteUrl: String, val items: List<ParsedItem>)

data class ParsedItem(
    val guid: String,
    val title: String,
    val link: String,
    val date: Long,
    val author: String,
    val contentHtml: String,
    val thumbnail: String?,
)

/** Stable short id (same input → same id on every run). */
fun stableId(input: String): String {
    var h = 1125899906842597L
    for (c in input) h = 31 * h + c.code
    return java.lang.Long.toString(h and Long.MAX_VALUE, 36)
}
