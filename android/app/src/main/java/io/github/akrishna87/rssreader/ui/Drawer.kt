package io.github.akrishna87.rssreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.rssreader.ReaderView
import io.github.akrishna87.rssreader.ReaderViewModel
import io.github.akrishna87.rssreader.UiState
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.data.Filters

/** Feedly-style left sidebar: smart views, boards, then feeds grouped by folder. */
@Composable
fun AppDrawer(
    state: UiState,
    vm: ReaderViewModel,
    onClose: () -> Unit,
    onAdd: () -> Unit,
    onNewBoard: () -> Unit,
    onDeleteBoard: (String) -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    val apple = LocalApple.current
    val filters = remember(state.mute, state.highlight) { Filters(state.mute, state.highlight) }
    val matcher = remember(state.feeds, filters, state.articles) { ViewMatcher(state.feeds, filters, System.currentTimeMillis()) }
    val unread = remember(state.articles, filters) { state.articles.filter { !it.read && filters.mutedBy(it) == null } }
    val unreadByFeed = remember(unread) { unread.groupingBy { it.feedId }.eachCount() }
    val sorted = remember(state.feeds) { state.feeds.sortedBy { it.name.lowercase() } }
    val folders = remember(state.feeds) { state.feeds.map { it.folder }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() } }
    val loose = sorted.filter { it.folder.isEmpty() }

    fun count(n: Int) = if (n > 0) n.toString() else null
    fun go(view: ReaderView) { vm.setView(view); onClose() }
    fun selected(view: ReaderView) = state.view == view

    ModalDrawerSheet(drawerContainerColor = apple.background, modifier = Modifier.width(310.dp)) {
        Row(Modifier.fillMaxWidth().statusBarsPadding().height(64.dp).padding(start = 20.dp, end = 4.dp), verticalAlignment = Alignment.CenterVertically) {
            Text("Vellum", style = AppleType.title2, color = apple.tint, modifier = Modifier.weight(1f))
            BarIcon(AppIcons.Plus, "Add feed") { onClose(); onAdd() }
            BarIcon(AppIcons.Sliders, "Settings", tint = apple.secondaryLabel) { onClose(); vm.openSettings() }
        }
        Hairline()
        LazyColumn(Modifier.weight(1f), contentPadding = androidx.compose.foundation.layout.PaddingValues(vertical = 8.dp)) {
            item {
                DrawerItem("Today", AppIcons.Sun, selected(ReaderView.Today), count(unread.count { matcher.matches(it, ReaderView.Today) })) { go(ReaderView.Today) }
                DrawerItem("Read Later", AppIcons.Bookmark, selected(ReaderView.Starred), count(state.articles.count { it.starred })) { go(ReaderView.Starred) }
                DrawerItem("Highlights & Notes", AppIcons.Highlighter, selected(ReaderView.Notes), count(state.articles.count { it.highlights.isNotEmpty() })) { go(ReaderView.Notes) }
                DrawerItem("All Articles", AppIcons.Tray, selected(ReaderView.All), count(unread.size)) { go(ReaderView.All) }
                if (state.feeds.any { it.priority }) {
                    DrawerItem("Priority", AppIcons.Bolt, selected(ReaderView.Priority), count(unread.count { matcher.matches(it, ReaderView.Priority) })) { go(ReaderView.Priority) }
                }
                if (state.highlight.isNotEmpty()) {
                    DrawerItem("Keyword Alerts", AppIcons.Tag, selected(ReaderView.Alerts), count(unread.count { filters.highlightedBy(it) != null })) { go(ReaderView.Alerts) }
                }
            }
            item { SectionHeader("Boards") { BarIcon(AppIcons.Plus, "New board", tint = apple.secondaryLabel, onClick = onNewBoard) } }
            if (state.boards.isEmpty()) {
                item {
                    Text(
                        "Save articles into boards to keep ideas together.",
                        style = AppleType.footnote, color = apple.secondaryLabel,
                        modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
                    )
                }
            }
            state.boards.forEach { name ->
                item(key = "board-$name") {
                    DrawerItem(
                        name, AppIcons.Grid, selected(ReaderView.Board(name)), count(state.articles.count { name in it.boards }),
                        trailing = { BarIcon(AppIcons.Trash, "Delete board $name", tint = apple.tertiaryLabel) { onDeleteBoard(name) } },
                    ) { go(ReaderView.Board(name)) }
                }
            }

            item { SectionHeader("Feeds") }
            if (state.feeds.isEmpty()) {
                item {
                    DrawerItem("Add your first feed", AppIcons.Plus, false, null) { onClose(); onAdd() }
                }
            }
            folders.forEach { folder ->
                val members = sorted.filter { it.folder == folder }
                val expanded = folder !in state.collapsed
                item(key = "folder-$folder") {
                    val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
                    DrawerItem(
                        folder, AppIcons.Folder, selected(ReaderView.Folder(folder)), count(members.sumOf { unreadByFeed[it.id] ?: 0 }),
                        trailing = {
                            Box(
                                Modifier.size(40.dp).clickable(interactionSource = remember { MutableInteractionSource() }, indication = null) { vm.toggleFolder(folder) },
                                contentAlignment = Alignment.Center,
                            ) {
                                Icon(AppIcons.ChevronRight, if (expanded) "Collapse $folder" else "Expand $folder", tint = apple.secondaryLabel, modifier = Modifier.size(18.dp).rotate(rotation))
                            }
                        },
                    ) { go(ReaderView.Folder(folder)) }
                }
                if (expanded) members.forEach { f -> item(key = "feed-${f.id}") { FeedItem(f, count(unreadByFeed[f.id] ?: 0), true, selected(ReaderView.OneFeed(f.id))) { go(ReaderView.OneFeed(f.id)) } } }
            }
            loose.forEach { f -> item(key = "feed-${f.id}") { FeedItem(f, count(unreadByFeed[f.id] ?: 0), false, selected(ReaderView.OneFeed(f.id))) { go(ReaderView.OneFeed(f.id)) } } }

            item {
                SectionHeader("More")
                DrawerItem("Discover Feeds", AppIcons.Compass, false, null) { onClose(); vm.openDiscover() }
                DrawerItem("Keyword Filters", AppIcons.Filter, false, count(state.mute.size + state.highlight.size)) { onClose(); vm.openFilters() }
                if (state.feeds.isNotEmpty()) DrawerItem("Manage Feeds", AppIcons.Pencil, false, null) { onClose(); vm.openManage() }
                DrawerItem("Import OPML", AppIcons.ImportDoc, false, null) { onClose(); onImport() }
                DrawerItem("Export OPML", AppIcons.ExportDoc, false, null) { onClose(); onExport() }
                Spacer(Modifier.navigationBarsPadding())
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String, action: (@Composable () -> Unit)? = null) {
    val apple = LocalApple.current
    Row(Modifier.fillMaxWidth().height(40.dp).padding(start = 20.dp, end = 4.dp, top = 6.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(
            text.uppercase(),
            style = AppleType.caption1.copy(fontWeight = FontWeight.Bold, letterSpacing = 0.8.sp),
            color = apple.secondaryLabel,
            modifier = Modifier.weight(1f),
        )
        action?.invoke()
    }
}

@Composable
private fun FeedItem(feed: Feed, unread: String?, indent: Boolean, selected: Boolean, onClick: () -> Unit) {
    val apple = LocalApple.current
    DrawerItem(
        feed.name, null, selected, unread,
        indent = indent,
        leading = { FeedBadge(feed, size = 22.dp) },
        trailing = if (feed.priority) ({ Icon(AppIcons.Bolt, "Priority", tint = apple.tint, modifier = Modifier.padding(end = 8.dp).size(16.dp)) }) else null,
        error = feed.error != null,
        onClick = onClick,
    )
}

@Composable
private fun DrawerItem(
    label: String,
    icon: ImageVector?,
    selected: Boolean,
    count: String?,
    indent: Boolean = false,
    leading: (@Composable () -> Unit)? = null,
    trailing: (@Composable () -> Unit)? = null,
    error: Boolean = false,
    onClick: () -> Unit,
) {
    val apple = LocalApple.current
    Row(
        Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 1.dp).clip(RoundedCornerShape(10.dp))
            .background(if (selected) apple.tint.copy(alpha = 0.14f) else androidx.compose.ui.graphics.Color.Transparent)
            .clickable(onClick = onClick).height(44.dp).padding(start = if (indent) 28.dp else 12.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        if (leading != null) leading() else if (icon != null) {
            Icon(icon, null, tint = if (selected) apple.tint else apple.secondaryLabel, modifier = Modifier.size(22.dp))
        }
        Spacer(Modifier.width(14.dp))
        Text(
            label,
            style = AppleType.body.copy(fontWeight = if (selected) FontWeight.SemiBold else FontWeight.Normal),
            color = if (error) apple.red else if (selected) apple.tint else apple.label,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        if (count != null) Text(count, style = AppleType.footnote, color = apple.secondaryLabel, modifier = Modifier.padding(horizontal = 8.dp))
        trailing?.invoke()
    }
}
