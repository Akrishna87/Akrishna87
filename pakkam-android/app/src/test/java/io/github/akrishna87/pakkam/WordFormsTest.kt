package io.github.akrishna87.pakkam

import io.github.akrishna87.pakkam.pdf.WordForms
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class WordFormsTest {
    @Test
    fun cleansPunctuationAndPossessives() {
        assertEquals("Elephant", WordForms.clean("“Elephant’s"))
        assertEquals("forgets", WordForms.clean("forgets."))
        assertEquals("well-known", WordForms.clean("(well-known),"))
        assertEquals("", WordForms.clean("—"))
    }

    @Test
    fun findsTheHeadword() {
        fun has(word: String, base: String) = assertTrue("$word → $base", base in WordForms.candidates(word))
        has("Forgets", "forget")
        has("running", "run")
        has("stopped", "stop")
        has("hoped", "hope")
        has("making", "make")
        has("boxes", "box")
        has("cities", "city")
        has("bigger", "big")
        has("happily", "happy")
        has("quickly", "quick")
        has("watches", "watch")
        assertEquals("elephant", WordForms.candidates("Elephant")[0])
        assertTrue(WordForms.candidates("…").isEmpty())
    }
}
