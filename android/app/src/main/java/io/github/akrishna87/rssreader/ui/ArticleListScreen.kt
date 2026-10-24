package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.pulltorefresh.PullToRefreshBox
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import io.github.akrishna87.rssreader.ReaderView
import io.github.akrishna87.rssreader.ReaderViewModel
import io.github.akrishna87.rssreader.UiState
import io.github.akrishna87.rssreader.data.Article
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.data.Filters

private const val DAY = 86_400_000L

/** Which articles belong to which view. Built once per state so lists and counts agree. */
class ViewMatcher(feeds: List<Feed>, private val filters: Filters, private val now: Long) {
    private val priorityIds = feeds.filter { it.priority }.map { it.id }.toHashSet()
    private val folderIds = feeds.groupBy { it.folder }.mapValues { e -> e.value.map { it.id }.toHashSet() }

    fun matches(a: Article, view: ReaderView): Boolean = when (view) {
        ReaderView.Today -> a.date >= now - DAY
        ReaderView.All -> true
        ReaderView.Starred -> a.starred
        ReaderView.Priority -> a.feedId in priorityIds
        is ReaderView.OneFeed -> a.feedId == view.feedId
        is ReaderView.Folder -> a.feedId in folderIds[view.name].orEmpty()
        is ReaderView.Board -> view.name in a.boards
        ReaderView.Notes -> a.highlights.isNotEmpty()
        ReaderView.Alerts -> filters.highlightedBy(a) != null
    }

    /** Saved views list everything in them, read or not. */
    fun ignoresUnreadFilter(view: ReaderView) = view == ReaderView.Starred || view is ReaderView.Board || view == ReaderView.Notes
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArticleListScreen(
    state: UiState,
    vm: ReaderViewModel,
    title: String,
    snackbar: SnackbarHostState,
    onMenu: () -> Unit,
    onAdd: () -> Unit,
) {
    val apple = LocalApple.current
    val filters = remember(state.mute, state.highlight) { Filters(state.mute, state.highlight) }
    val feedsById = remember(state.feeds) { state.feeds.associateBy { it.id } }
    val matcher = remember(state.feeds, filters, state.articles) { ViewMatcher(state.feeds, filters, System.currentTimeMillis()) }
    var query by remember(state.view) { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }
    val layout = state.settings.layout

    val inView = remember(matcher, state.articles, state.view, state.unreadOnly) {
        state.articles.filter { a ->
            matcher.matches(a, state.view) && (!state.unreadOnly || matcher.ignoresUnreadFilter(state.view) || !a.read)
        }
    }
    val mutedCount = remember(inView, filters) { inView.count { filters.mutedBy(it) != null } }
    val unmuted = remember(inView, filters, state.showMuted) {
        if (state.showMuted) inView else inView.filter { filters.mutedBy(it) == null }
    }
    val visible = remember(unmuted, query) {
        val q = query?.trim()?.lowercase().orEmpty()
        if (q.isEmpty()) unmuted else unmuted.filter { "${it.title} ${it.snippet} ${it.author}".lowercase().contains(q) }
    }
    val currentFeed = (state.view as? ReaderView.OneFeed)?.let { feedsById[it.feedId] }
    val showFeed = state.view !is ReaderView.OneFeed

    Box(Modifier.fillMaxSize().background(if (layout == "cards") apple.groupedBackground else apple.background)) {
        Column(Modifier.fillMaxSize()) {
            // Top bar
            Column(Modifier.fillMaxWidth().background(apple.background).statusBarsPadding()) {
                Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    BarIcon(AppIcons.Menu, "Open menu", tint = apple.label, onClick = onMenu)
                    Text(
                        title,
                        style = AppleType.title3,
                        color = apple.label,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f).padding(horizontal = 6.dp),
                    )
                    BarIcon(AppIcons.Magnifier, "Search this list", tint = apple.label) { query = if (query == null) "" else null }
                    BarIcon(AppIcons.CheckCircle, "Mark all as read", enabled = visible.any { !it.read }, tint = apple.label) {
                        vm.markAllRead(visible.map { it.id })
                    }
                    Box {
                        BarIcon(AppIcons.DotsVertical, "More options", tint = apple.label) { menu = true }
                        DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                            MenuLabel("Layout")
                            listOf("magazine" to "Magazine", "cards" to "Cards", "title" to "Title only").forEach { (key, label) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = AppleType.body) },
                                    trailingIcon = { if (layout == key) Icon(AppIcons.Checkmark, null, tint = apple.tint, modifier = Modifier.size(18.dp)) },
                                    onClick = { vm.setLayout(key); menu = false },
                                )
                            }
                            MenuLabel("Show")
                            DropdownMenuItem(
                                text = { Text("Unread only", style = AppleType.body) },
                                trailingIcon = { if (state.unreadOnly) Icon(AppIcons.Checkmark, null, tint = apple.tint, modifier = Modifier.size(18.dp)) },
                                onClick = { vm.setUnreadOnly(!state.unreadOnly); menu = false },
                            )
                            MenuLabel("Mark as read")
                            listOf("Older than a day" to DAY, "Older than a week" to 7 * DAY).forEach { (label, age) ->
                                DropdownMenuItem(
                                    text = { Text(label, style = AppleType.body) },
                                    onClick = { vm.markOlderRead(visible.map { it.id }, System.currentTimeMillis() - age); menu = false },
                                )
                            }
                            DropdownMenuItem(
                                text = { Text("Refresh", style = AppleType.body) },
                                onClick = { vm.refreshAll(); menu = false },
                            )
                        }
                    }
                }
                query?.let { q ->
                    SearchField(q, { query = it }, "Search ${title.lowercase()}", Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp))
                }
                Hairline()
            }

            PullToRefreshBox(isRefreshing = state.refreshing, onRefresh = { vm.refreshAll() }, modifier = Modifier.weight(1f).fillMaxWidth()) {
                LazyColumn(Modifier.fillMaxSize(), contentPadding = androidx.compose.foundation.layout.PaddingValues(bottom = 32.dp)) {
                    if (currentFeed?.error != null) {
                        item(key = "error") {
                            Text(
                                "Last update failed: ${currentFeed.error}",
                                style = AppleType.footnote,
                                color = apple.red,
                                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                            )
                        }
                    }
                    if (mutedCount > 0) {
                        item(key = "muted") {
                            Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                                Icon(AppIcons.Muted, null, tint = apple.secondaryLabel, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(
                                    if (state.showMuted) "Showing $mutedCount muted" else "$mutedCount muted hidden",
                                    style = AppleType.footnote,
                                    color = apple.secondaryLabel,
                                    modifier = Modifier.weight(1f),
                                )
                                BarText(if (state.showMuted) "Hide" else "Show") { vm.setShowMuted(!state.showMuted) }
                            }
                        }
                    }
                    if (visible.isEmpty() && state.loaded) {
                        item(key = "empty") {
                            val (heading, body) = emptyText(state, query)
                            EmptyMessage(heading, body) {
                                if (state.feeds.isEmpty()) {
                                    Spacer(Modifier.height(16.dp))
                                    CapsuleButton("Add your first feed", onClick = onAdd)
                                }
                            }
                        }
                    }
                    items(visible, key = { it.id }) { a ->
                        SwipeableArticle(
                            a = a,
                            layout = layout,
                            feedTitle = feedsById[a.feedId]?.name.orEmpty(),
                            showFeed = showFeed,
                            alert = filters.highlightedBy(a),
                            muted = filters.mutedBy(a),
                            showNotes = state.view == ReaderView.Notes,
                            onOpen = { vm.openArticle(a.id) },
                            onToggleRead = { vm.setRead(a.id, !a.read) },
                            onToggleSave = { vm.toggleStar(a.id) },
                        )
                    }
                }
            }
        }
        AppleSnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).navigationBarsPadding())
    }
}

@Composable
private fun MenuLabel(text: String) {
    Text(
        text.uppercase(),
        style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 0.5.sp),
        color = LocalApple.current.secondaryLabel,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 2.dp),
    )
}

private fun emptyText(state: UiState, query: String?): Pair<String, String> = when {
    state.feeds.isEmpty() -> "No Feeds Yet" to "Add a feed, or pick from popular ones in Discover."
    !query.isNullOrBlank() -> "No Results" to "Nothing here matches “${query.trim()}”."
    state.view == ReaderView.Starred -> "Nothing Saved" to "Tap the bookmark on an article, or swipe left on it, to read it later."
    state.view is ReaderView.Board -> "Empty Board" to "Open an article and tap the board icon to save it here."
    state.view == ReaderView.Notes -> "No Highlights" to "Select text in an article and choose Highlight, or Add note."
    state.view == ReaderView.Alerts -> "No Matches" to "Articles mentioning ${state.highlight.joinToString(", ")} will appear here."
    state.view == ReaderView.Priority -> "No Priority Articles" to "Mark feeds as priority in Manage Feeds and their articles show up here."
    state.view == ReaderView.Today -> "You're All Caught Up" to "Nothing new from the last 24 hours. Pull down to refresh."
    state.unreadOnly -> "All Caught Up" to "There are no unread articles here."
    else -> "No Articles" to "Pull down to refresh."
}

/** Swipe right: read/unread (blue). Swipe left: Read Later (green). The row springs back after the action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableArticle(
    a: Article,
    layout: String,
    feedTitle: String,
    showFeed: Boolean,
    alert: String?,
    muted: String?,
    showNotes: Boolean,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleSave: () -> Unit,
) {
    val apple = LocalApple.current
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onToggleRead()
                SwipeToDismissBoxValue.EndToStart -> onToggleSave()
                SwipeToDismissBoxValue.Settled -> {}
            }
            false
        },
        positionalThreshold = { distance -> distance * 0.3f },
    )
    SwipeToDismissBox(
        state = swipe,
        backgroundContent = {
            val toRight = swipe.dismissDirection == SwipeToDismissBoxValue.StartToEnd
            val color = when (swipe.dismissDirection) {
                SwipeToDismissBoxValue.StartToEnd -> apple.blue
                SwipeToDismissBoxValue.EndToStart -> apple.tint
                SwipeToDismissBoxValue.Settled -> Color.Transparent
            }
            Box(
                Modifier.fillMaxSize().background(color).padding(horizontal = 24.dp),
                contentAlignment = if (toRight) Alignment.CenterStart else Alignment.CenterEnd,
            ) {
                if (swipe.dismissDirection != SwipeToDismissBoxValue.Settled) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Icon(
                            if (toRight) (if (a.read) AppIcons.EnvelopeBadge else AppIcons.Envelope) else (if (a.starred) AppIcons.Bookmark else AppIcons.BookmarkFill),
                            null, tint = Color.White, modifier = Modifier.size(24.dp),
                        )
                        Text(
                            if (toRight) (if (a.read) "Unread" else "Read") else (if (a.starred) "Remove" else "Read later"),
                            style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                }
            }
        },
    ) {
        val source = if (showFeed) feedTitle else a.author.ifBlank { feedTitle }
        when (layout) {
            "cards" -> ArticleCard(a, source, alert, muted, showNotes, onOpen, onToggleSave)
            "title" -> ArticleTitleRow(a, source, alert, muted, onOpen)
            else -> ArticleMagazineRow(a, source, alert, muted, showNotes, onOpen, onToggleSave)
        }
    }
}

@Composable
private fun SourceLine(a: Article, source: String, modifier: Modifier = Modifier) {
    val apple = LocalApple.current
    Row(modifier, verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            source,
            style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold),
            color = apple.tint,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f, fill = false),
        )
        Text("· " + relativeTime(a.date), style = AppleType.footnote, color = apple.secondaryLabel, maxLines = 1)
    }
}

@Composable
private fun Tags(a: Article, alert: String?, muted: String?, showNotes: Boolean) {
    val apple = LocalApple.current
    if (alert != null || muted != null) {
        val (label, color) = if (muted != null) "Muted · $muted" to apple.gray else "★ $alert" to apple.tint
        Spacer(Modifier.height(6.dp))
        Text(
            label,
            style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold),
            color = color,
            modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
        )
    }
    if (showNotes) {
        a.highlights.take(2).forEach { h ->
            Spacer(Modifier.height(6.dp))
            Row(Modifier.fillMaxWidth().clip(RoundedCornerShape(6.dp)).background(apple.yellow.copy(alpha = 0.22f)).padding(8.dp)) {
                Column {
                    Text(h.text, style = AppleType.footnote, color = apple.label, maxLines = 3, overflow = TextOverflow.Ellipsis)
                    if (h.note.isNotEmpty()) Text(h.note, style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold), color = apple.secondaryLabel, maxLines = 2)
                }
            }
        }
    }
}

@Composable
private fun SavedMark(a: Article) {
    val apple = LocalApple.current
    if (a.starred) Icon(AppIcons.BookmarkFill, "Saved", tint = apple.tint, modifier = Modifier.size(14.dp))
}

@Composable
private fun ArticleMagazineRow(
    a: Article, source: String, alert: String?, muted: String?, showNotes: Boolean, onClick: () -> Unit, onToggleSave: () -> Unit,
) {
    val apple = LocalApple.current
    Column(Modifier.fillMaxWidth().background(apple.background).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().alpha(if (muted != null) 0.5f else 1f).padding(horizontal = 16.dp, vertical = 14.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Column(Modifier.weight(1f)) {
                SourceLine(a, source)
                Spacer(Modifier.height(4.dp))
                Text(
                    a.title,
                    style = AppleType.headline.copy(fontWeight = if (a.read) FontWeight.Medium else FontWeight.Bold),
                    color = if (a.read) apple.secondaryLabel else apple.label,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (a.snippet.isNotEmpty()) {
                    Spacer(Modifier.height(4.dp))
                    Text(a.snippet, style = AppleType.subheadline, color = apple.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Tags(a, alert, muted, showNotes)
                Row(Modifier.padding(top = 6.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SavedMark(a)
                    if (a.boards.isNotEmpty()) Text(a.boards.joinToString(", "), style = AppleType.caption1, color = apple.secondaryLabel, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            if (a.thumbnail != null) {
                Spacer(Modifier.width(14.dp))
                AsyncImage(
                    model = a.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.size(92.dp).clip(RoundedCornerShape(8.dp)).background(apple.fill),
                )
            }
        }
        Hairline(start = 16.dp)
    }
}

@Composable
private fun ArticleCard(
    a: Article, source: String, alert: String?, muted: String?, showNotes: Boolean, onClick: () -> Unit, onToggleSave: () -> Unit,
) {
    val apple = LocalApple.current
    Box(Modifier.fillMaxWidth().background(apple.groupedBackground).padding(horizontal = 12.dp, vertical = 6.dp)) {
        Column(
            Modifier.fillMaxWidth().alpha(if (muted != null) 0.5f else 1f).clip(RoundedCornerShape(12.dp)).background(apple.cell).clickable(onClick = onClick),
        ) {
            if (a.thumbnail != null) {
                AsyncImage(
                    model = a.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.fillMaxWidth().aspectRatio(16f / 9f).background(apple.fill),
                )
            }
            Column(Modifier.padding(14.dp)) {
                Text(
                    a.title,
                    style = AppleType.title3.copy(fontWeight = FontWeight.Bold),
                    color = if (a.read) apple.secondaryLabel else apple.label,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (a.snippet.isNotEmpty()) {
                    Spacer(Modifier.height(6.dp))
                    Text(a.snippet, style = AppleType.subheadline, color = apple.secondaryLabel, maxLines = 3, overflow = TextOverflow.Ellipsis)
                }
                Tags(a, alert, muted, showNotes)
                Spacer(Modifier.height(10.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    SourceLine(a, source, Modifier.weight(1f))
                    Icon(
                        if (a.starred) AppIcons.BookmarkFill else AppIcons.Bookmark,
                        if (a.starred) "Remove from Read Later" else "Read later",
                        tint = if (a.starred) apple.tint else apple.secondaryLabel,
                        modifier = Modifier.size(22.dp).clickable(onClick = onToggleSave),
                    )
                }
            }
        }
    }
}

@Composable
private fun ArticleTitleRow(a: Article, source: String, alert: String?, muted: String?, onClick: () -> Unit) {
    val apple = LocalApple.current
    Column(Modifier.fillMaxWidth().background(apple.background).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().alpha(if (muted != null) 0.5f else 1f).padding(start = 8.dp, end = 16.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.Top,
        ) {
            Box(Modifier.width(20.dp).padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
                if (!a.read) Box(Modifier.size(8.dp).clip(CircleShape).background(apple.tint))
            }
            Column(Modifier.weight(1f)) {
                Text(
                    a.title,
                    style = AppleType.body.copy(fontWeight = if (a.read) FontWeight.Normal else FontWeight.SemiBold),
                    color = if (a.read) apple.secondaryLabel else apple.label,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    SourceLine(a, source, Modifier.weight(1f, fill = false))
                    SavedMark(a)
                    if (alert != null) Text("★ $alert", style = AppleType.caption1, color = apple.tint, maxLines = 1)
                }
            }
        }
        Hairline(start = 28.dp)
    }
}

fun relativeTime(time: Long): String {
    val diff = System.currentTimeMillis() - time
    return when {
        diff < 60_000 -> "Now"
        diff < 3_600_000 -> "${diff / 60_000}m"
        diff < 86_400_000 -> "${diff / 3_600_000}h"
        diff < 7 * 86_400_000L -> "${diff / 86_400_000}d"
        else -> java.text.SimpleDateFormat("d MMM", java.util.Locale.getDefault()).format(java.util.Date(time))
    }
}
