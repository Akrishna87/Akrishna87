package io.github.akrishna87.pakkam

import io.github.akrishna87.pakkam.pdf.AnnotInfo
import io.github.akrishna87.pakkam.pdf.Block
import io.github.akrishna87.pakkam.pdf.MarkOrigin
import io.github.akrishna87.pakkam.pdf.MarkupEdit
import io.github.akrishna87.pakkam.pdf.MarkupKind
import io.github.akrishna87.pakkam.pdf.PdfRect
import io.github.akrishna87.pakkam.pdf.ReadingText
import io.github.akrishna87.pakkam.pdf.Reflow
import io.github.akrishna87.pakkam.pdf.SourceLine
import io.github.akrishna87.pakkam.pdf.TextSpots
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ReadingMarksTest {
    /** A line set in a fixed-width face: each letter 6 pt wide, starting at x = 72. */
    private fun line(page: Int, text: String, y: Float, size: Float = 11f): SourceLine {
        val boxes = ShortArray(text.length * 4)
        for (i in text.indices) {
            if (text[i] == ' ') {
                boxes[i * 4] = TextSpots.NONE
                continue
            }
            val x = 72f + i * 6f
            boxes[i * 4] = TextSpots.enc(x)
            boxes[i * 4 + 1] = TextSpots.enc(y - 2f)
            boxes[i * 4 + 2] = TextSpots.enc(x + 6f)
            boxes[i * 4 + 3] = TextSpots.enc(y + 9f)
        }
        return SourceLine(page, text, size, boxes = boxes)
    }

    private val wide = "This is a line of ordinary body text set the full width of the page"

    private fun book(): ReadingText {
        val blocks = Reflow.buildBlocks(
            listOf(
                line(0, "Chapter One", 700f, 20f),
                line(0, wide, 650f),
                line(0, "and then this long para-", 636f),
                line(0, "graph ends on the page.", 622f),
                line(0, wide, 600f),
                line(0, wide, 586f),
                line(1, "running onto the next page and stopping.", 700f),
            ),
            2,
        )
        val starts = IntArray(blocks.size)
        var at = 0
        for ((i, b) in blocks.withIndex()) {
            starts[i] = at
            at += b.text.length + 1
        }
        return ReadingText(blocks, starts)
    }

    @Test fun lettersKeepTheirPlaceThroughJoinedLines() {
        val t = book()
        val para = t.blocks[1]
        assertEquals(Block.Kind.PARAGRAPH, para.kind)
        val g = para.text.indexOf("paragraph") + 4
        assertEquals('g', para.text[g])
        // "graph" begins the second line: the hyphen was dropped and the letters still point at their line.
        val box = para.spots!!.box(g)!!
        assertEquals(72f, box.left, 0.01f)
        assertEquals(620f, box.bottom, 0.01f)
        assertNull(para.spots!!.box(para.text.indexOf(" this")))
        val last = t.blocks.last()
        assertEquals(1, last.spots!!.pageOf(last.text.indexOf("stopping")))
        assertEquals(0, last.spots!!.pageOf(0))
    }

    @Test fun boxesFollowTheLines() {
        val t = book()
        val text = t.blocks[1].text
        val s = t.blockStarts[1] + text.indexOf("long")
        val e = t.blockStarts[1] + text.indexOf(" ends")
        val parts = t.boxes(s, e)
        assertEquals(1, parts.size)
        assertEquals(0, parts[0].first)
        assertEquals(2, parts[0].second.size)
        assertEquals("long paragraph", t.textOf(s, e))
        // And back: the boxes cover exactly those letters.
        assertEquals(listOf(s to e), t.locate(0, parts[0].second))
    }

    @Test fun highlightsBecomeMarks() {
        val t = book()
        val text = t.blocks[1].text
        val s = t.blockStarts[1] + text.indexOf("long")
        val e = t.blockStarts[1] + text.indexOf(" ends")
        val rects = t.boxes(s, e)[0].second
        val pending = MarkupEdit(0, MarkupKind.HIGHLIGHT, rects, 0xFF76FF03.toInt(), "long paragraph", "Check this")
        val saved = AnnotInfo(0, 0, "Highlight", PdfRect(70f, 690f, 140f, 712f), "Chapter One", "Pakkam", 0xFFFFEB3B.toInt(), listOf(PdfRect(72f, 698f, 138f, 709f)))
        val marks = t.marks(listOf(saved), listOf(pending))
        assertEquals(2, marks.size)
        assertEquals(t.blockStarts[0], marks[0].start)
        assertEquals("", marks[0].note)
        assertEquals("Check this", marks[1].note)
        assertEquals(s, marks[1].start)
        // Removing the saved one hides it.
        val gone = t.marks(listOf(saved), listOf(io.github.akrishna87.pakkam.pdf.DeleteAnnotEdit(0, saved)))
        assertTrue(gone.isEmpty())
    }

    @Test fun aHighlightOverAPageBreakIsOneMark() {
        val t = book()
        val last = t.blocks.size - 1
        val text = t.blocks[last].text
        val s = t.blockStarts[last] + text.indexOf("page")
        val e = t.blockStarts[last] + text.indexOf(" and stopping")
        val parts = t.boxes(s, e)
        assertEquals(listOf(0, 1), parts.map { it.first })
        val edits = parts.mapIndexed { i, (page, rects) -> MarkupEdit(page, MarkupKind.HIGHLIGHT, rects, 0xFFFFEB3B.toInt(), "x", if (i == 0) "A note" else "") }
        val marks = t.marks(emptyList(), edits)
        assertEquals(1, marks.size)
        assertEquals(s, marks[0].start)
        assertEquals(e, marks[0].end)
        assertEquals("A note", marks[0].note)
        assertEquals(2, marks[0].origins.filterIsInstance<MarkOrigin.Pending>().size)
    }

    @Test fun markedWordsAreNotANote() {
        assertEquals("", ReadingText.noteFrom("long paragraph", "long para-\ngraph"))
        assertEquals("", ReadingText.noteFrom("", "anything"))
        assertEquals("Read again", ReadingText.noteFrom(" Read again ", "long paragraph"))
    }
}
