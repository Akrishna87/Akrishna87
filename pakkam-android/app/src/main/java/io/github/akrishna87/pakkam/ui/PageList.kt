package io.github.akrishna87.pakkam.ui

import android.graphics.Bitmap
import android.graphics.Paint
import android.graphics.PointF
import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.snapping.rememberSnapFlingBehavior
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.TransformOrigin
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import io.github.akrishna87.pakkam.data.PaperTheme
import io.github.akrishna87.pakkam.pdf.DeleteAnnotEdit
import io.github.akrishna87.pakkam.pdf.Edit
import io.github.akrishna87.pakkam.pdf.ImageEdit
import io.github.akrishna87.pakkam.pdf.InkEdit
import io.github.akrishna87.pakkam.pdf.MarkupEdit
import io.github.akrishna87.pakkam.pdf.MarkupKind
import io.github.akrishna87.pakkam.pdf.NoteEdit
import io.github.akrishna87.pakkam.pdf.PageGeometry
import io.github.akrishna87.pakkam.pdf.PdfEditor
import io.github.akrishna87.pakkam.pdf.PdfRect
import io.github.akrishna87.pakkam.pdf.ShapeEdit
import io.github.akrishna87.pakkam.pdf.ShapeKind
import io.github.akrishna87.pakkam.pdf.TextEdit
import io.github.akrishna87.pakkam.pdf.WhiteoutEdit
import kotlinx.coroutines.launch
import kotlin.math.hypot
import kotlin.math.roundToInt

/** Bitmaps wider than this are drawn scaled up rather than rendered, to keep memory in check. */
private const val MAX_RENDER_WIDTH = 2800

/** What happens when a page is tapped with no tool: follow a link or show/hide the toolbars. */
interface PageActions {
    fun onTap(page: Int, x: Float, y: Float)
    fun onLongPress(page: Int, x: Float, y: Float)
    fun onNoteTap(page: Int, x: Float, y: Float)
    fun onTextTap(page: Int, unitX: Float, unitY: Float)
}

@Composable
fun PageList(vs: ViewerState, actions: PageActions, modifier: Modifier = Modifier) {
    val session = vs.session
    val doc = session.doc
    val version = session.version
    LaunchedEffect(version) { vs.resetForNewVersion() }

    val bg = when (vs.paper) {
        PaperTheme.NIGHT -> Color(0xFF111111)
        PaperTheme.SEPIA -> Color(0xFF6E6152)
        PaperTheme.DAY -> Color(0xFFE6E2E1)
    }
    BoxWithConstraints(
        modifier
            .fillMaxSize()
            .background(bg)
            .onSizeChanged { vs.viewport = it }
            .onGloballyPositioned { vs.viewportCoords = it }
            .pointerInput(vs) { pinchAndPan(vs) },
    ) {
        if (vs.viewport.width == 0) return@BoxWithConstraints
        val density = LocalDensity.current
        val vw = vs.viewport.width
        val vh = vs.viewport.height
        val z = vs.zoom
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    scaleX = vs.pinchScale
                    scaleY = vs.pinchScale
                    transformOrigin = TransformOrigin(
                        (vs.pinchOrigin.x / vw).coerceIn(0f, 1f),
                        (vs.pinchOrigin.y / vh).coerceIn(0f, 1f),
                    )
                },
        ) {
            if (!vs.paged) {
                val contentW = with(density) { (vw * z).toDp() }
                Box(Modifier.fillMaxSize().horizontalScroll(vs.crossScroll, enabled = z > 1f)) {
                    LazyColumn(
                        state = vs.listState,
                        modifier = Modifier.width(contentW).fillMaxHeight().semantics { contentDescription = "Pages" },
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 12.dp),
                        verticalArrangement = androidx.compose.foundation.layout.Arrangement.spacedBy(10.dp),
                    ) {
                        items(doc.pageCount, key = { "$version-$it" }) { i ->
                            Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
                                PageView(vs, i, actions)
                            }
                        }
                    }
                }
            } else {
                val contentH = with(density) { (vh * z).toDp() }
                val slotW = with(density) { (vw * z).toDp() }
                val snap = rememberSnapFlingBehavior(lazyListState = vs.listState)
                Box(Modifier.fillMaxSize().verticalScroll(vs.crossScroll, enabled = z > 1f)) {
                    LazyRow(
                        state = vs.listState,
                        modifier = Modifier.height(contentH).fillMaxWidth().semantics { contentDescription = "Pages" },
                        flingBehavior = if (z <= 1f) snap else androidx.compose.foundation.gestures.ScrollableDefaults.flingBehavior(),
                    ) {
                        items(doc.pageCount, key = { "$version-$it" }) { i ->
                            Box(Modifier.width(slotW).height(contentH), contentAlignment = Alignment.Center) {
                                PageView(vs, i, actions)
                            }
                        }
                    }
                }
            }
        }
    }
}

/**
 * Two fingers zoom and move the pages, whatever tool is active. Runs before the pages see the
 * touch, and takes it away from them, so a pinch never draws or selects.
 */
private suspend fun androidx.compose.ui.input.pointer.PointerInputScope.pinchAndPan(vs: ViewerState) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var pinching = false
        var startDist = 0f
        var lastCentroid = Offset.Zero
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            val pressed = event.changes.filter { it.pressed }
            if (pressed.size >= 2) {
                val a = pressed[0].position
                val b = pressed[1].position
                val dist = hypot(a.x - b.x, a.y - b.y).coerceAtLeast(1f)
                val centroid = Offset((a.x + b.x) / 2f, (a.y + b.y) / 2f)
                if (!pinching) {
                    pinching = true
                    startDist = dist
                    vs.pinchOrigin = centroid
                } else {
                    val maxScale = ViewerState.MAX_ZOOM / vs.zoom
                    val minScale = 1f / vs.zoom
                    vs.pinchScale = (dist / startDist).coerceIn(minScale * 0.85f, maxScale * 1.1f)
                    vs.panBy((centroid - lastCentroid) / vs.pinchScale)
                }
                lastCentroid = centroid
                event.changes.forEach { it.consume() }
            } else if (pinching) {
                event.changes.forEach { it.consume() }
            }
            if (event.changes.none { it.pressed }) break
        }
        if (pinching) {
            val target = vs.zoom * vs.pinchScale
            val origin = vs.pinchOrigin
            vs.pinchScale = 1f
            vs.commitZoom(target, origin)
        }
    }
}

@Composable
private fun PageView(vs: ViewerState, index: Int, actions: PageActions) {
    val session = vs.session
    val doc = session.doc
    val version = session.version
    val size = vs.pageSizePx(index)
    val density = LocalDensity.current
    val renderW = minOf(size.width, MAX_RENDER_WIDTH)
    val renderH = (renderW.toFloat() * size.height / size.width).roundToInt().coerceAtLeast(1)

    var bitmap by remember(index, version) { mutableStateOf(session.bitmaps.any(index)) }
    LaunchedEffect(index, version, renderW) {
        val cached = session.bitmaps.get(index, renderW)
        if (cached != null) {
            bitmap = cached
            return@LaunchedEffect
        }
        val b: Bitmap = doc.render(index, renderW, renderH) ?: return@LaunchedEffect
        session.bitmaps.put(index, renderW, b)
        bitmap = b
    }
    val geo by produceState<PageGeometry?>(vs.geometries[index], index, version) {
        value = vs.geometry(index)
    }
    val tool = vs.tool
    // Text tools need every character's position on the page.
    LaunchedEffect(index, version, tool) {
        if (tool.usesText || tool == Tool.NONE) vs.layout(index)
    }

    var coords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val wDp = with(density) { size.width.toDp() }
    val hDp = with(density) { size.height.toDp() }
    val g = geo ?: PageGeometry.IDENTITY_LETTER
    fun unit(o: Offset) = Offset((o.x / size.width).coerceIn(0f, 1f), (o.y / size.height).coerceIn(0f, 1f))
    fun toPage(o: Offset): PointF {
        val u = unit(o)
        return g.fromUnit(u.x, u.y)
    }
    fun inViewport(o: Offset): Offset {
        val vc = vs.viewportCoords
        val c = coords
        return if (vc != null && c != null && c.isAttached && vc.isAttached) vc.localPositionOf(c, o) else o
    }

    val scope = rememberCoroutineScope()
    val gestures = Modifier.pointerInput(index, tool, geo, size) {
        when {
            tool == Tool.NONE -> detectTapGestures(
                onTap = { o -> toPage(o).let { actions.onTap(index, it.x, it.y) } },
                onDoubleTap = { o -> vs.toggleZoom(inViewport(o)) },
                onLongPress = { o -> toPage(o).let { actions.onLongPress(index, it.x, it.y) } },
            )
            tool.usesText -> awaitEachGesture {
                val down = awaitFirstDown(requireUnconsumed = false)
                val layout = vs.layouts[index] ?: return@awaitEachGesture
                val p0 = toPage(down.position)
                val start = layout.charAt(p0.x, p0.y, 10f)
                android.util.Log.i("Pakkam", "Text touch on page ${index + 1} at ${p0.x},${p0.y} (${down.position} of $size): char $start of ${layout.length}")
                if (start < 0) {
                    // Not on text: let the finger scroll, and clear a selection on a plain tap.
                    if (tool == Tool.SELECT) vs.selection = null
                    return@awaitEachGesture
                }
                down.consume()
                var moved = false
                vs.select(index, start, start)
                while (true) {
                    val event = awaitPointerEvent()
                    val c = event.changes.firstOrNull { it.id == down.id }
                    if (c == null || event.changes.count { it.pressed } > 1 || c.isConsumed) {
                        vs.selection = null
                        return@awaitEachGesture
                    }
                    if (!c.pressed) break
                    if (c.positionChange() != Offset.Zero) {
                        c.consume()
                        moved = true
                        val p = toPage(c.position)
                        vs.select(index, start, layout.nearestChar(p.x, p.y))
                    }
                }
                if (!moved) vs.selectWord(index, start)
                when (tool) {
                    Tool.HIGHLIGHT -> vs.markSelection(MarkupKind.HIGHLIGHT)
                    Tool.UNDERLINE -> vs.markSelection(MarkupKind.UNDERLINE)
                    Tool.STRIKEOUT -> vs.markSelection(MarkupKind.STRIKEOUT)
                    else -> Unit
                }
            }
            tool.draws -> awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                val pts = ArrayList<PointF>()
                pts += toPage(down.position)
                vs.liveInk = index to pts.toList()
                var lastPos = down.position
                while (true) {
                    val event = awaitPointerEvent()
                    val c = event.changes.firstOrNull { it.id == down.id }
                    if (c == null || event.changes.count { it.pressed } > 1 || c.isConsumed) {
                        vs.liveInk = null
                        return@awaitEachGesture
                    }
                    if (!c.pressed) break
                    c.consume()
                    if ((c.position - lastPos).getDistance() > 2f) {
                        lastPos = c.position
                        pts += toPage(c.position)
                        vs.liveInk = index to pts.toList()
                    }
                }
                vs.liveInk = null
                val marker = tool == Tool.MARKER
                vs.addEdit(
                    InkEdit(
                        index,
                        listOf(pts.toList()),
                        if (marker) vs.highlightColor else vs.penColor,
                        if (marker) vs.penWidth * 6f else vs.penWidth,
                        if (marker) 0.4f else 1f,
                    ),
                )
            }
            tool.drags -> awaitEachGesture {
                val down = awaitFirstDown()
                down.consume()
                val start = toPage(down.position)
                var end = start
                while (true) {
                    val event = awaitPointerEvent()
                    val c = event.changes.firstOrNull { it.id == down.id }
                    if (c == null || event.changes.count { it.pressed } > 1 || c.isConsumed) {
                        vs.liveShape = null
                        return@awaitEachGesture
                    }
                    if (!c.pressed) break
                    c.consume()
                    end = toPage(c.position)
                    vs.liveShape = Triple(index, start, end)
                }
                vs.liveShape = null
                if (hypot(end.x - start.x, end.y - start.y) < 4f) return@awaitEachGesture
                val e: Edit = when (tool) {
                    Tool.WHITEOUT -> WhiteoutEdit(index, PdfRect(minOf(start.x, end.x), minOf(start.y, end.y), maxOf(start.x, end.x), maxOf(start.y, end.y)))
                    Tool.RECT -> ShapeEdit(index, ShapeKind.RECTANGLE, start, end, vs.penColor, vs.penWidth)
                    Tool.OVAL -> ShapeEdit(index, ShapeKind.OVAL, start, end, vs.penColor, vs.penWidth)
                    Tool.LINE -> ShapeEdit(index, ShapeKind.LINE, start, end, vs.penColor, vs.penWidth)
                    else -> ShapeEdit(index, ShapeKind.ARROW, start, end, vs.penColor, vs.penWidth)
                }
                vs.addEdit(e)
            }
            tool == Tool.NOTE -> detectTapGestures(onTap = { o -> toPage(o).let { actions.onNoteTap(index, it.x, it.y) } })
            tool == Tool.TEXT -> detectTapGestures(onTap = { o -> unit(o).let { actions.onTextTap(index, it.x, it.y) } })
            tool == Tool.ERASER -> detectTapGestures(onTap = { o -> toPage(o).let { vs.eraseAt(index, it.x, it.y) } })
            else -> Unit
        }
    }

    val filter = when (vs.paper) {
        PaperTheme.NIGHT -> NIGHT_FILTER
        PaperTheme.SEPIA -> SEPIA_FILTER
        PaperTheme.DAY -> null
    }
    val paperColor = when (vs.paper) {
        PaperTheme.NIGHT -> Color(0xFF1C1C1C)
        PaperTheme.SEPIA -> Color(0xFFF5E9D0)
        PaperTheme.DAY -> Color.White
    }
    Box(
        Modifier
            .size(wDp, hDp)
            .background(paperColor)
            .onGloballyPositioned { coords = it }
            .semantics { contentDescription = "Page ${index + 1}" }
            .then(gestures),
    ) {
        val b = bitmap
        if (b != null) {
            val img = remember(b) { b.asImageBitmap() }
            Image(
                img,
                contentDescription = null,
                modifier = Modifier.fillMaxSize(),
                contentScale = androidx.compose.ui.layout.ContentScale.FillBounds,
                colorFilter = filter,
                filterQuality = FilterQuality.Medium,
            )
        }
        val pageW = doc.pageSizes.getOrNull(index)?.width ?: 612f
        Canvas(Modifier.fillMaxSize()) {
            val gg = geo ?: return@Canvas
            val ptToPx = size.width / pageW
            drawOverlay(vs, index, gg, ptToPx)
        }
        val pl = vs.placement
        if (pl != null && pl.page == index) {
            PlacementBox(vs, pl, size.width, size.height, pageW, onDone = { scope.launch { vs.commitPlacement() } })
        }
    }
}

private val NIGHT_FILTER = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            -0.87f, 0f, 0f, 0f, 235f,
            0f, -0.87f, 0f, 0f, 235f,
            0f, 0f, -0.87f, 0f, 235f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private val SEPIA_FILTER = ColorFilter.colorMatrix(
    ColorMatrix(
        floatArrayOf(
            0.96f, 0f, 0f, 0f, 0f,
            0f, 0.91f, 0f, 0f, 0f,
            0f, 0f, 0.80f, 0f, 0f,
            0f, 0f, 0f, 1f, 0f,
        ),
    ),
)

private fun DrawScope.p(g: PageGeometry, x: Float, y: Float): Offset {
    val u = g.toUnit(x, y)
    return Offset(u.x * size.width, u.y * size.height)
}

private fun DrawScope.r(g: PageGeometry, rect: PdfRect): androidx.compose.ui.geometry.Rect {
    val u = g.toUnitRect(rect)
    return androidx.compose.ui.geometry.Rect(u.left * size.width, u.top * size.height, u.right * size.width, u.bottom * size.height)
}

private fun DrawScope.drawOverlay(vs: ViewerState, page: Int, g: PageGeometry, ptToPx: Float) {
    // Search hits.
    val current = vs.hits.getOrNull(vs.hitIndex)
    for (hit in vs.hits) {
        if (hit.page != page) continue
        val color = if (hit == current) Color(0x99FF6D00) else Color(0x66FFC107)
        for (rect in hit.rects) {
            val rr = r(g, rect)
            drawRect(color, rr.topLeft, rr.size)
        }
    }
    // Selected text.
    vs.selection?.takeIf { it.page == page }?.let { sel ->
        for (rect in sel.rects) {
            val rr = r(g, rect)
            drawRect(Color(0x553D7BFF), rr.topLeft, rr.size)
        }
    }
    for (e in vs.session.edits) if (e.page == page) drawEdit(vs, e, g, ptToPx)
    vs.liveInk?.takeIf { it.first == page }?.let { (_, pts) ->
        val marker = vs.tool == Tool.MARKER
        drawStroke(g, pts, Color(if (marker) vs.highlightColor else vs.penColor).copy(alpha = if (marker) 0.4f else 1f), (if (marker) vs.penWidth * 6f else vs.penWidth) * ptToPx)
    }
    vs.liveShape?.takeIf { it.first == page }?.let { (_, a, b) ->
        val kind = when (vs.tool) {
            Tool.OVAL -> ShapeKind.OVAL
            Tool.LINE -> ShapeKind.LINE
            Tool.ARROW -> ShapeKind.ARROW
            else -> ShapeKind.RECTANGLE
        }
        if (vs.tool == Tool.WHITEOUT) {
            drawEdit(vs, WhiteoutEdit(page, PdfRect(minOf(a.x, b.x), minOf(a.y, b.y), maxOf(a.x, b.x), maxOf(a.y, b.y))), g, ptToPx)
        } else {
            drawEdit(vs, ShapeEdit(page, kind, a, b, vs.penColor, vs.penWidth), g, ptToPx)
        }
    }
}

private fun DrawScope.drawStroke(g: PageGeometry, pts: List<PointF>, color: Color, widthPx: Float) {
    if (pts.isEmpty()) return
    val path = Path()
    val first = p(g, pts[0].x, pts[0].y)
    path.moveTo(first.x, first.y)
    if (pts.size == 1) path.lineTo(first.x + 0.1f, first.y)
    for (i in 1 until pts.size) {
        val o = p(g, pts[i].x, pts[i].y)
        path.lineTo(o.x, o.y)
    }
    drawPath(path, color, style = Stroke(width = widthPx.coerceAtLeast(1f), cap = StrokeCap.Round, join = StrokeJoin.Round))
}

private fun DrawScope.drawEdit(vs: ViewerState, e: Edit, g: PageGeometry, ptToPx: Float) {
    when (e) {
        is MarkupEdit -> for (rect in e.rects) {
            val rr = r(g, rect)
            val c = Color(e.color)
            when (e.kind) {
                MarkupKind.HIGHLIGHT -> drawRect(c.copy(alpha = 0.4f), rr.topLeft, rr.size)
                MarkupKind.UNDERLINE -> drawLine(c, Offset(rr.left, rr.bottom), Offset(rr.right, rr.bottom), strokeWidth = (rr.height / 14f).coerceAtLeast(1.5f))
                MarkupKind.STRIKEOUT -> drawLine(c, Offset(rr.left, rr.center.y), Offset(rr.right, rr.center.y), strokeWidth = (rr.height / 14f).coerceAtLeast(1.5f))
            }
        }
        is InkEdit -> for (s in e.strokes) drawStroke(g, s, Color(e.color).copy(alpha = e.opacity), e.width * ptToPx)
        is ShapeEdit -> {
            val c = Color(e.color)
            val w = (e.width * ptToPx).coerceAtLeast(1f)
            when (e.kind) {
                ShapeKind.RECTANGLE -> {
                    val rr = r(g, e.bounds)
                    drawRect(c, rr.topLeft, rr.size, style = Stroke(w))
                }
                ShapeKind.OVAL -> {
                    val rr = r(g, e.bounds)
                    drawOval(c, rr.topLeft, rr.size, style = Stroke(w))
                }
                ShapeKind.LINE, ShapeKind.ARROW -> {
                    val a = p(g, e.start.x, e.start.y)
                    val b = p(g, e.end.x, e.end.y)
                    drawLine(c, a, b, strokeWidth = w, cap = StrokeCap.Round)
                    if (e.kind == ShapeKind.ARROW) {
                        val h = PdfEditor.arrowHead(e.start.x, e.start.y, e.end.x, e.end.y, e.width)
                        drawLine(c, p(g, h[0], h[1]), b, strokeWidth = w, cap = StrokeCap.Round)
                        drawLine(c, p(g, h[2], h[3]), b, strokeWidth = w, cap = StrokeCap.Round)
                    }
                }
            }
        }
        is NoteEdit -> {
            val tl = p(g, e.at.x, e.at.y)
            val s = 20f * ptToPx
            drawRect(Color(e.color), tl, Size(s, s))
            drawRect(Color(0x99000000), tl, Size(s, s), style = Stroke(1.5f))
            for (k in 1..3) drawLine(Color(0x99000000), Offset(tl.x + s * 0.2f, tl.y + s * k / 4.5f), Offset(tl.x + s * 0.8f, tl.y + s * k / 4.5f), 1.5f)
        }
        is TextEdit -> {
            val rr = r(g, e.rect)
            drawText(e.text, rr.left, rr.top, e.sizePt, ptToPx, e.color)
        }
        is ImageEdit -> {
            val rr = r(g, e.rect)
            val bmp = vs.image(e.image) ?: return
            drawIntoCanvas { c ->
                c.nativeCanvas.drawBitmap(bmp, null, RectF(rr.left, rr.top, rr.right, rr.bottom), Paint(Paint.FILTER_BITMAP_FLAG))
            }
        }
        is WhiteoutEdit -> {
            val rr = r(g, e.rect)
            drawRect(Color.White, rr.topLeft, rr.size)
            drawRect(Color(0x55888888), rr.topLeft, rr.size, style = Stroke(1f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(6f, 6f))))
        }
        is DeleteAnnotEdit -> {
            val rr = r(g, e.annot.rect)
            val red = Color(0xFFD32F2F)
            drawRect(red.copy(alpha = 0.12f), rr.topLeft, rr.size)
            drawRect(red, rr.topLeft, rr.size, style = Stroke(2f, pathEffect = PathEffect.dashPathEffect(floatArrayOf(8f, 6f))))
            drawLine(red, rr.topLeft, rr.bottomRight, 2f)
            drawLine(red, Offset(rr.right, rr.top), Offset(rr.left, rr.bottom), 2f)
        }
    }
}

/** Draws typed text the way [PdfEditor] will write it: from the top left, line by line. */
private fun DrawScope.drawText(text: String, left: Float, top: Float, sizePt: Float, ptToPx: Float, color: Int) {
    val sizePx = sizePt * ptToPx
    val pad = PdfEditor.TEXT_PADDING * ptToPx
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        this.color = color
        textSize = sizePx.coerceAtLeast(1f)
        typeface = android.graphics.Typeface.SANS_SERIF
    }
    drawIntoCanvas { c ->
        var y = top + pad + sizePx * PdfEditor.TEXT_ASCENT
        for (line in text.split('\n')) {
            c.nativeCanvas.drawText(line, left + pad, y, paint)
            y += sizePx * PdfEditor.TEXT_LEADING
        }
    }
}

/** The box being placed: drag it to move, drag the corner dot to resize, then tick or cross. */
@Composable
private fun PlacementBox(vs: ViewerState, pl: Placement, pageWPx: Int, pageHPx: Int, pageWPt: Float, onDone: () -> Unit) {
    val density = LocalDensity.current
    val rect = pl.rect
    val left = rect.left * pageWPx
    val top = rect.top * pageHPx
    val w = rect.width() * pageWPx
    val h = rect.height() * pageHPx
    fun move(dx: Float, dy: Float) {
        val cur = vs.placement ?: return
        val r = RectF(cur.rect)
        val ux = dx / pageWPx
        val uy = dy / pageHPx
        val nx = (r.left + ux).coerceIn(0f, 1f - r.width())
        val ny = (r.top + uy).coerceIn(0f, 1f - r.height())
        vs.placement = cur.copy(rect = RectF(nx, ny, nx + r.width(), ny + r.height()))
    }
    fun resize(dx: Float, dy: Float) {
        val cur = vs.placement ?: return
        val r = cur.rect
        val aspect = r.width() / r.height().coerceAtLeast(0.0001f)
        var nw = (r.width() + dx / pageWPx).coerceIn(0.02f, 1f - r.left)
        var nh = nw / aspect
        if (r.top + nh > 1f) {
            nh = 1f - r.top
            nw = nh * aspect
        }
        vs.placement = cur.copy(rect = RectF(r.left, r.top, r.left + nw, r.top + nh))
    }
    Box(
        Modifier
            .offset { IntOffset(left.roundToInt(), top.roundToInt()) }
            .size(with(density) { w.toDp() }, with(density) { h.toDp() })
            .border(1.5.dp, MaterialTheme.colorScheme.primary)
            .pointerInput(pageWPx, pageHPx) { detectDragGestures { c, d -> c.consume(); move(d.x, d.y) } },
    ) {
        Canvas(Modifier.fillMaxSize()) {
            when (pl.kind) {
                Placement.Kind.TEXT -> {
                    val sizePt = if (pl.baseHeight > 0f) pl.sizePt * (rect.height() / pl.baseHeight) else pl.sizePt
                    drawText(pl.text, 0f, 0f, sizePt, pageWPx / pageWPt, pl.color)
                }
                Placement.Kind.IMAGE -> pl.image?.let { f ->
                    vs.image(f)?.let { bmp ->
                        drawIntoCanvas { it.nativeCanvas.drawBitmap(bmp, null, RectF(0f, 0f, size.width, size.height), Paint(Paint.FILTER_BITMAP_FLAG)) }
                    }
                }
                Placement.Kind.CHECK -> {
                    val path = Path().apply {
                        moveTo(size.width * 0.1f, size.height * 0.55f)
                        lineTo(size.width * 0.4f, size.height * 0.85f)
                        lineTo(size.width * 0.92f, size.height * 0.12f)
                    }
                    drawPath(path, Color(pl.color), style = Stroke(size.width * 0.12f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                }
                Placement.Kind.CROSS -> {
                    val s = Stroke(size.width * 0.12f, cap = StrokeCap.Round)
                    drawLine(Color(pl.color), Offset(size.width * 0.12f, size.height * 0.12f), Offset(size.width * 0.88f, size.height * 0.88f), s.width, s.cap)
                    drawLine(Color(pl.color), Offset(size.width * 0.88f, size.height * 0.12f), Offset(size.width * 0.12f, size.height * 0.88f), s.width, s.cap)
                }
                Placement.Kind.DOT -> drawCircle(Color(pl.color), radius = size.minDimension / 2f)
            }
        }
        // Resize handle.
        Box(
            Modifier
                .align(Alignment.BottomEnd)
                .offset(12.dp, 12.dp)
                .size(28.dp)
                .padding(4.dp)
                .background(MaterialTheme.colorScheme.primary, CircleShape)
                .semantics { contentDescription = "Resize" }
                .pointerInput(pageWPx, pageHPx) { detectDragGestures { c, d -> c.consume(); resize(d.x, d.y) } },
        )
    }
    // Confirm and cancel buttons above the box.
    Row(
        Modifier.offset {
            IntOffset(left.roundToInt(), (top - with(density) { 48.dp.toPx() }).roundToInt().coerceAtLeast(0))
        },
    ) {
        FilledIconButton(onClick = onDone, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Check, "Place it") }
        FilledIconButton(
            onClick = { vs.placement = null },
            modifier = Modifier.padding(start = 8.dp).size(40.dp),
            colors = IconButtonDefaults.filledIconButtonColors(containerColor = MaterialTheme.colorScheme.surfaceVariant, contentColor = MaterialTheme.colorScheme.onSurfaceVariant),
        ) { Icon(Icons.Filled.Close, "Cancel placing") }
    }
}

/** Builds a text placement sized to fit [text] at [sizePt], with its top left at ([ux], [uy]). */
fun textPlacement(page: Int, ux: Float, uy: Float, text: String, sizePt: Float, color: Int, pageW: Float, pageH: Float): Placement {
    val paint = Paint().apply { textSize = sizePt; typeface = android.graphics.Typeface.SANS_SERIF }
    val lines = text.split('\n')
    val wPt = (lines.maxOf { paint.measureText(it) } + PdfEditor.TEXT_PADDING * 2 + sizePt * 0.3f).coerceAtLeast(sizePt)
    val hPt = lines.size * sizePt * PdfEditor.TEXT_LEADING + PdfEditor.TEXT_PADDING * 2
    val uw = (wPt / pageW).coerceAtMost(1f)
    val uh = (hPt / pageH).coerceAtMost(1f)
    val left = ux.coerceIn(0f, 1f - uw)
    val top = uy.coerceIn(0f, 1f - uh)
    return Placement(page, RectF(left, top, left + uw, top + uh), Placement.Kind.TEXT, text = text, sizePt = sizePt, baseHeight = uh, color = color)
}

/** Builds a picture placement a third of the page wide, centred on the screen's middle of [page]. */
fun imagePlacement(page: Int, bmpW: Int, bmpH: Int, pageW: Float, pageH: Float, file: java.io.File, label: String, widthFraction: Float = 0.33f): Placement {
    val uw = widthFraction
    val uh = (uw * pageW * bmpH / bmpW.coerceAtLeast(1) / pageH).coerceAtMost(0.9f)
    return Placement(page, RectF(0.5f - uw / 2, 0.45f - uh / 2, 0.5f + uw / 2, 0.45f + uh / 2), Placement.Kind.IMAGE, image = file, label = label)
}

/** A small square mark (tick, cross or dot) in the middle of the page. */
fun markPlacement(page: Int, kind: Placement.Kind, pageW: Float, pageH: Float, color: Int): Placement {
    val sidePt = 16f
    val uw = sidePt / pageW
    val uh = sidePt / pageH
    return Placement(page, RectF(0.5f - uw / 2, 0.45f - uh / 2, 0.5f + uw / 2, 0.45f + uh / 2), kind, color = color)
}
