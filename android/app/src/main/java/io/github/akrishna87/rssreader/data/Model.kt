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
    /** Priority feeds get their own list and lead the Today view. */
    val priority: Boolean = false,
) {
    val name: String get() = customTitle.ifBlank { title }
}

/** A passage the reader selected in an article, with an optional note. */
data class Highlight(val text: String, val note: String = "", val time: Long = 0)

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
    /** Saved to Read Later. */
    val starred: Boolean = false,
    /** Names of the boards this article was saved to. */
    val boards: List<String> = emptyList(),
    val highlights: List<Highlight> = emptyList(),
) {
    /** Saved by the reader in some way, so it's never cleaned up. */
    val kept: Boolean get() = starred || boards.isNotEmpty() || highlights.isNotEmpty()
}

/** Reader preferences. [layout] is magazine, cards or title; [theme] is system, light or dark. */
data class Settings(val layout: String = "magazine", val textSize: Int = 18, val theme: String = "system")

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
