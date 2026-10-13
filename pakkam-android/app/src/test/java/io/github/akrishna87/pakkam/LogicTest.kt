package io.github.akrishna87.pakkam

import io.github.akrishna87.pakkam.pdf.Docx
import io.github.akrishna87.pakkam.pdf.PageTextLayout
import io.github.akrishna87.pakkam.pdf.PdfTools
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.util.zip.ZipInputStream

class LogicTest {

    /** A line of text where every character is 6 points wide and 10 high, starting at (x0, y0). */
    private fun layout(vararg lines: String): PageTextLayout {
        val text = StringBuilder()
        val boxes = ArrayList<Float>()
        for ((li, line) in lines.withIndex()) {
            val y = 700f - li * 14f
            for ((ci, c) in line.withIndex()) {
                text.append(c)
                val x = 72f + ci * 6f
                boxes += listOf(x, y, x + 6f, y + 10f)
            }
            if (li < lines.lastIndex) {
                text.append("\r\n")
                repeat(2) { boxes += listOf(0f, 0f, 0f, 0f) }
            }
        }
        return PageTextLayout(text.toString(), boxes.toFloatArray())
    }

    @Test
    fun findIgnoresCaseAndLineBreaks() {
        val l = layout("The Quick brown", "fox jumps")
        assertEquals(listOf(4 to 9), l.find("quick"))
        // "brown fox" runs across the line break.
        val hits = l.find("Brown  Fox")
        assertEquals(1, hits.size)
        assertEquals("brown\nfox", l.textOf(hits[0].first, hits[0].second))
        assertTrue(l.find("cat").isEmpty())
        assertEquals(2, layout("ab ab").find("ab").size)
    }

    @Test
    fun findJoinsHyphenatedWords() {
        val l = layout("an inter-", "national deal")
        assertEquals(1, l.find("international").size)
    }

    @Test
    fun rectsAreOnePerLine() {
        val l = layout("hello world", "second line")
        val start = l.find("world")[0].first
        val end = l.find("second")[0].second
        val rects = l.rectsFor(start, end)
        assertEquals(2, rects.size)
        assertEquals(72f + 6 * 6f, rects[0].left, 0.01f)
        assertEquals(72f, rects[1].left, 0.01f)
        // One rectangle for a whole line, not one per word.
        assertEquals(1, l.rectsFor(0, 11).size)
    }

    @Test
    fun hitTestingFindsCharactersAndWords() {
        val l = layout("hello world")
        val i = l.charAt(72f + 7 * 6f + 1f, 705f)
        assertEquals('o', l.text[i])
        assertEquals(6 to 11, l.wordAt(i))
        assertEquals(-1, l.charAt(400f, 100f))
        assertTrue(l.nearestChar(400f, 100f) >= 0)
    }

    @Test
    fun pageRanges() {
        assertEquals(listOf(0..2, 4..4, 7..9), PdfTools.parseRanges("1-3, 5, 8-", 10))
        assertEquals(listOf(0..9), PdfTools.parseRanges("-", 10))
        assertEquals(listOf(1..3), PdfTools.parseRanges(" 2 – 4 ", 10))
        assertNull(PdfTools.parseRanges("0-2", 10))
        assertNull(PdfTools.parseRanges("3-1", 10))
        assertNull(PdfTools.parseRanges("11", 10))
        assertNull(PdfTools.parseRanges("abc", 10))
        assertNull(PdfTools.parseRanges("", 10))
        assertEquals(listOf(0..2, 3..5, 6..6), PdfTools.everyN(3, 7))
    }

    @Test
    fun wordParagraphs() {
        val page = "A heading\n\nThis is a long line of text that keeps\ngoing on the next line.\nShort.\nAnother para-\ngraph that is long enough to continue on."
        val p = Docx.paragraphs(page)
        assertEquals("A heading", p[0])
        assertEquals("This is a long line of text that keeps going on the next line.", p[1])
        assertEquals("Short.", p[2])
        assertEquals("Another paragraph that is long enough to continue on.", p[3])
    }

    @Test
    fun docxIsAZipWithEscapedText() {
        val out = ByteArrayOutputStream()
        Docx.write("T & C", listOf("Fish & chips <hot>", "Page two"), out)
        val names = ArrayList<String>()
        var body = ""
        ZipInputStream(out.toByteArray().inputStream()).use { z ->
            while (true) {
                val e = z.nextEntry ?: break
                names += e.name
                if (e.name == "word/document.xml") body = z.readBytes().toString(Charsets.UTF_8)
            }
        }
        assertTrue(names.containsAll(listOf("[Content_Types].xml", "_rels/.rels", "word/document.xml")))
        assertTrue(body.contains("Fish &amp; chips &lt;hot&gt;"))
        assertTrue(body.contains("w:type=\"page\""))
    }
}
