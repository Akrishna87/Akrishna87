package io.github.akrishna87.pakkam.pdf

import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * Lifts handwriting off a photo of paper: the paper becomes see-through and only the ink is
 * kept, so a signature signed on paper can be placed on a PDF like one drawn on the screen.
 *
 * Works on plain ARGB pixel arrays. Shadows and uneven light are evened out by comparing each
 * pixel with the brightness of the paper around it, not with one fixed level.
 */
class InkLifter(private val argb: IntArray, val width: Int, val height: Int) {
    /** Each pixel's brightness divided by the paper's around it: about 1 on paper, lower on ink. */
    private val ratio = FloatArray(width * height)

    init {
        require(argb.size == width * height) { "Wrong pixel count" }
        measurePaper()
    }

    private fun lum(c: Int): Int = (((c shr 16) and 0xFF) * 299 + ((c shr 8) and 0xFF) * 587 + (c and 0xFF) * 114) / 1000

    private fun measurePaper() {
        // The paper's brightness in a grid of cells: a bright percentile, so ink in a cell doesn't count.
        val cell = max(8, min(width, height) / 20)
        val gw = (width + cell - 1) / cell
        val gh = (height + cell - 1) / cell
        val grid = FloatArray(gw * gh)
        val hist = IntArray(256)
        for (gy in 0 until gh) for (gx in 0 until gw) {
            hist.fill(0)
            var n = 0
            for (y in gy * cell until min(height, (gy + 1) * cell)) for (x in gx * cell until min(width, (gx + 1) * cell)) {
                hist[lum(argb[y * width + x])]++
                n++
            }
            var seen = 0
            var level = 255
            val want = n * 0.10
            for (v in 255 downTo 0) {
                seen += hist[v]
                if (seen >= want) {
                    level = v
                    break
                }
            }
            grid[gy * gw + gx] = max(level, 24).toFloat()
        }
        // Smoothly between cell centres.
        for (y in 0 until height) {
            val fy = ((y + 0.5f) / cell - 0.5f).coerceIn(0f, (gh - 1).toFloat())
            val y0 = fy.toInt()
            val y1 = min(y0 + 1, gh - 1)
            val ty = fy - y0
            for (x in 0 until width) {
                val fx = ((x + 0.5f) / cell - 0.5f).coerceIn(0f, (gw - 1).toFloat())
                val x0 = fx.toInt()
                val x1 = min(x0 + 1, gw - 1)
                val tx = fx - x0
                val top = grid[y0 * gw + x0] * (1 - tx) + grid[y0 * gw + x1] * tx
                val bottom = grid[y1 * gw + x0] * (1 - tx) + grid[y1 * gw + x1] * tx
                val paper = top * (1 - ty) + bottom * ty
                ratio[y * width + x] = (lum(argb[y * width + x]) / paper).coerceIn(0f, 1.5f)
            }
        }
    }

    /**
     * How much ink each pixel of [crop] holds, 0 to 255, with specks of dirt and paper grain
     * left out. [strength] from 0 (keep faint strokes) to 1 (clean the paper hard).
     */
    fun inkAlpha(crop: IntArray, strength: Float): IntArray {
        val (cx0, cy0, cx1, cy1) = clampCrop(crop)
        val cw = cx1 - cx0
        val ch = cy1 - cy0
        val hi = 0.92f - 0.22f * strength.coerceIn(0f, 1f)
        val lo = hi - 0.24f
        val alpha = IntArray(cw * ch)
        for (y in 0 until ch) for (x in 0 until cw) {
            val r = ratio[(y + cy0) * width + x + cx0]
            alpha[y * cw + x] = (((hi - r) / (hi - lo)).coerceIn(0f, 1f) * 255).roundToInt()
        }
        // Strokes are the solid parts; anything too small to be writing is dirt or grain.
        val solid = BooleanArray(alpha.size) { alpha[it] >= 110 }
        val keep = BooleanArray(alpha.size)
        val minArea = max(12, cw * ch / 25_000)
        val label = IntArray(alpha.size) { -1 }
        val stack = IntArray(alpha.size)
        val members = ArrayList<Int>()
        for (start in alpha.indices) {
            if (!solid[start] || label[start] >= 0) continue
            members.clear()
            var top = 0
            stack[top++] = start
            label[start] = start
            while (top > 0) {
                val p = stack[--top]
                members += p
                val px = p % cw
                val py = p / cw
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx
                    val ny = py + dy
                    if (nx < 0 || ny < 0 || nx >= cw || ny >= ch) continue
                    val q = ny * cw + nx
                    if (solid[q] && label[q] < 0) {
                        label[q] = start
                        stack[top++] = q
                    }
                }
            }
            if (members.size >= minArea) for (p in members) keep[p] = true
        }
        // Soft edges next to kept strokes stay, for smooth lines; faint pixels elsewhere go.
        val out = IntArray(alpha.size)
        for (p in alpha.indices) {
            if (alpha[p] == 0) continue
            if (keep[p]) {
                out[p] = alpha[p]
                continue
            }
            val px = p % cw
            val py = p / cw
            var near = false
            for (dy in -1..1) for (dx in -1..1) {
                val nx = px + dx
                val ny = py + dy
                if (nx in 0 until cw && ny in 0 until ch && keep[ny * cw + nx]) near = true
            }
            if (near) out[p] = alpha[p]
        }
        return out
    }

    /**
     * Where the writing is: left, top, right, bottom in pixels, with a little room around it,
     * leaving out marks that touch the edge of the photo (table edges, shadows, fingers).
     * Null when no writing is found.
     */
    fun findInk(strength: Float = 0.5f): IntArray? {
        val full = intArrayOf(0, 0, width, height)
        val a = inkAlpha(full, strength)
        val seen = BooleanArray(a.size)
        var box: IntArray? = null
        val stack = IntArray(a.size)
        for (start in a.indices) {
            if (a[start] < 110 || seen[start]) continue
            var top = 0
            stack[top++] = start
            seen[start] = true
            var x0 = width
            var y0 = height
            var x1 = 0
            var y1 = 0
            var count = 0
            while (top > 0) {
                val p = stack[--top]
                count++
                val px = p % width
                val py = p / width
                x0 = min(x0, px)
                y0 = min(y0, py)
                x1 = max(x1, px)
                y1 = max(y1, py)
                for (dy in -1..1) for (dx in -1..1) {
                    val nx = px + dx
                    val ny = py + dy
                    if (nx < 0 || ny < 0 || nx >= width || ny >= height) continue
                    val q = ny * width + nx
                    if (a[q] >= 110 && !seen[q]) {
                        seen[q] = true
                        stack[top++] = q
                    }
                }
            }
            val touchesEdge = x0 <= 1 || y0 <= 1 || x1 >= width - 2 || y1 >= height - 2
            if (touchesEdge || count < max(12, width * height / 25_000)) continue
            val b = box
            box = if (b == null) intArrayOf(x0, y0, x1 + 1, y1 + 1) else intArrayOf(min(b[0], x0), min(b[1], y0), max(b[2], x1 + 1), max(b[3], y1 + 1))
        }
        val b = box ?: return null
        val pad = max(6, max(b[2] - b[0], b[3] - b[1]) / 20)
        return intArrayOf(max(0, b[0] - pad), max(0, b[1] - pad), min(width, b[2] + pad), min(height, b[3] + pad))
    }

    /**
     * The ink's own colour, as it would look on white paper under white light, darkened a little
     * so it reads like fresh ink rather than a photo of it.
     */
    fun inkColor(crop: IntArray, alpha: IntArray): Int {
        val (cx0, cy0, cx1, _) = clampCrop(crop)
        val cw = cx1 - cx0
        var ir = 0.0
        var ig = 0.0
        var ib = 0.0
        var inkN = 0
        var pr = 0.0
        var pg = 0.0
        var pb = 0.0
        var paperN = 0
        for (i in alpha.indices) {
            val c = argb[(i / cw + cy0) * width + i % cw + cx0]
            val r = (c shr 16) and 0xFF
            val g = (c shr 8) and 0xFF
            val b = c and 0xFF
            if (alpha[i] >= 230) {
                ir += r; ig += g; ib += b; inkN++
            } else if (alpha[i] == 0) {
                pr += r; pg += g; pb += b; paperN++
            }
        }
        if (inkN == 0) return DARK_BLUE
        // White-balance against the paper, so yellow lamplight doesn't tint the ink.
        val wr = if (paperN > 0) 255.0 / max(1.0, pr / paperN) else 1.0
        val wg = if (paperN > 0) 255.0 / max(1.0, pg / paperN) else 1.0
        val wb = if (paperN > 0) 255.0 / max(1.0, pb / paperN) else 1.0
        var r = (ir / inkN * wr).coerceIn(0.0, 255.0)
        var g = (ig / inkN * wg).coerceIn(0.0, 255.0)
        var b = (ib / inkN * wb).coerceIn(0.0, 255.0)
        val l = (r * 0.299 + g * 0.587 + b * 0.114).coerceAtLeast(1.0)
        if (l > 70) {
            val k = 70 / l
            r *= k; g *= k; b *= k
        }
        return (0xFF shl 24) or (r.roundToInt() shl 16) or (g.roundToInt() shl 8) or b.roundToInt()
    }

    /** The ink of [crop] in one colour on a see-through background, trimmed to the writing. */
    fun lift(crop: IntArray, strength: Float, color: Int?): Lifted? {
        val c = clampCrop(crop)
        val cw = c[2] - c[0]
        val alpha = inkAlpha(c, strength)
        var x0 = cw
        var y0 = alpha.size / max(1, cw)
        var x1 = -1
        var y1 = -1
        for (i in alpha.indices) {
            if (alpha[i] < 40) continue
            val x = i % cw
            val y = i / cw
            x0 = min(x0, x); y0 = min(y0, y); x1 = max(x1, x); y1 = max(y1, y)
        }
        if (x1 < 0) return null
        val pad = 4
        x0 = max(0, x0 - pad)
        y0 = max(0, y0 - pad)
        x1 = min(cw - 1, x1 + pad)
        y1 = min(alpha.size / cw - 1, y1 + pad)
        val ink = (color ?: inkColor(c, alpha)) and 0xFFFFFF
        val w = x1 - x0 + 1
        val h = y1 - y0 + 1
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            out[y * w + x] = (alpha[(y + y0) * cw + x + x0] shl 24) or ink
        }
        return Lifted(out, w, h)
    }

    private fun clampCrop(crop: IntArray): IntArray {
        val x0 = crop[0].coerceIn(0, width - 1)
        val y0 = crop[1].coerceIn(0, height - 1)
        val x1 = crop[2].coerceIn(x0 + 1, width)
        val y1 = crop[3].coerceIn(y0 + 1, height)
        return intArrayOf(x0, y0, x1, y1)
    }

    class Lifted(val argb: IntArray, val width: Int, val height: Int)

    companion object {
        const val DARK_BLUE = 0xFF0D1B6E.toInt()
    }
}
