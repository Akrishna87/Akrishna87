package io.github.akrishna87.pakkam.pdf

import android.graphics.Bitmap
import android.graphics.PointF
import android.graphics.RectF
import android.os.ParcelFileDescriptor
import io.legere.pdfiumandroid.PdfDocument
import io.legere.pdfiumandroid.PdfPasswordException
import io.legere.pdfiumandroid.PdfiumCore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.asCoroutineDispatcher
import kotlinx.coroutines.withContext
import java.io.Closeable
import java.io.File
import java.io.IOException

/** A page's size in PDF points (1/72 inch), as it is shown: width and height already swapped for turned pages. */
data class PageSize(val width: Float, val height: Float) {
    val aspect: Float get() = if (height > 0f) width / height else 0.7f
}

/** One entry of the document's own table of contents. */
data class OutlineItem(val title: String, val page: Int, val depth: Int)

/** A tappable link on a page, in page space. */
data class PageLink(val rect: PdfRect, val page: Int?, val uri: String?)

class WrongPasswordException : IOException("This PDF needs a password")

/**
 * An open PDF, drawn by PDFium.
 *
 * Every call goes through one background thread: PDFium is not thread safe, and keeping its
 * work in one queue also means a page that scrolled away doesn't block the one that came in.
 */
class PdfDoc private constructor(
    val file: File,
    val password: String?,
    private val pfd: ParcelFileDescriptor,
    private val doc: PdfDocument,
) : Closeable {

    val pageCount: Int = doc.getPageCount()
    val pageSizes: List<PageSize>
    private val geometries = arrayOfNulls<PageGeometry>(pageCount)
    private val layouts = HashMap<Int, PageTextLayout>()

    @Volatile
    var isClosed = false
        private set

    init {
        pageSizes = List(pageCount) { i ->
            try {
                doc.openPage(i).use { p ->
                    PageSize(p.getPageWidthPoint().toFloat().coerceAtLeast(1f), p.getPageHeightPoint().toFloat().coerceAtLeast(1f))
                }
            } catch (e: Exception) {
                PageSize(612f, 792f)
            }
        }
    }

    /** Draws a whole page into a bitmap of the given size, annotations and form fields included. */
    suspend fun render(page: Int, width: Int, height: Int): Bitmap? = onPdfThread {
        if (isClosed || page !in 0 until pageCount || width <= 0 || height <= 0) return@onPdfThread null
        val bmp = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return@onPdfThread null
        }
        bmp.eraseColor(android.graphics.Color.WHITE)
        doc.openPage(page).use { p ->
            p.renderPageBitmap(bmp, 0, 0, width, height, renderAnnot = true)
        }
        bmp
    }

    /**
     * Draws part of a page: the page is laid out at [fullWidth]×[fullHeight] and the window starting
     * at ([left], [top]) of the bitmap's size is drawn. Used for sharp detail when zoomed in.
     */
    suspend fun renderRegion(page: Int, fullWidth: Int, fullHeight: Int, left: Int, top: Int, width: Int, height: Int): Bitmap? = onPdfThread {
        if (isClosed || page !in 0 until pageCount || width <= 0 || height <= 0) return@onPdfThread null
        val bmp = try {
            Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
        } catch (e: OutOfMemoryError) {
            return@onPdfThread null
        }
        bmp.eraseColor(android.graphics.Color.WHITE)
        doc.openPage(page).use { p ->
            p.renderPageBitmap(bmp, -left, -top, fullWidth, fullHeight, renderAnnot = true)
        }
        bmp
    }

    /** How page space maps to the shown page, for drawing highlights and turning touches into page points. */
    suspend fun geometry(page: Int): PageGeometry = onPdfThread { geometryNow(page) }

    private fun geometryNow(page: Int): PageGeometry {
        geometries[page]?.let { return it }
        val g = doc.openPage(page).use { p ->
            val s = GEOMETRY_SCALE
            val o = p.mapPageCoordsToDevice(0, 0, s, s, 0, 0.0, 0.0)
            val x = p.mapPageCoordsToDevice(0, 0, s, s, 0, 1000.0, 0.0)
            val y = p.mapPageCoordsToDevice(0, 0, s, s, 0, 0.0, 1000.0)
            val f = s.toFloat()
            PageGeometry(
                a = (x.x - o.x) / f / 1000f,
                b = (x.y - o.y) / f / 1000f,
                c = (y.x - o.x) / f / 1000f,
                d = (y.y - o.y) / f / 1000f,
                e = o.x / f,
                f = o.y / f,
                rotation = (p.getPageRotation().coerceAtLeast(0) * 90) % 360,
            )
        }
        geometries[page] = g
        return g
    }

    /** All the text of a page with the position of every character, for search and selection. */
    suspend fun textLayout(page: Int): PageTextLayout = onPdfThread { textLayoutNow(page) }

    private fun textLayoutNow(page: Int): PageTextLayout {
        layouts[page]?.let { return it }
        val layout = try {
            doc.openPage(page).use { p ->
                p.openTextPage().use { tp ->
                    val n = tp.textPageCountChars().coerceAtLeast(0)
                    var text = if (n > 0) tp.textPageGetText(0, n) ?: "" else ""
                    if (text.length > n) text = text.substring(0, n)
                    if (text.length < n) {
                        text = buildString(n) { for (i in 0 until n) append(tp.textPageGetUnicode(i)) }
                    }
                    val boxes = FloatArray(n * 4)
                    for (i in 0 until n) {
                        val r = tp.textPageGetCharBox(i)
                        if (r != null) {
                            boxes[i * 4] = minOf(r.left, r.right)
                            boxes[i * 4 + 1] = minOf(r.top, r.bottom)
                            boxes[i * 4 + 2] = maxOf(r.left, r.right)
                            boxes[i * 4 + 3] = maxOf(r.top, r.bottom)
                        }
                    }
                    PageTextLayout(text, boxes)
                }
            }
        } catch (e: Exception) {
            PageTextLayout("", FloatArray(0))
        }
        // Keep a few pages around; selection and search come back to the same pages.
        if (layouts.size > 24) layouts.remove(layouts.keys.first())
        layouts[page] = layout
        return layout
    }

    suspend fun pageText(page: Int): String = textLayout(page).text

    /** Searches every page. [onPage] is told about each page's hits as they are found. */
    suspend fun search(query: String, onPage: (page: Int, hits: List<SearchHit>) -> Unit) {
        val q = query.trim()
        if (q.isEmpty()) return
        for (page in 0 until pageCount) {
            if (isClosed) return
            val hits = onPdfThread {
                val layout = textLayoutNow(page)
                layout.find(q).map { (start, end) -> SearchHit(page, start, end, layout.rectsFor(start, end)) }
            }
            if (hits.isNotEmpty()) onPage(page, hits)
        }
    }

    suspend fun links(page: Int): List<PageLink> = onPdfThread {
        try {
            doc.openPage(page).use { p ->
                p.getPageLinks().map {
                    PageLink(PdfRect.of(it.bounds), it.destPageIdx?.takeIf { d -> d >= 0 }, it.uri)
                }
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    suspend fun outline(): List<OutlineItem> = onPdfThread {
        val out = ArrayList<OutlineItem>()
        fun walk(items: List<PdfDocument.Bookmark>, depth: Int) {
            for (b in items) {
                out += OutlineItem(b.title?.trim().orEmpty().ifEmpty { "Untitled" }, b.pageIdx.toInt(), depth)
                walk(b.children, depth + 1)
            }
        }
        try {
            walk(doc.getTableOfContents(), 0)
        } catch (e: Exception) {
            // A broken outline shouldn't stop the document from opening.
        }
        out
    }

    suspend fun metadata(): Map<String, String> = onPdfThread {
        val m = doc.getDocumentMeta()
        listOfNotNull(
            m.title?.let { "Title" to it },
            m.author?.let { "Author" to it },
            m.subject?.let { "Subject" to it },
            m.keywords?.let { "Keywords" to it },
            m.creator?.let { "Created with" to it },
            m.producer?.let { "PDF made by" to it },
            m.creationDate?.let { "Created" to prettyPdfDate(it) },
            m.modDate?.let { "Modified" to prettyPdfDate(it) },
        ).filter { it.second.isNotBlank() }.toMap()
    }

    override fun close() {
        if (isClosed) return
        isClosed = true
        // Close on the PDF thread so a render in progress finishes first.
        pdfExecutor.execute {
            try {
                doc.close()
            } catch (_: Exception) {
            }
            try {
                pfd.close()
            } catch (_: Exception) {
            }
        }
    }

    companion object {
        private const val GEOMETRY_SCALE = 100_000

        private val pdfExecutor = java.util.concurrent.Executors.newSingleThreadExecutor { r ->
            Thread(r, "pdfium").apply { priority = Thread.NORM_PRIORITY - 1 }
        }

        private val pdfDispatcher = pdfExecutor.asCoroutineDispatcher()

        suspend fun <T> onPdfThread(block: () -> T): T = withContext(pdfDispatcher) { block() }

        private val core: PdfiumCore by lazy { PdfiumCore() }

        /** Opens [file]. Throws [WrongPasswordException] when it is locked and [password] is missing or wrong. */
        suspend fun open(file: File, password: String?): PdfDoc = withContext(Dispatchers.IO) {
            onPdfThread {
                val pfd = ParcelFileDescriptor.open(file, ParcelFileDescriptor.MODE_READ_ONLY)
                try {
                    val d = core.newDocument(pfd, password)
                    PdfDoc(file, password, pfd, d)
                } catch (e: PdfPasswordException) {
                    pfd.close()
                    throw WrongPasswordException()
                } catch (e: Exception) {
                    pfd.close()
                    throw IOException(e.message ?: "This file couldn't be opened as a PDF", e)
                }
            }
        }

        /** "D:20240131120000+05'30'" → "2024-01-31 12:00". */
        fun prettyPdfDate(raw: String): String {
            val s = raw.removePrefix("D:")
            if (s.length < 8 || !s.take(8).all { it.isDigit() }) return raw
            val date = "${s.substring(0, 4)}-${s.substring(4, 6)}-${s.substring(6, 8)}"
            return if (s.length >= 12 && s.substring(8, 12).all { it.isDigit() }) "$date ${s.substring(8, 10)}:${s.substring(10, 12)}" else date
        }
    }
}

/** Point in page space → position on the shown page as fractions of its width and height (0..1). */
data class PageGeometry(
    val a: Float, val b: Float, val c: Float, val d: Float, val e: Float, val f: Float,
    /** The page's own turn, clockwise, in degrees. */
    val rotation: Int,
) {
    fun toUnit(x: Float, y: Float): PointF = PointF(a * x + c * y + e, b * x + d * y + f)

    fun fromUnit(u: Float, v: Float): PointF {
        val det = a * d - b * c
        if (det == 0f) return PointF(0f, 0f)
        val px = u - e
        val py = v - f
        return PointF((d * px - c * py) / det, (-b * px + a * py) / det)
    }

    /** A page-space rectangle as a rectangle on the shown page, in fractions. */
    fun toUnitRect(r: PdfRect): RectF {
        val p1 = toUnit(r.left, r.bottom)
        val p2 = toUnit(r.right, r.top)
        return RectF(minOf(p1.x, p2.x), minOf(p1.y, p2.y), maxOf(p1.x, p2.x), maxOf(p1.y, p2.y))
    }

    fun fromUnitRect(r: RectF): PdfRect {
        val p1 = fromUnit(r.left, r.top)
        val p2 = fromUnit(r.right, r.bottom)
        return PdfRect(minOf(p1.x, p2.x), minOf(p1.y, p2.y), maxOf(p1.x, p2.x), maxOf(p1.y, p2.y))
    }

    companion object {
        val IDENTITY_LETTER = PageGeometry(1 / 612f, 0f, 0f, -1 / 792f, 0f, 1f, 0)
    }
}

/** A rectangle in page space (PDF points, y grows upwards). */
data class PdfRect(val left: Float, val bottom: Float, val right: Float, val top: Float) {
    val width get() = right - left
    val height get() = top - bottom

    fun union(o: PdfRect) = PdfRect(minOf(left, o.left), minOf(bottom, o.bottom), maxOf(right, o.right), maxOf(top, o.top))
    fun contains(x: Float, y: Float) = x in left..right && y in bottom..top
    fun inset(d: Float) = PdfRect(left - d, bottom - d, right + d, top + d)

    companion object {
        fun of(r: RectF) = PdfRect(minOf(r.left, r.right), minOf(r.top, r.bottom), maxOf(r.left, r.right), maxOf(r.top, r.bottom))
    }
}

data class SearchHit(val page: Int, val start: Int, val end: Int, val rects: List<PdfRect>)
