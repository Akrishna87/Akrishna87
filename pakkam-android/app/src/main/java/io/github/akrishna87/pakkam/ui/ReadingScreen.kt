@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.akrishna87.pakkam.ui

import android.app.Activity
import android.graphics.Typeface
import android.text.Layout
import android.text.SpannableStringBuilder
import android.text.Spanned
import android.text.StaticLayout
import android.text.TextPaint
import android.text.style.LeadingMarginSpan
import android.text.style.RelativeSizeSpan
import android.text.style.StyleSpan
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.material3.OutlinedIconButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.key
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.view.WindowCompat
import io.github.akrishna87.pakkam.data.ReadStyle
import io.github.akrishna87.pakkam.data.ReadTheme
import io.github.akrishna87.pakkam.pdf.Block
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.waitForUpOrCancellation
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.TextButton
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.text.font.FontStyle
import io.github.akrishna87.pakkam.pdf.DeleteAnnotEdit
import io.github.akrishna87.pakkam.pdf.MarkOrigin
import io.github.akrishna87.pakkam.pdf.MarkupEdit
import io.github.akrishna87.pakkam.pdf.MarkupKind
import io.github.akrishna87.pakkam.pdf.ReadMark
import io.github.akrishna87.pakkam.pdf.ReadingText
import kotlin.math.max
import kotlin.math.min

/** One screenful of the reflowed text: lines [startLine, endLine) of one chunk's layout. */
data class ReadPage(val chunk: Int, val startLine: Int, val endLine: Int, val startOffset: Int)

/**
 * The reflowed document laid out into pages for one text style and screen size. The text is
 * split into chunks (starting at headings where possible) so a long book doesn't become one
 * enormous layout; each chunk starts on a new page, like a chapter.
 */
class ReadBook(
    val layouts: List<StaticLayout>,
    val bases: IntArray,
    val pages: List<ReadPage>,
    val blockStarts: IntArray,
    val blocks: List<Block>,
    val totalChars: Int,
) {
    fun pageAt(offset: Int): Int {
        if (pages.isEmpty()) return 0
        var lo = 0
        var hi = pages.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (pages[mid].startOffset <= offset) lo = mid else hi = mid - 1
        }
        return lo
    }

    /** The block a position falls in. */
    fun blockAt(offset: Int): Int {
        var lo = 0
        var hi = blockStarts.lastIndex
        while (lo < hi) {
            val mid = (lo + hi + 1) / 2
            if (blockStarts[mid] <= offset) lo = mid else hi = mid - 1
        }
        return lo.coerceAtLeast(0)
    }

    /** The PDF page the text of a reading page comes from. */
    fun pdfPageOf(page: Int): Int = pages.getOrNull(page)?.let { blocks.getOrNull(blockAt(it.startOffset))?.page } ?: 0

    /** The text on a reading page, for TalkBack. */
    fun textOf(page: Int): String {
        val p = pages.getOrNull(page) ?: return ""
        val l = layouts[p.chunk]
        return l.text.subSequence(l.getLineStart(p.startLine), l.getLineEnd(p.endLine - 1)).toString().trim()
    }

    companion object {
        private const val CHUNK_SOFT = 12_000
        private const val CHUNK_HARD = 40_000

        fun build(blocks: List<Block>, style: ReadStyle, widthPx: Int, heightPx: Int, density: Float): ReadBook {
            val paint = TextPaint(TextPaint.ANTI_ALIAS_FLAG).apply {
                textSize = style.sizeSp * density
                typeface = if (style.serif) Typeface.SERIF else Typeface.SANS_SERIF
                color = style.theme.text.toInt()
            }
            val indent = (paint.textSize * 1.4f).roundToInt()
            val layouts = ArrayList<StaticLayout>()
            val bases = ArrayList<Int>()
            val pages = ArrayList<ReadPage>()
            val blockStarts = IntArray(blocks.size)
            var base = 0
            var i = 0
            while (i < blocks.size) {
                val sb = SpannableStringBuilder()
                val chunkIndex = layouts.size
                while (i < blocks.size) {
                    val b = blocks[i]
                    if (sb.length > CHUNK_HARD || (sb.length > CHUNK_SOFT && b.kind == Block.Kind.HEADING)) break
                    if (b.kind == Block.Kind.HEADING && sb.isNotEmpty()) {
                        // A short blank line before a heading.
                        val s = sb.length
                        sb.append("\n")
                        sb.setSpan(RelativeSizeSpan(0.7f), s, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    val start = sb.length
                    blockStarts[i] = base + start
                    sb.append(b.text).append("\n")
                    if (b.kind == Block.Kind.HEADING) {
                        sb.setSpan(RelativeSizeSpan(1.3f), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                        sb.setSpan(StyleSpan(Typeface.BOLD), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    } else if (i > 0 && blocks[i - 1].kind == Block.Kind.PARAGRAPH) {
                        // Indent the first line of a paragraph that follows another, as books do.
                        // Fixed ends: a paragraph span would otherwise stretch over everything appended after it.
                        sb.setSpan(LeadingMarginSpan.Standard(indent, 0), start, sb.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
                    }
                    i++
                }
                val builder = StaticLayout.Builder.obtain(sb, 0, sb.length, paint, widthPx.coerceAtLeast(50))
                    .setLineSpacing(0f, style.spacing)
                    .setIncludePad(false)
                    .setBreakStrategy(Layout.BREAK_STRATEGY_HIGH_QUALITY)
                    .setHyphenationFrequency(Layout.HYPHENATION_FREQUENCY_NORMAL)
                if (style.justify) builder.setJustificationMode(Layout.JUSTIFICATION_MODE_INTER_WORD)
                val layout = builder.build()
                layouts += layout
                bases += base
                // Fill pages with whole lines.
                var line = 0
                while (line < layout.lineCount) {
                    val top = layout.getLineTop(line)
                    var end = line
                    while (end < layout.lineCount && layout.getLineBottom(end) - top <= heightPx) end++
                    if (end == line) end = line + 1
                    pages += ReadPage(chunkIndex, line, end, base + layout.getLineStart(line))
                    line = end
                }
                base += sb.length
            }
            return ReadBook(layouts, bases.toIntArray(), pages, blockStarts, blocks, base)
        }
    }
}

private sealed interface ReadSheet {
    data object Style : ReadSheet
    data object Contents : ReadSheet
    data object Notes : ReadSheet
}

/** What the note being written is for: newly chosen text, or a highlight already made. */
private sealed interface NoteFor {
    data class New(val start: Int, val end: Int) : NoteFor
    data class Edit(val mark: ReadMark) : NoteFor
}

/**
 * Reading mode: the PDF's text flowed into pages like a Kindle book, in the reader's own font,
 * size and colours. Tap the right or left edge (or swipe) to turn pages, the middle for controls.
 */
@Composable
fun ReadingScreen(vm: AppViewModel, session: DocSession) {
    val uriKey = session.uri.toString()
    var style by remember { mutableStateOf(vm.settings.readStyle) }
    var chrome by remember { mutableStateOf(false) }
    var sheet by remember { mutableStateOf<ReadSheet?>(null) }
    // The chosen text: (chunk, start, end) to shade, where on the screen it was pressed, and the word to look up.
    var picked by remember { mutableStateOf<Triple<Int, Int, Int>?>(null) }
    var pickedY by remember { mutableStateOf(0f) }
    var selecting by remember { mutableStateOf(false) }
    var lookup by remember { mutableStateOf<String?>(null) }
    // The highlight being looked at (by where it starts), and the note being written.
    var openMark by remember { mutableStateOf<Int?>(null) }
    var noteFor by remember { mutableStateOf<NoteFor?>(null) }
    var highlightColor by remember { mutableIntStateOf(vm.settings.highlightColor) }
    // While two fingers pinch: the text size they would set.
    var pinchSize by remember { mutableStateOf<Float?>(null) }
    val context = LocalContext.current
    val haptic = LocalHapticFeedback.current
    var offset by remember { mutableIntStateOf(vm.settings.readPosition(uriKey)) }
    var pdfPage by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val theme = style.theme

    val blocks by produceState<List<Block>?>(null, session.textVersion) {
        value = try {
            session.reflowBlocks()
        } catch (e: Exception) {
            emptyList()
        }
    }

    // Opened in Reading mode next time too, until the reader switches to page view.
    LaunchedEffect(uriKey) { vm.settings.setOpensInReadingMode(uriKey, true) }
    /** To page view, which the file then opens in next time. */
    fun toPageView() {
        vm.settings.setOpensInReadingMode(uriKey, false)
        if (pdfPage >= 0) vm.rememberPage(pdfPage)
        vm.back()
    }
    val saver = rememberDocSaver(vm, session) { pdfPage.coerceAtLeast(0) }
    var askToSave by remember { mutableStateOf(false) }
    /** Back closes the book, like an ebook reader, asking first about highlights and notes not yet saved. */
    fun leave() {
        if (session.dirty || session.edits.isNotEmpty()) {
            askToSave = true
            return
        }
        if (pdfPage >= 0) vm.rememberPage(pdfPage)
        vm.leaveDocument()
    }
    fun leaveNow() {
        askToSave = false
        if (pdfPage >= 0) vm.rememberPage(pdfPage)
        vm.leaveDocument()
    }
    BackHandler {
        when {
            sheet != null -> sheet = null
            picked != null -> picked = null
            else -> leave()
        }
    }
    if (askToSave) {
        AlertDialog(
            onDismissRequest = { askToSave = false },
            title = { Text("Save your changes?") },
            text = { Text("Your highlights, notes or other changes to “${session.name}” haven't been saved.") },
            confirmButton = {
                TextButton(onClick = {
                    askToSave = false
                    saver.save { leaveNow() }
                }) { Text("Save") }
            },
            dismissButton = {
                Row {
                    TextButton(onClick = { askToSave = false }) { Text("Cancel") }
                    TextButton(onClick = { leaveNow() }) { Text("Don't save") }
                }
            },
        )
    }

    // Light or dark status-bar icons to suit the page colour.
    val view = LocalView.current
    DisposableEffect(theme) {
        val window = (view.context as? Activity)?.window
        val controller = window?.let { WindowCompat.getInsetsController(it, view) }
        val before = controller?.isAppearanceLightStatusBars
        controller?.isAppearanceLightStatusBars = !theme.dark
        controller?.isAppearanceLightNavigationBars = !theme.dark
        onDispose {
            if (before != null) {
                controller?.isAppearanceLightStatusBars = before
                controller?.isAppearanceLightNavigationBars = before
            }
        }
    }

    val density = LocalDensity.current
    val bg = Color(theme.background)
    val fg = Color(theme.text)
    BoxWithConstraints(Modifier.fillMaxSize().background(bg)) {
        val topInset = WindowInsets.statusBars.getTop(density)
        val bottomInset = WindowInsets.navigationBars.getBottom(density)
        val marginPx = with(density) { style.marginDp.dp.toPx() }.roundToInt()
        val topPad = topInset + with(density) { 14.dp.toPx() }.roundToInt()
        val bottomPad = bottomInset + with(density) { 30.dp.toPx() }.roundToInt()
        val textW = constraints.maxWidth - 2 * marginPx
        val textH = constraints.maxHeight - topPad - bottomPad

        val list = blocks
        val book by produceState<ReadBook?>(null, list, style.copy(theme = ReadTheme.WHITE, scroll = false), textW, textH) {
            value = null
            if (list != null && list.isNotEmpty()) {
                value = withContext(Dispatchers.Default) { ReadBook.build(list, style, textW, textH, density.density) }
            }
        }

        when {
            list == null || (list.isNotEmpty() && book == null) -> Column(Modifier.align(Alignment.Center), horizontalAlignment = Alignment.CenterHorizontally) {
                CircularProgressIndicator(color = fg)
                Spacer(Modifier.size(16.dp))
                Text("Preparing reading mode…", color = fg)
            }
            list.isEmpty() -> Column(Modifier.align(Alignment.Center).padding(32.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(16.dp)) {
                Text("No text to reflow", style = MaterialTheme.typography.titleLarge, color = fg)
                Text(
                    "This PDF seems to be made of pictures of pages (a scan), so there are no words to set as a book. It still reads normally in page view.",
                    color = fg,
                )
                LaunchedEffect(Unit) { vm.settings.setOpensInReadingMode(uriKey, false) }
                Button(onClick = { toPageView() }) { Text("Back to page view") }
            }
            else -> {
                val b = book!!
                key(b, style.scroll) {
                    val pager = rememberPagerState(initialPage = b.pageAt(offset)) { b.pages.size }
                    val scroller = rememberLazyListState(initialFirstVisibleItemIndex = b.pageAt(offset))
                    val current = (if (style.scroll) scroller.firstVisibleItemIndex else pager.currentPage).coerceIn(0, b.pages.lastIndex)
                    val settled = if (style.scroll) (if (scroller.isScrollInProgress) -1 else scroller.firstVisibleItemIndex) else pager.settledPage
                    LaunchedEffect(settled) {
                        if (settled < 0) return@LaunchedEffect
                        offset = b.pages.getOrNull(settled)?.startOffset ?: 0
                        pdfPage = b.pdfPageOf(settled)
                        vm.settings.setReadPosition(uriKey, offset)
                    }
                    fun goTo(page: Int) {
                        scope.launch { if (style.scroll) scroller.scrollToItem(page) else pager.scrollToPage(page) }
                    }

                    // Highlights and notes: kept as highlight annotations in the PDF itself.
                    val reading = remember(b) { ReadingText(b.blocks, b.blockStarts) }
                    val edits = session.edits.toList()
                    val marks = remember(reading, session.annotations, edits) { reading.marks(session.annotations, edits) }
                    val marksNow = rememberUpdatedState(marks)
                    fun quote(start: Int, end: Int) = reading.textOf(start, end).replace('\n', ' ').trim()
                    fun addHighlight(start: Int, end: Int, color: Int, note: String = ""): Boolean {
                        val parts = reading.boxes(start, end)
                        if (parts.isEmpty()) {
                            vm.say("This text can't be marked: the PDF doesn't say where it sits on the page")
                            return false
                        }
                        val text = quote(start, end)
                        for ((i, part) in parts.withIndex()) {
                            session.edits.add(MarkupEdit(part.first, MarkupKind.HIGHLIGHT, part.second, color, text, if (i == 0) note else ""))
                        }
                        session.redo.clear()
                        return true
                    }
                    fun removeMark(m: ReadMark) {
                        for (o in m.origins) when (o) {
                            is MarkOrigin.Pending -> session.edits.remove(o.edit)
                            is MarkOrigin.Saved -> session.edits.add(DeleteAnnotEdit(o.annot.page, o.annot))
                        }
                        session.redo.clear()
                    }
                    fun restyle(m: ReadMark, color: Int, note: String) {
                        val text = quote(m.start, m.end)
                        for ((i, o) in m.origins.withIndex()) {
                            val n = if (i == 0) note else ""
                            when (o) {
                                is MarkOrigin.Pending -> {
                                    val at = session.edits.indexOf(o.edit)
                                    if (at >= 0) session.edits[at] = o.edit.copy(color = color, note = n)
                                }
                                is MarkOrigin.Saved -> {
                                    // A changed annotation is written as a new one in place of the old.
                                    session.edits.add(DeleteAnnotEdit(o.annot.page, o.annot))
                                    session.edits.add(MarkupEdit(o.annot.page, MarkupKind.HIGHLIGHT, o.annot.quads.ifEmpty { listOf(o.annot.rect) }, color, text, n))
                                }
                            }
                        }
                        session.redo.clear()
                    }

                    val markPaint = remember { android.graphics.Paint(android.graphics.Paint.ANTI_ALIAS_FLAG) }
                    val markPath = remember { android.graphics.Path() }
                    val dotRadius = with(density) { 4.dp.toPx() }
                    /** Draws reading page [i] with its first line [y] pixels from the top of the canvas. */
                    fun androidx.compose.ui.graphics.drawscope.DrawScope.drawReadPage(i: Int, y: Float) {
                        val p = b.pages[i]
                        val layout = b.layouts[p.chunk]
                        val top = layout.getLineTop(p.startLine)
                        val bottom = layout.getLineBottom(p.endLine - 1)
                        layout.paint.color = theme.text.toInt()
                        drawIntoCanvas { c ->
                            val nc = c.nativeCanvas
                            nc.save()
                            nc.translate(marginPx.toFloat(), y - top)
                            nc.clipRect(0, top, layout.width, bottom)
                            val cb = b.bases[p.chunk]
                            val ce = cb + layout.text.length
                            val pageStart = cb + layout.getLineStart(p.startLine)
                            val pageEnd = cb + layout.getLineEnd(p.endLine - 1)
                            for (m in marksNow.value) {
                                if (m.end <= pageStart || m.start >= pageEnd) continue
                                val s0 = max(m.start, cb) - cb
                                val e0 = min(m.end, ce) - cb
                                markPath.reset()
                                layout.getSelectionPath(s0, e0, markPath)
                                markPaint.style = android.graphics.Paint.Style.FILL
                                markPaint.color = (m.color and 0xFFFFFF) or (if (theme.dark) 0x5C000000 else 0x73000000)
                                nc.drawPath(markPath, markPaint)
                                if (m.note.isNotBlank() && m.end <= ce) {
                                    // A small dot after the highlight says it has a note.
                                    val line = layout.getLineForOffset((e0 - 1).coerceAtLeast(0))
                                    val x = if (layout.getLineForOffset(e0) != line) layout.getLineRight(line) else layout.getPrimaryHorizontal(e0)
                                    val cy = layout.getLineTop(line) + dotRadius * 1.2f
                                    markPaint.color = m.color or (0xFF shl 24)
                                    nc.drawCircle(x + dotRadius * 0.8f, cy, dotRadius, markPaint)
                                    markPaint.style = android.graphics.Paint.Style.STROKE
                                    markPaint.strokeWidth = dotRadius * 0.35f
                                    markPaint.color = (theme.text.toInt() and 0xFFFFFF) or 0x99000000.toInt()
                                    nc.drawCircle(x + dotRadius * 0.8f, cy, dotRadius, markPaint)
                                }
                            }
                            picked?.takeIf { it.first == p.chunk }?.let { (_, s0, e0) ->
                                markPath.reset()
                                layout.getSelectionPath(s0, e0, markPath)
                                markPaint.style = android.graphics.Paint.Style.FILL
                                markPaint.color = if (theme.dark) 0x664F8DFF else 0x553D7BFF
                                nc.drawPath(markPath, markPaint)
                            }
                            layout.draw(nc)
                            nc.restore()
                        }
                    }
                    /** The character under a touch, as (chunk, offset in the chunk's text); null off the text. */
                    fun charAt(o: androidx.compose.ui.geometry.Offset): Pair<Int, Int>? {
                        val (i, pageTop) = if (style.scroll) {
                            val item = scroller.layoutInfo.visibleItemsInfo.firstOrNull { o.y >= it.offset && o.y < it.offset + it.size } ?: return null
                            item.index to item.offset.toFloat()
                        } else {
                            // Read now, not when the gesture handler was set up, so it's the page on screen.
                            pager.currentPage to topPad.toFloat()
                        }
                        val p = b.pages.getOrNull(i) ?: return null
                        val layout = b.layouts[p.chunk]
                        val top = layout.getLineTop(p.startLine)
                        val y = (o.y - pageTop + top).roundToInt()
                        if (y < top || y > layout.getLineBottom(p.endLine - 1)) return null
                        val line = layout.getLineForVertical(y)
                        val x = o.x - marginPx
                        if (x < layout.getLineLeft(line) - 12 || x > layout.getLineRight(line) + 12) return null
                        return p.chunk to layout.getOffsetForHorizontal(line, x).coerceIn(0, layout.text.length)
                    }
                    /** The word under a touch: what to shade and the word itself. */
                    fun wordAt(o: androidx.compose.ui.geometry.Offset): Pair<Triple<Int, Int, Int>, String>? {
                        val (chunk, at) = charAt(o) ?: return null
                        val text = b.layouts[chunk].text
                        fun isWord(c: Char) = c.isLetterOrDigit() || c == '\'' || c == '’' || c == '-'
                        var s0 = at
                        if ((s0 >= text.length || !isWord(text[s0])) && s0 > 0 && isWord(text[s0 - 1])) s0--
                        if (s0 >= text.length || !isWord(text[s0])) return null
                        var e0 = s0
                        while (s0 > 0 && isWord(text[s0 - 1])) s0--
                        while (e0 < text.length && isWord(text[e0])) e0++
                        while (s0 < e0 && !text[s0].isLetterOrDigit()) s0++
                        while (e0 > s0 && !text[e0 - 1].isLetterOrDigit()) e0--
                        if (e0 <= s0) return null
                        return Triple(chunk, s0, e0) to text.substring(s0, e0)
                    }
                    fun onTap(o: androidx.compose.ui.geometry.Offset, w: Int, h: Int) {
                        if (picked != null) {
                            picked = null
                            return
                        }
                        val hit = charAt(o)?.let { (chunk, at) -> b.bases[chunk] + at }
                        val mark = hit?.let { g -> marksNow.value.firstOrNull { g >= it.start && g <= it.end } }
                        when {
                            mark != null -> openMark = mark.start
                            // With the menus showing, any tap puts them away; the top of the page or,
                            // when scrolling, anywhere brings them back, as in an ebook reader.
                            chrome || style.scroll || o.y < h * 0.15f -> chrome = !chrome
                            o.x < w * 0.28f -> scope.launch {
                                if (style.scroll) scroller.animateScrollBy(-h * 0.85f) else if (pager.currentPage > 0) pager.animateScrollToPage(pager.currentPage - 1)
                            }
                            o.x > w * 0.72f -> scope.launch {
                                if (style.scroll) scroller.animateScrollBy(h * 0.85f) else if (pager.currentPage < b.pages.lastIndex) pager.animateScrollToPage(pager.currentPage + 1)
                            }
                            else -> chrome = !chrome
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            // Two fingers change the text size, like zooming; the pages are laid out again on letting go.
                            .pointerInput(Unit) {
                                awaitEachGesture {
                                    awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                                    var startDist = 0f
                                    var startSize = 0f
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val pressed = event.changes.filter { it.pressed }
                                        if (pressed.size >= 2) {
                                            val a = pressed[0].position
                                            val d = kotlin.math.hypot(a.x - pressed[1].position.x, a.y - pressed[1].position.y).coerceAtLeast(1f)
                                            if (startDist == 0f) {
                                                startDist = d
                                                startSize = style.sizeSp
                                                picked = null
                                            }
                                            pinchSize = (startSize * d / startDist).coerceIn(10f, 34f).roundToInt().toFloat()
                                        }
                                        if (startDist > 0f) event.changes.forEach { it.consume() }
                                        if (event.changes.none { it.pressed }) break
                                    }
                                    val size = pinchSize
                                    pinchSize = null
                                    if (size != null && size != style.sizeSp) {
                                        style = style.copy(sizeSp = size)
                                        vm.settings.readStyle = style
                                    }
                                }
                            }
                            .pointerInput(b, style.scroll) {
                                awaitEachGesture {
                                    val down = awaitFirstDown(requireUnconsumed = false)
                                    var cancelled = false
                                    val up = withTimeoutOrNull(viewConfiguration.longPressTimeoutMillis) {
                                        waitForUpOrCancellation().also { if (it == null) cancelled = true }
                                    }
                                    if (up != null) {
                                        onTap(up.position, size.width, size.height)
                                        return@awaitEachGesture
                                    }
                                    if (cancelled) return@awaitEachGesture
                                    // A long press picks the word; moving on before lifting stretches the choice word by word.
                                    val (first, word) = wordAt(down.position) ?: return@awaitEachGesture
                                    haptic.performHapticFeedback(HapticFeedbackType.LongPress)
                                    lookup = null
                                    picked = first
                                    pickedY = down.position.y / size.height
                                    selecting = true
                                    var moved = false
                                    while (true) {
                                        val event = awaitPointerEvent(PointerEventPass.Initial)
                                        val change = event.changes.firstOrNull { it.id == down.id } ?: break
                                        change.consume()
                                        if (!change.pressed) break
                                        val w = wordAt(change.position)?.first ?: continue
                                        if (w.first != first.first) continue
                                        val next = Triple(first.first, min(first.second, w.second), max(first.third, w.third))
                                        if (next != picked) {
                                            picked = next
                                            moved = true
                                        }
                                    }
                                    selecting = false
                                    // A single word opens the dictionary, as before; a longer stretch shows the highlight bar.
                                    if (!moved) lookup = word
                                }
                            },
                    ) {
                        if (style.scroll) {
                            // One long column, like an ebook's continuous scrolling.
                            LazyColumn(
                                state = scroller,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(
                                    top = with(density) { topPad.toDp() },
                                    bottom = with(density) { bottomPad.toDp() },
                                ),
                            ) {
                                items(b.pages.size) { i ->
                                    val pageText = remember(b, i) { b.textOf(i).take(3000) }
                                    val p = b.pages[i]
                                    val l = b.layouts[p.chunk]
                                    val hPx = l.getLineBottom(p.endLine - 1) - l.getLineTop(p.startLine)
                                    Canvas(
                                        Modifier
                                            .fillMaxWidth()
                                            .height(with(density) { hPx.toDp() })
                                            .semantics { contentDescription = pageText },
                                    ) { drawReadPage(i, 0f) }
                                }
                            }
                        } else {
                            HorizontalPager(state = pager, modifier = Modifier.fillMaxSize(), beyondViewportPageCount = 1) { i ->
                                val pageText = remember(b, i) { b.textOf(i).take(3000) }
                                Canvas(
                                    Modifier
                                        .fillMaxSize()
                                        .semantics { contentDescription = pageText },
                                ) { drawReadPage(i, topPad.toFloat()) }
                            }
                        }
                        pinchSize?.let { size ->
                            Surface(
                                Modifier.align(Alignment.Center),
                                shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp),
                                tonalElevation = 6.dp,
                                shadowElevation = 8.dp,
                            ) {
                                Column(Modifier.padding(horizontal = 28.dp, vertical = 16.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text("Aa", fontSize = size.sp, fontFamily = if (style.serif) FontFamily.Serif else FontFamily.SansSerif)
                                    Text("Text size ${size.roundToInt()}", style = MaterialTheme.typography.labelLarge)
                                }
                            }
                        }
                        // The page line at the bottom, like an ebook's.
                        val pct = if (b.totalChars > 0) (b.pages[current].startOffset * 100L / b.totalChars).toInt() else 0
                        Text(
                            "Page ${current + 1} of ${b.pages.size} · $pct%",
                            color = fg.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                        )
                        val unsaved = session.dirty || session.edits.isNotEmpty()
                        ReadingChrome(
                            visible = chrome,
                            title = session.name,
                            page = current,
                            pageCount = b.pages.size,
                            chapter = b.blocks.subList(0, b.blockAt(b.pages[current].startOffset) + 1)
                                .lastOrNull { it.kind == Block.Kind.HEADING }?.text,
                            pdfPage = b.pdfPageOf(current),
                            unsaved = unsaved,
                            onBack = { leave() },
                            onPageView = { toPageView() },
                            onNotes = { sheet = ReadSheet.Notes },
                            onSave = { saver.save(null) },
                            onStyle = { sheet = ReadSheet.Style },
                            onContents = { sheet = ReadSheet.Contents },
                            onSeek = { goTo(it) },
                        )

                        // The bar for a chosen stretch of text, away from the words it is about.
                        val sel = picked
                        if (sel != null && !selecting && lookup == null && noteFor == null) {
                            val (chunk, s0, e0) = sel
                            val start = b.bases[chunk] + s0
                            val end = b.bases[chunk] + e0
                            val chosen = b.layouts[chunk].text.substring(s0, e0)
                            ReadSelectionBar(
                                modifier = Modifier
                                    .align(if (pickedY > 0.5f) Alignment.TopCenter else Alignment.BottomCenter)
                                    .then(if (pickedY > 0.5f) Modifier.statusBarsPadding() else Modifier.navigationBarsPadding())
                                    .padding(horizontal = 12.dp, vertical = 36.dp),
                                color = highlightColor,
                                canDefine = chosen.length <= 40 && chosen.none { it.isWhitespace() },
                                onColor = { col ->
                                    highlightColor = col
                                    vm.settings.highlightColor = col
                                    if (addHighlight(start, end, col)) picked = null
                                },
                                onNote = { noteFor = NoteFor.New(start, end) },
                                onCopy = {
                                    copyText(context, quote(start, end))
                                    picked = null
                                },
                                onDefine = { lookup = chosen },
                                onClose = { picked = null },
                            )
                        }

                        when (sheet) {
                            ReadSheet.Contents -> ReadingContents(
                                vm, session, b,
                                onDismiss = { sheet = null },
                                onGo = { target ->
                                    sheet = null
                                    chrome = false
                                    goTo(b.pageAt(target))
                                },
                            )
                            ReadSheet.Notes -> ReadingNotes(
                                marks = marks,
                                quote = { m -> quote(m.start, m.end) },
                                pageOf = { m -> b.pageAt(m.start) },
                                unsaved = unsaved,
                                onSave = { saver.save(null) },
                                onDismiss = { sheet = null },
                                onGo = { target ->
                                    sheet = null
                                    chrome = false
                                    goTo(b.pageAt(target))
                                },
                            )
                            else -> Unit
                        }
                    }

                    lookup?.let { w ->
                        val sel = picked
                        DefinitionSheet(
                            vm, w,
                            actions = if (sel == null) {
                                null
                            } else {
                                {
                                    val (chunk, s0, e0) = sel
                                    val start = b.bases[chunk] + s0
                                    val end = b.bases[chunk] + e0
                                    // Highlight the word in any colour, or add a note to it.
                                    Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                                        Text("Highlight", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(end = 12.dp))
                                        ColorDots(HIGHLIGHT_COLORS, highlightColor) { col ->
                                            highlightColor = col
                                            vm.settings.highlightColor = col
                                            if (addHighlight(start, end, col)) {
                                                lookup = null
                                                picked = null
                                            }
                                        }
                                    }
                                    FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        AssistChip(
                                            onClick = {
                                                lookup = null
                                                noteFor = NoteFor.New(start, end)
                                            },
                                            label = { Text("Add note") },
                                            leadingIcon = { Icon(Icons.Filled.EditNote, null, Modifier.size(18.dp)) },
                                        )
                                    }
                                }
                            },
                        ) {
                            lookup = null
                            picked = null
                        }
                    }

                    // A highlight that was tapped, followed through changes (it is found again by where it starts).
                    openMark?.let { at ->
                        val m = marks.firstOrNull { at >= it.start && at < it.end }
                        if (m == null) {
                            LaunchedEffect(at) { openMark = null }
                        } else {
                            MarkSheet(
                                mark = m,
                                quote = quote(m.start, m.end),
                                pending = m.origins.any { it is MarkOrigin.Pending } || session.edits.any { it is DeleteAnnotEdit },
                                onDismiss = { openMark = null },
                                onColor = { col -> restyle(m, col, m.note) },
                                onNote = { noteFor = NoteFor.Edit(m) },
                                onCopy = {
                                    copyText(context, quote(m.start, m.end))
                                    openMark = null
                                },
                                onRemove = {
                                    removeMark(m)
                                    openMark = null
                                },
                            )
                        }
                    }

                    noteFor?.let { target ->
                        val (start, end, initial) = when (target) {
                            is NoteFor.New -> Triple(target.start, target.end, "")
                            is NoteFor.Edit -> Triple(target.mark.start, target.mark.end, target.mark.note)
                        }
                        NoteDialog(
                            quote = quote(start, end),
                            initial = initial,
                            onDismiss = {
                                noteFor = null
                                picked = null
                            },
                        ) { text ->
                            when (target) {
                                is NoteFor.New -> addHighlight(start, end, highlightColor, text)
                                is NoteFor.Edit -> restyle(target.mark, target.mark.color, text)
                            }
                            noteFor = null
                            picked = null
                        }
                    }
                }
            }
        }
        if (sheet == ReadSheet.Style) {
            ReadingStyleSheet(style, onDismiss = { sheet = null }) {
                style = it
                vm.settings.readStyle = it
            }
        }
    }
}

@Composable
private fun ReadingChrome(
    visible: Boolean,
    title: String,
    page: Int,
    pageCount: Int,
    chapter: String?,
    pdfPage: Int,
    unsaved: Boolean,
    onBack: () -> Unit,
    onPageView: () -> Unit,
    onNotes: () -> Unit,
    onSave: () -> Unit,
    onStyle: () -> Unit,
    onContents: () -> Unit,
    onSeek: (Int) -> Unit,
) {
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                actions = {
                    if (unsaved) IconButton(onClick = onSave) { Icon(Icons.Filled.Save, "Save") }
                    IconButton(onClick = onNotes) { Icon(Icons.Filled.EditNote, "Notes and highlights") }
                    IconButton(onClick = onContents) { Icon(Icons.Filled.List, "Chapters") }
                    IconButton(onClick = onStyle) { Icon(Icons.Filled.TextFields, "Text and colours") }
                    IconButton(onClick = onPageView) { Icon(Icons.Filled.PictureAsPdf, "Page view") }
                },
            )
        }
        AnimatedVisibility(visible, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(horizontal = 20.dp, vertical = 12.dp)) {
                    if (chapter != null) Text(chapter, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    var drag by remember(page) { mutableStateOf(page.toFloat()) }
                    Slider(
                        value = drag,
                        onValueChange = { drag = it },
                        onValueChangeFinished = { onSeek(drag.roundToInt()) },
                        valueRange = 0f..(pageCount - 1).coerceAtLeast(1).toFloat(),
                    )
                    Text(
                        "Page ${drag.roundToInt() + 1} of $pageCount · from PDF page ${pdfPage + 1}",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun ReadingContents(vm: AppViewModel, session: DocSession, book: ReadBook, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    val outline by produceState<List<io.github.akrishna87.pakkam.pdf.OutlineItem>?>(null, session.version) { value = session.doc.outline() }
    // The PDF's own table of contents when it has one; otherwise the headings found in the text.
    val entries: List<Triple<String, Int, Int>> = remember(outline, book) {
        val o = outline.orEmpty()
        if (o.isNotEmpty()) {
            o.map { item ->
                val bi = book.blocks.indexOfFirst { it.page >= item.page }.let { if (it < 0) book.blocks.lastIndex else it }
                Triple(item.title, item.depth, book.blockStarts.getOrElse(bi) { 0 })
            }
        } else {
            book.blocks.withIndex().filter { it.value.kind == Block.Kind.HEADING }.take(500).map { Triple(it.value.text, 0, book.blockStarts[it.index]) }
        }
    }
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Text("Chapters", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp))
        if (entries.isEmpty()) {
            Text("This document has no chapters or headings.", Modifier.padding(16.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            items(entries) { (title, depth, at) ->
                ListItem(
                    modifier = Modifier.clickable { onGo(at) }.padding(start = (depth * 16).dp),
                    headlineContent = { Text(title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                    trailingContent = { Text("${book.pageAt(at) + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }
}

@Composable
private fun ReadingStyleSheet(style: ReadStyle, onDismiss: () -> Unit, onChange: (ReadStyle) -> Unit) {
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            SectionTitle("Page colour")
            Row(horizontalArrangement = Arrangement.spacedBy(16.dp)) {
                for (t in ReadTheme.entries) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Box(
                            Modifier
                                .size(48.dp)
                                .background(Color(t.background), CircleShape)
                                .border(if (style.theme == t) 3.dp else 1.dp, if (style.theme == t) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                                .clickable { onChange(style.copy(theme = t)) }
                                .semantics { contentDescription = "${t.label} pages" },
                            contentAlignment = Alignment.Center,
                        ) {
                            if (style.theme == t) Icon(Icons.Filled.Check, null, tint = Color(t.text)) else Text("Aa", color = Color(t.text), fontWeight = FontWeight.SemiBold)
                        }
                        Text(t.label, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                    }
                }
            }
            Row(verticalAlignment = Alignment.CenterVertically) {
                OutlinedIconButton(onClick = { onChange(style.copy(sizeSp = (style.sizeSp - 1f).coerceAtLeast(10f))) }) {
                    Text("A", fontSize = 14.sp, fontFamily = FontFamily.Serif)
                }
                Slider(
                    value = style.sizeSp,
                    onValueChange = { onChange(style.copy(sizeSp = it.roundToInt().toFloat())) },
                    valueRange = 10f..34f,
                    modifier = Modifier.weight(1f).padding(horizontal = 8.dp),
                )
                OutlinedIconButton(onClick = { onChange(style.copy(sizeSp = (style.sizeSp + 1f).coerceAtMost(34f))) }) {
                    Text("A", fontSize = 22.sp, fontFamily = FontFamily.Serif)
                }
            }
            Text("Text size ${style.sizeSp.roundToInt()}", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
            SectionTitle("Font")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = style.serif, onClick = { onChange(style.copy(serif = true)) }, label = { Text("Book (serif)", fontFamily = FontFamily.Serif) })
                FilterChip(selected = !style.serif, onClick = { onChange(style.copy(serif = false)) }, label = { Text("Clean (sans)", fontFamily = FontFamily.SansSerif) })
            }
            SectionTitle("Line spacing")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((v, l) in listOf(1.15f to "Compact", 1.3f to "Normal", 1.6f to "Relaxed")) {
                    FilterChip(selected = style.spacing == v, onClick = { onChange(style.copy(spacing = v)) }, label = { Text(l) })
                }
            }
            SectionTitle("Margins")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((v, l) in listOf(0 to "Narrow", 1 to "Normal", 2 to "Wide")) {
                    FilterChip(selected = style.margin == v, onClick = { onChange(style.copy(margin = v)) }, label = { Text(l) })
                }
            }
            SectionTitle("Moving through the book")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = !style.scroll, onClick = { onChange(style.copy(scroll = false)) }, label = { Text("Turn pages") })
                FilterChip(selected = style.scroll, onClick = { onChange(style.copy(scroll = true)) }, label = { Text("Scroll") })
            }
            SwitchRow("Justify text (even right edge)", style.justify) { onChange(style.copy(justify = it)) }
        }
    }
}

private fun copyText(context: android.content.Context, text: String) {
    val cm = context.getSystemService(android.content.Context.CLIPBOARD_SERVICE) as android.content.ClipboardManager
    cm.setPrimaryClip(android.content.ClipData.newPlainText("Text", text))
}

/** Shown for chosen text: a colour highlights it straight away, or add a note, copy or look it up. */
@Composable
private fun ReadSelectionBar(
    modifier: Modifier,
    color: Int,
    canDefine: Boolean,
    onColor: (Int) -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onDefine: () -> Unit,
    onClose: () -> Unit,
) {
    Surface(modifier, shape = androidx.compose.foundation.shape.RoundedCornerShape(16.dp), tonalElevation = 6.dp, shadowElevation = 8.dp) {
        Column(Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
            // Tapping a colour highlights the chosen words in it straight away.
            Row(verticalAlignment = Alignment.CenterVertically) {
                Row(Modifier.weight(1f).horizontalScroll(rememberScrollState()).padding(start = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    ColorDots(HIGHLIGHT_COLORS, color, onColor)
                }
                IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Close") }
            }
            Row {
                TextButton(onClick = onNote) {
                    Icon(Icons.Filled.EditNote, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Note")
                }
                TextButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Copy")
                }
                if (canDefine) {
                    TextButton(onClick = onDefine) {
                        Icon(Icons.Filled.MenuBook, null, Modifier.size(18.dp))
                        Spacer(Modifier.size(6.dp))
                        Text("Define")
                    }
                }
            }
        }
    }
}

/** A tapped highlight: its words, its note, and ways to change or remove it. */
@Composable
private fun MarkSheet(
    mark: ReadMark,
    quote: String,
    pending: Boolean,
    onDismiss: () -> Unit,
    onColor: (Int) -> Unit,
    onNote: () -> Unit,
    onCopy: () -> Unit,
    onRemove: () -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(
            Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 20.dp).padding(bottom = 28.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Row {
                Box(Modifier.size(width = 4.dp, height = 48.dp).background(Color(mark.color or (0xFF shl 24))))
                Text(
                    quote,
                    Modifier.padding(start = 12.dp),
                    style = MaterialTheme.typography.bodyLarge,
                    fontFamily = FontFamily.Serif,
                    maxLines = 8,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            if (mark.note.isNotBlank()) {
                Surface(color = MaterialTheme.colorScheme.secondaryContainer, shape = androidx.compose.foundation.shape.RoundedCornerShape(12.dp)) {
                    Text(mark.note, Modifier.fillMaxWidth().padding(12.dp), style = MaterialTheme.typography.bodyMedium)
                }
            }
            ColorDots(HIGHLIGHT_COLORS, mark.color or (0xFF shl 24), onColor)
            FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = onNote) {
                    Icon(Icons.Filled.EditNote, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text(if (mark.note.isBlank()) "Add note" else "Edit note")
                }
                TextButton(onClick = onCopy) {
                    Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Copy")
                }
                TextButton(onClick = onRemove) {
                    Icon(Icons.Filled.Delete, null, Modifier.size(18.dp))
                    Spacer(Modifier.size(6.dp))
                    Text("Remove highlight")
                }
            }
            if (pending) {
                Text(
                    "Highlights are kept in the PDF itself once you save it, so they show in page view and in other PDF apps too.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** Every highlight and note in the book, in reading order; tap one to go to it. */
@Composable
private fun ReadingNotes(
    marks: List<ReadMark>,
    quote: (ReadMark) -> String,
    pageOf: (ReadMark) -> Int,
    unsaved: Boolean,
    onSave: () -> Unit,
    onDismiss: () -> Unit,
    onGo: (Int) -> Unit,
) {
    ModalBottomSheet(onDismissRequest = onDismiss) {
        Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Notes and highlights", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            if (unsaved) TextButton(onClick = onSave) { Text("Save to PDF") }
        }
        if (marks.isEmpty()) {
            Text(
                "Nothing marked yet. Press and hold a word, then slide your finger to choose more, and pick a colour to highlight it. Add a note to remember why.",
                Modifier.padding(16.dp).padding(bottom = 24.dp),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        LazyColumn(Modifier.padding(bottom = 24.dp)) {
            items(marks) { m ->
                ListItem(
                    modifier = Modifier.clickable { onGo(m.start) },
                    leadingContent = { Box(Modifier.size(width = 6.dp, height = 40.dp).background(Color(m.color or (0xFF shl 24)), CircleShape)) },
                    headlineContent = { Text(quote(m), maxLines = 3, overflow = TextOverflow.Ellipsis, fontFamily = FontFamily.Serif) },
                    supportingContent = if (m.note.isNotBlank()) {
                        { Text(m.note, fontStyle = FontStyle.Italic, maxLines = 4, overflow = TextOverflow.Ellipsis) }
                    } else {
                        null
                    },
                    trailingContent = { Text("p. ${pageOf(m) + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                )
            }
        }
    }
}

@Composable
private fun NoteDialog(quote: String, initial: String, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    val focus = remember { FocusRequester() }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial.isBlank()) "Add a note" else "Edit note") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text("“$quote”", style = MaterialTheme.typography.bodyMedium, fontStyle = FontStyle.Italic, maxLines = 3, overflow = TextOverflow.Ellipsis)
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Your note") },
                    minLines = 3,
                    modifier = Modifier.fillMaxWidth().focusRequester(focus),
                )
            }
        },
        confirmButton = { TextButton(onClick = { onSave(text.trim()) }) { Text("Save note") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
    LaunchedEffect(Unit) {
        try {
            focus.requestFocus()
        } catch (_: Exception) {
        }
    }
}
