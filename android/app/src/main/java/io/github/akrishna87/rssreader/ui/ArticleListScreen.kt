package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import io.github.akrishna87.rssreader.ReaderView
import io.github.akrishna87.rssreader.ReaderViewModel
import io.github.akrishna87.rssreader.UiState
import io.github.akrishna87.rssreader.data.Article
import io.github.akrishna87.rssreader.data.Filters

@Composable
fun ArticleListScreen(
    state: UiState,
    vm: ReaderViewModel,
    title: String,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
) {
    val apple = LocalApple.current
    val filters = remember(state.mute, state.highlight) { Filters(state.mute, state.highlight) }
    val feedsById = remember(state.feeds) { state.feeds.associateBy { it.id } }
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

    AppleScreen(
        title = title,
        snackbar = snackbar,
        backLabel = "Feeds",
        onBack = onBack,
        grouped = false,
        refreshing = state.refreshing,
        onRefresh = { vm.refreshAll() },
        trailing = {
            BarIcon(AppIcons.CheckCircle, "Mark all as read", enabled = visible.any { !it.read }) { vm.markAllRead(visible.map { it.id }) }
        },
        belowTitle = {
            if (state.view != ReaderView.Starred) {
                item(key = "segment") {
                    SegmentedControl(
                        options = listOf("All", "Unread"),
                        selected = if (state.unreadOnly) 1 else 0,
                        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 10.dp),
                    ) { vm.setUnreadOnly(it == 1) }
                }
            }
            if (currentFeed?.error != null) {
                item(key = "error") {
                    Text(
                        "Last update failed: ${currentFeed.error}",
                        style = AppleType.footnote,
                        color = apple.red,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 6.dp),
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
                    Hairline(start = 16.dp)
                }
            }
        },
    ) {
        if (visible.isEmpty() && state.loaded) {
            item(key = "empty") {
                val (heading, body) = when {
                    state.feeds.isEmpty() -> "No Feeds" to "Go back and tap + to add your first feed."
                    state.view == ReaderView.Starred -> "No Starred Articles" to "Swipe left on an article, or tap ☆ while reading, to star it."
                    state.view == ReaderView.Highlights -> "No Highlights" to "Articles mentioning ${state.highlight.joinToString(", ")} will appear here."
                    state.unreadOnly -> "All Caught Up" to "There are no unread articles here."
                    else -> "No Articles" to "Pull down to refresh."
                }
                EmptyMessage(heading, body)
            }
        }
        items(visible, key = { it.id }) { a ->
            SwipeableArticle(
                a = a,
                feedTitle = feedsById[a.feedId]?.name.orEmpty(),
                showFeed = state.view !is ReaderView.OneFeed,
                highlight = filters.highlightedBy(a),
                muted = filters.mutedBy(a),
                onOpen = { vm.openArticle(a.id) },
                onToggleRead = { vm.setRead(a.id, !a.read) },
                onToggleStar = { vm.toggleStar(a.id) },
            )
        }
    }
}

/** Swipe right: read/unread (blue). Swipe left: star (orange). The row springs back after the action. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SwipeableArticle(
    a: Article,
    feedTitle: String,
    showFeed: Boolean,
    highlight: String?,
    muted: String?,
    onOpen: () -> Unit,
    onToggleRead: () -> Unit,
    onToggleStar: () -> Unit,
) {
    val apple = LocalApple.current
    val swipe = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> onToggleRead()
                SwipeToDismissBoxValue.EndToStart -> onToggleStar()
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
                            if (toRight) (if (a.read) AppIcons.EnvelopeBadge else AppIcons.Envelope) else (if (a.starred) AppIcons.Star else AppIcons.StarFill),
                            null, tint = Color.White, modifier = Modifier.size(24.dp),
                        )
                        Text(
                            if (toRight) (if (a.read) "Unread" else "Read") else (if (a.starred) "Unstar" else "Star"),
                            style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold),
                            color = Color.White,
                        )
                    }
                }
            }
        },
    ) {
        ArticleRow(a, feedTitle, showFeed, highlight, muted, onOpen)
    }
}

@Composable
private fun ArticleRow(a: Article, feedTitle: String, showFeed: Boolean, highlight: String?, muted: String?, onClick: () -> Unit) {
    val apple = LocalApple.current
    Column(Modifier.fillMaxWidth().background(apple.background).clickable(onClick = onClick)) {
        Row(
            Modifier.fillMaxWidth().alpha(if (muted != null) 0.5f else 1f).padding(start = 8.dp, end = 16.dp, top = 11.dp, bottom = 11.dp),
            verticalAlignment = Alignment.Top,
        ) {
            // Unread dot column (like Mail).
            Box(Modifier.width(24.dp).padding(top = 6.dp), contentAlignment = Alignment.TopCenter) {
                if (!a.read) Box(Modifier.size(10.dp).clip(CircleShape).background(apple.tint))
            }
            Column(Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text(
                        if (showFeed) feedTitle else (a.author.ifBlank { feedTitle }),
                        style = AppleType.footnote.copy(fontWeight = FontWeight.SemiBold),
                        color = apple.secondaryLabel,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false),
                    )
                    if (a.starred) Icon(AppIcons.StarFill, "Starred", tint = apple.yellow, modifier = Modifier.size(13.dp))
                    Spacer(Modifier.weight(1f))
                    Text(relativeTime(a.date), style = AppleType.footnote, color = apple.secondaryLabel, maxLines = 1)
                }
                Spacer(Modifier.height(2.dp))
                Text(
                    a.title,
                    style = AppleType.headline.copy(fontWeight = if (a.read) FontWeight.Medium else FontWeight.SemiBold),
                    color = if (a.read) apple.secondaryLabel else apple.label,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                )
                if (a.snippet.isNotEmpty()) {
                    Spacer(Modifier.height(3.dp))
                    Text(a.snippet, style = AppleType.subheadline, color = apple.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                if (highlight != null || muted != null) {
                    Spacer(Modifier.height(6.dp))
                    val (label, color) = if (muted != null) "Muted · $muted" to apple.gray else "★ $highlight" to apple.tint
                    Text(
                        label,
                        style = AppleType.caption1.copy(fontWeight = FontWeight.SemiBold),
                        color = color,
                        modifier = Modifier.clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.14f)).padding(horizontal = 8.dp, vertical = 2.dp),
                    )
                }
            }
            if (a.thumbnail != null) {
                Spacer(Modifier.width(12.dp))
                AsyncImage(
                    model = a.thumbnail,
                    contentDescription = null,
                    contentScale = ContentScale.Crop,
                    modifier = Modifier.padding(top = 2.dp).size(68.dp).clip(RoundedCornerShape(10.dp)).background(apple.fill),
                )
            }
        }
        Hairline(start = 32.dp)
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
