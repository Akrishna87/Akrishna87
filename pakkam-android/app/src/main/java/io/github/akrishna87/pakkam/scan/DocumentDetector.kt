package io.github.akrishna87.pakkam.scan

import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin
import kotlin.math.sqrt

/** A point in a picture, in pixels or as a fraction of its width and height. */
data class Pt(val x: Float, val y: Float)

/**
 * The four corners of a page in a photo: top left, top right, bottom right, bottom left, each as a
 * fraction (0 to 1) of the picture's width and height, so it fits any size of the same picture.
 */
data class Quad(val tl: Pt, val tr: Pt, val br: Pt, val bl: Pt) {
    val points get() = listOf(tl, tr, br, bl)

    fun map(f: (Pt) -> Pt) = Quad(f(tl), f(tr), f(br), f(bl))

    /** The corner [i] (0 top left, then clockwise) moved to [p]. */
    fun with(i: Int, p: Pt) = when (i) {
        0 -> copy(tl = p)
        1 -> copy(tr = p)
        2 -> copy(br = p)
        else -> copy(bl = p)
    }

    /** True when the corners go round clockwise without crossing, so the page can be flattened. */
    val isConvex: Boolean
        get() {
            val p = points
            var sign = 0
            for (i in 0 until 4) {
                val a = p[i]
                val b = p[(i + 1) % 4]
                val c = p[(i + 2) % 4]
                val cross = (b.x - a.x) * (c.y - b.y) - (b.y - a.y) * (c.x - b.x)
                val s = if (cross > 0) 1 else if (cross < 0) -1 else 0
                if (s == 0) return false
                if (sign == 0) sign = s else if (s != sign) return false
            }
            return true
        }

    /** The area inside, in the same units squared. */
    val area: Float
        get() {
            val p = points
            var a = 0f
            for (i in 0 until 4) a += p[i].x * p[(i + 1) % 4].y - p[(i + 1) % 4].x * p[i].y
            return abs(a) / 2f
        }

    companion object {
        val FULL = Quad(Pt(0f, 0f), Pt(1f, 0f), Pt(1f, 1f), Pt(0f, 1f))

        /** Puts four corners in order: top left, top right, bottom right, bottom left. */
        fun ordered(pts: List<Pt>): Quad {
            val cx = pts.sumOf { it.x.toDouble() }.toFloat() / pts.size
            val cy = pts.sumOf { it.y.toDouble() }.toFloat() / pts.size
            // Round the centre from straight up-left, clockwise.
            val sorted = pts.sortedBy { p ->
                val a = kotlin.math.atan2((p.y - cy).toDouble(), (p.x - cx).toDouble())
                (a + 3 * PI / 4 + 2 * PI) % (2 * PI)
            }
            return Quad(sorted[0], sorted[1], sorted[2], sorted[3])
        }
    }
}

/**
 * Finds the page in a photo of a document, the way scanner apps outline it: the edges in the
 * picture are found, straight lines are picked out of them, and the four lines that best make up
 * a large, well-edged four-sided shape are taken as the page's sides. No AI: plain geometry.
 *
 * Works on a small greyscale picture (about 300 pixels across is plenty, and quick).
 */
object DocumentDetector {

    /** Finds the page in [lum] (brightness 0–255, [w] × [h]); null when no clear page shows. */
    fun detect(lum: IntArray, w: Int, h: Int): Quad? {
        if (w < 32 || h < 32) return null
        val blurred = blur(blur(lum, w, h), w, h)
        val edges = edgeMap(blurred, w, h)
        val lines = houghLines(edges, w, h)
        val horizontal = lines.filter { it.horizontal }.take(MAX_LINES)
        val vertical = lines.filter { !it.horizontal }.take(MAX_LINES)
        if (horizontal.size < 2 || vertical.size < 2) return null
        val near = dilate(edges, w, h)

        var best: Quad? = null
        var bestScore = 0f
        val minArea = MIN_AREA * w * h
        for (i in horizontal.indices) for (j in horizontal.indices) {
            val top = horizontal[i]
            val bottom = horizontal[j]
            if (top.at(w / 2f) >= bottom.at(w / 2f) - h * 0.12f) continue
            for (k in vertical.indices) for (l in vertical.indices) {
                val left = vertical[k]
                val right = vertical[l]
                if (left.at(h / 2f) >= right.at(h / 2f) - w * 0.12f) continue
                val tl = top.cross(left) ?: continue
                val tr = top.cross(right) ?: continue
                val br = bottom.cross(right) ?: continue
                val bl = bottom.cross(left) ?: continue
                val q = Quad(tl, tr, br, bl)
                if (q.points.any { it.x < -w * 0.04f || it.y < -h * 0.04f || it.x > w * 1.04f || it.y > h * 1.04f }) continue
                if (!q.isConvex || q.area < minArea) continue
                val score = support(q, near, w, h) ?: continue
                if (score > bestScore) {
                    bestScore = score
                    best = q
                }
            }
        }
        val q = best ?: return null
        return q.map { Pt((it.x / (w - 1)).coerceIn(0f, 1f), (it.y / (h - 1)).coerceIn(0f, 1f)) }
    }

    private const val MAX_LINES = 8
    private const val MIN_AREA = 0.12f

    /** A straight line x·cos θ + y·sin θ = ρ, with how many edge points lie on it. */
    private class Line(val theta: Double, val rho: Double, val votes: Int) {
        val c = cos(theta)
        val s = sin(theta)

        /** Mostly across the picture (its normal points up or down). */
        val horizontal = abs(s) > abs(c)

        /** Where the line is: its y at [x] when it runs across, its x at [y] when it runs up and down. */
        fun at(v: Float): Float = if (horizontal) ((rho - v * c) / s).toFloat() else ((rho - v * s) / c).toFloat()

        fun cross(o: Line): Pt? {
            val d = c * o.s - s * o.c
            if (abs(d) < 1e-6) return null
            return Pt(((rho * o.s - s * o.rho) / d).toFloat(), ((c * o.rho - rho * o.c) / d).toFloat())
        }
    }

    private fun blur(src: IntArray, w: Int, h: Int): IntArray {
        val tmp = IntArray(w * h)
        val out = IntArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            for (d in -1..1) s += src[y * w + (x + d).coerceIn(0, w - 1)]
            tmp[y * w + x] = s / 3
        }
        for (y in 0 until h) for (x in 0 until w) {
            var s = 0
            for (d in -1..1) s += tmp[(y + d).coerceIn(0, h - 1) * w + x]
            out[y * w + x] = s / 3
        }
        return out
    }

    /** Thin edges: where brightness changes sharply, kept only at the strongest point across the edge. */
    private fun edgeMap(src: IntArray, w: Int, h: Int): BooleanArray {
        val mag = FloatArray(w * h)
        val dir = ByteArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val gx = (src[i - w + 1] + 2 * src[i + 1] + src[i + w + 1]) - (src[i - w - 1] + 2 * src[i - 1] + src[i + w - 1])
            val gy = (src[i + w - 1] + 2 * src[i + w] + src[i + w + 1]) - (src[i - w - 1] + 2 * src[i - w] + src[i - w + 1])
            mag[i] = hypot(gx.toFloat(), gy.toFloat())
            val ax = abs(gx)
            val ay = abs(gy)
            dir[i] = when {
                ay * 5 < ax * 2 -> 0 // across: compare left and right
                ax * 5 < ay * 2 -> 1 // up and down
                (gx > 0) == (gy > 0) -> 2
                else -> 3
            }
        }
        // Keep the strongest edges: above a fixed floor and among the strongest tenth.
        val sorted = mag.filter { it > 0f }.sorted()
        val floor = if (sorted.isEmpty()) Float.MAX_VALUE else max(60f, sorted[(sorted.size * 0.88).toInt().coerceAtMost(sorted.lastIndex)])
        val out = BooleanArray(w * h)
        for (y in 1 until h - 1) for (x in 1 until w - 1) {
            val i = y * w + x
            val m = mag[i]
            if (m < floor) continue
            val (a, b) = when (dir[i].toInt()) {
                0 -> mag[i - 1] to mag[i + 1]
                1 -> mag[i - w] to mag[i + w]
                2 -> mag[i - w - 1] to mag[i + w + 1]
                else -> mag[i - w + 1] to mag[i + w - 1]
            }
            if (m >= a && m >= b) out[i] = true
        }
        return out
    }

    private fun dilate(src: BooleanArray, w: Int, h: Int): BooleanArray {
        val out = BooleanArray(w * h)
        for (y in 0 until h) for (x in 0 until w) {
            if (!src[y * w + x]) continue
            for (dy in -2..2) for (dx in -2..2) {
                val nx = x + dx
                val ny = y + dy
                if (nx in 0 until w && ny in 0 until h) out[ny * w + nx] = true
            }
        }
        return out
    }

    /** The strongest straight lines among the edges, strongest first, one per bundle of near-identical lines. */
    private fun houghLines(edges: BooleanArray, w: Int, h: Int): List<Line> {
        val thetas = 180
        val diag = sqrt((w * w + h * h).toDouble()).toInt() + 1
        val rhos = 2 * diag + 1
        val acc = IntArray(thetas * rhos)
        val cs = DoubleArray(thetas) { cos(it * PI / thetas) }
        val sn = DoubleArray(thetas) { sin(it * PI / thetas) }
        for (y in 0 until h) for (x in 0 until w) {
            if (!edges[y * w + x]) continue
            for (t in 0 until thetas) {
                val r = (x * cs[t] + y * sn[t]).roundToInt() + diag
                acc[t * rhos + r]++
            }
        }
        val minVotes = (min(w, h) * 0.2).toInt()
        val peaks = ArrayList<Line>()
        for (t in 0 until thetas) for (r in 0 until rhos) {
            val v = acc[t * rhos + r]
            if (v < minVotes) continue
            var isMax = true
            loop@ for (dt in -4..4) for (dr in -5..5) {
                if (dt == 0 && dr == 0) continue
                val tt = t + dt
                val rr = r + dr
                // θ wraps round: a line at 179° is next to one at 0° with its ρ turned over.
                val (t2, r2) = when {
                    tt < 0 -> (tt + thetas) to (rhos - 1 - rr)
                    tt >= thetas -> (tt - thetas) to (rhos - 1 - rr)
                    else -> tt to rr
                }
                if (r2 !in 0 until rhos) continue
                val o = acc[t2 * rhos + r2]
                if (o > v || (o == v && (dt < 0 || (dt == 0 && dr < 0)))) {
                    isMax = false
                    break@loop
                }
            }
            if (isMax) peaks += Line(t * PI / thetas, (r - diag).toDouble(), v)
        }
        // Lines up to 40° off level count as the page's top or bottom, the rest as its sides.
        return peaks.filter { l -> abs(l.s) > 0.64 || abs(l.c) > 0.64 }.sortedByDescending { it.votes }.take(40)
    }

    /**
     * How well a shape follows real edges: the length of its sides that run along edges in the
     * picture. Null when any side is mostly not on an edge (then it isn't a page's side).
     */
    private fun support(q: Quad, near: BooleanArray, w: Int, h: Int): Float? {
        var total = 0f
        val p = q.points
        for (i in 0 until 4) {
            val a = p[i]
            val b = p[(i + 1) % 4]
            val len = hypot(b.x - a.x, b.y - a.y)
            val steps = max(2, (len / 2f).toInt())
            var hit = 0
            var inside = 0
            for (k in 0..steps) {
                val x = (a.x + (b.x - a.x) * k / steps).roundToInt()
                val y = (a.y + (b.y - a.y) * k / steps).roundToInt()
                if (x !in 0 until w || y !in 0 until h) continue
                inside++
                if (near[y * w + x]) hit++
            }
            // Sides running along the picture's border can't be seen; let them through on their own.
            val onBorder = (a.x < 3 && b.x < 3) || (a.y < 3 && b.y < 3) || (a.x > w - 4 && b.x > w - 4) || (a.y > h - 4 && b.y > h - 4)
            val frac = if (inside == 0) 0f else hit.toFloat() / inside
            // A side made of two different edges (a notebook's top and the box under it) is only partly
            // on an edge; squaring the share favours sides that follow one edge all the way.
            if (frac < 0.6f && !onBorder) return null
            total += len * (if (onBorder) 0.2f else frac * frac)
        }
        return total
    }
}
