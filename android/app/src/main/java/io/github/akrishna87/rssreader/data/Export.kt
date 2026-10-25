package io.github.akrishna87.rssreader.data

/** Highlights, notes and saved articles as Markdown, for Notion, Obsidian, Evernote and the like. */
object Export {
    fun markdown(title: String, articles: List<Article>, feedName: (String) -> String): String {
        val sb = StringBuilder("# ").append(title).append("\n")
        articles.forEach { a ->
            sb.append("\n## ").append(if (a.link.isNotEmpty()) "[${a.title.replace("]", "\\]")}](${a.link})" else a.title).append("\n")
            val meta = listOf(feedName(a.feedId), a.author.takeIf { it.isNotBlank() }).filterNotNull().filter { it.isNotBlank() }.joinToString(" · ")
            if (meta.isNotEmpty()) sb.append("*").append(meta).append("*\n")
            if (a.boards.isNotEmpty()) sb.append("Boards: ").append(a.boards.joinToString(", ")).append("\n")
            a.highlights.sortedBy { it.time }.forEach { h ->
                sb.append("\n").append(h.text.lines().joinToString("\n") { "> $it" }).append("\n")
                if (h.note.isNotEmpty()) sb.append("\n**Note:** ").append(h.note).append("\n")
            }
            if (a.highlights.isEmpty() && a.snippet.isNotEmpty()) sb.append("\n").append(a.snippet).append("\n")
        }
        return sb.toString()
    }
}
