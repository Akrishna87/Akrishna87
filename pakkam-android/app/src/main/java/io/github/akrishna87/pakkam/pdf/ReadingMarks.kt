package io.github.akrishna87.pakkam.pdf

import kotlin.math.max
import kotlin.math.min

/** Where a highlight shown in Reading mode comes from. */
sealed interface MarkOrigin {
    /** A highlight already in the file. */
    data class Saved(val annot: AnnotInfo) : MarkOrigin

    /** A highlight added since the file was last saved. */
    data class Pending(val edit: MarkupEdit) : MarkOrigin
}

/** A highlight as Reading mode shows it: characters [start, end) of the running text. */
data class ReadMark(val start: Int, val end: Int, val color: Int, val note: String, val origins: List<MarkOrigin>)

/**
 * Ties the running text of Reading mode to the PDF pages: which characters a highlight on a page
 * covers, and which boxes on the pages to mark for a stretch of text. Highlights are kept as
 * ordinary PDF highlight annotations, so they show in page view and in other PDF apps too.
 *
 * [blockStarts] is where each block begins in the running text (blocks are separated by line breaks).
 */
class ReadingText(val blocks: List<Block>, val blockStarts: IntArray) {
    private val byPage: Map<Int, IntArray> = HashMap<Int, MutableList<Int>>().also { map ->
        for ((bi, b) in blocks.withIndex()) {
            val spots = b.spots ?: continue
            for (page in spots.runs(b.text.length).map { it.first }.distinct()) map.getOrPut(page) { ArrayList() } += bi
        }
    }.mapValues { it.value.toIntArray() }

    /** The block a position of the running text falls in. */
    fun blockAt(offset: Int): Int {
        var lo = 0
        var hi = blockStarts.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (blockStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo.coerceAtLeast(0)
    }

    /** The text of [start, end), with a line break between blocks. */
    fun textOf(start: Int, end: Int): String {
        if (end <= start || blocks.isEmpty()) return ""
        val parts = ArrayList<String>()
        for (bi in blockAt(start)..blockAt(end - 1)) {
            val base = blockStarts[bi]
            val text = blocks[bi].text
            val from = (start - base).coerceIn(0, text.length)
            val to = (end - base).coerceIn(0, text.length)
            if (to > from) parts += text.substring(from, to)
        }
        return parts.joinToString("\n")
    }

    /** The stretches of running text whose letters lie inside [quads] on PDF page [page]. */
    fun locate(page: Int, quads: List<PdfRect>): List<Pair<Int, Int>> {
        if (quads.isEmpty()) return emptyList()
        val boxes = quads.map { it.inset(1f) }
        val out = ArrayList<Pair<Int, Int>>()
        for (bi in byPage[page] ?: return emptyList()) {
            val b = blocks[bi]
            val spots = b.spots ?: continue
            val base = blockStarts[bi]
            var first = -1
            var last = -1
            for ((p, rs, re) in spots.runs(b.text.length)) {
                if (p != page) continue
                for (i in rs until re) {
                    val box = spots.box(i) ?: continue
                    val cx = (box.left + box.right) / 2
                    val cy = (box.bottom + box.top) / 2
                    if (boxes.none { it.contains(cx, cy) }) continue
                    if (first >= 0 && blankBetween(b, spots, last + 1, i)) {
                        last = i
                    } else {
                        if (first >= 0) out += base + first to base + last + 1
                        first = i
                        last = i
                    }
                }
            }
            if (first >= 0) out += base + first to base + last + 1
        }
        return out.sortedBy { it.first }
    }

    private fun blankBetween(b: Block, spots: TextSpots, from: Int, to: Int): Boolean =
        (from until to).all { b.text[it].isWhitespace() || spots.box(it) == null }

    /** The boxes to mark for [start, end): for each PDF page in turn, one box per line. */
    fun boxes(start: Int, end: Int): List<Pair<Int, List<PdfRect>>> {
        if (end <= start || blocks.isEmpty()) return emptyList()
        val out = ArrayList<Pair<Int, MutableList<PdfRect>>>()
        var cur: PdfRect? = null
        var curPage = -1
        fun close() {
            val c = cur ?: return
            if (out.lastOrNull()?.first == curPage) out.last().second += c else out += curPage to mutableListOf(c)
            cur = null
        }
        for (bi in blockAt(start)..blockAt(end - 1)) {
            val b = blocks[bi]
            val spots = b.spots ?: continue
            val base = blockStarts[bi]
            for (i in max(start - base, 0) until min(end - base, b.text.length)) {
                if (b.text[i].isWhitespace()) continue
                val box = spots.box(i) ?: continue
                val page = spots.pageOf(i)
                if (page != curPage) {
                    close()
                    curPage = page
                }
                val c = cur
                cur = when {
                    c == null -> box
                    sameLine(c, box) -> c.union(box)
                    else -> {
                        close()
                        box
                    }
                }
            }
        }
        close()
        return out
    }

    /** Every highlight to show: those in the file (less any being removed) and those added since. */
    fun marks(annots: List<AnnotInfo>, edits: List<Edit>): List<ReadMark> {
        val gone = edits.filterIsInstance<DeleteAnnotEdit>().map { it.annot }.toSet()
        val found = ArrayList<ReadMark>()
        for (a in annots) {
            if (a.subtype != "Highlight" || a in gone) continue
            for ((s, e) in locate(a.page, a.quads.ifEmpty { listOf(a.rect) })) {
                found += ReadMark(s, e, a.color ?: DEFAULT_COLOR, noteFrom(a.contents, textOf(s, e)), listOf(MarkOrigin.Saved(a)))
            }
        }
        for (e in edits) {
            if (e !is MarkupEdit || e.kind != MarkupKind.HIGHLIGHT) continue
            for ((s, t) in locate(e.page, e.rects)) found += ReadMark(s, t, e.color, e.note.trim(), listOf(MarkOrigin.Pending(e)))
        }
        found.sortWith(compareBy({ it.start }, { it.end }))
        // A highlight that runs over a page break is two annotations, one on each page: show it as one.
        val out = ArrayList<ReadMark>()
        for (m in found) {
            val prev = out.lastOrNull()
            if (prev != null && m.color == prev.color && m.start >= prev.start && m.start - prev.end <= 3 && textOf(prev.end, m.start).isBlank()) {
                out[out.lastIndex] = ReadMark(
                    prev.start,
                    max(prev.end, m.end),
                    prev.color,
                    listOf(prev.note, m.note).filter { it.isNotBlank() }.distinct().joinToString("\n"),
                    (prev.origins + m.origins).distinct(),
                )
            } else {
                out += m
            }
        }
        return out
    }

    companion object {
        const val DEFAULT_COLOR = 0xFFFFEB3B.toInt()

        /**
         * The reader's note on a highlight. Highlights made in page view keep the marked words as
         * their comment, and that isn't a note.
         */
        fun noteFrom(contents: String, marked: String): String {
            val c = contents.trim()
            if (c.isEmpty()) return ""
            fun letters(s: String) = s.filter { it.isLetterOrDigit() }.lowercase()
            val lc = letters(c)
            val lm = letters(marked)
            if (lc == lm) return ""
            if (lc.length >= 6 && lm.contains(lc)) return ""
            if (lm.length >= 6 && lc.contains(lm) && lc.length <= lm.length * 1.3) return ""
            return c
        }

        private fun sameLine(a: PdfRect, b: PdfRect): Boolean {
            val overlap = min(a.top, b.top) - max(a.bottom, b.bottom)
            val h = min(a.height, b.height).coerceAtLeast(0.1f)
            return overlap > h * 0.5f && b.left >= a.left - h
        }
    }
}
