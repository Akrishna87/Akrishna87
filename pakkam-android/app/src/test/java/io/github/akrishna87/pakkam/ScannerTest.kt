package io.github.akrishna87.pakkam

import io.github.akrishna87.pakkam.scan.DocumentDetector
import io.github.akrishna87.pakkam.scan.Pixels
import io.github.akrishna87.pakkam.scan.Pt
import io.github.akrishna87.pakkam.scan.Quad
import io.github.akrishna87.pakkam.scan.ScanImage
import io.github.akrishna87.pakkam.scan.ScanLook
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs
import kotlin.random.Random

class ScannerTest {
    private val w = 400
    private val h = 320

    private fun rgb(r: Int, g: Int, b: Int) = (0xFF shl 24) or (r.coerceIn(0, 255) shl 16) or (g.coerceIn(0, 255) shl 8) or b.coerceIn(0, 255)

    /** Is (x, y) inside the four corners (clockwise)? */
    private fun inside(q: List<Pt>, x: Float, y: Float): Boolean {
        for (i in 0 until 4) {
            val a = q[i]
            val b = q[(i + 1) % 4]
            if ((b.x - a.x) * (y - a.y) - (b.y - a.y) * (x - a.x) < 0) return false
        }
        return true
    }

    private val corners = listOf(Pt(70f, 40f), Pt(330f, 62f), Pt(312f, 288f), Pt(48f, 266f))

    /**
     * A page photographed at an angle on a wooden table: lit from one side, grainy, with lines
     * of dark "text" across it and a shadow over one corner.
     */
    private fun photo(background: Int = 95): Pixels {
        val rnd = Random(11)
        val px = IntArray(w * h)
        val m = ScanImage.homography(listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(0f, 1f)), corners)
        // From the photo back to the page, to know where on the page a pixel is.
        val back = ScanImage.homography(corners, listOf(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(0f, 1f)))
        for (y in 0 until h) for (x in 0 until w) {
            val n = rnd.nextInt(-8, 9)
            val light = 1f - 0.3f * x / w
            if (inside(corners, x.toFloat(), y.toFloat())) {
                val d = back[6] * x + back[7] * y + 1
                val u = ((back[0] * x + back[1] * y + back[2]) / d).toFloat()
                val v = ((back[3] * x + back[4] * y + back[5]) / d).toFloat()
                val text = v > 0.15f && v < 0.85f && ((v * 20).toInt() % 2 == 0) && (v * 20 % 1f) < 0.45f && u > 0.1f && u < 0.85f
                val base = if (text) 40 else 235
                val l = (base * light).toInt() + n
                px[y * w + x] = rgb(l, l - 3, l - 12)
            } else {
                val grain = ((x / 7 + y / 3) % 5) * 4
                val l = ((background + grain) * light).toInt() + n
                px[y * w + x] = rgb(l + 25, l, l - 20)
            }
        }
        return Pixels(px, w, h)
    }

    private fun near(a: Pt, b: Pt, tol: Float) = abs(a.x - b.x) <= tol && abs(a.y - b.y) <= tol

    @Test fun findsTheTiltedPage() {
        val p = photo()
        val q = DocumentDetector.detect(p.luminance(), w, h)
        assertNotNull(q)
        val found = q!!.map { Pt(it.x * (w - 1), it.y * (h - 1)) }.points
        for (i in 0 until 4) assertTrue("corner $i: ${found[i]} vs ${corners[i]}", near(found[i], corners[i], 6f))
    }

    @Test fun findsThePageOnALightTable() {
        val q = DocumentDetector.detect(photo(background = 150).luminance(), w, h)
        assertNotNull(q)
        val found = q!!.map { Pt(it.x * (w - 1), it.y * (h - 1)) }.points
        for (i in 0 until 4) assertTrue("corner $i: ${found[i]}", near(found[i], corners[i], 6f))
    }

    @Test fun noPageInAPlainPicture() {
        val rnd = Random(3)
        val lum = IntArray(w * h) { 120 + rnd.nextInt(-10, 11) }
        assertNull(DocumentDetector.detect(lum, w, h))
    }

    @Test fun flatteningStraightensThePage() {
        val p = photo()
        val q = Quad(corners[0], corners[1], corners[2], corners[3]).map { Pt(it.x / (w - 1), it.y / (h - 1)) }
        val flat = ScanImage.flatten(p, q, 600)
        assertTrue(flat.width > flat.height)
        // The text lines come out level: each row of the page is all paper or crosses the same lines.
        val lum = flat.luminance()
        val mid = flat.height / 2
        var darkRows = 0
        for (y in 0 until flat.height) {
            val row = (flat.width * 0.15).toInt() until (flat.width * 0.8).toInt()
            val dark = row.count { lum[y * flat.width + it] < 120 }
            if (dark > row.count() * 0.8) darkRows++
        }
        assertTrue("text rows: $darkRows", darkRows > flat.height * 0.1)
        // Corners of the result are paper, not table.
        assertTrue(lum[2 * flat.width + 2] > 150)
        assertTrue(lum[(flat.height - 3) * flat.width + flat.width - 3] > 120)
        assertTrue(lum[mid * flat.width + 3] > 120)
    }

    @Test fun blackAndWhiteEvensOutTheLight() {
        val p = photo()
        val q = Quad(corners[0], corners[1], corners[2], corners[3]).map { Pt(it.x / (w - 1), it.y / (h - 1)) }
        val flat = ScanImage.flatten(p, q, 600)
        val bw = ScanImage.enhance(flat, ScanLook.BW).luminance()
        // Paper on the lit side and on the dim side both come out white.
        val y = (flat.height * 0.07).toInt()
        assertEquals(255, bw[y * flat.width + (flat.width * 0.1).toInt()])
        assertTrue(bw[y * flat.width + (flat.width * 0.9).toInt()] > 240)
        // And the text black.
        val textY = (0 until flat.height).first { yy -> (flat.width / 4 until flat.width / 2).all { flat.luminance()[yy * flat.width + it] < 90 } }
        assertTrue(bw[textY * flat.width + flat.width / 3] < 30)
        val auto = ScanImage.enhance(flat, ScanLook.AUTO)
        assertTrue(Pixels.lum(auto.argb[y * flat.width + (flat.width * 0.9).toInt()]) > 235)
    }

    @Test fun aColourfulCoverIsBroughtOutNotWhitened() {
        // A dull, hazy photo of a blue and green cover: low contrast, a purple cast.
        val w = 200
        val h = 260
        val px = IntArray(w * h) { i ->
            val y = i / w
            if (y < h / 2) rgb(110, 115, 160) else rgb(90, 130, 95)
        }
        val cover = Pixels(px, w, h)
        assertTrue(ScanImage.paperShare(cover) < 0.35f)
        val out = ScanImage.enhance(cover, ScanLook.AUTO)
        val sky = out.argb[10 * w + 10]
        val grass = out.argb[(h - 10) * w + 10]
        // The sky stays blue and the grass green, with more contrast than before, not turned white.
        assertTrue((sky and 0xFF) > ((sky shr 16) and 0xFF) + 30)
        assertTrue(((grass shr 8) and 0xFF) > ((grass shr 16) and 0xFF) + 30)
        assertTrue(Pixels.lum(sky) < 235)
    }

    @Test fun aLetterIsTreatedAsPaper() {
        assertTrue(ScanImage.paperShare(photo().shrunk(400)) > 0.0f)
        val q = Quad(corners[0], corners[1], corners[2], corners[3]).map { Pt(it.x / (w - 1), it.y / (h - 1)) }
        val flat = ScanImage.flatten(photo(), q, 600)
        assertTrue("share ${ScanImage.paperShare(flat)}", ScanImage.paperShare(flat) > 0.35f)
    }

    @Test fun cornersComeInOrder() {
        val q = Quad.ordered(listOf(Pt(1f, 1f), Pt(0f, 0f), Pt(0f, 1f), Pt(1f, 0f)))
        assertEquals(Pt(0f, 0f), q.tl)
        assertEquals(Pt(1f, 0f), q.tr)
        assertEquals(Pt(1f, 1f), q.br)
        assertEquals(Pt(0f, 1f), q.bl)
        assertTrue(q.isConvex)
    }
}
