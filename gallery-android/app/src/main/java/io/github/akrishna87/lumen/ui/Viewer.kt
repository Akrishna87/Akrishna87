package io.github.akrishna87.lumen.ui

import android.view.LayoutInflater
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.exifinterface.media.ExifInterface
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import coil.compose.AsyncImage
import coil.request.ImageRequest
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.LibraryState
import io.github.akrishna87.lumen.MediaItem
import io.github.akrishna87.lumen.R
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.Locale
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * The full-screen viewer, like Photos: swipe between items, tap to hide the bars (the background
 * goes black), pinch or double-tap to zoom, swipe down to close. Videos play in place.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun Viewer(
    target: ViewerTarget,
    state: LibraryState,
    favorites: Set<Long>,
    actions: GalleryActions,
    onClose: () -> Unit,
) {
    val apple = LocalApple.current
    val context = LocalContext.current
    val density = LocalDensity.current
    val scope = rememberCoroutineScope()

    // The list as it was when the viewer opened (so un-hearting in Favorites doesn't yank the photo
    // away), minus anything deleted (or, in Recently Deleted, recovered) since.
    val snapshot = remember(target) { target.album.items(state, favorites) }
    val present = remember(state, target) {
        (if (target.album.isTrash) state.trash else state.items).mapTo(HashSet()) { it.id }
    }
    // Deleted items leave like in Photos: the photo shrinks and fades, then the next one slides in.
    var hidden by remember { mutableStateOf(emptySet<Long>()) }
    var vanish by remember { mutableFloatStateOf(1f) }
    var arrive by remember { mutableFloatStateOf(1f) }
    val items = remember(snapshot, present, hidden) { snapshot.filter { it.id in present && it.id !in hidden } }
    if (items.isEmpty()) {
        LaunchedEffect(Unit) { onClose() }
        return
    }

    val pager = rememberPagerState(initialPage = snapshot.indexOfFirst { it.id == target.startId }.coerceAtLeast(0)) { items.size }
    val current = items[pager.currentPage.coerceIn(0, items.lastIndex)]

    var chrome by remember { mutableStateOf(true) }
    var zoomed by remember { mutableStateOf(false) }
    var dismissY by remember { mutableFloatStateOf(0f) }
    var showInfo by remember { mutableStateOf(false) }

    BackHandler { onClose() }

    // One player for the whole viewer; it follows whichever video is on screen.
    val player = remember { ExoPlayer.Builder(context).build() }
    var playing by remember { mutableStateOf(false) }
    var firstFrame by remember { mutableStateOf(false) }
    var muted by remember { mutableStateOf(false) }
    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) { playing = isPlaying }
            override fun onRenderedFirstFrame() { firstFrame = true }
        }
        player.addListener(listener)
        onDispose {
            player.removeListener(listener)
            player.release()
        }
    }
    LaunchedEffect(current.id) {
        firstFrame = false
        if (current.isVideo) {
            player.setMediaItem(androidx.media3.common.MediaItem.fromUri(current.uri))
            player.prepare()
            player.playWhenReady = true
        } else {
            player.stop()
            player.clearMediaItems()
        }
        zoomed = false
    }
    LaunchedEffect(muted) { player.volume = if (muted) 0f else 1f }
    LifecycleEventEffect(Lifecycle.Event.ON_PAUSE) { player.pause() }

    LaunchedEffect(actions.removed) {
        val gone = actions.removed - hidden
        if (gone.isEmpty()) return@LaunchedEffect
        val showingGone = current.id in gone
        if (showingGone) {
            player.pause()
            animate(1f, 0f, animationSpec = tween(220)) { v, _ -> vanish = v }
        }
        hidden = hidden + gone
        vanish = 1f
        if (showingGone) {
            arrive = 0f
            animate(0f, 1f, animationSpec = tween(280)) { v, _ -> arrive = v }
        }
    }

    val dismissProgress = min(1f, abs(dismissY) / with(density) { 400.dp.toPx() })
    val black = !chrome || apple.dark
    val background by animateColorAsState(if (black) Color.Black else apple.background, label = "viewerBg")
    val closeDistance = with(density) { 110.dp.toPx() }

    Box(Modifier.fillMaxSize().background(background.copy(alpha = 1f - dismissProgress))) {
        HorizontalPager(
            state = pager,
            modifier = Modifier.fillMaxSize(),
            pageSpacing = 14.dp,
            beyondViewportPageCount = 1,
            userScrollEnabled = !zoomed && dismissY == 0f,
            key = { items[it].id },
        ) { page ->
            val item = items[page]
            val active = page == pager.currentPage
            ZoomablePage(
                item = item,
                active = active,
                zoomEnabled = !item.isVideo,
                dismissY = if (active) dismissY else 0f,
                onTap = { chrome = !chrome },
                onZoomedChange = { if (active) zoomed = it },
                onDismissDrag = { dismissY = it },
                onDismissEnd = { distance ->
                    if (distance > closeDistance) onClose()
                    else scope.launch { animate(dismissY, 0f) { v, _ -> dismissY = v } }
                },
            ) {
                Box(
                    Modifier.fillMaxSize().graphicsLayer {
                        if (active) {
                            alpha = vanish * arrive
                            scaleX = 0.8f + 0.2f * vanish
                            scaleY = 0.8f + 0.2f * vanish
                            translationX = (1f - arrive) * size.width * 0.3f
                        }
                    },
                    contentAlignment = Alignment.Center,
                ) {
                    if (item.isVideo) {
                        VideoPage(item, player, active, firstFrame)
                    } else {
                        PhotoPage(item)
                    }
                }
            }
        }

        // Video play button in the middle while paused (as Photos shows), only when the bars are up.
        if (current.isVideo && !playing && chrome && dismissY == 0f) {
            Box(
                Modifier.align(Alignment.Center).size(72.dp).clip(CircleShape).background(Color.Black.copy(alpha = 0.4f))
                    .tap { player.play() }.semantics { contentDescription = "Play" },
                contentAlignment = Alignment.Center,
            ) {
                Icon(AppIcons.Play, null, tint = Color.White, modifier = Modifier.padding(start = 4.dp).size(36.dp))
            }
        }

        AnimatedVisibility(chrome && dismissY == 0f, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.TopCenter)) {
            NavBar(
                title = Format.dayTitle(current.date),
                subtitle = Format.time(current.dateTime),
                onBack = onClose,
                trailing = {
                    if (current.isVideo) BarIcon(if (muted) AppIcons.Muted else AppIcons.Speaker, if (muted) "Unmute" else "Mute") { muted = !muted }
                },
            )
        }

        AnimatedVisibility(chrome && dismissY == 0f, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.align(Alignment.BottomCenter)) {
            Column(Modifier.fillMaxWidth().background(apple.bar)) {
                if (current.isVideo) VideoControls(player, playing, current)
                Filmstrip(items, pager.currentPage) { scope.launch { pager.scrollToPage(it) } }
                Hairline()
                Row(
                    Modifier.fillMaxWidth().navigationBarsPadding().height(49.dp).padding(horizontal = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    if (target.album.isTrash) {
                        BarText("Delete", color = apple.red) { actions.deleteForever(listOf(current)) }
                        Text(daysLeft(current), style = AppleType.footnote, color = apple.secondaryLabel)
                        BarText("Recover") { actions.recover(listOf(current)) }
                    } else {
                        val favorite = current.id in favorites
                        BarIcon(AppIcons.Share, "Share") { actions.share(listOf(current)) }
                        BarIcon(if (favorite) AppIcons.HeartFill else AppIcons.Heart, if (favorite) "Unfavorite" else "Favorite") { actions.toggleFavorite(current) }
                        BarIcon(AppIcons.Info, "Info") { showInfo = true }
                        BarIcon(AppIcons.Trash, "Delete") { actions.delete(listOf(current)) }
                    }
                }
            }
        }

        if (showInfo) {
            ModalBottomSheet(
                onDismissRequest = { showInfo = false },
                sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
                containerColor = apple.groupedBackground,
                shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
                dragHandle = { Box(Modifier.padding(top = 6.dp, bottom = 4.dp).size(36.dp, 5.dp).clip(CircleShape).background(apple.tertiaryLabel)) },
            ) {
                InfoPanel(current, onDone = { showInfo = false })
            }
        }
    }
}

/**
 * A page that can be pinched or double-tapped to zoom (and panned while zoomed), or dragged down
 * to close the viewer. Sideways drags are left to the pager.
 */
@Composable
private fun ZoomablePage(
    item: MediaItem,
    active: Boolean,
    zoomEnabled: Boolean,
    dismissY: Float,
    onTap: () -> Unit,
    onZoomedChange: (Boolean) -> Unit,
    onDismissDrag: (Float) -> Unit,
    onDismissEnd: (Float) -> Unit,
    content: @Composable () -> Unit,
) {
    var scale by remember { mutableFloatStateOf(1f) }
    var offset by remember { mutableStateOf(Offset.Zero) }
    var box by remember { mutableStateOf(IntSize.Zero) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(active) {
        if (!active) { scale = 1f; offset = Offset.Zero }
    }
    LaunchedEffect(scale > 1.01f) { onZoomedChange(scale > 1.01f) }

    /** The photo's size on screen at 1×, fitted into the page. */
    fun fitted(): Size {
        if (item.width <= 0 || item.height <= 0 || box.width == 0) return Size(box.width.toFloat(), box.height.toFloat())
        val ratio = min(box.width.toFloat() / item.width, box.height.toFloat() / item.height)
        return Size(item.width * ratio, item.height * ratio)
    }

    fun clamp(o: Offset, s: Float): Offset {
        val f = fitted()
        val maxX = max(0f, (f.width * s - box.width) / 2)
        val maxY = max(0f, (f.height * s - box.height) / 2)
        return Offset(o.x.coerceIn(-maxX, maxX), o.y.coerceIn(-maxY, maxY))
    }

    fun animateTo(targetScale: Float, targetOffset: Offset) {
        val fromScale = scale
        val fromOffset = offset
        scope.launch {
            animate(0f, 1f) { t, _ ->
                scale = fromScale + (targetScale - fromScale) * t
                offset = fromOffset + (targetOffset - fromOffset) * t
            }
        }
    }

    Box(
        Modifier.fillMaxSize()
            .onSizeChanged { box = it }
            .pointerInput(item.id, zoomEnabled) {
                detectTapGestures(
                    onTap = { onTap() },
                    onDoubleTap = { tap ->
                        if (!zoomEnabled) return@detectTapGestures
                        if (scale > 1.01f) {
                            animateTo(1f, Offset.Zero)
                        } else {
                            val s = 2.5f
                            val center = Offset(box.width / 2f, box.height / 2f)
                            animateTo(s, clamp((center - tap) * (s - 1f), s))
                        }
                    },
                )
            }
            .pointerInput(item.id, zoomEnabled) {
                val slop = viewConfiguration.touchSlop
                awaitEachGesture {
                    awaitFirstDown(requireUnconsumed = false)
                    var mode = Mode.UNDECIDED
                    var travel = Offset.Zero
                    var drag = 0f
                    do {
                        val event = awaitPointerEvent()
                        val fingers = event.changes.count { it.pressed }
                        if (mode == Mode.UNDECIDED) {
                            travel += event.calculatePan()
                            mode = when {
                                fingers >= 2 && zoomEnabled -> Mode.TRANSFORM
                                travel.getDistance() < slop -> Mode.UNDECIDED
                                scale > 1.01f -> Mode.TRANSFORM
                                travel.y > 0 && abs(travel.y) > abs(travel.x) * 1.4f -> Mode.DISMISS
                                else -> Mode.PAGER
                            }
                        }
                        when (mode) {
                            Mode.TRANSFORM -> {
                                val zoom = if (fingers >= 2) event.calculateZoom() else 1f
                                val pan = event.calculatePan()
                                val centroid = event.calculateCentroid(useCurrent = true).takeIf { it != Offset.Unspecified } ?: Offset.Zero
                                val center = Offset(box.width / 2f, box.height / 2f)
                                val newScale = (scale * zoom).coerceIn(0.8f, 6f)
                                val anchored = centroid + pan - center - (centroid - center - offset) * (newScale / scale)
                                scale = newScale
                                offset = if (newScale >= 1f) clamp(anchored, newScale) else anchored
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            Mode.DISMISS -> {
                                drag = max(0f, drag + event.calculatePan().y)
                                onDismissDrag(drag)
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                            else -> Unit
                        }
                    } while (event.changes.any { it.pressed })
                    when (mode) {
                        Mode.TRANSFORM -> if (scale < 1f) animateTo(1f, Offset.Zero)
                        Mode.DISMISS -> onDismissEnd(drag)
                        else -> Unit
                    }
                }
            }
            .graphicsLayer {
                val shrink = 1f - min(0.3f, dismissY / 3000f)
                scaleX = scale * shrink
                scaleY = scale * shrink
                translationX = offset.x
                translationY = offset.y + dismissY
            },
        contentAlignment = Alignment.Center,
    ) {
        content()
    }
}

private enum class Mode { UNDECIDED, TRANSFORM, DISMISS, PAGER }

@Composable
private fun PhotoPage(item: MediaItem) {
    val context = LocalContext.current
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        // The small thumbnail shows at once; the full photo replaces it a moment later.
        ThumbImage(item, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
        AsyncImage(
            model = remember(item.id) { ImageRequest.Builder(context).data(item.uri).size(2048).build() },
            contentDescription = item.description,
            contentScale = ContentScale.Fit,
            modifier = Modifier.fillMaxSize(),
        )
    }
}

@Composable
private fun VideoPage(item: MediaItem, player: ExoPlayer, active: Boolean, firstFrame: Boolean) {
    Box(Modifier.fillMaxSize().semantics { contentDescription = item.description }, contentAlignment = Alignment.Center) {
        if (active) {
            AndroidView(
                factory = { ctx -> (LayoutInflater.from(ctx).inflate(R.layout.player_view, null) as PlayerView).also { it.player = player } },
                onRelease = { it.player = null },
                modifier = Modifier.fillMaxSize(),
            )
        }
        if (!active || !firstFrame) ThumbImage(item, Modifier.fillMaxSize(), contentScale = ContentScale.Fit)
    }
}

/** Play/pause, a thin scrubber and the time, above the toolbar while a video is showing. */
@Composable
private fun VideoControls(player: ExoPlayer, playing: Boolean, item: MediaItem) {
    val apple = LocalApple.current
    var position by remember { mutableLongStateOf(0L) }
    var duration by remember(item.id) { mutableLongStateOf(item.durationMs) }
    var scrubbing by remember { mutableStateOf<Float?>(null) }
    LaunchedEffect(item.id) {
        while (true) {
            if (scrubbing == null) position = player.currentPosition
            player.duration.takeIf { it > 0 }?.let { duration = it }
            delay(200)
        }
    }
    val fraction = scrubbing ?: if (duration > 0) (position.toFloat() / duration).coerceIn(0f, 1f) else 0f
    Row(Modifier.fillMaxWidth().padding(start = 4.dp, end = 16.dp, top = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        BarIcon(if (playing) AppIcons.Pause else AppIcons.Play, if (playing) "Pause" else "Play", tint = apple.label, size = 22.dp) {
            if (playing) player.pause() else {
                if (player.playbackState == Player.STATE_ENDED) player.seekTo(0)
                player.play()
            }
        }
        Text(Format.duration((fraction * duration).toLong()), style = AppleType.caption1.copy(fontWeight = FontWeight.Medium), color = apple.secondaryLabel, modifier = Modifier.width(44.dp), textAlign = TextAlign.End)
        Spacer(Modifier.width(10.dp))
        BoxWithConstraints(
            Modifier.weight(1f).height(28.dp)
                .pointerInput(item.id) {
                    detectTapGestures { p -> player.seekTo((p.x / size.width * duration).toLong()) }
                }
                .pointerInput(item.id) {
                    detectHorizontalDragGestures(
                        onDragStart = { p -> scrubbing = (p.x / size.width).coerceIn(0f, 1f) },
                        onDragEnd = {
                            scrubbing?.let { player.seekTo((it * duration).toLong()) }
                            scrubbing = null
                        },
                        onDragCancel = { scrubbing = null },
                    ) { change, _ ->
                        scrubbing = (change.position.x / size.width).coerceIn(0f, 1f)
                        change.consume()
                    }
                }
                .semantics { contentDescription = "Playback position" },
            contentAlignment = Alignment.CenterStart,
        ) {
            val thick = if (scrubbing != null) 7.dp else 4.dp
            Box(Modifier.fillMaxWidth().height(thick).clip(CircleShape).background(apple.fill))
            Box(Modifier.width(maxWidth * fraction).height(thick).clip(CircleShape).background(apple.label.copy(alpha = 0.75f)))
        }
        Spacer(Modifier.width(10.dp))
        Text("-" + Format.duration(((1 - fraction) * duration).toLong()), style = AppleType.caption1.copy(fontWeight = FontWeight.Medium), color = apple.secondaryLabel, modifier = Modifier.width(48.dp))
    }
}

/** The strip of small thumbnails above the toolbar; the current one is wider. */
@Composable
private fun Filmstrip(items: List<MediaItem>, current: Int, onPick: (Int) -> Unit) {
    val apple = LocalApple.current
    val list = rememberLazyListState(initialFirstVisibleItemIndex = current)
    LaunchedEffect(current) { list.animateScrollToItem(current) }
    BoxWithConstraints(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
        val side = maxWidth / 2 - 22.dp
        LazyRow(
            state = list,
            contentPadding = PaddingValues(start = side, end = side),
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(items, key = { _, it -> it.id }) { i, item ->
                val selected = i == current
                Box(
                    Modifier.padding(horizontal = if (selected) 4.dp else 0.dp).size(if (selected) 36.dp else 26.dp, 40.dp)
                        .clip(RoundedCornerShape(2.dp)).background(apple.placeholder).tap { onPick(i) },
                ) {
                    ThumbImage(item, Modifier.fillMaxSize())
                }
            }
        }
    }
}

/** What Photos shows under the (i) button: date, camera, file details and exposure. */
@Composable
private fun InfoPanel(item: MediaItem, onDone: () -> Unit) {
    val apple = LocalApple.current
    val context = LocalContext.current
    val exif by produceState<CameraInfo?>(null, item.id) {
        value = withContext(Dispatchers.IO) { CameraInfo.read(context, item) }
    }
    Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 28.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(Format.infoDate(item.dateTime), style = AppleType.headline, color = apple.label, modifier = Modifier.weight(1f))
            BarText("Done", bold = true, onClick = onDone)
        }
        Spacer(Modifier.height(12.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(apple.cell)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(exif?.camera ?: if (item.isVideo) "Video" else "No Camera Information", style = AppleType.headline, color = apple.label, modifier = Modifier.weight(1f))
                Text(
                    item.format,
                    style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold),
                    color = apple.secondaryLabel,
                    modifier = Modifier.clip(RoundedCornerShape(5.dp)).background(apple.fill).padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Hairline()
            Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
                exif?.lens?.let { Text(it, style = AppleType.subheadline, color = apple.label) }
                Text(item.fileName, style = AppleType.subheadline, color = apple.secondaryLabel)
                val details = listOfNotNull(
                    if (item.isVideo) null else Format.megapixels(item.width, item.height),
                    if (item.width > 0) "${item.width} × ${item.height}" else null,
                    if (item.isVideo) Format.duration(item.durationMs) else null,
                    Format.size(item.sizeBytes),
                )
                Text(details.joinToString(" • "), style = AppleType.subheadline, color = apple.secondaryLabel)
            }
            val exposure = exif?.exposure.orEmpty()
            if (exposure.isNotEmpty()) {
                Hairline()
                Row(Modifier.fillMaxWidth().height(36.dp), verticalAlignment = Alignment.CenterVertically) {
                    exposure.forEachIndexed { i, value ->
                        if (i > 0) Box(Modifier.width(0.5.dp).height(18.dp).background(apple.separator))
                        Text(value, style = AppleType.footnote, color = apple.secondaryLabel, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                    }
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Column(Modifier.fillMaxWidth().clip(RoundedCornerShape(12.dp)).background(apple.cell)) {
            AppleRow("Album", value = item.albumName, chevron = false, showDivider = false)
        }
    }
}

/** Camera details from a photo's EXIF data, formatted the way Photos shows them. */
private data class CameraInfo(val camera: String?, val lens: String?, val exposure: List<String>) {
    companion object {
        fun read(context: android.content.Context, item: MediaItem): CameraInfo? {
            if (item.isVideo) return null
            return try {
                context.contentResolver.openInputStream(item.uri)?.use { stream ->
                    val exif = ExifInterface(stream)
                    val make = exif.getAttribute(ExifInterface.TAG_MAKE)?.trim().orEmpty()
                    val model = exif.getAttribute(ExifInterface.TAG_MODEL)?.trim().orEmpty()
                    val camera = when {
                        model.isEmpty() -> make.ifEmpty { null }
                        make.isEmpty() || model.startsWith(make, ignoreCase = true) -> model
                        else -> "$make $model"
                    }
                    val focal = exif.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0).takeIf { it > 0 }
                        ?: exif.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0).takeIf { it > 0 }?.roundToInt()
                    val aperture = exif.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0).takeIf { it > 0 }
                    val iso = exif.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0).takeIf { it > 0 }
                    val bias = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, Double.NaN).takeIf { !it.isNaN() }
                    val time = exif.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0).takeIf { it > 0 }
                    val lens = exif.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim()?.ifEmpty { null }
                        ?: listOfNotNull(focal?.let { "$it mm" }, aperture?.let { String.format(Locale.US, "ƒ%.1f", it) }).joinToString(" ").ifEmpty { null }
                    val exposure = listOfNotNull(
                        iso?.let { "ISO $it" },
                        focal?.let { "$it mm" },
                        bias?.let { String.format(Locale.US, if (it == 0.0) "0 ev" else "%+.1f ev", it) },
                        aperture?.let { String.format(Locale.US, "ƒ%.1f", it) },
                        time?.let { if (it >= 1) String.format(Locale.US, "%.0f s", it) else "1/${(1 / it).roundToInt()} s" },
                    )
                    if (camera == null && lens == null && exposure.isEmpty()) null else CameraInfo(camera, lens, exposure)
                }
            } catch (e: Exception) {
                null
            }
        }
    }
}
