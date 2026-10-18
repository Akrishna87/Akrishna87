package io.github.akrishna87.rssreader.ui

import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
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

/** Screen routing: Feeds (home) → article list → article, plus Discover / Manage / Filters. */
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
                onToggleStar = { vm.toggleStar(open.id) },
                onMarkUnread = { vm.setRead(open.id, false); vm.closeArticle() },
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
        state.listOpen -> {
            BackHandler { vm.closeList() }
            ArticleListScreen(
                state = state,
                vm = vm,
                title = viewTitle(state, short = false),
                snackbar = snackbar,
                onBack = vm::closeList,
            )
        }
        else -> FeedsScreen(
            state = state,
            vm = vm,
            snackbar = snackbar,
            onAdd = { showAdd = true },
            onImport = { importLauncher.launch(arrayOf("*/*")) },
            onExport = { exportLauncher.launch("subscriptions.opml") },
        )
    }

    if (showAdd) {
        AddFeedSheet(
            initial = shared ?: "",
            initialFolder = (state.view as? ReaderView.Folder)?.takeIf { state.listOpen }?.name ?: "",
            folders = state.feeds.map(Feed::folder).filter { it.isNotEmpty() }.distinct().sorted(),
            onDismiss = { showAdd = false; vm.clearShared() },
            onAdd = { url, folder -> vm.addFeed(url, folder = folder) },
            onAdded = { showAdd = false; vm.clearShared() },
        )
    }
}

fun viewTitle(state: UiState, short: Boolean): String = when (val v = state.view) {
    ReaderView.All -> if (short) "All" else "All Articles"
    ReaderView.Starred -> "Starred"
    ReaderView.Highlights -> "Highlights"
    is ReaderView.OneFeed -> state.feeds.firstOrNull { it.id == v.feedId }?.name ?: "Feed"
    is ReaderView.Folder -> v.name
}
