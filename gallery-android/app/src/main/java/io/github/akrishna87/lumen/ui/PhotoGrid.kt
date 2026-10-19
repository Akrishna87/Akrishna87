package io.github.akrishna87.lumen.ui

import android.graphics.Bitmap
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridScope
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.Stable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Shadow
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.selected
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.MediaItem
import io.github.akrishna87.lumen.Thumbnails

/** Which items are picked while "Select" is on. */
@Stable
class Selection {
    var active by mutableStateOf(false)
    val ids = mutableStateListOf<Long>()

    fun toggle(item: MediaItem) {
        if (!ids.remove(item.id)) ids += item.id
    }

    fun start() { active = true; ids.clear() }

    fun end() { active = false; ids.clear() }

    fun items(all: List<MediaItem>): List<MediaItem> = ids.toSet().let { picked -> all.filter { it.id in picked } }
}

/** The grid sizes Photos steps through when you pinch: 3 across by default. */
val GridSizes = listOf(3, 5, 7)

/**
 * The square photo grid with 1.5dp gaps. Tapping opens the viewer (or picks, while selecting);
 * long-pressing starts selecting; pinching steps through [GridSizes].
 */
@Composable
fun MediaGrid(
    items: List<MediaItem>,
    favorites: Set<Long>,
    state: LazyGridState,
    columns: Int,
    onColumnsChange: (Int) -> Unit,
    selection: Selection,
    onOpen: (Int) -> Unit,
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(0.dp),
    header: (LazyGridScope.() -> Unit)? = null,
    footer: (LazyGridScope.() -> Unit)? = null,
    badge: ((MediaItem) -> String)? = null,
) {
    LazyVerticalGrid(
        columns = GridCells.Fixed(columns),
        state = state,
        modifier = modifier.fillMaxSize().pinchToResize(columns, onColumnsChange),
        contentPadding = contentPadding,
        horizontalArrangement = Arrangement.spacedBy(1.5.dp),
        verticalArrangement = Arrangement.spacedBy(1.5.dp),
    ) {
        header?.invoke(this)
        itemsIndexed(items, key = { _, item -> item.id }, contentType = { _, _ -> "thumb" }) { index, item ->
            Thumb(
                item = item,
                favorite = item.id in favorites,
                selecting = selection.active,
                selected = selection.active && item.id in selection.ids,
                small = columns >= 5,
                badge = badge?.invoke(item),
                onClick = { if (selection.active) selection.toggle(item) else onOpen(index) },
                onLongClick = {
                    if (!selection.active) selection.start()
                    selection.toggle(item)
                },
            )
        }
        footer?.invoke(this)
    }
}

/** "1,234 Photos, 56 Videos" under the last row, as in Photos. */
fun LazyGridScope.countFooter(items: List<MediaItem>) {
    item(key = "__count", span = { GridItemSpan(maxLineSpan) }, contentType = "footer") {
        val apple = LocalApple.current
        Text(
            Format.counts(items.count { !it.isVideo }, items.count { it.isVideo }),
            style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold),
            color = apple.label,
            textAlign = TextAlign.Center,
            modifier = Modifier.fillMaxWidth().padding(top = 14.dp, bottom = 10.dp),
        )
    }
}

/** Two-finger pinch: spread to show fewer, bigger photos, pinch in for more. One finger still scrolls. */
private fun Modifier.pinchToResize(columns: Int, onChange: (Int) -> Unit): Modifier = pointerInput(columns) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var zoom = 1f
        var done = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) break
            if (event.changes.count { it.pressed } >= 2 && !done) {
                zoom *= event.calculateZoom()
                event.changes.forEach { it.consume() }
                val i = GridSizes.indexOf(columns).coerceAtLeast(0)
                when {
                    zoom > 1.3f && i > 0 -> { onChange(GridSizes[i - 1]); done = true }
                    zoom < 0.77f && i < GridSizes.lastIndex -> { onChange(GridSizes[i + 1]); done = true }
                }
            } else if (done) {
                event.changes.forEach { it.consume() }
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun Thumb(
    item: MediaItem,
    favorite: Boolean,
    selecting: Boolean,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    small: Boolean = false,
    badge: String? = null,
    onLongClick: (() -> Unit)? = null,
) {
    val apple = LocalApple.current
    Box(
        modifier.aspectRatio(1f).background(apple.placeholder)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick)
            .semantics { contentDescription = item.description; if (selecting) this.selected = selected },
    ) {
        ThumbImage(item, Modifier.fillMaxSize())
        if ((item.isVideo || favorite || badge != null) && !small) {
            // A soft scrim so the white duration and heart read over bright photos.
            Box(
                Modifier.align(Alignment.BottomCenter).fillMaxWidth().aspectRatio(2.6f)
                    .background(Brush.verticalGradient(listOf(Color.Transparent, Color.Black.copy(alpha = 0.32f)))),
            )
        }
        if (favorite && !small) {
            Icon(AppIcons.HeartFill, null, tint = Color.White, modifier = Modifier.align(Alignment.BottomStart).padding(5.dp).size(13.dp))
        }
        if (item.isVideo && !selecting && !small) {
            Text(
                Format.duration(item.durationMs),
                style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold, shadow = Shadow(Color.Black.copy(alpha = 0.4f), blurRadius = 4f)),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomEnd).padding(end = 5.dp, bottom = 3.dp),
            )
        }
        if (badge != null && !small) {
            Text(
                badge,
                style = AppleType.caption2.copy(fontWeight = FontWeight.SemiBold),
                color = Color.White,
                modifier = Modifier.align(Alignment.BottomStart).padding(start = 5.dp, bottom = 4.dp),
            )
        }
        if (selecting) {
            if (selected) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.18f)))
            SelectionBadge(selected, Modifier.align(Alignment.BottomEnd).padding(5.dp))
        }
    }
}

/** The blue check circle on a picked photo (an empty ring on the others). */
@Composable
fun SelectionBadge(selected: Boolean, modifier: Modifier = Modifier) {
    val apple = LocalApple.current
    Box(
        modifier.size(22.dp).clip(CircleShape)
            .background(if (selected) apple.tint else Color.Black.copy(alpha = 0.12f))
            .border(1.5.dp, Color.White, CircleShape),
        contentAlignment = Alignment.Center,
    ) {
        if (selected) Icon(AppIcons.Checkmark, null, tint = Color.White, modifier = Modifier.size(13.dp))
    }
}

/** The media library's thumbnail, filling (and by default cropping to) its box. */
@Composable
fun ThumbImage(item: MediaItem, modifier: Modifier = Modifier, contentScale: ContentScale = ContentScale.Crop) {
    val context = LocalContext.current
    val bitmap by produceState<Bitmap?>(Thumbnails.cached(item), item.id) {
        if (value == null) value = Thumbnails.load(context, item)
    }
    bitmap?.let {
        Image(it.asImageBitmap(), null, contentScale = contentScale, modifier = modifier)
    }
}
