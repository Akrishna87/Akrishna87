package io.github.akrishna87.rssreader.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.graphics.Canvas
import android.graphics.ColorFilter
import android.graphics.PixelFormat
import android.graphics.drawable.Drawable
import android.net.Uri
import android.text.Html
import android.text.Spannable
import android.text.Spanned
import android.text.style.BackgroundColorSpan
import android.view.ActionMode
import android.view.Menu
import android.view.MenuItem
import android.text.method.LinkMovementMethod
import android.widget.TextView
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.toArgb
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.res.ResourcesCompat
import coil.compose.AsyncImage
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import io.github.akrishna87.rssreader.R
import io.github.akrishna87.rssreader.data.Article
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.data.resolveUrl
import io.github.akrishna87.rssreader.hostOf
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun ArticleScreen(
    article: Article,
    feed: Feed?,
    backLabel: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    textSize: Int,
    boards: List<String>,
    onToggleStar: () -> Unit,
    onMarkUnread: () -> Unit,
    onToggleBoard: (String) -> Unit,
    onCreateBoard: (String) -> Boolean,
    onHighlight: (text: String, note: String) -> Unit,
    onRemoveHighlight: (String) -> Unit,
) {
    val apple = LocalApple.current
    val context = LocalContext.current
    val scroll = rememberScrollState()
    var showBoards by remember { mutableStateOf(false) }
    var newBoard by remember { mutableStateOf(false) }
    // Text selected for "Add note" while the note sheet is open.
    var noteFor by remember { mutableStateOf<String?>(null) }
    Box(Modifier.fillMaxSize().background(apple.background)) {
        Column(Modifier.fillMaxSize()) {
            NavBar(
                title = feed?.name.orEmpty(),
                showTitle = scroll.value > 200,
                showDivider = scroll.value > 0,
                backLabel = backLabel,
                onBack = onBack,
            )
            Column(
                Modifier.weight(1f).fillMaxWidth().verticalScroll(scroll).padding(horizontal = 20.dp),
            ) {
                Spacer(Modifier.height(8.dp))
                if (feed != null) {
                    Text(
                        feed.name.uppercase(),
                        style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.4.sp),
                        color = apple.tint,
                    )
                    Spacer(Modifier.height(6.dp))
                }
                Text(article.title, style = AppleType.title1, color = apple.label)
                Spacer(Modifier.height(8.dp))
                val meta = listOfNotNull(
                    article.author.takeIf { it.isNotBlank() },
                    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(article.date)),
                ).joinToString(" · ")
                Text(meta, style = AppleType.subheadline, color = apple.secondaryLabel)
                Spacer(Modifier.height(18.dp))
                val hasInlineImages = remember(article.contentHtml) { Regex("<img\\b", RegexOption.IGNORE_CASE).containsMatchIn(article.contentHtml) }
                if (article.thumbnail != null && !hasInlineImages) {
                    AsyncImage(
                        model = article.thumbnail,
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxWidth().heightIn(max = 260.dp).clip(RoundedCornerShape(12.dp)).background(apple.fill),
                    )
                    Spacer(Modifier.height(18.dp))
                }
                if (article.contentHtml.isNotBlank()) {
                    HtmlText(
                        article.contentHtml,
                        baseUrl = article.link.ifEmpty { feed?.url.orEmpty() },
                        textSize = textSize,
                        highlights = article.highlights.map { it.text },
                        onSelection = { text, wantNote -> if (wantNote) noteFor = text else onHighlight(text, "") },
                    )
                } else {
                    Text(
                        "This feed only includes a headline. Open the original to read the full article.",
                        style = AppleType.body,
                        color = apple.secondaryLabel,
                    )
                }
                if (article.highlights.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Text("YOUR HIGHLIGHTS", style = AppleType.footnote.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.5.sp), color = apple.secondaryLabel)
                    article.highlights.forEach { h ->
                        Spacer(Modifier.height(10.dp))
                        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(8.dp)).background(apple.yellow.copy(alpha = 0.22f)).padding(12.dp)) {
                            Text(h.text, style = AppleType.subheadline, color = apple.label)
                            if (h.note.isNotEmpty()) {
                                Spacer(Modifier.height(4.dp))
                                Text(h.note, style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold), color = apple.label)
                            }
                            Text(
                                "Remove",
                                style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold),
                                color = apple.red,
                                modifier = Modifier.padding(top = 6.dp).clickable { onRemoveHighlight(h.text) },
                            )
                        }
                    }
                }
                if (article.link.isNotEmpty()) {
                    Spacer(Modifier.height(28.dp))
                    Text(
                        "Read on ${hostOf(article.link)}",
                        style = AppleType.body.copy(fontWeight = FontWeight.SemiBold),
                        color = apple.tint,
                        modifier = Modifier.clip(RoundedCornerShape(10.dp)).background(apple.fill)
                            .clickable { openLink(context, article.link) }.padding(horizontal = 16.dp, vertical = 11.dp),
                    )
                }
                Spacer(Modifier.height(40.dp))
            }
            BottomToolbar {
                BarIcon(AppIcons.EnvelopeBadge, "Mark as unread", onClick = onMarkUnread)
                BarIcon(
                    if (article.starred) AppIcons.BookmarkFill else AppIcons.Bookmark,
                    if (article.starred) "Remove from Read Later" else "Read later",
                    onClick = onToggleStar,
                )
                BarIcon(AppIcons.Grid, "Save to board", tint = if (article.boards.isNotEmpty()) null else apple.secondaryLabel) { showBoards = true }
                BarIcon(AppIcons.Share, "Share", enabled = article.link.isNotEmpty()) { share(context, article) }
                BarIcon(AppIcons.Safari, "Open in browser", enabled = article.link.isNotEmpty()) { openLink(context, article.link) }
            }
        }
        AppleSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp))
    }

    if (showBoards) {
        BoardSheet(
            boards = boards,
            selected = article.boards,
            onToggle = onToggleBoard,
            onNewBoard = { showBoards = false; newBoard = true },
            onDismiss = { showBoards = false },
        )
    }
    if (newBoard) {
        TextEntrySheet(
            title = "New Board",
            placeholder = "Board name",
            confirm = "Create",
            onDismiss = { newBoard = false },
            onSubmit = { name ->
                if (onCreateBoard(name)) { onToggleBoard(name.trim()); null } else "That name is already used"
            },
        )
    }
    noteFor?.let { text ->
        TextEntrySheet(
            title = "Add Note",
            placeholder = "Your note",
            confirm = "Save",
            footer = "“${text.take(120)}${if (text.length > 120) "…" else ""}”",
            onDismiss = { noteFor = null },
            onSubmit = { note -> onHighlight(text, note); null },
        )
    }
}

/** Renders feed HTML natively in a TextView, with links and inline images. */
@Composable
private fun HtmlText(html: String, baseUrl: String, textSize: Int, highlights: List<String>, onSelection: (String, Boolean) -> Unit) {
    val apple = LocalApple.current
    val latestSelection by rememberUpdatedState(onSelection)
    val highlightColor = apple.yellow.copy(alpha = 0.45f).toArgb()
    val textColor = apple.label.toArgb()
    val linkColor = apple.tint.toArgb()
    val scope = rememberCoroutineScope()
    val cleaned = remember(html, baseUrl) { cleanHtml(html, baseUrl) }
    AndroidView(
        modifier = Modifier.fillMaxWidth(),
        factory = { ctx ->
            TextView(ctx).apply {
                movementMethod = LinkMovementMethod.getInstance()
                typeface = ResourcesCompat.getFont(ctx, R.font.inter)
                letterSpacing = -0.012f
                setLineSpacing(0f, 1.42f)
                // Selectable so the reader can highlight; keep links tappable.
                setTextIsSelectable(true)
                movementMethod = LinkMovementMethod.getInstance()
                val tv = this
                customSelectionActionModeCallback = object : ActionMode.Callback {
                    override fun onCreateActionMode(mode: ActionMode, menu: Menu): Boolean {
                        menu.add(Menu.NONE, MENU_HIGHLIGHT, 0, "Highlight")
                        menu.add(Menu.NONE, MENU_NOTE, 1, "Add note")
                        return true
                    }
                    override fun onPrepareActionMode(mode: ActionMode, menu: Menu) = false
                    override fun onActionItemClicked(mode: ActionMode, item: MenuItem): Boolean {
                        if (item.itemId != MENU_HIGHLIGHT && item.itemId != MENU_NOTE) return false
                        val start = minOf(tv.selectionStart, tv.selectionEnd)
                        val end = maxOf(tv.selectionStart, tv.selectionEnd)
                        if (start >= 0 && end > start) latestSelection(tv.text.subSequence(start, end).toString(), item.itemId == MENU_NOTE)
                        mode.finish()
                        return true
                    }
                    override fun onDestroyActionMode(mode: ActionMode) {}
                }
            }
        },
        update = { tv ->
            tv.textSize = textSize.toFloat()
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            if (tv.tag != cleaned) {
                tv.tag = cleaned
                tv.text = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY, ImageGetter(tv, baseUrl, scope), null).trimEnd()
            }
            applyHighlights(tv, highlights, highlightColor)
        },
    )
}

private const val MENU_HIGHLIGHT = 9001
private const val MENU_NOTE = 9002

private class HighlightSpan(color: Int) : BackgroundColorSpan(color)

/** Paint every saved highlight (all of its occurrences) in the text, replacing earlier paint. */
private fun applyHighlights(tv: TextView, highlights: List<String>, color: Int) {
    val text = tv.text as? Spannable ?: return
    text.getSpans(0, text.length, HighlightSpan::class.java).forEach { text.removeSpan(it) }
    val plain = text.toString()
    highlights.forEach { h ->
        var i = plain.indexOf(h)
        while (h.isNotEmpty() && i >= 0) {
            text.setSpan(HighlightSpan(color), i, i + h.length, Spanned.SPAN_EXCLUSIVE_EXCLUSIVE)
            i = plain.indexOf(h, i + h.length)
        }
    }
}

private val DROP_BLOCKS = Regex("(?is)<(script|style|iframe|noscript|svg|form)\\b.*?</\\1>")
private val COMMENTS = Regex("(?s)<!--.*?-->")
private val HREF = Regex("(?i)(href\\s*=\\s*)([\"'])(.*?)\\2")

/** Drop things TextView can't show, and make links absolute so tapping them works. */
private fun cleanHtml(html: String, baseUrl: String): String =
    HREF.replace(COMMENTS.replace(DROP_BLOCKS.replace(html, ""), "")) { m ->
        val abs = resolveUrl(baseUrl, m.groupValues[3].replace("&amp;", "&"))
        if (abs != null) m.groupValues[1] + m.groupValues[2] + abs.replace("&", "&amp;") + m.groupValues[2] else ""
    }

private fun CharSequence.trimEnd(): CharSequence {
    var end = length
    while (end > 0 && this[end - 1].isWhitespace()) end--
    return subSequence(0, end)
}

/** Loads <img> sources with Coil and swaps them into the text once downloaded, scaled to the text width. */
private class ImageGetter(
    private val tv: TextView,
    private val baseUrl: String,
    private val scope: CoroutineScope,
) : Html.ImageGetter {
    override fun getDrawable(source: String?): Drawable {
        val holder = HolderDrawable()
        val url = resolveUrl(baseUrl, source) ?: return holder
        scope.launch {
            val result = tv.context.imageLoader.execute(ImageRequest.Builder(tv.context).data(url).allowHardware(false).build())
            val image = (result as? SuccessResult)?.drawable ?: return@launch
            val w = image.intrinsicWidth
            val h = image.intrinsicHeight
            if (w < 4 || h < 4) return@launch // tracking pixels
            val maxWidth = (tv.width - tv.paddingLeft - tv.paddingRight).takeIf { it > 0 } ?: w
            val scale = minOf(1f, maxWidth.toFloat() / w).let { if (w < maxWidth / 2) it else maxWidth.toFloat() / w }
            val dw = (w * scale).toInt()
            val dh = (h * scale).toInt()
            image.setBounds(0, 0, dw, dh)
            holder.inner = image
            holder.setBounds(0, 0, dw, dh)
            tv.text = tv.text // re-layout with the real image size
        }
        return holder
    }
}

private class HolderDrawable : Drawable() {
    var inner: Drawable? = null
    override fun draw(canvas: Canvas) { inner?.draw(canvas) }
    override fun setAlpha(alpha: Int) {}
    override fun setColorFilter(colorFilter: ColorFilter?) {}
    @Deprecated("Deprecated in Java")
    override fun getOpacity(): Int = PixelFormat.TRANSLUCENT
}

private fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
    } catch (e: ActivityNotFoundException) {
        // No app to open web links; nothing more we can do.
    }
}

private fun share(context: Context, article: Article) {
    val send = Intent(Intent.ACTION_SEND).apply {
        type = "text/plain"
        putExtra(Intent.EXTRA_SUBJECT, article.title)
        putExtra(Intent.EXTRA_TEXT, "${article.title}\n${article.link}")
    }
    context.startActivity(Intent.createChooser(send, "Share article"))
}
