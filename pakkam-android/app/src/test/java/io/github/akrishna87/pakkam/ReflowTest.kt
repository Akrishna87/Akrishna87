package io.github.akrishna87.pakkam

import io.github.akrishna87.pakkam.pdf.Block
import io.github.akrishna87.pakkam.pdf.Reflow
import io.github.akrishna87.pakkam.pdf.SourceLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ReflowTest {
    private val full = "This line of body text runs all the way across to the margin of"

    @Test
    fun linesJoinIntoParagraphsAndBigLinesBecomeHeadings() {
        val lines = listOf(
            SourceLine(0, "Chapter One", 20f),
            SourceLine(0, full, 11f),
            SourceLine(0, "the page and then this para-", 11f),
            SourceLine(0, "graph ends here.", 11f),
            SourceLine(0, full, 11f),
            SourceLine(0, "the next paragraph, which ends.", 11f),
        )
        val blocks = Reflow.buildBlocks(lines, 1)
        assertEquals(3, blocks.size)
        assertEquals(Block(Block.Kind.HEADING, "Chapter One", 0), blocks[0])
        assertEquals(Block.Kind.PARAGRAPH, blocks[1].kind)
        assertEquals("$full the page and then this paragraph ends here.", blocks[1].text)
        assertEquals("$full the next paragraph, which ends.", blocks[2].text)
    }

    @Test
    fun paragraphsCarryOnAcrossPages() {
        val lines = listOf(
            SourceLine(0, full, 11f),
            SourceLine(0, full, 11f),
            SourceLine(1, "continues on the next page.", 11f),
        )
        val blocks = Reflow.buildBlocks(lines, 2)
        assertEquals(1, blocks.size)
        assertEquals(0, blocks[0].page)
        assertTrue(blocks[0].text.endsWith("of continues on the next page."))
    }

    @Test
    fun pageNumbersAndRunningHeadersAreDropped() {
        val lines = (0 until 6).flatMap { p ->
            listOf(
                SourceLine(p, "A Book Title · Chapter 2", 9f),
                SourceLine(p, "$full ${"abcdef"[p]}.", 11f),
                SourceLine(p, "Short end of page ${"abcdef"[p]}.", 11f),
                SourceLine(p, "${p + 1}", 9f),
            )
        }
        val blocks = Reflow.buildBlocks(lines, 6)
        assertFalse(blocks.any { it.text.contains("A Book Title") })
        assertFalse(blocks.any { it.text.matches(Regex("\\d+")) })
        assertTrue(blocks.all { it.kind == Block.Kind.PARAGRAPH })
        assertEquals(6, blocks.size)
    }

    @Test
    fun bigSentencesAreNotHeadings() {
        val lines = listOf(
            SourceLine(0, "Introduction", 20f),
            SourceLine(0, "Highlight this sentence please.", 20f),
            SourceLine(0, full, 11f),
            SourceLine(0, "ends here.", 11f),
            SourceLine(0, full, 11f),
            SourceLine(0, "and another.", 11f),
        )
        val blocks = Reflow.buildBlocks(lines, 1)
        assertEquals(Block.Kind.HEADING, blocks[0].kind)
        assertEquals(Block(Block.Kind.PARAGRAPH, "Highlight this sentence please.", 0), blocks[1])
    }

    @Test
    fun emptyPdfGivesNoBlocks() {
        assertTrue(Reflow.buildBlocks(emptyList(), 3).isEmpty())
    }
}
