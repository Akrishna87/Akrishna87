package io.github.akrishna87.pakkam.ui

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.ScrollState
import androidx.compose.foundation.gestures.scrollBy
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.unit.IntSize
import io.github.akrishna87.pakkam.data.PaperTheme
import io.github.akrishna87.pakkam.data.Settings
import io.github.akrishna87.pakkam.data.ViewMode
import io.github.akrishna87.pakkam.pdf.AnnotInfo
import io.github.akrishna87.pakkam.pdf.DeleteAnnotEdit
import io.github.akrishna87.pakkam.pdf.Edit
import io.github.akrishna87.pakkam.pdf.ImageEdit
import io.github.akrishna87.pakkam.pdf.InkEdit
import io.github.akrishna87.pakkam.pdf.MarkupEdit
import io.github.akrishna87.pakkam.pdf.MarkupKind
import io.github.akrishna87.pakkam.pdf.NoteEdit
import io.github.akrishna87.pakkam.pdf.PageGeometry
import io.github.akrishna87.pakkam.pdf.PageTextLayout
import io.github.akrishna87.pakkam.pdf.PdfRect
import io.github.akrishna87.pakkam.pdf.SearchHit
import io.github.akrishna87.pakkam.pdf.ShapeEdit
import io.github.akrishna87.pakkam.pdf.ShapeKind
import io.github.akrishna87.pakkam.pdf.TextEdit
import io.github.akrishna87.pakkam.pdf.WhiteoutEdit
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt

/** The toolbar along the bottom, as in Acrobat. */
enum class ViewerMode(val label: String) { READ("Read"), COMMENT("Comment"), FILL_SIGN("Fill & Sign"), EDIT("Edit") }

enum class Tool(val label: String) {
    NONE("Read"),
    SELECT("Select text"),
    HIGHLIGHT("Highlight"),
    UNDERLINE("Underline"),
    STRIKEOUT("Strikethrough"),
    PEN("Draw"),
    MARKER("Marker"),
    RECT("Rectangle"),
    OVAL("Oval"),
    LINE("Line"),
    ARROW("Arrow"),
    NOTE("Note"),
    TEXT("Add text"),
    ERASER("Erase"),
    WHITEOUT("White-out");

    val usesText get() = this == SELECT || this == HIGHLIGHT || this == UNDERLINE || this == STRIKEOUT
    val draws get() = this == PEN || this == MARKER
    val drags get() = this == RECT || this == OVAL || this == LINE || this == ARROW || this == WHITEOUT
}

data class Selection(val page: Int, val anchor: Int, val focus: Int, val rects: List<PdfRect>, val text: String) {
    val start get() = minOf(anchor, focus)
    val end get() = maxOf(anchor, focus) + 1
}

/** Something being placed on a page: dragged into position, then confirmed. Rect is in fractions of the shown page. */
data class Placement(
    val page: Int,
    val rect: RectF,
    val kind: Kind,
    val text: String = "",
    val sizePt: Float = 12f,
    val baseHeight: Float = 0f,
    val color: Int = 0xFF000000.toInt(),
    val image: File? = null,
    val label: String = "",
) {
    enum class Kind { TEXT, IMAGE, CHECK, CROSS, DOT }
}

class ViewerState(
    val session: DocSession,
    private val scope: CoroutineScope,
    private val settings: Settings,
    startPage: Int,
) {
    val listState = LazyListState(firstVisibleItemIndex = startPage)
    val crossScroll = ScrollState(0)

    var paged by mutableStateOf(settings.viewMode == ViewMode.SINGLE)
        private set
    var paper by mutableStateOf(settings.paperTheme)
        private set
    var keepScreenOn by mutableStateOf(settings.keepScreenOn)
        private set

    var zoom by mutableFloatStateOf(1f)
        private set
    var pinchScale by mutableFloatStateOf(1f)
    var pinchOrigin by mutableStateOf(Offset.Zero)
    var viewport by mutableStateOf(IntSize.Zero)
    var viewportCoords: LayoutCoordinates? = null

    var mode by mutableStateOf(ViewerMode.READ)
    var tool by mutableStateOf(Tool.NONE)
    var chromeVisible by mutableStateOf(true)

    // Pen and highlighter settings.
    var penColor by mutableStateOf(settings.penColor)
    var penWidth by mutableFloatStateOf(settings.penWidth)
    var highlightColor by mutableStateOf(settings.highlightColor)
    var textColor by mutableStateOf(settings.textColor)

    // Search.
    var searchOpen by mutableStateOf(false)
    var query by mutableStateOf("")
    val hits = mutableStateListOf<SearchHit>()
    var hitIndex by mutableStateOf(-1)
    var searching by mutableStateOf(false)
    var searchedFor by mutableStateOf("")
    private var searchJob: Job? = null

    // Selection and drawing in progress.
    var selection by mutableStateOf<Selection?>(null)
    var liveInk by mutableStateOf<Pair<Int, List<PointF>>?>(null)
    var liveShape by mutableStateOf<Triple<Int, PointF, PointF>?>(null)
    var placement by mutableStateOf<Placement?>(null)

    val geometries = mutableStateMapOf<Int, PageGeometry>()
    val layouts = HashMap<Int, PageTextLayout>()
    private val images = HashMap<File, Bitmap?>()

    val pageCount get() = session.doc.pageCount

    /** The page in the middle of the screen. */
    val currentPage by derivedStateOf {
        val info = listState.layoutInfo
        val items = info.visibleItemsInfo
        if (items.isEmpty()) {
            listState.firstVisibleItemIndex
        } else {
            val mid = (info.viewportStartOffset + info.viewportEndOffset) / 2
            (items.firstOrNull { mid >= it.offset && mid < it.offset + it.size } ?: items.first()).index
        }
    }

    // ---- Preferences ----

    fun setViewMode(v: ViewMode) {
        settings.viewMode = v
        val page = currentPage
        paged = v == ViewMode.SINGLE
        zoom = 1f
        scope.launch {
            withFrameNanos { }
            listState.scrollToItem(page)
            crossScroll.scrollTo(0)
        }
    }

    fun setPaper(p: PaperTheme) {
        settings.paperTheme = p
        paper = p
    }

    fun setScreenOn(on: Boolean) {
        settings.keepScreenOn = on
        keepScreenOn = on
    }

    fun rememberPen() {
        settings.penColor = penColor
        settings.penWidth = penWidth
        settings.highlightColor = highlightColor
        settings.textColor = textColor
    }

    // ---- Moving around ----

    fun goTo(page: Int, offsetPx: Int = 0) {
        val p = page.coerceIn(0, (pageCount - 1).coerceAtLeast(0))
        scope.launch { listState.scrollToItem(p, offsetPx.coerceAtLeast(0)) }
    }

    /** Scrolls by a finger movement (used while two fingers pinch). */
    fun panBy(delta: Offset) {
        val main = if (paged) delta.x else delta.y
        val cross = if (paged) delta.y else delta.x
        listState.dispatchRawDelta(-main)
        crossScroll.dispatchRawDelta(-cross)
    }

    /** Sets a new zoom, keeping the point [focus] (in the viewport) where it is. */
    fun commitZoom(target: Float, focus: Offset) {
        val z = target.coerceIn(1f, MAX_ZOOM)
        val ratio = z / zoom
        if (abs(ratio - 1f) < 0.01f) return
        val mainFocus = if (paged) focus.x else focus.y
        val crossFocus = if (paged) focus.y else focus.x
        val items = listState.layoutInfo.visibleItemsInfo
        val item = items.firstOrNull { mainFocus >= it.offset && mainFocus < it.offset + it.size } ?: items.firstOrNull()
        val newCross = (crossScroll.value + crossFocus) * ratio - crossFocus
        zoom = z
        if (item == null) return
        val into = mainFocus - item.offset
        scope.launch {
            withFrameNanos { }
            withFrameNanos { }
            val target2 = (into * ratio - mainFocus).roundToInt()
            listState.scrollToItem(item.index, target2.coerceAtLeast(0))
            if (target2 < 0) listState.scrollBy(target2.toFloat())
            crossScroll.scrollTo(newCross.roundToInt().coerceIn(0, crossScroll.maxValue))
        }
    }

    fun toggleZoom(focus: Offset) = commitZoom(if (zoom > 1.2f) 1f else 2.5f, focus)

    // ---- Search ----

    fun search(q: String) {
        searchJob?.cancel()
        hits.clear()
        hitIndex = -1
        searchedFor = q.trim()
        if (searchedFor.isEmpty()) return
        val from = currentPage
        searching = true
        searchJob = scope.launch {
            try {
                session.doc.search(searchedFor) { _, pageHits ->
                    hits.addAll(pageHits)
                    // Jump to the first hit at or after the page being read.
                    if (hitIndex < 0 && pageHits.first().page >= from) {
                        hitIndex = hits.indexOf(pageHits.first())
                        showHit()
                    }
                }
                if (hitIndex < 0 && hits.isNotEmpty()) {
                    hitIndex = 0
                    showHit()
                }
            } finally {
                searching = false
            }
        }
    }

    fun nextHit(step: Int) {
        if (hits.isEmpty()) return
        hitIndex = ((hitIndex + step) % hits.size + hits.size) % hits.size
        showHit()
    }

    private fun showHit() {
        val hit = hits.getOrNull(hitIndex) ?: return
        scope.launch {
            val geo = geometry(hit.page)
            val r = hit.rects.firstOrNull()
            val unitTop = if (r != null) geo.toUnitRect(r).top else 0f
            val size = pageSizePx(hit.page)
            val mainSize = if (paged) size.width else size.height
            // A third of the way down the screen, so the hit isn't under the toolbar.
            val offset = if (paged) 0 else (unitTop * mainSize - viewport.height / 3f).roundToInt()
            listState.scrollToItem(hit.page, offset.coerceAtLeast(0))
            if (zoom > 1f && r != null) {
                val ur = geo.toUnitRect(r)
                val crossPos = if (paged) ur.top * size.height else ur.left * size.width
                val crossView = if (paged) viewport.height else viewport.width
                crossScroll.scrollTo((crossPos - crossView / 3f).roundToInt().coerceIn(0, crossScroll.maxValue))
            }
        }
    }

    fun closeSearch() {
        searchJob?.cancel()
        searchOpen = false
        hits.clear()
        hitIndex = -1
        query = ""
        searchedFor = ""
    }

    // ---- Geometry ----

    suspend fun geometry(page: Int): PageGeometry = geometries[page] ?: session.doc.geometry(page).also { geometries[page] = it }

    suspend fun layout(page: Int): PageTextLayout = layouts[page] ?: session.doc.textLayout(page).also { layouts[page] = it }

    fun resetForNewVersion() {
        geometries.clear()
        layouts.clear()
        selection = null
        if (searchedFor.isNotEmpty()) search(searchedFor)
    }

    /** The shown size of [page] in pixels at the current zoom. */
    fun pageSizePx(page: Int): IntSize {
        val s = session.doc.pageSizes.getOrNull(page) ?: return IntSize(1, 1)
        val vw = viewport.width.toFloat().coerceAtLeast(1f)
        val vh = viewport.height.toFloat().coerceAtLeast(1f)
        return if (!paged) {
            val w = (vw - 2 * MARGIN_PX) * zoom
            IntSize(w.roundToInt().coerceAtLeast(1), (w / s.aspect).roundToInt().coerceAtLeast(1))
        } else {
            val fit = minOf((vw - 2 * MARGIN_PX) / s.width, (vh - 2 * MARGIN_PX) / s.height)
            IntSize((s.width * fit * zoom).roundToInt().coerceAtLeast(1), (s.height * fit * zoom).roundToInt().coerceAtLeast(1))
        }
    }

    // ---- Text selection ----

    fun select(page: Int, anchor: Int, focus: Int) {
        val layout = layouts[page] ?: return
        val s = minOf(anchor, focus)
        val e = maxOf(anchor, focus) + 1
        selection = Selection(page, anchor, focus, layout.rectsFor(s, e), layout.textOf(s, e).trim())
    }

    fun selectWord(page: Int, index: Int) {
        val layout = layouts[page] ?: return
        val (s, e) = layout.wordAt(index)
        if (e <= s) return
        selection = Selection(page, s, e - 1, layout.rectsFor(s, e), layout.textOf(s, e).trim())
    }

    /** Turns the current selection into a highlight, underline or strikethrough. */
    fun markSelection(kind: MarkupKind) {
        val sel = selection ?: return
        if (sel.rects.isEmpty()) return
        val color = when (kind) {
            MarkupKind.HIGHLIGHT -> highlightColor
            MarkupKind.UNDERLINE -> 0xFF1E88E5.toInt()
            MarkupKind.STRIKEOUT -> 0xFFE53935.toInt()
        }
        addEdit(MarkupEdit(sel.page, kind, sel.rects, color, sel.text))
        selection = null
    }

    // ---- Edits ----

    fun addEdit(e: Edit) {
        session.edits.add(e)
        session.redo.clear()
    }

    fun undo() {
        if (session.edits.isNotEmpty()) session.redo.add(session.edits.removeAt(session.edits.lastIndex))
    }

    fun redo() {
        if (session.redo.isNotEmpty()) session.edits.add(session.redo.removeAt(session.redo.lastIndex))
    }

    /** What the eraser would remove at a point: a pending edit first, then an annotation in the file. */
    fun eraseAt(page: Int, x: Float, y: Float): Boolean {
        val pending = session.edits.lastOrNull { it.page == page && it !is DeleteAnnotEdit && boundsOf(it)?.inset(6f)?.contains(x, y) == true }
        if (pending != null) {
            session.edits.remove(pending)
            session.redo.add(pending)
            return true
        }
        val gone = session.edits.filterIsInstance<DeleteAnnotEdit>().map { it.annot }.toSet()
        val existing = session.annotations.lastOrNull { it.page == page && it !in gone && it.rect.inset(4f).contains(x, y) }
        if (existing != null) {
            addEdit(DeleteAnnotEdit(page, existing))
            return true
        }
        return false
    }

    fun deleteExisting(a: AnnotInfo) = addEdit(DeleteAnnotEdit(a.page, a))

    fun image(f: File): Bitmap? = images.getOrPut(f) {
        val opts = BitmapFactory.Options().apply { inSampleSize = 1 }
        BitmapFactory.decodeFile(f.path, opts)
    }

    /** Places the confirmed [placement] as an edit. */
    suspend fun commitPlacement() {
        val p = placement ?: return
        val geo = geometry(p.page)
        val r = p.rect
        when (p.kind) {
            Placement.Kind.TEXT -> {
                val size = if (p.baseHeight > 0f) p.sizePt * (r.height() / p.baseHeight) else p.sizePt
                addEdit(TextEdit(p.page, geo.fromUnitRect(r), p.text, size, p.color, geo.rotation))
            }
            Placement.Kind.IMAGE -> addEdit(ImageEdit(p.page, geo.fromUnitRect(r), p.image ?: return, geo.rotation, p.label))
            Placement.Kind.CHECK, Placement.Kind.CROSS -> {
                fun at(u: Float, v: Float) = geo.fromUnit(r.left + u * r.width(), r.top + v * r.height())
                val strokes = if (p.kind == Placement.Kind.CHECK) {
                    listOf(listOf(at(0.1f, 0.55f), at(0.4f, 0.85f), at(0.92f, 0.12f)))
                } else {
                    listOf(listOf(at(0.12f, 0.12f), at(0.88f, 0.88f)), listOf(at(0.88f, 0.12f), at(0.12f, 0.88f)))
                }
                val pageW = session.doc.pageSizes[p.page].width
                addEdit(InkEdit(p.page, strokes, p.color, (r.width() * pageW * 0.12f).coerceIn(1f, 6f)))
            }
            Placement.Kind.DOT -> {
                val c = geo.fromUnit(r.centerX(), r.centerY())
                val pageW = session.doc.pageSizes[p.page].width
                val d = r.width() * pageW
                addEdit(ShapeEdit(p.page, ShapeKind.OVAL, PointF(c.x - d / 4, c.y - d / 4), PointF(c.x + d / 4, c.y + d / 4), p.color, d / 2))
            }
        }
        placement = null
    }

    companion object {
        const val MAX_ZOOM = 5f
        const val MARGIN_PX = 16f

        fun boundsOf(e: Edit): PdfRect? = when (e) {
            is MarkupEdit -> e.rects.reduceOrNull { a, b -> a.union(b) }
            is InkEdit -> e.strokes.flatten().let { pts ->
                if (pts.isEmpty()) null else PdfRect(pts.minOf { it.x }, pts.minOf { it.y }, pts.maxOf { it.x }, pts.maxOf { it.y })
            }
            is ShapeEdit -> e.bounds
            is NoteEdit -> PdfRect(e.at.x, e.at.y - 20f, e.at.x + 20f, e.at.y)
            is TextEdit -> e.rect
            is ImageEdit -> e.rect
            is WhiteoutEdit -> e.rect
            is DeleteAnnotEdit -> e.annot.rect
        }
    }
}
