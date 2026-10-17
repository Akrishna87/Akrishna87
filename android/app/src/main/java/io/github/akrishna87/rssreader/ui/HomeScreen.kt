package io.github.akrishna87.rssreader.ui

import android.text.format.DateUtils
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberDrawerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import coil.compose.AsyncImage
import io.github.akrishna87.rssreader.ReaderView
import io.github.akrishna87.rssreader.ReaderViewModel
import io.github.akrishna87.rssreader.UiState
import io.github.akrishna87.rssreader.data.Article
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.data.Filters
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

val StarColor = Color(0xFFE0A100)

private val SUGGESTED = listOf(
    "Hacker News" to "https://hnrss.org/frontpage",
    "BBC News" to "https://feeds.bbci.co.uk/news/rss.xml",
    "The Verge" to "https://www.theverge.com/rss/index.xml",
    "Azure Blog" to "https://azure.microsoft.com/en-us/blog/feed/",
    "GitHub Blog" to "https://github.blog/feed/",
    "The Hindu" to "https://www.thehindu.com/news/national/feeder/default.rss",
)

@Composable
fun ReaderApp(vm: ReaderViewModel) {
    val state by vm.state.collectAsStateWithLifecycle()
    val snackbar = remember { SnackbarHostState() }
    LaunchedEffect(Unit) { vm.messages.collect { snackbar.showSnackbar(it) } }

    val open = state.openArticleId?.let { id -> state.articles.firstOrNull { it.id == id } }
    when {
        open != null -> {
            BackHandler { vm.closeArticle() }
            ArticleScreen(
                article = open,
                feed = state.feeds.firstOrNull { it.id == open.feedId },
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
                onOpen = { id -> vm.setView(ReaderView.OneFeed(id)); vm.closeManage() },
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
        else -> HomeScreen(state, vm, snackbar)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun HomeScreen(state: UiState, vm: ReaderViewModel, snackbar: SnackbarHostState) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val drawer = rememberDrawerState(DrawerValue.Closed)
    var showAdd by rememberSaveable { mutableStateOf(false) }
    var menuOpen by remember { mutableStateOf(false) }
    var confirmRemove by remember { mutableStateOf<Feed?>(null) }
    val shared by vm.shared.collectAsStateWithLifecycle()
    LaunchedEffect(shared) { if (shared != null) showAdd = true }

    val filters = remember(state.mute, state.highlight) { Filters(state.mute, state.highlight) }
    val feedsById = remember(state.feeds) { state.feeds.associateBy { it.id } }
    val unreadByFeed = remember(state.articles, filters) {
        state.articles.filter { !it.read && filters.mutedBy(it) == null }.groupingBy { it.feedId }.eachCount()
    }
    // Articles in the current view, before muting; then split into shown / muted.
    val inView = remember(state.articles, state.view, state.unreadOnly, state.feeds, filters) {
        val folderFeeds = (state.view as? ReaderView.Folder)?.let { v -> state.feeds.filter { it.folder == v.name }.map { it.id }.toHashSet() }
        state.articles.filter { a ->
            when (val v = state.view) {
                ReaderView.All -> true
                ReaderView.Starred -> a.starred
                ReaderView.Highlights -> filters.highlightedBy(a) != null
                is ReaderView.OneFeed -> a.feedId == v.feedId
                is ReaderView.Folder -> folderFeeds?.contains(a.feedId) == true
            } && (!state.unreadOnly || state.view == ReaderView.Starred || !a.read)
        }
    }
    val mutedCount = remember(inView, filters) { inView.count { filters.mutedBy(it) != null } }
    val visible = remember(inView, filters, state.showMuted) {
        if (state.showMuted) inView else inView.filter { filters.mutedBy(it) == null }
    }

    val currentFeed = (state.view as? ReaderView.OneFeed)?.let { feedsById[it.feedId] }
    val title = when (val v = state.view) {
        ReaderView.All -> "All articles"
        ReaderView.Starred -> "Starred"
        ReaderView.Highlights -> "Highlights"
        is ReaderView.OneFeed -> currentFeed?.name ?: "Feed"
        is ReaderView.Folder -> v.name
    }

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

    BackHandler(enabled = drawer.isOpen) { scope.launch { drawer.close() } }
    BackHandler(enabled = drawer.isClosed && state.view != ReaderView.All) { vm.setView(ReaderView.All) }

    fun closeDrawerThen(action: () -> Unit) {
        action()
        scope.launch { drawer.close() }
    }

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(
                    state = state,
                    filters = filters,
                    unreadByFeed = unreadByFeed,
                    onSelect = { view -> closeDrawerThen { vm.setView(view) } },
                    onToggleFolder = vm::toggleFolder,
                    onDiscover = { closeDrawerThen { vm.openDiscover() } },
                    onManage = { closeDrawerThen { vm.openManage() } },
                    onFilters = { closeDrawerThen { vm.openFilters() } },
                )
            }
        },
    ) {
        Scaffold(
            topBar = {
                TopAppBar(
                    title = { Text(title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    navigationIcon = {
                        IconButton(onClick = { scope.launch { drawer.open() } }) { Icon(Icons.Filled.Menu, "Show feeds") }
                    },
                    actions = {
                        IconButton(onClick = { vm.refreshAll() }, enabled = !state.refreshing) { Icon(Icons.Filled.Refresh, "Refresh") }
                        Box {
                            IconButton(onClick = { menuOpen = true }) { Icon(Icons.Filled.MoreVert, "More") }
                            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                                DropdownMenuItem(
                                    text = { Text("Unread only") },
                                    trailingIcon = { Checkbox(checked = state.unreadOnly, onCheckedChange = null) },
                                    onClick = { vm.setUnreadOnly(!state.unreadOnly); menuOpen = false },
                                )
                                DropdownMenuItem(
                                    text = { Text("Mark all as read") },
                                    onClick = { vm.markAllRead(visible.map { it.id }); menuOpen = false },
                                )
                                HorizontalDivider()
                                DropdownMenuItem(text = { Text("Manage feeds") }, onClick = { menuOpen = false; vm.openManage() })
                                DropdownMenuItem(text = { Text("Keyword filters") }, onClick = { menuOpen = false; vm.openFilters() })
                                DropdownMenuItem(text = { Text("Import OPML") }, onClick = {
                                    menuOpen = false
                                    importLauncher.launch(arrayOf("*/*"))
                                })
                                DropdownMenuItem(text = { Text("Export OPML") }, onClick = {
                                    menuOpen = false
                                    exportLauncher.launch("subscriptions.opml")
                                })
                                if (currentFeed != null) {
                                    HorizontalDivider()
                                    DropdownMenuItem(text = { Text("Unsubscribe") }, onClick = {
                                        menuOpen = false
                                        confirmRemove = currentFeed
                                    })
                                }
                            }
                        }
                    },
                )
            },
            floatingActionButton = {
                FloatingActionButton(onClick = { showAdd = true }) { Icon(Icons.Filled.Add, "Add feed") }
            },
            snackbarHost = { SnackbarHost(snackbar) },
        ) { padding ->
            PullToRefreshBox(
                isRefreshing = state.refreshing,
                onRefresh = { vm.refreshAll() },
                modifier = Modifier.padding(padding).fillMaxSize(),
            ) {
                LazyColumn(Modifier.fillMaxSize()) {
                    if (currentFeed?.error != null) {
                        item(key = "error") {
                            Row(
                                Modifier.fillMaxWidth().background(MaterialTheme.colorScheme.errorContainer).padding(12.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Icon(Icons.Filled.Warning, null, tint = MaterialTheme.colorScheme.onErrorContainer)
                                Spacer(Modifier.width(8.dp))
                                Text("Last update failed: ${currentFeed.error}", color = MaterialTheme.colorScheme.onErrorContainer, style = MaterialTheme.typography.bodySmall)
                            }
                        }
                    }
                    if (mutedCount > 0) {
                        item(key = "muted") {
                            Row(
                                Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                                verticalAlignment = Alignment.CenterVertically,
                            ) {
                                Text(
                                    if (state.showMuted) "Showing $mutedCount muted" else "$mutedCount muted article${if (mutedCount == 1) "" else "s"} hidden",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    modifier = Modifier.weight(1f),
                                )
                                TextButton(onClick = { vm.setShowMuted(!state.showMuted) }) { Text(if (state.showMuted) "Hide" else "Show") }
                            }
                            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                        }
                    }
                    if (visible.isEmpty() && state.loaded) {
                        item(key = "empty") {
                            EmptyState(
                                state = state,
                                onSuggest = { url -> scope.launch { vm.addFeed(url)?.let { snackbar.showSnackbar(it) } } },
                                onAdd = { showAdd = true },
                                onImport = { importLauncher.launch(arrayOf("*/*")) },
                                onDiscover = vm::openDiscover,
                            )
                        }
                    }
                    items(visible, key = { it.id }) { a ->
                        ArticleRow(
                            a = a,
                            feedTitle = feedsById[a.feedId]?.name.orEmpty(),
                            showFeed = state.view !is ReaderView.OneFeed,
                            highlight = filters.highlightedBy(a),
                            muted = filters.mutedBy(a),
                        ) { vm.openArticle(a.id) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddFeedDialog(
            initial = shared ?: "",
            initialFolder = (state.view as? ReaderView.Folder)?.name ?: "",
            folders = state.feeds.map { it.folder }.filter { it.isNotEmpty() }.distinct().sorted(),
            onDismiss = { showAdd = false; vm.clearShared() },
            onAdd = { url, folder -> vm.addFeed(url, folder = folder) },
            onAdded = { showAdd = false; vm.clearShared() },
        )
    }
    confirmRemove?.let { feed ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Unsubscribe?") },
            text = { Text("Remove “${feed.name}” and its saved articles, including starred ones?") },
            confirmButton = { TextButton(onClick = { vm.removeFeed(feed.id); confirmRemove = null }) { Text("Unsubscribe") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DrawerContent(
    state: UiState,
    filters: Filters,
    unreadByFeed: Map<String, Int>,
    onSelect: (ReaderView) -> Unit,
    onToggleFolder: (String) -> Unit,
    onDiscover: () -> Unit,
    onManage: () -> Unit,
    onFilters: () -> Unit,
) {
    val starred = remember(state.articles) { state.articles.count { it.starred } }
    val highlighted = remember(state.articles, filters) {
        state.articles.count { !it.read && filters.highlightedBy(it) != null && filters.mutedBy(it) == null }
    }
    val sorted = remember(state.feeds) { state.feeds.sortedBy { it.name.lowercase() } }
    val folders = remember(state.feeds) { state.feeds.map { it.folder }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() } }

    @Composable
    fun FeedItem(f: Feed, indent: Boolean) {
        NavigationDrawerItem(
            label = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
            icon = { FeedBadge(f) },
            badge = {
                if (f.error != null) Icon(Icons.Filled.Warning, "Update failed", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                else Count(unreadByFeed[f.id] ?: 0)
            },
            selected = state.view == ReaderView.OneFeed(f.id),
            onClick = { onSelect(ReaderView.OneFeed(f.id)) },
            modifier = if (indent) Modifier.padding(start = 20.dp) else Modifier,
        )
    }

    Column(Modifier.verticalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 16.dp)) {
        Text("Reader", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold, modifier = Modifier.padding(start = 16.dp, bottom = 12.dp))
        NavigationDrawerItem(
            label = { Text("All articles") },
            icon = { Icon(Icons.AutoMirrored.Filled.List, null) },
            badge = { Count(unreadByFeed.values.sum()) },
            selected = state.view == ReaderView.All,
            onClick = { onSelect(ReaderView.All) },
        )
        NavigationDrawerItem(
            label = { Text("Starred") },
            icon = { Icon(Icons.Filled.Star, null) },
            badge = { Count(starred) },
            selected = state.view == ReaderView.Starred,
            onClick = { onSelect(ReaderView.Starred) },
        )
        if (state.highlight.isNotEmpty()) {
            NavigationDrawerItem(
                label = { Text("Highlights") },
                icon = { Icon(AppIcons.Label, null) },
                badge = { Count(highlighted) },
                selected = state.view == ReaderView.Highlights,
                onClick = { onSelect(ReaderView.Highlights) },
            )
        }
        NavigationDrawerItem(label = { Text("Discover feeds") }, icon = { Icon(AppIcons.Explore, null) }, selected = false, onClick = onDiscover)
        if (state.feeds.isNotEmpty()) {
            NavigationDrawerItem(label = { Text("Manage feeds") }, icon = { Icon(Icons.Filled.Settings, null) }, selected = false, onClick = onManage)
        }
        NavigationDrawerItem(label = { Text("Keyword filters") }, icon = { Icon(AppIcons.Filter, null) }, selected = false, onClick = onFilters)

        if (state.feeds.isNotEmpty()) {
            Text(
                "FEEDS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        folders.forEach { folder ->
            val members = sorted.filter { it.folder == folder }
            val collapsed = folder in state.collapsed
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { onToggleFolder(folder) }) {
                    Icon(
                        if (collapsed) Icons.Filled.KeyboardArrowRight else Icons.Filled.KeyboardArrowDown,
                        if (collapsed) "Expand $folder" else "Collapse $folder",
                    )
                }
                NavigationDrawerItem(
                    label = { Text(folder, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                    badge = { Count(members.sumOf { unreadByFeed[it.id] ?: 0 }) },
                    selected = state.view == ReaderView.Folder(folder),
                    onClick = { onSelect(ReaderView.Folder(folder)) },
                    modifier = Modifier.weight(1f),
                )
            }
            if (!collapsed) members.forEach { FeedItem(it, indent = true) }
        }
        sorted.filter { it.folder.isEmpty() }.forEach { FeedItem(it, indent = false) }
    }
}

@Composable
private fun Count(n: Int) {
    if (n > 0) Text(n.toString(), style = MaterialTheme.typography.labelMedium)
}

@Composable
fun FeedBadge(feed: Feed) {
    val hues = listOf(0xFFD4521F, 0xFF2E7D32, 0xFF1565C0, 0xFF6A1B9A, 0xFF00838F, 0xFFAD1457, 0xFF5D4037, 0xFF283593)
    val color = Color(hues[(feed.id.hashCode() and 0x7fffffff) % hues.size])
    Box(Modifier.size(24.dp).clip(RoundedCornerShape(6.dp)).background(color), contentAlignment = Alignment.Center) {
        Text(feed.name.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun Tag(text: String, color: Color) {
    Surface(color = color.copy(alpha = 0.14f), shape = RoundedCornerShape(50)) {
        Text(text, color = color, style = MaterialTheme.typography.labelSmall, fontWeight = FontWeight.Bold, maxLines = 1, modifier = Modifier.padding(horizontal = 6.dp, vertical = 1.dp))
    }
}

@Composable
private fun ArticleRow(a: Article, feedTitle: String, showFeed: Boolean, highlight: String?, muted: String?, onClick: () -> Unit) {
    val mutedColor = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().height(IntrinsicSize.Min).clickable(onClick = onClick).alpha(if (muted != null) 0.55f else 1f),
        verticalAlignment = Alignment.Top,
    ) {
        // Gold edge marks highlighted articles.
        Box(Modifier.width(3.dp).fillMaxHeight().background(if (highlight != null && muted == null) StarColor else Color.Transparent))
        Row(Modifier.weight(1f).padding(start = 9.dp, end = 16.dp, top = 12.dp, bottom = 12.dp), verticalAlignment = Alignment.Top) {
            Box(
                Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape)
                    .background(if (a.read) Color.Transparent else MaterialTheme.colorScheme.primary),
            )
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    val meta = listOfNotNull(feedTitle.takeIf { showFeed && it.isNotEmpty() }, relativeTime(a.date)).joinToString(" · ")
                    Text(meta, style = MaterialTheme.typography.labelMedium, color = mutedColor, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                    if (a.starred) {
                        Spacer(Modifier.width(4.dp))
                        Icon(Icons.Filled.Star, "Starred", tint = StarColor, modifier = Modifier.size(14.dp))
                    }
                    when {
                        muted != null -> { Spacer(Modifier.width(6.dp)); Tag("muted: $muted", mutedColor) }
                        highlight != null -> { Spacer(Modifier.width(6.dp)); Tag("★ $highlight", StarColor) }
                    }
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    a.title,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = if (a.read) FontWeight.Normal else FontWeight.SemiBold,
                    color = if (a.read) mutedColor else MaterialTheme.colorScheme.onSurface,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (a.snippet.isNotEmpty()) {
                    Spacer(Modifier.height(2.dp))
                    Text(a.snippet, style = MaterialTheme.typography.bodySmall, color = mutedColor, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
            }
            if (a.thumbnail != null) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = a.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(72.dp).clip(RoundedCornerShape(10.dp)).background(MaterialTheme.colorScheme.surfaceVariant),
                )
            }
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(state: UiState, onSuggest: (String) -> Unit, onAdd: () -> Unit, onImport: () -> Unit, onDiscover: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val (heading, body) = when {
            state.feeds.isEmpty() -> "Welcome 👋" to "Add a feed to get started. Paste any RSS/Atom link or website address, or tap one of these:"
            state.view == ReaderView.Starred -> "No starred articles" to "Tap ★ on any article to save it here."
            state.view == ReaderView.Highlights -> "No highlights yet" to "Articles mentioning ${state.highlight.joinToString(", ")} will show up here."
            state.unreadOnly -> "All caught up ✨" to "No unread articles here."
            else -> "No articles yet" to "Pull down to refresh."
        }
        Text(heading, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Spacer(Modifier.height(8.dp))
        Text(body, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, textAlign = TextAlign.Center)
        if (state.feeds.isEmpty()) {
            Spacer(Modifier.height(16.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                SUGGESTED.forEach { (name, url) -> OutlinedButton(onClick = { onSuggest(url) }) { Text("+ $name") } }
            }
            Spacer(Modifier.height(20.dp))
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onDiscover) { Text("Discover feeds") }
                OutlinedButton(onClick = onAdd) { Text("Add a feed") }
                OutlinedButton(onClick = onImport) { Text("Import OPML") }
            }
        }
    }
}

fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
