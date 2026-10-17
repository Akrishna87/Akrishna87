package io.github.akrishna87.pakkam.pdf

import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * The characters of one page and the box of each, in page space.
 * [boxes] holds left, bottom, right, top for every character of [text].
 */
class PageTextLayout(val text: String, private val boxes: FloatArray) {
    val length get() = text.length

    fun box(i: Int): PdfRect? {
        if (i !in 0 until length || boxes.size < (i + 1) * 4) return null
        val l = boxes[i * 4]
        val b = boxes[i * 4 + 1]
        val r = boxes[i * 4 + 2]
        val t = boxes[i * 4 + 3]
        return if (r - l <= 0f && t - b <= 0f) null else PdfRect(l, b, r, t)
    }

    /** Where [query] occurs, ignoring case and treating any run of spaces or line breaks as one space. */
    fun find(query: String): List<Pair<Int, Int>> {
        val q = query.trim().replace(Regex("\\s+"), " ").map { it.lowercaseChar() }
        if (q.isEmpty() || text.isEmpty()) return emptyList()
        val t = CharArray(text.length) { i ->
            val c = text[i]
            if (c.isWhitespace() || c == '\u0000') ' ' else c.lowercaseChar()
        }
        val hits = ArrayList<Pair<Int, Int>>()
        var i = 0
        while (i < t.size) {
            val end = matchAt(t, i, q)
            if (end > 0) {
                hits += i to end
                i = end
            } else {
                i++
            }
        }
        return hits
    }

    private fun matchAt(t: CharArray, start: Int, q: List<Char>): Int {
        var ti = start
        var qi = 0
        while (qi < q.size) {
            if (ti >= t.size) return -1
            if (q[qi] == ' ') {
                if (t[ti] != ' ') {
                    // A line break inside a hyphenated word shows up as nothing at all.
                    qi++
                    continue
                }
                while (ti < t.size && t[ti] == ' ') ti++
                qi++
            } else {
                if (t[ti] != q[qi]) {
                    // PDFium marks soft hyphens at line ends; let "inter-\nnational" match "international".
                    if ((t[ti] == '-' || t[ti] == '­' || t[ti] == '\u0002') && ti + 1 < t.size && t[ti + 1] == ' ' && ti > start) {
                        ti++
                        while (ti < t.size && t[ti] == ' ') ti++
                        continue
                    }
                    return -1
                }
                ti++
                qi++
            }
        }
        return ti
    }

    /** The boxes covering characters [start, end), merged into one rectangle per line. */
    fun rectsFor(start: Int, end: Int): List<PdfRect> {
        val out = ArrayList<PdfRect>()
        var cur: PdfRect? = null
        for (i in max(0, start) until min(end, length)) {
            // Spaces often have boxes of a different height, which would split a line into words.
            if (text[i].isWhitespace()) continue
            val b = box(i) ?: continue
            val c = cur
            cur = if (c == null) {
                b
            } else if (sameLine(c, b)) {
                c.union(b)
            } else {
                out += c
                b
            }
        }
        cur?.let { out += it }
        return out
    }

    private fun sameLine(a: PdfRect, b: PdfRect): Boolean {
        val overlap = min(a.top, b.top) - max(a.bottom, b.bottom)
        val h = min(a.height, b.height).coerceAtLeast(0.1f)
        return overlap > h * 0.5f && b.left >= a.left - h
    }

    /** The character under a point, or the nearest one within [tolerance] points; -1 when there is none. */
    fun charAt(x: Float, y: Float, tolerance: Float = 6f): Int {
        var best = -1
        var bestDist = Float.MAX_VALUE
        for (i in 0 until length) {
            val b = box(i) ?: continue
            if (b.contains(x, y)) return i
            val d = distance(b, x, y)
            if (d < bestDist) {
                bestDist = d
                best = i
            }
        }
        return if (bestDist <= tolerance) best else -1
    }

    /** The character closest to a point, however far. Used while dragging a selection. */
    fun nearestChar(x: Float, y: Float): Int = charAt(x, y, Float.MAX_VALUE)

    /** The word (letters and digits) around character [i], as [start, end). */
    fun wordAt(i: Int): Pair<Int, Int> {
        if (i !in 0 until length) return i to i
        fun isWord(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '_'
        if (!isWord(text[i])) return i to i + 1
        var s = i
        var e = i + 1
        while (s > 0 && isWord(text[s - 1])) s--
        while (e < length && isWord(text[e])) e++
        return s to e
    }

    fun textOf(start: Int, end: Int): String {
        if (start >= end) return ""
        return text.substring(max(0, start), min(end, length))
            .replace("\r\n", "\n")
            .replace('\r', '\n')
            .replace("\u0002", "")
            .replace("\u0000", "")
    }

    private fun distance(b: PdfRect, x: Float, y: Float): Float {
        val dx = if (x < b.left) b.left - x else if (x > b.right) x - b.right else 0f
        // Lines are close together; being off vertically counts for more than being off sideways.
        val dy = if (y < b.bottom) b.bottom - y else if (y > b.top) y - b.top else 0f
        return sqrt(dx * dx + 4 * dy * dy)
    }
}
