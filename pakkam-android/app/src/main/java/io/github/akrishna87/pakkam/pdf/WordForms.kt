package io.github.akrishna87.pakkam.pdf

/**
 * Turns a word as it appears in a book ("forgets", "running", "geese’s") into the headwords a
 * dictionary might list it under ("forget", "run", "goose"), using the same ending rules as
 * WordNet's own "morphy". Irregular forms (ran, geese) come from the dictionary itself.
 */
object WordForms {

    /** The word on its own: no surrounding punctuation or possessive ’s, quotes made plain. */
    fun clean(raw: String): String {
        var w = raw.trim().replace('’', '\'').replace('‘', '\'')
        w = w.trim { !it.isLetterOrDigit() }
        if (w.endsWith("'s", ignoreCase = true)) w = w.dropLast(2)
        if (w.endsWith("s'", ignoreCase = true)) w = w.dropLast(1)
        return w
    }

    private val RULES = listOf(
        // Nouns
        "ses" to "s", "xes" to "x", "zes" to "z", "ches" to "ch", "shes" to "sh", "men" to "man", "ies" to "y", "s" to "",
        // Verbs
        "es" to "e", "es" to "", "ed" to "e", "ed" to "", "ing" to "e", "ing" to "",
        // Adjectives
        "er" to "", "est" to "", "er" to "e", "est" to "e",
        // Adverbs made from adjectives
        "ily" to "y", "ly" to "",
    )

    /** Headwords to try, best first, starting with the word itself. */
    fun candidates(raw: String): List<String> {
        val w = clean(raw).lowercase()
        if (w.isEmpty()) return emptyList()
        val out = LinkedHashSet<String>()
        out += w
        for ((ending, replacement) in RULES) {
            if (w.length > ending.length + 1 && w.endsWith(ending)) {
                val base = w.dropLast(ending.length) + replacement
                out += base
                // "running" → "runn" → "run", "stopped" → "stopp" → "stop".
                if (replacement.isEmpty() && base.length >= 3 && base[base.length - 1] == base[base.length - 2] && base.last() !in "aeiouls") {
                    out += base.dropLast(1)
                }
            }
        }
        return out.toList()
    }
}
