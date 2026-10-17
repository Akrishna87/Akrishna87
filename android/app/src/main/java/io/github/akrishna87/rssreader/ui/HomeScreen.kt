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
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
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
    if (open != null) {
        BackHandler { vm.closeArticle() }
        ArticleScreen(
            article = open,
            feed = state.feeds.firstOrNull { it.id == open.feedId },
            snackbar = snackbar,
            onBack = vm::closeArticle,
            onToggleStar = { vm.toggleStar(open.id) },
            onMarkUnread = { vm.setRead(open.id, false); vm.closeArticle() },
        )
    } else {
        HomeScreen(state, vm, snackbar)
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

    val feedsById = remember(state.feeds) { state.feeds.associateBy { it.id } }
    val unreadByFeed = remember(state.articles) { state.articles.filter { !it.read }.groupingBy { it.feedId }.eachCount() }
    val visible = remember(state.articles, state.view, state.unreadOnly, state.openArticleId) {
        state.articles.filter { a ->
            when (val v = state.view) {
                ReaderView.All -> true
                ReaderView.Starred -> a.starred
                is ReaderView.OneFeed -> a.feedId == v.feedId
            } && (!state.unreadOnly || state.view == ReaderView.Starred || !a.read)
        }
    }
    val currentFeed = (state.view as? ReaderView.OneFeed)?.let { feedsById[it.feedId] }
    val title = when (state.view) {
        ReaderView.All -> "All articles"
        ReaderView.Starred -> "Starred"
        is ReaderView.OneFeed -> currentFeed?.title ?: "Feed"
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

    ModalNavigationDrawer(
        drawerState = drawer,
        drawerContent = {
            ModalDrawerSheet {
                DrawerContent(state, unreadByFeed) { view ->
                    vm.setView(view)
                    scope.launch { drawer.close() }
                }
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
                    if (visible.isEmpty() && state.loaded) {
                        item(key = "empty") {
                            EmptyState(
                                state = state,
                                onSuggest = { url -> scope.launch { vm.addFeed(url)?.let { snackbar.showSnackbar(it) } } },
                                onAdd = { showAdd = true },
                                onImport = { importLauncher.launch(arrayOf("*/*")) },
                            )
                        }
                    }
                    items(visible, key = { it.id }) { a ->
                        ArticleRow(a, feedsById[a.feedId]?.title.orEmpty(), showFeed = state.view !is ReaderView.OneFeed) { vm.openArticle(a.id) }
                        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
                    }
                }
            }
        }
    }

    if (showAdd) {
        AddFeedDialog(
            initial = shared ?: "",
            onDismiss = { showAdd = false; vm.clearShared() },
            onAdd = { url -> vm.addFeed(url) },
            onAdded = { showAdd = false; vm.clearShared() },
        )
    }
    confirmRemove?.let { feed ->
        AlertDialog(
            onDismissRequest = { confirmRemove = null },
            title = { Text("Unsubscribe?") },
            text = { Text("Remove “${feed.title}” and its saved articles, including starred ones?") },
            confirmButton = { TextButton(onClick = { vm.removeFeed(feed.id); confirmRemove = null }) { Text("Unsubscribe") } },
            dismissButton = { TextButton(onClick = { confirmRemove = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun DrawerContent(state: UiState, unreadByFeed: Map<String, Int>, onSelect: (ReaderView) -> Unit) {
    val starred = remember(state.articles) { state.articles.count { it.starred } }
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
        if (state.feeds.isNotEmpty()) {
            Text(
                "FEEDS",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
            )
        }
        state.feeds.sortedBy { it.title.lowercase() }.forEach { f ->
            NavigationDrawerItem(
                label = { Text(f.title, maxLines = 1, overflow = TextOverflow.Ellipsis) },
                icon = { FeedBadge(f) },
                badge = {
                    if (f.error != null) Icon(Icons.Filled.Warning, "Update failed", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(16.dp))
                    else Count(unreadByFeed[f.id] ?: 0)
                },
                selected = state.view == ReaderView.OneFeed(f.id),
                onClick = { onSelect(ReaderView.OneFeed(f.id)) },
            )
        }
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
        Text(feed.title.trim().take(1).uppercase().ifEmpty { "?" }, color = Color.White, style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
    }
}

@Composable
private fun ArticleRow(a: Article, feedTitle: String, showFeed: Boolean, onClick: () -> Unit) {
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 12.dp, end = 16.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Box(
            Modifier.padding(top = 7.dp).size(8.dp).clip(CircleShape)
                .background(if (a.read) Color.Transparent else MaterialTheme.colorScheme.primary),
        )
        Spacer(Modifier.width(10.dp))
        Column(Modifier.weight(1f)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                val meta = listOfNotNull(feedTitle.takeIf { showFeed && it.isNotEmpty() }, relativeTime(a.date)).joinToString(" · ")
                Text(meta, style = MaterialTheme.typography.labelMedium, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f, fill = false))
                if (a.starred) {
                    Spacer(Modifier.width(4.dp))
                    Icon(Icons.Filled.Star, "Starred", tint = StarColor, modifier = Modifier.size(14.dp))
                }
            }
            Spacer(Modifier.height(2.dp))
            Text(
                a.title,
                style = MaterialTheme.typography.titleMedium,
                fontWeight = if (a.read) FontWeight.Normal else FontWeight.SemiBold,
                color = if (a.read) muted else MaterialTheme.colorScheme.onSurface,
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
            if (a.snippet.isNotEmpty()) {
                Spacer(Modifier.height(2.dp))
                Text(a.snippet, style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
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

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun EmptyState(state: UiState, onSuggest: (String) -> Unit, onAdd: () -> Unit, onImport: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 56.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        val (heading, body) = when {
            state.feeds.isEmpty() -> "Welcome 👋" to "Add a feed to get started. Paste any RSS/Atom link or website address, or tap one of these:"
            state.view == ReaderView.Starred -> "No starred articles" to "Tap ★ on any article to save it here."
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
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onAdd) { Text("Add a feed") }
                OutlinedButton(onClick = onImport) { Text("Import OPML") }
            }
        }
    }
}

fun relativeTime(time: Long): String =
    DateUtils.getRelativeTimeSpanString(time, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS, DateUtils.FORMAT_ABBREV_RELATIVE).toString()
