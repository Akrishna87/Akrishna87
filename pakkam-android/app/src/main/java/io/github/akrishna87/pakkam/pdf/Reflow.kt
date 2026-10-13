package io.github.akrishna87.pakkam.pdf

import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.text.PDFTextStripper
import com.tom_roush.pdfbox.text.TextPosition
import java.io.File
import kotlin.math.max

/** One line of text as it sits on a PDF page, with the average size of its letters. */
data class SourceLine(val page: Int, val text: String, val size: Float, val paragraphStart: Boolean = false)

/** A heading or a paragraph of the reflowed book, and the PDF page it starts on. */
data class Block(val kind: Kind, val text: String, val page: Int) {
    enum class Kind { HEADING, PARAGRAPH }
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
        private var sizeSum = 0f
        private var sizeCount = 0
        private var paraStart = false

        init {
            sortByPosition = true
        }

        override fun writeString(text: String, textPositions: List<TextPosition>) {
            cur.append(text)
            for (p in textPositions) {
                val s = p.fontSizeInPt
                if (s > 0f && s < 500f) {
                    sizeSum += s
                    sizeCount++
                }
            }
        }

        override fun writeWordSeparator() {
            cur.append(' ')
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
            val t = cur.toString().replace(' ', ' ').trim()
            if (t.isNotEmpty()) {
                lines += SourceLine(currentPageNo - 1, t, if (sizeCount > 0) sizeSum / sizeCount else 0f, paraStart)
                paraStart = false
            }
            cur.setLength(0)
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
        var curPage = 0
        var curHeading = false
        var lastLine: SourceLine? = null

        fun flush() {
            val t = cur.toString().replace(Regex("\\s+"), " ").trim()
            if (t.isNotEmpty()) out += Block(if (curHeading) Block.Kind.HEADING else Block.Kind.PARAGRAPH, t, curPage)
            cur.setLength(0)
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
                } else {
                    cur.append(' ')
                }
            }
            cur.append(l.text)
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
