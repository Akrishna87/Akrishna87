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
import androidx.compose.runtime.remember
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
    onToggleStar: () -> Unit,
    onMarkUnread: () -> Unit,
) {
    val apple = LocalApple.current
    val context = LocalContext.current
    val scroll = rememberScrollState()
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
                    HtmlText(article.contentHtml, baseUrl = article.link.ifEmpty { feed?.url.orEmpty() })
                } else {
                    Text(
                        "This feed only includes a headline. Open the original to read the full article.",
                        style = AppleType.body,
                        color = apple.secondaryLabel,
                    )
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
                    if (article.starred) AppIcons.StarFill else AppIcons.Star,
                    if (article.starred) "Unstar" else "Star",
                    tint = if (article.starred) apple.yellow else null,
                    onClick = onToggleStar,
                )
                BarIcon(AppIcons.Share, "Share", enabled = article.link.isNotEmpty()) { share(context, article) }
                BarIcon(AppIcons.Safari, "Open in browser", enabled = article.link.isNotEmpty()) { openLink(context, article.link) }
            }
        }
        AppleSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = 56.dp))
    }
}

/** Renders feed HTML natively in a TextView, with links and inline images. */
@Composable
private fun HtmlText(html: String, baseUrl: String) {
    val apple = LocalApple.current
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
                textSize = 18f
                letterSpacing = -0.012f
                setLineSpacing(0f, 1.42f)
            }
        },
        update = { tv ->
            tv.setTextColor(textColor)
            tv.setLinkTextColor(linkColor)
            if (tv.tag != cleaned) {
                tv.tag = cleaned
                tv.text = Html.fromHtml(cleaned, Html.FROM_HTML_MODE_LEGACY, ImageGetter(tv, baseUrl, scope), null).trimEnd()
            }
        },
    )
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
