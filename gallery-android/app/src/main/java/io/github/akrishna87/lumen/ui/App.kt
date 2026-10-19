package io.github.akrishna87.lumen.ui

import android.app.Activity
import android.content.ClipData
import android.content.Intent
import android.content.IntentSender
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.akrishna87.lumen.Access
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.GalleryViewModel
import io.github.akrishna87.lumen.LibraryState
import io.github.akrishna87.lumen.MediaItem

/** An album-like list of items: a folder, Favorites, a media type, or Recently Deleted. */
data class AlbumRef(val key: String, val title: String) {
    fun items(state: LibraryState, favorites: Set<Long>): List<MediaItem> = when {
        key == RECENTS -> state.items
        key == FAVORITES -> state.items.filter { it.id in favorites }
        key == VIDEOS -> state.videos
        key == SCREENSHOTS -> state.screenshots
        key == PANORAMAS -> state.panoramas
        key == ANIMATED -> state.animated
        key == TRASH -> state.trash
        key.startsWith(FOLDER) -> state.albums.firstOrNull { it.folder == key.removePrefix(FOLDER) }?.items.orEmpty()
        key.startsWith(MONTH) -> key.removePrefix(MONTH).split('-').let { (y, m) ->
            state.items.filter { it.date.year == y.toInt() && it.date.monthValue == m.toInt() }
        }
        key.startsWith(SEARCH) -> searchWords(key.removePrefix(SEARCH)).let { words -> state.items.filter { it.matches(words) } }
        else -> emptyList()
    }

    val isTrash: Boolean get() = key == TRASH

    companion object {
        const val RECENTS = "recents"
        const val FAVORITES = "favorites"
        const val VIDEOS = "videos"
        const val SCREENSHOTS = "screenshots"
        const val PANORAMAS = "panoramas"
        const val ANIMATED = "animated"
        const val TRASH = "trash"
        const val FOLDER = "folder:"
        const val MONTH = "month:"
        const val SEARCH = "search:"

        fun folder(folder: String, name: String) = AlbumRef(FOLDER + folder, name)
        fun month(year: Int, month: Int, title: String) = AlbumRef("$MONTH$year-$month", title)
        fun search(query: String) = AlbumRef(SEARCH + query, "Search")

        fun searchWords(query: String): List<String> = query.trim().split(Regex("\\s+")).filter { it.isNotEmpty() }
    }
}

sealed interface Route {
    data class AlbumDetail(val album: AlbumRef, val backLabel: String) : Route
    data object AllAlbums : Route
}

/** What the full-screen viewer is showing. */
data class ViewerTarget(val album: AlbumRef, val startId: Long)

/** Things any screen can do with photos: share, favourite, delete. */
@Stable
class GalleryActions(
    private val vm: GalleryViewModel,
    private val activity: Activity?,
    private val launcher: ActivityResultLauncher<IntentSenderRequest>,
) {
    /** Before Android 11 there's no system confirmation, so the app asks itself, like Photos does. */
    var pendingDelete by mutableStateOf<List<MediaItem>?>(null)

    fun share(items: List<MediaItem>) {
        val activity = activity ?: return
        if (items.isEmpty()) return
        val type = when {
            items.all { it.isVideo } -> "video/*"
            items.none { it.isVideo } -> "image/*"
            else -> "*/*"
        }
        val uris = ArrayList<Uri>(items.map { it.uri })
        val send = if (uris.size == 1) {
            Intent(Intent.ACTION_SEND).putExtra(Intent.EXTRA_STREAM, uris[0])
        } else {
            Intent(Intent.ACTION_SEND_MULTIPLE).putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
        }
        send.type = type
        send.clipData = ClipData.newUri(activity.contentResolver, "Photos", uris[0]).apply { uris.drop(1).forEach { addItem(ClipData.Item(it)) } }
        send.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        activity.startActivity(Intent.createChooser(send, null))
    }

    fun toggleFavorite(item: MediaItem) = vm.toggleFavorite(item)

    fun setFavorite(items: List<MediaItem>, favorite: Boolean) = vm.setFavorite(items, favorite)

    fun delete(items: List<MediaItem>) {
        if (items.isEmpty()) return
        if (Build.VERSION.SDK_INT >= 30) launch(vm.deleteRequest(items)) else pendingDelete = items
    }

    fun confirmDelete(items: List<MediaItem>) = launch(runCatching { vm.deleteRequest(items) }.getOrNull())

    fun recover(items: List<MediaItem>) = launch(vm.recoverRequest(items))

    fun deleteForever(items: List<MediaItem>) = launch(vm.deleteForeverRequest(items))

    private fun launch(sender: IntentSender?) {
        if (sender != null) launcher.launch(IntentSenderRequest.Builder(sender).build())
    }
}

@Composable
fun GalleryApp(vm: GalleryViewModel = viewModel()) {
    val apple = LocalApple.current
    val access by vm.access.collectAsStateWithLifecycle()
    val state by vm.state.collectAsStateWithLifecycle()
    val favorites by vm.favorites.collectAsStateWithLifecycle()

    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) { vm.refresh() }

    val permissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { vm.refresh() }
    val requestAccess = { permissionLauncher.launch(GalleryViewModel.permissions) }

    if (access == Access.NONE) {
        WelcomeScreen(onContinue = requestAccess)
        return
    }

    val activity = LocalContext.current as? Activity
    val changeLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) { vm.refresh() }
    val actions = remember(vm, activity, changeLauncher) { GalleryActions(vm, activity, changeLauncher) }

    var tab by rememberSaveable { mutableIntStateOf(0) }
    val stacks = remember { List(3) { mutableStateListOf<Route>() } }
    var viewer by remember { mutableStateOf<ViewerTarget?>(null) }
    val selection = remember { Selection() }

    // The Library's own state lives here so it's kept while browsing albums or the viewer.
    val library = remember { LibraryUi() }
    val libraryGrid = rememberLazyGridState()

    val open: (AlbumRef, MediaItem) -> Unit = { album, item -> viewer = ViewerTarget(album, item.id) }
    val push: (Route) -> Unit = { route ->
        selection.end()
        stacks[tab].add(route)
    }

    BackHandler(enabled = selection.active && viewer == null) { selection.end() }
    BackHandler(enabled = stacks[tab].isNotEmpty() && viewer == null && !selection.active) { stacks[tab].removeAt(stacks[tab].lastIndex) }
    BackHandler(enabled = tab != 0 && stacks[tab].isEmpty() && viewer == null && !selection.active) { tab = 0 }

    Box(Modifier.fillMaxSize().background(apple.background)) {
        Column(Modifier.fillMaxSize()) {
            Box(Modifier.weight(1f).fillMaxWidth()) {
                val route = stacks[tab].lastOrNull()
                val depth = stacks[tab].size
                AnimatedContent(
                    targetState = Triple(tab, depth, route),
                    transitionSpec = {
                        when {
                            targetState.first != initialState.first -> fadeIn(tween(120)) togetherWith fadeOut(tween(120))
                            targetState.second > initialState.second ->
                                slideInHorizontally(tween(320)) { it } togetherWith slideOutHorizontally(tween(320)) { -it / 3 }
                            else -> slideInHorizontally(tween(320)) { -it / 3 } togetherWith slideOutHorizontally(tween(320)) { it }
                        }
                    },
                    label = "nav",
                ) { (t, _, r) ->
                    when (r) {
                        is Route.AlbumDetail -> AlbumScreen(
                            album = r.album,
                            backLabel = r.backLabel,
                            state = state,
                            favorites = favorites,
                            selection = selection,
                            actions = actions,
                            onBack = { stacks[t].removeAt(stacks[t].lastIndex) },
                            onOpen = open,
                        )
                        Route.AllAlbums -> AllAlbumsScreen(
                            state = state,
                            onBack = { stacks[t].removeAt(stacks[t].lastIndex) },
                            onAlbum = { push(Route.AlbumDetail(it, "My Albums")) },
                        )
                        null -> when (t) {
                            0 -> LibraryScreen(
                                state = state,
                                favorites = favorites,
                                ui = library,
                                gridState = libraryGrid,
                                selection = selection,
                                onOpen = { open(AlbumRef(AlbumRef.RECENTS, "Library"), it) },
                            )
                            1 -> AlbumsScreen(
                                state = state,
                                favorites = favorites,
                                access = access,
                                onManageAccess = requestAccess,
                                onAlbum = { push(Route.AlbumDetail(it, "Albums")) },
                                onSeeAll = { push(Route.AllAlbums) },
                            )
                            else -> SearchScreen(
                                state = state,
                                favorites = favorites,
                                onAlbum = { push(Route.AlbumDetail(it, "Search")) },
                                onOpen = open,
                            )
                        }
                    }
                }
            }
            if (selection.active) {
                SelectionToolbar(state, favorites, selection, actions, trash = (stacks[tab].lastOrNull() as? Route.AlbumDetail)?.album?.isTrash == true)
            } else {
                TabBar(
                    tabs = listOf(TabItem("Library", AppIcons.Library), TabItem("Albums", AppIcons.Albums), TabItem("Search", AppIcons.Search)),
                    selected = tab,
                    onSelect = { i ->
                        // Tapping the current tab again goes back to its first screen, as on iOS.
                        if (i == tab) stacks[i].clear()
                        tab = i
                    },
                )
            }
        }

        AnimatedVisibility(viewer != null, enter = fadeIn(tween(180)), exit = fadeOut(tween(180))) {
            val target = viewer
            if (target != null) {
                Viewer(
                    target = target,
                    state = state,
                    favorites = favorites,
                    actions = actions,
                    onClose = { viewer = null },
                )
            }
        }

        actions.pendingDelete?.let { items ->
            ActionSheet(
                message = if (items.size == 1) "This ${if (items[0].isVideo) "video" else "photo"} will be deleted from your phone."
                else "These ${items.size} items will be deleted from your phone.",
                actions = listOf(
                    SheetAction(
                        if (items.size == 1) "Delete ${if (items[0].isVideo) "Video" else "Photo"}" else "Delete ${items.size} Items",
                        destructive = true,
                    ) {
                        actions.confirmDelete(items)
                        selection.end()
                    },
                ),
                onDismiss = { actions.pendingDelete = null },
            )
        }
    }
}

/** Replaces the tab bar while selecting: Share · "3 Photos Selected" · Favorite · Delete. */
@Composable
private fun SelectionToolbar(state: LibraryState, favorites: Set<Long>, selection: Selection, actions: GalleryActions, trash: Boolean) {
    val apple = LocalApple.current
    val picked = selection.items(if (trash) state.trash else state.items)
    Column(Modifier.fillMaxWidth().background(apple.bar)) {
        Hairline()
        Row(Modifier.fillMaxWidth().navigationBarsPadding().height(49.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            if (trash) {
                BarText("Delete", color = apple.red, enabled = picked.isNotEmpty()) { actions.deleteForever(picked); selection.end() }
                Text(Format.selected(picked), style = AppleType.headline.copy(fontSize = AppleType.subheadline.fontSize), color = apple.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                BarText("Recover", enabled = picked.isNotEmpty()) { actions.recover(picked); selection.end() }
            } else {
                BarIcon(AppIcons.Share, "Share", enabled = picked.isNotEmpty()) { actions.share(picked) }
                Text(Format.selected(picked), style = AppleType.headline.copy(fontSize = AppleType.subheadline.fontSize), color = apple.label, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
                val allFavorite = picked.isNotEmpty() && picked.all { it.id in favorites }
                BarIcon(if (allFavorite) AppIcons.HeartFill else AppIcons.Heart, if (allFavorite) "Unfavorite" else "Favorite", enabled = picked.isNotEmpty()) {
                    actions.setFavorite(picked, !allFavorite)
                    selection.end()
                }
                BarIcon(AppIcons.Trash, "Delete", enabled = picked.isNotEmpty()) {
                    actions.delete(picked)
                    if (Build.VERSION.SDK_INT >= 30) selection.end()
                }
            }
        }
    }
}

/** The first-run screen, in the style of Apple's "What's New" sheets, asking for access to photos. */
@Composable
private fun WelcomeScreen(onContinue: () -> Unit) {
    val apple = LocalApple.current
    val context = LocalContext.current
    var asked by rememberSaveable { mutableStateOf(false) }
    Column(
        Modifier.fillMaxSize().background(apple.background).statusBarsPadding().navigationBarsPadding().padding(horizontal = 32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Spacer(Modifier.weight(0.8f))
        Box(
            Modifier.size(84.dp).clip(RoundedCornerShape(19.dp))
                .background(Brush.verticalGradient(listOf(Color(0xFF3A3FD9), Color(0xFF8E5BE8), Color(0xFFFF8A6B)))),
            contentAlignment = Alignment.Center,
        ) {
            Icon(AppIcons.Library, null, tint = Color.White, modifier = Modifier.size(46.dp))
        }
        Spacer(Modifier.height(22.dp))
        Text("Welcome to Lumen", style = AppleType.largeTitle, color = apple.label, textAlign = TextAlign.Center)
        Spacer(Modifier.height(34.dp))
        Column(verticalArrangement = Arrangement.spacedBy(24.dp)) {
            Feature(AppIcons.Library, apple.tint, "Your Library", "Every photo and video on your phone, in one place. Nothing is uploaded or copied.")
            Feature(AppIcons.Albums, apple.orange, "Albums", "Camera, Screenshots, WhatsApp and every other folder, plus Favorites and Videos.")
            Feature(AppIcons.Search, apple.purple, "Search", "Find photos by month, year, album or file name.")
        }
        Spacer(Modifier.weight(1f))
        Text(
            if (asked) "Lumen needs access to your photos and videos to show them. You can allow it in Settings."
            else "Lumen will ask to see the photos and videos on this phone.",
            style = AppleType.footnote,
            color = apple.secondaryLabel,
            textAlign = TextAlign.Center,
            modifier = Modifier.padding(bottom = 16.dp),
        )
        if (asked) {
            FilledButton("Open Settings") {
                context.startActivity(Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null)))
            }
            Spacer(Modifier.height(4.dp))
            BarText("Try Again") { onContinue() }
        } else {
            FilledButton("Continue") {
                asked = true
                onContinue()
            }
        }
        Spacer(Modifier.height(20.dp))
    }
}

@Composable
private fun Feature(icon: ImageVector, tint: Color, title: String, text: String) {
    val apple = LocalApple.current
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, tint = tint, modifier = Modifier.size(36.dp))
        Spacer(Modifier.width(18.dp))
        Column {
            Text(title, style = AppleType.headline, color = apple.label)
            Text(text, style = AppleType.subheadline, color = apple.secondaryLabel)
        }
    }
}
