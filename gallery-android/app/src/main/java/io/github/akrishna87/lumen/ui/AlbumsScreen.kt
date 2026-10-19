package io.github.akrishna87.lumen.ui

import android.os.Build
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.offset
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akrishna87.lumen.Access
import io.github.akrishna87.lumen.Album
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.LibraryState
import io.github.akrishna87.lumen.MediaItem

private data class Cover(val ref: AlbumRef, val cover: MediaItem?, val count: Int)

@Composable
fun AlbumsScreen(
    state: LibraryState,
    favorites: Set<Long>,
    access: Access,
    onManageAccess: () -> Unit,
    onAlbum: (AlbumRef) -> Unit,
    onSeeAll: () -> Unit,
) {
    val apple = LocalApple.current
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 60 } }
    val favoriteItems = remember(state, favorites) { state.items.filter { it.id in favorites } }
    val covers = remember(state, favoriteItems) {
        listOf(
            Cover(AlbumRef(AlbumRef.RECENTS, "Recents"), state.items.lastOrNull(), state.items.size),
            Cover(AlbumRef(AlbumRef.FAVORITES, "Favorites"), favoriteItems.lastOrNull(), favoriteItems.size),
        ) + state.albums.map { Cover(AlbumRef.folder(it.folder, it.name), it.cover, it.items.size) }
    }

    Column(Modifier.fillMaxSize().background(apple.background)) {
        NavBar("Albums", showTitle = scrolled, showBackground = scrolled)
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "title") { LargeTitle("Albums") }
            if (access == Access.LIMITED) {
                item(key = "limited") {
                    Column(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp).clip(RoundedCornerShape(12.dp))
                            .background(apple.fill).padding(14.dp),
                    ) {
                        Text("Limited Access", style = AppleType.headline, color = apple.label)
                        Text("Lumen can only see the photos and videos you picked.", style = AppleType.subheadline, color = apple.secondaryLabel)
                        Spacer(Modifier.height(6.dp))
                        Text("Manage…", style = AppleType.subheadline, color = apple.tint, modifier = Modifier.tap(onClick = onManageAccess))
                    }
                }
            }
            item(key = "myAlbums") {
                SectionHeader("My Albums", action = "See All", onAction = onSeeAll)
                AlbumCarousel(covers, onAlbum)
            }
            item(key = "types") {
                Spacer(Modifier.height(18.dp))
                SectionHeader("Media Types")
                MediaTypeRow(AppIcons.Video, "Videos", state.videos.size) { onAlbum(AlbumRef(AlbumRef.VIDEOS, "Videos")) }
                MediaTypeRow(AppIcons.Screenshot, "Screenshots", state.screenshots.size) { onAlbum(AlbumRef(AlbumRef.SCREENSHOTS, "Screenshots")) }
                MediaTypeRow(AppIcons.Panorama, "Panoramas", state.panoramas.size) { onAlbum(AlbumRef(AlbumRef.PANORAMAS, "Panoramas")) }
                MediaTypeRow(AppIcons.Animated, "Animated", state.animated.size, last = true) { onAlbum(AlbumRef(AlbumRef.ANIMATED, "Animated")) }
            }
            if (Build.VERSION.SDK_INT >= 30) {
                item(key = "utilities") {
                    Spacer(Modifier.height(18.dp))
                    SectionHeader("Utilities")
                    MediaTypeRow(AppIcons.Trash, "Recently Deleted", state.trash.size, last = true) { onAlbum(AlbumRef(AlbumRef.TRASH, "Recently Deleted")) }
                }
            }
        }
    }
}

/** Photos' "My Albums": two rows of square covers that scroll sideways. */
@Composable
private fun AlbumCarousel(covers: List<Cover>, onAlbum: (AlbumRef) -> Unit) {
    BoxWithConstraints(Modifier.fillMaxWidth()) {
        val side = (maxWidth - 16.dp * 2 - 12.dp) / 2.15f
        val columns = covers.chunked(2)
        LazyRow(contentPadding = PaddingValues(horizontal = 16.dp), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            items(columns, key = { it.first().ref.key }) { pair ->
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    pair.forEach { AlbumCover(it, side) { onAlbum(it.ref) } }
                }
            }
        }
    }
}

@Composable
private fun AlbumCover(cover: Cover, side: Dp, onClick: () -> Unit) {
    val apple = LocalApple.current
    Column(Modifier.width(side).clickable(onClick = onClick).semantics(mergeDescendants = true) { contentDescription = "${cover.ref.title}, ${cover.count}" }) {
        Box(Modifier.size(side).clip(RoundedCornerShape(8.dp)).background(apple.placeholder), contentAlignment = Alignment.Center) {
            if (cover.cover != null) {
                ThumbImage(cover.cover, Modifier.fillMaxSize())
            } else {
                Icon(if (cover.ref.key == AlbumRef.FAVORITES) AppIcons.Heart else AppIcons.Photo, null, tint = apple.secondaryLabel, modifier = Modifier.size(side / 3))
            }
        }
        Spacer(Modifier.height(5.dp))
        Text(cover.ref.title, style = AppleType.subheadline, color = apple.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
        Text(String.format(java.util.Locale.getDefault(), "%,d", cover.count), style = AppleType.subheadline, color = apple.secondaryLabel)
    }
}

/** A row under "Media Types": blue symbol, name, count, chevron. */
@Composable
private fun MediaTypeRow(icon: ImageVector, title: String, count: Int, last: Boolean = false, onClick: () -> Unit) {
    AppleRow(
        title = title,
        icon = icon,
        value = String.format(java.util.Locale.getDefault(), "%,d", count),
        showDivider = !last,
        onClick = onClick,
    )
}

/** "See All": every folder album in a two-column grid. */
@Composable
fun AllAlbumsScreen(state: LibraryState, onBack: () -> Unit, onAlbum: (AlbumRef) -> Unit) {
    val apple = LocalApple.current
    val grid = rememberLazyGridState()
    val scrolled by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 60 } }
    Column(Modifier.fillMaxSize().background(apple.background)) {
        NavBar("My Albums", showTitle = scrolled, showBackground = scrolled, backLabel = "Albums", onBack = onBack)
        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            state = grid,
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, bottom = 24.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            // The grid is already inset by 16dp, which LargeTitle adds itself.
            item(span = { GridItemSpan(maxLineSpan) }) { LargeTitle("My Albums", Modifier.offset(x = (-16).dp)) }
            items(state.albums, key = { it.folder }) { album: Album ->
                BoxWithConstraints {
                    AlbumCover(Cover(AlbumRef.folder(album.folder, album.name), album.cover, album.items.size), maxWidth) {
                        onAlbum(AlbumRef.folder(album.folder, album.name))
                    }
                }
            }
        }
    }
}

/** One album's photos: large title, Select, the grid and its count. Recently Deleted adds days left. */
@Composable
fun AlbumScreen(
    album: AlbumRef,
    backLabel: String,
    state: LibraryState,
    favorites: Set<Long>,
    selection: Selection,
    actions: GalleryActions,
    onBack: () -> Unit,
    onOpen: (AlbumRef, MediaItem) -> Unit,
) {
    val apple = LocalApple.current
    val items = remember(state, favorites, album) { album.items(state, favorites) }
    val grid = rememberLazyGridState()
    val scrolled by remember { derivedStateOf { grid.firstVisibleItemIndex > 0 || grid.firstVisibleItemScrollOffset > 60 } }
    var columns by rememberSaveable { mutableIntStateOf(3) }

    // Albums open on their newest photos, at the bottom, like Photos. Recently Deleted lists newest first.
    LaunchedEffect(items.isNotEmpty()) { if (items.isNotEmpty() && !album.isTrash) grid.scrollToItem(items.size + 1) }

    Column(Modifier.fillMaxSize().background(apple.background)) {
        NavBar(album.title, showTitle = scrolled, showBackground = scrolled, backLabel = backLabel, onBack = { selection.end(); onBack() }) {
            if (items.isNotEmpty()) {
                CapsuleButton(if (selection.active) "Cancel" else "Select", overMedia = false, modifier = Modifier.padding(end = 4.dp)) {
                    if (selection.active) selection.end() else selection.start()
                }
            }
        }
        if (state.loaded && items.isEmpty()) {
            LargeTitle(album.title)
            when (album.key) {
                AlbumRef.FAVORITES -> EmptyState(AppIcons.Heart, "No Favorites", "Tap the heart on photos and videos you love to add them here.")
                AlbumRef.TRASH -> EmptyState(AppIcons.Trash, "No Recently Deleted Items", "Deleted photos and videos stay here for 30 days before they're removed for good.")
                else -> EmptyState(AppIcons.Photo, "No Photos or Videos", "Photos and videos in this album will appear here.")
            }
            return@Column
        }
        MediaGrid(
            items = items,
            favorites = favorites,
            state = grid,
            columns = columns,
            onColumnsChange = { columns = it },
            selection = selection,
            onOpen = { onOpen(album, items[it]) },
            contentPadding = PaddingValues(bottom = 16.dp),
            header = {
                item(key = "__title", span = { GridItemSpan(maxLineSpan) }) {
                    Column {
                        LargeTitle(album.title, subtitle = if (album.isTrash) null else Format.counts(items.count { !it.isVideo }, items.count { it.isVideo }))
                        if (album.isTrash) {
                            Text(
                                "Photos and videos show the days remaining before deletion. After that time, items will be permanently deleted.",
                                style = AppleType.footnote,
                                color = apple.secondaryLabel,
                                modifier = Modifier.padding(start = 16.dp, end = 16.dp, bottom = 10.dp),
                            )
                            if (!selection.active) {
                                Row(Modifier.padding(horizontal = 8.dp, vertical = 2.dp)) {
                                    BarText("Recover All") { actions.recover(items) }
                                    Spacer(Modifier.weight(1f))
                                    BarText("Delete All", color = apple.red) { actions.deleteForever(items) }
                                }
                            }
                        }
                    }
                }
            },
            footer = { if (!album.isTrash) countFooter(items) },
            badge = if (album.isTrash) ::daysLeft else null,
        )
    }
}

/** "29 days" for an item in Recently Deleted. */
fun daysLeft(item: MediaItem): String {
    val days = ((item.trashedUntil - System.currentTimeMillis() / 1000) / 86_400).coerceAtLeast(0)
    return if (days == 1L) "1 day" else "$days days"
}
