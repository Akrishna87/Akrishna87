package io.github.akrishna87.pakkam

import io.github.akrishna87.pakkam.pdf.InkLifter
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.random.Random

class InkLifterTest {
    private val w = 400
    private val h = 240

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /**
     * Cream paper lit from the left (the right side in shadow), grain, a few specks, a dark
     * table edge along the left of the photo, and a blue pen stroke in the middle.
     */
    private fun photo(): IntArray {
        val rnd = Random(7)
        val px = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            val light = 245 - x * 90 / w
            val n = rnd.nextInt(-6, 7)
            px[y * w + x] = rgb(light + n, light - 4 + n, light - 18 + n)
        }
        for (y in 0 until h) for (x in 0 until 10) px[y * w + x] = rgb(40, 30, 25)
        for (i in 0 until 6) px[rnd.nextInt(h) * w + 20 + rnd.nextInt(w - 40)] = rgb(90, 90, 90)
        // A wavy stroke across the middle, 5 px thick, in blue ink seen under the same light.
        for (x in 100 until 320) {
            val cy = 120 + (kotlin.math.sin(x / 15.0) * 30).toInt()
            val light = 245 - x * 90 / w
            for (dy in -2..2) px[(cy + dy) * w + x] = rgb(light * 20 / 255, light * 45 / 255, light * 140 / 255)
        }
        return px
    }

    @Test fun findsTheSignatureAndNotTheTableEdge() {
        val lifter = InkLifter(photo(), w, h)
        val box = lifter.findInk()
        assertNotNull(box)
        box!!
        assertTrue("left ${box[0]}", box[0] in 80..100)
        assertTrue("right ${box[2]}", box[2] in 320..340)
        assertTrue("top ${box[1]}", box[1] in 70..92)
        assertTrue("bottom ${box[3]}", box[3] in 150..172)
    }

    @Test fun paperGoesInkStays() {
        val lifter = InkLifter(photo(), w, h)
        val crop = intArrayOf(0, 0, w, h)
        val a = lifter.inkAlpha(crop, 0.5f)
        // The stroke, in the light and in the shadow.
        val cyLight = 120 + (kotlin.math.sin(110 / 15.0) * 30).toInt()
        assertEquals(255, a[cyLight * w + 110])
        val cyShadow = 120 + (kotlin.math.sin(300 / 15.0) * 30).toInt()
        assertTrue(a[cyShadow * w + 300] > 200)
        // Shadowed paper and specks are see-through.
        var paperInk = 0
        for (y in 0 until h) for (x in 20 until w) {
            val cy = 120 + (kotlin.math.sin(x / 15.0) * 30).toInt()
            if (x in 95 until 325 && y in cy - 5..cy + 5) continue
            if (a[y * w + x] > 0) paperInk++
        }
        assertEquals(0, paperInk)
    }

    @Test fun liftedInkIsBlueAndTrimmed() {
        val lifter = InkLifter(photo(), w, h)
        val box = lifter.findInk()!!
        val lifted = lifter.lift(box, 0.5f, null)!!
        assertTrue(lifted.width in 220..235)
        val c = lifted.argb.first { (it ushr 24) == 255 }
        val r = (c shr 16) and 0xFF
        val b = c and 0xFF
        assertTrue("ink $r/$b", b > r * 2)
        // A chosen colour is used as it is.
        val black = lifter.lift(box, 0.5f, 0xFF000000.toInt())!!
        assertEquals(0, black.argb.first { (it ushr 24) == 255 } and 0xFFFFFF)
    }
}
