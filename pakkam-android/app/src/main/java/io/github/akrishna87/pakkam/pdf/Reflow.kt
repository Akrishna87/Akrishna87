package io.github.akrishna87.pakkam.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import java.text.Normalizer
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/**
 * One line of text as it sits on a PDF page, with the average size of its letters and, when
 * known, where each character is on the page ([boxes], see [TextSpots]).
 */
class SourceLine(val page: Int, val text: String, val size: Float, val paragraphStart: Boolean = false, val boxes: ShortArray? = null)

/** A heading or a paragraph of the reflowed book, and the PDF page it starts on. */
data class Block(val kind: Kind, val text: String, val page: Int) {
    enum class Kind { HEADING, PARAGRAPH }

    /** Where each character of [text] sits in the PDF, so highlights can be put on the real page. */
    var spots: TextSpots? = null
}

/**
 * The PDF page and box of every character of a block. Boxes are left, bottom, right, top in
 * page space, kept in half points as shorts to save memory on long books; spaces put in while
 * joining lines have no box.
 */
class TextSpots(private val boxes: ShortArray, private val runStarts: IntArray, private val runPages: IntArray) {
    fun box(i: Int): PdfRect? {
        if (i < 0 || i * 4 + 3 >= boxes.size || boxes[i * 4] == NONE) return null
        return PdfRect(dec(boxes[i * 4]), dec(boxes[i * 4 + 1]), dec(boxes[i * 4 + 2]), dec(boxes[i * 4 + 3]))
    }

    fun pageOf(i: Int): Int {
        var lo = 0
        var hi = runStarts.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (runStarts[mid] <= i) lo = mid else hi = mid - 1
        }
        return runPages.getOrElse(lo) { -1 }
    }

    /** The pages the block touches, and the characters [start, end) on each. */
    fun runs(length: Int): List<Triple<Int, Int, Int>> = runStarts.indices.map { r ->
        Triple(runPages[r], runStarts[r], if (r + 1 < runStarts.size) runStarts[r + 1] else length)
    }

    companion object {
        const val NONE: Short = Short.MIN_VALUE
        fun enc(v: Float): Short = (v * 2f).let { if (it.isNaN()) 0f else it }.coerceIn(-32767f, 32767f).toInt().toShort()
        fun dec(v: Short): Float = v / 2f
    }
}

/** A growing list of character boxes, four shorts each. */
internal class BoxBuffer {
    var data = ShortArray(256)
        private set
    var count = 0
        private set

    fun add(l: Float, b: Float, r: Float, t: Float) = put(TextSpots.enc(l), TextSpots.enc(b), TextSpots.enc(r), TextSpots.enc(t))

    fun addNone() = put(TextSpots.NONE, 0, 0, 0)

    /** Copies character [i] of [from]; an index past its end adds a character without a box. */
    fun addFrom(from: ShortArray?, i: Int) {
        if (from == null || i * 4 + 3 >= from.size) addNone() else put(from[i * 4], from[i * 4 + 1], from[i * 4 + 2], from[i * 4 + 3])
    }

    fun removeLast() {
        if (count > 0) count--
    }

    fun clear() {
        count = 0
    }

    fun slice(from: Int, to: Int): ShortArray = data.copyOfRange(from * 4, to * 4)

    private fun put(a: Short, b: Short, c: Short, d: Short) {
        if ((count + 1) * 4 > data.size) data = data.copyOf(data.size * 2)
        data[count * 4] = a
        data[count * 4 + 1] = b
        data[count * 4 + 2] = c
        data[count * 4 + 3] = d
        count++
    }
}

/**
 * Turns a PDF into running text for "Reading mode", like an ebook: the words are taken out of
 * the page layout, lines are joined back into paragraphs, larger lines become headings, and page
 * numbers and running headers are left out. No AI: plain rules about sizes, positions and punctuation.
 */
object Reflow {

    fun extract(src: File, password: String?): List<Block> = PdfEditor.load(src, password).use { doc ->
        val stripper = LineCollector()
        stripper.getText(doc)
        buildBlocks(stripper.lines, doc.numberOfPages)
    }

    /** Collects the lines PDFBox finds, in reading order, instead of writing them out as text. */
    private class LineCollector : PDFTextStripper() {
        val lines = ArrayList<SourceLine>()
        private val cur = StringBuilder()
        private val curBoxes = BoxBuffer()
        private var sizeSum = 0f
        private var sizeCount = 0
        private var paraStart = false
        private var cropX = 0f
        private var cropY = 0f

        init {
            sortByPosition = true
        }

        override fun startPage(page: PDPage) {
            super.startPage(page)
            // PDFBox measures text from the crop box's corner; page space starts at the origin.
            val c = page.cropBox
            cropX = c.lowerLeftX
            cropY = c.lowerLeftY
        }

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            for (p in textPositions) {
                val s = p.fontSizeInPt
                if (s > 0f && s < 500f) {
                    sizeSum += s
                    sizeCount++
                }
            }
            // Each letter's own text, put together the way PDFBox does (ligatures split into letters).
            val parts = textPositions.map { p -> p.unicode.orEmpty().let { u -> if (u.any { it in '\uFB00'..'\uFDFF' || it in '\uFE70'..'\uFEFF' }) Normalizer.normalize(u, Normalizer.Form.NFKC) else u } }
            if (parts.joinToString("") != text) {
                // Reordered (right-to-left) or otherwise changed text: keep the words, without places.
                cur.append(text)
                repeat(text.length) { curBoxes.addNone() }
                return
            }
            for ((p, u) in textPositions.zip(parts)) {
                cur.append(u)
                addBoxes(p, u.length)
            }
        }

        /** The box of a letter in page space, from its baseline and size, even for turned text. */
        private fun addBoxes(p: TextPosition, count: Int) {
            val m = p.textMatrix
            val ox = m.translateX + cropX
            val oy = m.translateY + cropY
            var ux = m.scaleX
            var uy = m.shearY
            val len = hypot(ux, uy)
            if (len < 1e-6f) {
                ux = 1f
                uy = 0f
            } else {
                ux /= len
                uy /= len
            }
            val fs = p.fontSizeInPt.takeIf { it > 0f && it < 500f } ?: p.heightDir.coerceAtLeast(1f)
            val w = p.widthDirAdj
            val below = -0.22f * fs
            val above = 0.82f * fs
            val xs = floatArrayOf(ox - uy * below, ox + ux * w - uy * below, ox - uy * above, ox + ux * w - uy * above)
            val ys = floatArrayOf(oy + ux * below, oy + uy * w + ux * below, oy + ux * above, oy + uy * w + ux * above)
            val l = min(min(xs[0], xs[1]), min(xs[2], xs[3]))
            val r = max(max(xs[0], xs[1]), max(xs[2], xs[3]))
            val b = min(min(ys[0], ys[1]), min(ys[2], ys[3]))
            val t = max(max(ys[0], ys[1]), max(ys[2], ys[3]))
            repeat(count) { curBoxes.add(l, b, r, t) }
        }

        override fun writeWordSeparator() {
            cur.append(' ')
            curBoxes.addNone()
        }

        override fun writeLineSeparator() = flush()

        override fun writeParagraphStart() {
            flush()
            paraStart = true
        }

        override fun writeParagraphEnd() = flush()

        override fun endPage(page: PDPage) {
            flush()
            super.endPage(page)
        }

        private fun flush() {
            val raw = cur.toString().replace('\u00A0', ' ')
            var a = 0
            var b = raw.length
            while (a < b && raw[a].isWhitespace()) a++
            while (b > a && raw[b - 1].isWhitespace()) b--
            if (b > a) {
                val boxes = if (curBoxes.count == raw.length) curBoxes.slice(a, b) else null
                lines += SourceLine(currentPageNo - 1, raw.substring(a, b), if (sizeCount > 0) sizeSum / sizeCount else 0f, paraStart, boxes)
                paraStart = false
            }
            cur.setLength(0)
            curBoxes.clear()
            sizeSum = 0f
            sizeCount = 0
        }
    }

    private val PAGE_NUMBER = Regex("^[\\s\\-–—|]*(page\\s*)?\\d{1,4}(\\s*(of|/)\\s*\\d{1,4})?[\\s\\-–—|]*$", RegexOption.IGNORE_CASE)
    private val ROMAN_PAGE = Regex("^[\\s\\-–—]*[ivxlcdm]{1,7}[\\s\\-–—]*$", RegexOption.IGNORE_CASE)

    /** Rebuilds headings and paragraphs from the lines of every page. */
    fun buildBlocks(all: List<SourceLine>, pageCount: Int): List<Block> {
        val lines = dropRunningHeaders(all, pageCount).filterNot { PAGE_NUMBER.matches(it.text) || ROMAN_PAGE.matches(it.text) }
        if (lines.isEmpty()) return emptyList()

        // The size most of the text is set in.
        val bySize = HashMap<Int, Int>()
        for (l in lines) if (l.size > 0f) bySize.merge((l.size * 2).toInt(), l.text.length, Int::plus)
        val body = (bySize.maxByOrNull { it.value }?.key ?: 20) / 2f
        val lengths = lines.filter { it.size <= body * 1.1f }.map { it.text.length }.sorted()
        val typical = if (lengths.isEmpty()) 60 else lengths[lengths.size * 3 / 4].coerceAtLeast(20)

        val out = ArrayList<Block>()
        val cur = StringBuilder()
        // The box of every character in cur, and the page each line of it came from.
        val curBoxes = BoxBuffer()
        val curRuns = ArrayList<IntArray>()
        var curPage = 0
        var curHeading = false
        var lastLine: SourceLine? = null

        fun flush() {
            // Runs of spaces become one space, as in the text; the boxes follow the letters they belong to.
            val text = StringBuilder()
            val boxes = BoxBuffer()
            val runStarts = ArrayList<Int>()
            val runPages = ArrayList<Int>()
            var run = 0
            var pendingSpace = false
            for (i in cur.indices) {
                val c = cur[i]
                if (c.isWhitespace()) {
                    pendingSpace = text.isNotEmpty()
                    continue
                }
                if (pendingSpace) {
                    text.append(' ')
                    boxes.addNone()
                    pendingSpace = false
                }
                while (run + 1 < curRuns.size && curRuns[run + 1][0] <= i) run++
                val page = curRuns.getOrNull(run)?.get(1) ?: curPage
                if (runPages.lastOrNull() != page) {
                    runStarts += text.length
                    runPages += page
                }
                text.append(c)
                boxes.addFrom(curBoxes.data.takeIf { i < curBoxes.count }, i)
            }
            if (text.isNotEmpty()) {
                val block = Block(if (curHeading) Block.Kind.HEADING else Block.Kind.PARAGRAPH, text.toString(), curPage)
                block.spots = TextSpots(boxes.slice(0, boxes.count), runStarts.toIntArray(), runPages.toIntArray())
                out += block
            }
            cur.setLength(0)
            curBoxes.clear()
            curRuns.clear()
        }

        for (l in lines) {
            val heading = body > 0f && l.size >= body * 1.18f && looksLikeHeading(l.text)
            val prev = lastLine
            val newBlock = when {
                prev == null -> true
                heading != curHeading -> true
                heading -> l.page != prev.page || l.size < prev.size * 0.9f || l.size > prev.size * 1.1f
                l.paragraphStart && l.page == prev.page -> true
                endsParagraph(prev.text, typical) && !(l.page != prev.page && startsMidSentence(l.text)) -> true
                else -> false
            }
            if (newBlock) {
                flush()
                curPage = l.page
                curHeading = heading
            } else if (cur.isNotEmpty()) {
                // Join the line on: drop a hyphen that only split a word across lines.
                if (cur.endsWith("-") && cur.length > 1 && cur[cur.length - 2].isLetter() && l.text.firstOrNull()?.isLowerCase() == true) {
                    cur.setLength(cur.length - 1)
                    curBoxes.removeLast()
                } else {
                    cur.append(' ')
                    curBoxes.addNone()
                }
            }
            if (curRuns.lastOrNull()?.get(1) != l.page) curRuns += intArrayOf(cur.length, l.page)
            cur.append(l.text)
            for (i in l.text.indices) curBoxes.addFrom(l.boxes, i)
            lastLine = l
        }
        flush()
        return out
    }

    /** Headings are short and don't end like a sentence; big text that reads as a sentence stays a paragraph. */
    private fun looksLikeHeading(text: String): Boolean {
        val t = text.trim()
        return t.length <= 80 && !(t.endsWith(".") && !t.endsWith("..")) && !t.endsWith(",") && !t.endsWith(";")
    }

    /** A line ends its paragraph when it stops well short of the margin after finishing a sentence, or is clearly short. */
    private fun endsParagraph(text: String, typical: Int): Boolean {
        val t = text.trimEnd()
        if (t.isEmpty()) return true
        if (t.endsWith("-") && t.length > 1 && t[t.length - 2].isLetter()) return false
        val sentenceEnd = t.last() in ".!?:”\"’)"
        return t.length < typical * 0.6 || (sentenceEnd && t.length < typical * 0.85)
    }

    private fun startsMidSentence(text: String): Boolean = text.firstOrNull()?.isLowerCase() == true

    /**
     * Drops lines that repeat at the top or bottom of many pages (book titles, chapter names in
     * the header, copyright footers). Digits are ignored when comparing, so "Chapter 3 · 41" and
     * "Chapter 3 · 42" count as the same line.
     */
    fun dropRunningHeaders(lines: List<SourceLine>, pageCount: Int): List<SourceLine> {
        if (pageCount < 4) return lines
        fun key(s: String) = s.lowercase().replace(Regex("\\d+"), "#").replace(Regex("\\s+"), " ").trim()
        val byPage = lines.groupBy { it.page }
        val counts = HashMap<String, Int>()
        for ((_, ls) in byPage) {
            val edges = (ls.take(2) + ls.takeLast(2)).map { key(it.text) }.toSet()
            for (k in edges) counts[k] = (counts[k] ?: 0) + 1
        }
        val threshold = max(3, (byPage.size * 0.4).toInt())
        val repeated = counts.filter { it.value >= threshold && it.key.length <= 100 }.keys
        if (repeated.isEmpty()) return lines
        return byPage.flatMap { (_, ls) ->
            ls.filterIndexed { i, l -> !((i < 2 || i >= ls.size - 2) && key(l.text) in repeated) }
        }
    }
}
