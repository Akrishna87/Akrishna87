package io.github.akrishna87.pakkam.scan

import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/** A picture as plain ARGB pixels. */
class Pixels(val argb: IntArray, val width: Int, val height: Int) {
    init {
        require(argb.size == width * height) { "Wrong pixel count" }
    }

    /** Brightness 0–255 of every pixel. */
    fun luminance(): IntArray = IntArray(argb.size) { lum(argb[it]) }

    /** A smaller copy (each pixel the average of the block it stands for), at most [maxSide] across. */
    fun shrunk(maxSide: Int): Pixels {
        val step = max(1, (max(width, height) + maxSide - 1) / maxSide)
        if (step == 1) return this
        val w = width / step
        val h = height / step
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (dy in 0 until step) for (dx in 0 until step) {
                val c = argb[(y * step + dy) * width + x * step + dx]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            val n = step * step
            out[y * w + x] = (0xFF shl 24) or ((r / n) shl 16) or ((g / n) shl 8) or (b / n)
        }
        return Pixels(out, w, h)
    }

    /** Turned clockwise by [degrees] (a multiple of 90). */
    fun turned(degrees: Int): Pixels {
        val d = ((degrees % 360) + 360) % 360
        if (d == 0) return this
        val w = if (d == 180) width else height
        val h = if (d == 180) height else width
        val out = IntArray(w * h)
        for (y in 0 until height) for (x in 0 until width) {
            val (nx, ny) = when (d) {
                90 -> (height - 1 - y) to x
                180 -> (width - 1 - x) to (height - 1 - y)
                else -> y to (width - 1 - x)
            }
            out[ny * w + nx] = argb[y * width + x]
        }
        return Pixels(out, w, h)
    }

    companion object {
        fun lum(c: Int) = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000
    }
}

/** How a scanned page is cleaned up. */
enum class ScanLook(val label: String) {
    AUTO("Auto colour"),
    GRAY("Greyscale"),
    BW("Black & white"),
    ORIGINAL("Original photo"),
}

/** Flattening a photographed page and cleaning it up, as a document scanner does. No AI: arithmetic on pixels. */
object ScanImage {

    /** The size the page in [q] would have when flattened, keeping its proportions as photographed. */
    fun flatSize(src: Pixels, q: Quad, maxSide: Int): Pair<Int, Int> {
        val p = q.map { Pt(it.x * (src.width - 1), it.y * (src.height - 1)) }
        val w = max(hypot(p.tr.x - p.tl.x, p.tr.y - p.tl.y), hypot(p.br.x - p.bl.x, p.br.y - p.bl.y))
        val h = max(hypot(p.bl.x - p.tl.x, p.bl.y - p.tl.y), hypot(p.br.x - p.tr.x, p.br.y - p.tr.y))
        val s = min(1f, maxSide / max(w, h).coerceAtLeast(1f))
        return (w * s).roundToInt().coerceAtLeast(1) to (h * s).roundToInt().coerceAtLeast(1)
    }

    /**
     * The page inside [q] (fractions of [src]'s size) straightened into a rectangle, as if
     * photographed from straight above, at most [maxSide] pixels across.
     */
    fun flatten(src: Pixels, q: Quad, maxSide: Int): Pixels {
        val (w, h) = flatSize(src, q, maxSide)
        // A hair inside the edges, so no sliver of the table shows along them.
        val cx = q.points.sumOf { it.x.toDouble() }.toFloat() / 4
        val cy = q.points.sumOf { it.y.toDouble() }.toFloat() / 4
        val p = q.map { Pt((it.x + (cx - it.x) * 0.006f) * (src.width - 1), (it.y + (cy - it.y) * 0.006f) * (src.height - 1)) }
        val m = homography(
            listOf(Pt(0f, 0f), Pt(w - 1f, 0f), Pt(w - 1f, h - 1f), Pt(0f, h - 1f)),
            p.points,
        )
        val out = IntArray(w * h)
        for (y in 0 until h) {
            for (x in 0 until w) {
                val d = m[6] * x + m[7] * y + 1.0
                val sx = (m[0] * x + m[1] * y + m[2]) / d
                val sy = (m[3] * x + m[4] * y + m[5]) / d
                out[y * w + x] = sample(src, sx.toFloat(), sy.toFloat())
            }
        }
        return Pixels(out, w, h)
    }

    /** Bilinear: the colour between pixels, for smooth straightened text. */
    private fun sample(src: Pixels, x: Float, y: Float): Int {
        val x0 = x.toInt().coerceIn(0, src.width - 1)
        val y0 = y.toInt().coerceIn(0, src.height - 1)
        val x1 = min(x0 + 1, src.width - 1)
        val y1 = min(y0 + 1, src.height - 1)
        val fx = (x - x0).coerceIn(0f, 1f)
        val fy = (y - y0).coerceIn(0f, 1f)
        val a = src.argb[y0 * src.width + x0]
        val b = src.argb[y0 * src.width + x1]
        val c = src.argb[y1 * src.width + x0]
        val d = src.argb[y1 * src.width + x1]
        fun ch(shift: Int): Int {
            val top = ((a shr shift) and 0xFF) * (1 - fx) + ((b shr shift) and 0xFF) * fx
            val bottom = ((c shr shift) and 0xFF) * (1 - fx) + ((d shr shift) and 0xFF) * fx
            return (top * (1 - fy) + bottom * fy).roundToInt().coerceIn(0, 255)
        }
        return (0xFF shl 24) or (ch(16) shl 16) or (ch(8) shl 8) or ch(0)
    }

    /** The 3×3 projective map (last entry 1) taking each of [from] to the matching point of [to]. */
    fun homography(from: List<Pt>, to: List<Pt>): DoubleArray {
        val a = Array(8) { DoubleArray(9) }
        for (i in 0 until 4) {
            val x = from[i].x.toDouble()
            val y = from[i].y.toDouble()
            val u = to[i].x.toDouble()
            val v = to[i].y.toDouble()
            a[2 * i] = doubleArrayOf(x, y, 1.0, 0.0, 0.0, 0.0, -u * x, -u * y, u)
            a[2 * i + 1] = doubleArrayOf(0.0, 0.0, 0.0, x, y, 1.0, -v * x, -v * y, v)
        }
        // Gaussian elimination with partial pivoting.
        for (col in 0 until 8) {
            val pivot = (col until 8).maxByOrNull { kotlin.math.abs(a[it][col]) }!!
            val t = a[col]
            a[col] = a[pivot]
            a[pivot] = t
            val pv = a[col][col]
            if (kotlin.math.abs(pv) < 1e-12) continue
            for (k in col until 9) a[col][k] /= pv
            for (r in 0 until 8) {
                if (r == col) continue
                val f = a[r][col]
                if (f == 0.0) continue
                for (k in col until 9) a[r][k] -= f * a[col][k]
            }
        }
        return DoubleArray(8) { a[it][8] }
    }

    /** Cleans up a flattened page the way [look] says. */
    fun enhance(src: Pixels, look: ScanLook): Pixels {
        if (look == ScanLook.ORIGINAL) return src
        val cleaned = clean(src, look)
        // Black and white is already crisp; the others get a light sharpening for text and detail.
        return if (look == ScanLook.BW) cleaned else sharpen(cleaned, 0.4f)
    }

    /**
     * How much of a page looks like plain paper (bright and without colour): high for letters
     * and forms, low for covers, photos and posters, which want their colours brought out instead.
     */
    fun paperShare(src: Pixels): Float {
        // Measured against the paper's brightness nearby, so a shadow over the page doesn't count against it.
        val small = src.shrunk(400)
        val lum = small.luminance()
        val light = paperLight(lum, small.width, small.height)
        var paper = 0
        for (i in lum.indices) {
            val c = small.argb[i]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            val chroma = max(r, max(g, b)) - min(r, min(g, b))
            if (lum[i] >= light[i] * 0.85f && chroma < 40 && light[i] > 90) paper++
        }
        return paper.toFloat() / lum.size
    }

    /**
     * A colourful page made clear and natural, like a phone camera's own processing: the colour
     * cast taken out and the darkest and brightest tones stretched to black and white per colour
     * (a little is let through at each end so highlights don't burn out), then a little more colour.
     */
    private fun vivid(src: Pixels): Pixels {
        val hist = Array(3) { IntArray(256) }
        for (c in src.argb) {
            hist[0][(c shr 16) and 0xFF]++
            hist[1][(c shr 8) and 0xFF]++
            hist[2][c and 0xFF]++
        }
        val n = src.argb.size
        val lo = IntArray(3)
        val hi = IntArray(3)
        for (ch in 0 until 3) {
            var acc = 0
            lo[ch] = (0 until 256).first { acc += hist[ch][it]; acc > n * 0.006 }
            acc = 0
            hi[ch] = (255 downTo 0).first { acc += hist[ch][it]; acc > n * 0.006 }
            if (hi[ch] - lo[ch] < 40) {
                lo[ch] = 0
                hi[ch] = 255
            }
        }
        val out = IntArray(n)
        for (i in 0 until n) {
            val c = src.argb[i]
            var r = (((c shr 16) and 0xFF) - lo[0]).toFloat() / (hi[0] - lo[0])
            var g = (((c shr 8) and 0xFF) - lo[1]).toFloat() / (hi[1] - lo[1])
            var b = ((c and 0xFF) - lo[2]).toFloat() / (hi[2] - lo[2])
            val m = (r + g + b) / 3f
            r = m + (r - m) * 1.15f
            g = m + (g - m) * 1.15f
            b = m + (b - m) * 1.15f
            // A gentle S-curve for depth.
            fun tone(v: Float): Int {
                val x = v.coerceIn(0f, 1f)
                return (255 * (x * x * (3 - 2 * x) * 0.35f + x * 0.65f)).roundToInt().coerceIn(0, 255)
            }
            out[i] = (0xFF shl 24) or (tone(r) shl 16) or (tone(g) shl 8) or tone(b)
        }
        return Pixels(out, src.width, src.height)
    }

    /** Unsharp masking: each pixel pushed away from the blur around it, so edges and letters stand out. */
    fun sharpen(src: Pixels, amount: Float): Pixels {
        val w = src.width
        val h = src.height
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var r = 0
            var g = 0
            var b = 0
            for (dy in -1..1) for (dx in -1..1) {
                val c = src.argb[(y + dy).coerceIn(0, h - 1) * w + (x + dx).coerceIn(0, w - 1)]
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
            }
            val c = src.argb[y * w + x]
            fun ch(v: Int, blur: Int) = (v + amount * (v - blur / 9f)).roundToInt().coerceIn(0, 255)
            out[y * w + x] = (0xFF shl 24) or (ch((c shr 16) and 0xFF, r) shl 16) or (ch((c shr 8) and 0xFF, g) shl 8) or ch(c and 0xFF, b)
        }
        return Pixels(out, w, h)
    }

    private fun clean(src: Pixels, look: ScanLook): Pixels {
        // Covers and photos aren't paper to whiten: bring their colours out instead.
        if (look == ScanLook.AUTO && paperShare(src) < 0.35f) return vivid(src)
        val w = src.width
        val h = src.height
        val lum = src.luminance()
        val paper = paperLight(lum, w, h)
        val out = IntArray(w * h)
        when (look) {
            ScanLook.BW -> for (i in out.indices) {
                // Ink is what's clearly darker than the paper around it; a soft step keeps letters smooth.
                val n = lum[i] / paper[i]
                val v = ((n - 0.58f) / (0.78f - 0.58f)).coerceIn(0f, 1f)
                val g = (v * 255).roundToInt()
                out[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
            ScanLook.GRAY -> for (i in out.indices) {
                val g = stretch(lum[i] / paper[i])
                out[i] = (0xFF shl 24) or (g shl 16) or (g shl 8) or g
            }
            else -> {
                // The paper's own colour across the page, to take a lamp's tint out of it.
                var pr = 0.0
                var pg = 0.0
                var pb = 0.0
                var n = 0
                for (i in out.indices) {
                    if (lum[i] < paper[i] * 0.92f) continue
                    val c = src.argb[i]
                    pr += (c shr 16) and 0xFF
                    pg += (c shr 8) and 0xFF
                    pb += c and 0xFF
                    n++
                }
                val l = if (n == 0) 1.0 else (pr * 0.299 + pg * 0.587 + pb * 0.114) / n
                val wr = if (n == 0) 1.0 else l / (pr / n).coerceAtLeast(1.0)
                val wg = if (n == 0) 1.0 else l / (pg / n).coerceAtLeast(1.0)
                val wb = if (n == 0) 1.0 else l / (pb / n).coerceAtLeast(1.0)
                for (i in out.indices) {
                    val c = src.argb[i]
                    val k = 1f / paper[i]
                    var r = (((c shr 16) and 0xFF) * wr * k).toFloat()
                    var g = (((c shr 8) and 0xFF) * wg * k).toFloat()
                    var b = ((c and 0xFF) * wb * k).toFloat()
                    // Whiten the paper by brightness alone, scaling the three colours together, so pale
                    // colours (a flag, a logo, a coloured band) keep their hue instead of bleaching out.
                    val l = (r * 0.299f + g * 0.587f + b * 0.114f).coerceAtLeast(0.002f)
                    val s = softCurve(l) / l
                    r *= s
                    g *= s
                    b *= s
                    // And a little richer, as colour on paper looks to the eye.
                    val m = (r + g + b) / 3f
                    r = m + (r - m) * 1.25f
                    g = m + (g - m) * 1.25f
                    b = m + (b - m) * 1.25f
                    out[i] = (0xFF shl 24) or (unit(r) shl 16) or (unit(g) shl 8) or unit(b)
                }
            }
        }
        return Pixels(out, w, h)
    }

    /** Paper at or near full brightness becomes white; darker tones keep their steps. */
    private fun stretch(n: Float): Int = (((n - 0.12f) / (0.86f - 0.12f)) * 255).roundToInt().coerceIn(0, 255)

    private fun unit(v: Float): Int = (v * 255).roundToInt().coerceIn(0, 255)

    /** Brightness (0–1 of the paper's) to how bright it is shown: paper white, ink dark, mid-tones kept. */
    private fun softCurve(n: Float): Float {
        val v = ((n - 0.04f) / (0.90f - 0.04f)).coerceIn(0f, 1f)
        return v * (1.15f - 0.15f * v)
    }

    /** For colour: paper turns white, but ink and pictures keep their depth instead of going harsh. */
    private fun soft(n: Float): Int {
        val v = ((n - 0.04f) / (0.90f - 0.04f)).coerceIn(0f, 1f)
        // A gentle curve: lifts the paper to white without crushing darker colours.
        return (255 * (v * (1.15f - 0.15f * v))).roundToInt().coerceIn(0, 255)
    }

    /**
     * How bright the paper is around each pixel (0–255), so shadows and uneven light can be
     * evened out: a bright percentile in each cell of a grid (ink in a cell doesn't count), smoothed
     * between cells.
     */
    fun paperLight(lum: IntArray, w: Int, h: Int): FloatArray {
        val cell = max(8, min(w, h) / 12)
        val gw = (w + cell - 1) / cell
        val gh = (h + cell - 1) / cell
        val grid = FloatArray(gw * gh)
        val hist = IntArray(256)
        for (gy in 0 until gh) for (gx in 0 until gw) {
            hist.fill(0)
            var n = 0
            for (y in gy * cell until min(h, (gy + 1) * cell)) for (x in gx * cell until min(w, (gx + 1) * cell)) {
                hist[lum[y * w + x]]++
                n++
            }
            var seen = 0
            var level = 255
            for (v in 255 downTo 0) {
                seen += hist[v]
                if (seen >= n * 0.1) {
                    level = v
                    break
                }
            }
            grid[gy * gw + gx] = level.toFloat()
        }
        // A cell all of ink (a big black heading) borrows its neighbours' paper.
        val fixed = grid.copyOf()
        for (gy in 0 until gh) for (gx in 0 until gw) {
            var best = grid[gy * gw + gx]
            for (dy in -1..1) for (dx in -1..1) {
                val nx = gx + dx
                val ny = gy + dy
                if (nx in 0 until gw && ny in 0 until gh) best = max(best, grid[ny * gw + nx] * 0.92f)
            }
            fixed[gy * gw + gx] = max(best, 24f)
        }
        val out = FloatArray(w * h)
        for (y in 0 until h) {
            val fy = ((y + 0.5f) / cell - 0.5f).coerceIn(0f, (gh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, gh - 1)
            val ty = fy - y0
            for (x in 0 until w) {
                val fx = ((x + 0.5f) / cell - 0.5f).coerceIn(0f, (gw - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, gw - 1)
                val tx = fx - x0
                val top = fixed[y0 * gw + x0] * (1 - tx) + fixed[y0 * gw + x1] * tx
                val bottom = fixed[y1 * gw + x0] * (1 - tx) + fixed[y1 * gw + x1] * tx
                out[y * w + x] = top * (1 - ty) + bottom * ty
            }
        }
        return out
    }
}
