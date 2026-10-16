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
    var offset by remember { mutableIntStateOf(vm.settings.readPosition(uriKey)) }
    var pdfPage by remember { mutableIntStateOf(-1) }
    val scope = rememberCoroutineScope()
    val theme = style.theme

    val blocks by produceState<List<Block>?>(null, session.version) {
        value = try {
            session.reflowBlocks()
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun leave() {
        if (pdfPage >= 0) vm.rememberPage(pdfPage)
        vm.back()
    }
    BackHandler {
        when {
            sheet != null -> sheet = null
            else -> leave()
        }
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
                Button(onClick = { leave() }) { Text("Back to page view") }
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
                            layout.draw(nc)
                            nc.restore()
                        }
                    }
                    Box(
                        Modifier
                            .fillMaxSize()
                            .pointerInput(b, style.scroll) {
                                detectTapGestures { o ->
                                    val w = size.width
                                    val h = size.height
                                    when {
                                        o.x < w * 0.28f -> scope.launch {
                                            if (style.scroll) scroller.animateScrollBy(-h * 0.85f) else if (pager.currentPage > 0) pager.animateScrollToPage(pager.currentPage - 1)
                                        }
                                        o.x > w * 0.72f -> scope.launch {
                                            if (style.scroll) scroller.animateScrollBy(h * 0.85f) else if (pager.currentPage < b.pages.lastIndex) pager.animateScrollToPage(pager.currentPage + 1)
                                        }
                                        else -> chrome = !chrome
                                    }
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
                        // The page line at the bottom, like an ebook's.
                        val pct = if (b.totalChars > 0) (b.pages[current].startOffset * 100L / b.totalChars).toInt() else 0
                        Text(
                            "Page ${current + 1} of ${b.pages.size} · $pct%",
                            color = fg.copy(alpha = 0.6f),
                            style = MaterialTheme.typography.labelMedium,
                            modifier = Modifier.align(Alignment.BottomCenter).navigationBarsPadding().padding(bottom = 12.dp),
                        )
                        ReadingChrome(
                            visible = chrome,
                            title = session.name,
                            page = current,
                            pageCount = b.pages.size,
                            chapter = b.blocks.subList(0, b.blockAt(b.pages[current].startOffset) + 1)
                                .lastOrNull { it.kind == Block.Kind.HEADING }?.text,
                            pdfPage = b.pdfPageOf(current),
                            onBack = { leave() },
                            onStyle = { sheet = ReadSheet.Style },
                            onContents = { sheet = ReadSheet.Contents },
                            onSeek = { goTo(it) },
                        )
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
                            else -> Unit
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
    onBack: () -> Unit,
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
                    IconButton(onClick = onContents) { Icon(Icons.Filled.List, "Chapters") }
                    IconButton(onClick = onStyle) { Icon(Icons.Filled.TextFields, "Text and colours") }
                    IconButton(onClick = onBack) { Icon(Icons.Filled.PictureAsPdf, "Page view") }
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
