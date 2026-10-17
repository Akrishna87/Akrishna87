package io.github.akrishna87.rssreader.data

/**
 * Keyword filters. "Mute" hides articles mentioning a word, "highlight" flags them.
 * Matching uses the title and opening text; whole words, not case-sensitive
 * (so "AI" doesn't match "said").
 */
class Filters(mute: List<String>, highlight: List<String>) {
    private val muteRes = mute.map { it to compile(it) }
    private val highlightRes = highlight.map { it to compile(it) }
    private val cache = HashMap<String, Pair<String?, String?>>()

    val isEmpty get() = muteRes.isEmpty() && highlightRes.isEmpty()

    /** The mute word this article matches, or null. Starred articles are never muted. */
    fun mutedBy(a: Article): String? = if (a.starred) null else match(a).first

    /** The highlight word this article matches, or null. */
    fun highlightedBy(a: Article): String? = match(a).second

    private fun match(a: Article): Pair<String?, String?> = cache.getOrPut(a.id + "|" + a.title.hashCode() + "|" + a.snippet.hashCode()) {
        val text = a.title + "\n" + a.snippet
        muteRes.firstOrNull { it.second.containsMatchIn(text) }?.first to
            highlightRes.firstOrNull { it.second.containsMatchIn(text) }?.first
    }

    companion object {
        fun compile(term: String): Regex {
            val t = term.trim()
            val body = Regex.escape(t).replace(" ", "\\E\\s+\\Q")
            val start = if (t.firstOrNull()?.isLetterOrDigit() == true) "\\b" else ""
            val end = if (t.lastOrNull()?.isLetterOrDigit() == true) "\\b" else ""
            return Regex(start + body + end, RegexOption.IGNORE_CASE)
        }

        /** Split "a, b ,c" into new terms not already in [existing] (case-insensitive). */
        fun addTerms(existing: List<String>, input: String): List<String> {
            val out = existing.toMutableList()
            input.split(",").map { it.trim() }.filter { it.isNotEmpty() }.forEach { t ->
                if (out.none { it.equals(t, ignoreCase = true) }) out.add(t)
            }
            return out
        }
    }
}
