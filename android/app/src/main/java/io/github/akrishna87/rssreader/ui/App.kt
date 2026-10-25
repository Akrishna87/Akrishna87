package io.github.akrishna87.rssreader.ui

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.core.content.ContextCompat
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.rssreader.ReaderView
import io.github.akrishna87.rssreader.ReaderViewModel
import io.github.akrishna87.rssreader.UiState
import io.github.akrishna87.rssreader.data.Feed
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.material3.SnackbarHostState

/** Screen routing: article list with a side menu → article, plus Discover / Manage / Filters / Settings. */
@Composable
fun ReaderApp(vm: ReaderViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    var showAdd by rememberSaveable { mutableStateOf(false) }
    val shared by vm.shared.collectAsStateWithLifecycle()
    LaunchedEffect(shared) { if (shared != null) showAdd = true }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) scope.launch {
            val text = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() } }.getOrNull()
            }
            if (text != null) vm.importOpml(text) else snackbar.showSnackbar("Couldn't read that file")
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/x-opml")) { uri ->
        if (uri != null) scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching { context.contentResolver.openOutputStream(uri)?.use { it.write(vm.exportOpml().toByteArray()) } }.isSuccess
            }
            snackbar.showSnackbar(if (ok) "Subscriptions exported" else "Couldn't save the file")
        }
    }

    val notificationPermission = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    // Android 13+ asks before an app may post notifications.
    fun askNotifications() {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) notificationPermission.launch(Manifest.permission.POST_NOTIFICATIONS)
    }

    val drawer = rememberDrawerState(DrawerValue.Closed)
    var newBoard by remember { mutableStateOf(false) }
    var deleteBoard by remember { mutableStateOf<String?>(null) }

    val open = state.openArticleId?.let { id -> state.articles.firstOrNull { it.id == id } }
    when {
        open != null -> {
            BackHandler { vm.closeArticle() }
            ArticleScreen(
                article = open,
                feed = state.feeds.firstOrNull { it.id == open.feedId },
                backLabel = viewTitle(state, short = true),
                snackbar = snackbar,
                onBack = vm::closeArticle,
                textSize = state.settings.textSize,
                fullLoading = open.id in state.fullLoading,
                onLoadFull = { vm.loadFullText(open.id) },
                boards = state.boards,
                onToggleStar = { vm.toggleStar(open.id) },
                onMarkUnread = { vm.setRead(open.id, false); vm.closeArticle() },
                onToggleBoard = { vm.toggleBoard(open.id, it) },
                onCreateBoard = vm::createBoard,
                onHighlight = { text, note -> vm.addTextHighlight(open.id, text, note) },
                onRemoveHighlight = { vm.removeTextHighlight(open.id, it) },
            )
        }
        state.settingsOpen -> {
            BackHandler { vm.closeSettings() }
            SettingsScreen(
                settings = state.settings,
                snackbar = snackbar,
                onBack = vm::closeSettings,
                onLayout = vm::setLayout,
                onTextSize = vm::setTextSize,
                onTheme = vm::setTheme,
                email = state.syncEmail,
                status = state.syncStatus,
                error = state.syncError,
                lastSync = state.lastSync,
                onRefreshHours = { hours ->
                    vm.setRefreshHours(hours)
                    if (hours > 0 && state.settings.notify != "off") askNotifications()
                },
                onNotify = { mode ->
                    vm.setNotify(mode)
                    if (mode != "off") askNotifications()
                },
                onOffline = vm::setOffline,
                onSignIn = {
                    scope.launch {
                        try {
                            googleIdToken(context)?.let(vm::signInWithGoogle)
                        } catch (e: Exception) {
                            vm.signInFailed(e.message ?: "Google sign-in failed")
                        }
                    }
                },
                onSyncNow = vm::syncNow,
                onSignOut = vm::signOut,
            )
        }
        state.filtersOpen -> {
            BackHandler { vm.closeFilters() }
            FiltersScreen(
                mute = state.mute,
                highlight = state.highlight,
                snackbar = snackbar,
                onBack = vm::closeFilters,
                onAddMute = vm::addMute,
                onRemoveMute = vm::removeMute,
                onAddHighlight = vm::addHighlight,
                onRemoveHighlight = vm::removeHighlight,
            )
        }
        state.manageOpen -> {
            BackHandler { vm.closeManage() }
            ManageFeedsScreen(
                feeds = state.feeds,
                articles = state.articles,
                snackbar = snackbar,
                onBack = vm::closeManage,
                onRemove = vm::removeFeed,
                onEdit = vm::editFeed,
                onOpen = { id -> vm.closeManage(); vm.setView(ReaderView.OneFeed(id)) },
            )
        }
        state.discoverOpen -> {
            BackHandler { vm.closeDiscover() }
            DiscoverScreen(
                feeds = state.feeds,
                snackbar = snackbar,
                onBack = vm::closeDiscover,
                onAdd = { url, folder -> vm.addFeed(url, showFeed = false, folder = folder) },
            )
        }
        else -> {
            BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
            ModalNavigationDrawer(
                drawerState = drawer,
                drawerContent = {
                    AppDrawer(
                        state = state,
                        vm = vm,
                        onClose = { scope.launch { drawer.close() } },
                        onAdd = { showAdd = true },
                        onNewBoard = { newBoard = true },
                        onDeleteBoard = { deleteBoard = it },
                        onImport = { importLauncher.launch(arrayOf("*/*")) },
                        onExport = {
                            if (state.feeds.isEmpty()) scope.launch { snackbar.showSnackbar("No feeds to export yet") } else exportLauncher.launch("subscriptions.opml")
                        },
                    )
                },
            ) {
                ArticleListScreen(
                    state = state,
                    vm = vm,
                    title = viewTitle(state, short = false),
                    snackbar = snackbar,
                    onMenu = { scope.launch { drawer.open() } },
                    onAdd = { showAdd = true },
                )
            }
        }
    }

    if (newBoard) {
        TextEntrySheet(
            title = "New Board",
            placeholder = "Board name",
            confirm = "Create",
            onDismiss = { newBoard = false },
            onSubmit = { if (vm.createBoard(it)) null else "That name is already used" },
        )
    }
    deleteBoard?.let { name ->
        AppleAlert(
            title = "Delete board “$name”?",
            message = "The articles stay in your feeds, they're just removed from this board.",
            confirm = "Delete",
            destructive = true,
            onConfirm = { vm.deleteBoard(name); deleteBoard = null },
            onDismiss = { deleteBoard = null },
        )
    }

    if (showAdd) {
        AddFeedSheet(
            initial = shared ?: "",
            initialFolder = (state.view as? ReaderView.Folder)?.name ?: "",
            folders = state.feeds.map(Feed::folder).filter { it.isNotEmpty() }.distinct().sorted(),
            onDismiss = { showAdd = false; vm.clearShared() },
            onAdd = { url, folder -> vm.addFeed(url, folder = folder) },
            onAdded = { showAdd = false; vm.clearShared() },
        )
    }
}

fun viewTitle(state: UiState, short: Boolean): String = when (val v = state.view) {
    ReaderView.Today -> "Today"
    ReaderView.All -> if (short) "All" else "All Articles"
    ReaderView.Starred -> "Read Later"
    ReaderView.Priority -> "Priority"
    ReaderView.Notes -> "Highlights & Notes"
    ReaderView.Alerts -> "Keyword Alerts"
    is ReaderView.OneFeed -> state.feeds.firstOrNull { it.id == v.feedId }?.name ?: "Feed"
    is ReaderView.Folder -> v.name
    is ReaderView.Board -> v.name
}
