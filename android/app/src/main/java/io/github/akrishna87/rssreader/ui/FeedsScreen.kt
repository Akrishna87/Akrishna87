package io.github.akrishna87.rssreader.ui

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.unit.dp
import io.github.akrishna87.rssreader.ReaderView
import io.github.akrishna87.rssreader.ReaderViewModel
import io.github.akrishna87.rssreader.UiState
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.data.Filters
import kotlinx.coroutines.launch

/** Home screen: smart feeds, folders and feeds as inset-grouped lists (like Mail's Mailboxes). */
@Composable
fun FeedsScreen(
    state: UiState,
    vm: ReaderViewModel,
    snackbar: SnackbarHostState,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onExport: () -> Unit,
) {
    val apple = LocalApple.current
    val scope = rememberCoroutineScope()
    val filters = remember(state.mute, state.highlight) { Filters(state.mute, state.highlight) }
    val unreadByFeed = remember(state.articles, filters) {
        state.articles.filter { !it.read && filters.mutedBy(it) == null }.groupingBy { it.feedId }.eachCount()
    }
    val starred = remember(state.articles) { state.articles.count { it.starred } }
    val highlighted = remember(state.articles, filters) {
        state.articles.count { !it.read && filters.highlightedBy(it) != null && filters.mutedBy(it) == null }
    }
    val sorted = remember(state.feeds) { state.feeds.sortedBy { it.name.lowercase() } }
    val folders = remember(state.feeds) { state.feeds.map { it.folder }.filter { it.isNotEmpty() }.distinct().sortedBy { it.lowercase() } }
    val loose = sorted.filter { it.folder.isEmpty() }

    fun count(n: Int) = if (n > 0) n.toString() else null

    AppleScreen(
        title = "Feeds",
        snackbar = snackbar,
        refreshing = state.refreshing,
        onRefresh = { vm.refreshAll() },
        trailing = { BarIcon(AppIcons.Plus, "Add feed", onClick = onAdd) },
    ) {
        item(key = "smart") {
            GroupedSection {
                AppleRow(
                    "All Articles",
                    leading = { SymbolTile(AppIcons.Tray, apple.blue) },
                    value = count(unreadByFeed.values.sum()),
                    onClick = { vm.setView(ReaderView.All) },
                )
                AppleRow(
                    "Starred",
                    leading = { SymbolTile(AppIcons.StarFill, apple.yellow) },
                    value = count(starred),
                    showDivider = state.highlight.isNotEmpty(),
                    onClick = { vm.setView(ReaderView.Starred) },
                )
                if (state.highlight.isNotEmpty()) {
                    AppleRow(
                        "Highlights",
                        leading = { SymbolTile(AppIcons.Tag, apple.tint) },
                        value = count(highlighted),
                        showDivider = false,
                        onClick = { vm.setView(ReaderView.Highlights) },
                    )
                }
            }
        }

        if (state.loaded && state.feeds.isEmpty()) {
            item(key = "welcome") {
                GroupedSection(header = "Get started", footer = "Paste any feed or website address, or pick from popular feeds by topic.") {
                    AppleRow("Discover Feeds", leading = { SymbolTile(AppIcons.Compass, apple.purple) }, onClick = vm::openDiscover)
                    AppleRow("Add a Feed", leading = { SymbolTile(AppIcons.Plus, apple.tint) }, onClick = onAdd)
                    AppleRow("Import OPML", leading = { SymbolTile(AppIcons.ImportDoc, apple.blue) }, showDivider = false, onClick = onImport)
                }
            }
        }

        if (folders.isNotEmpty()) {
            item(key = "folders") {
                GroupedSection(header = "Folders") {
                    folders.forEachIndexed { i, folder ->
                        val members = sorted.filter { it.folder == folder }
                        val expanded = folder !in state.collapsed
                        val isLast = i == folders.lastIndex
                        val rotation by animateFloatAsState(if (expanded) 90f else 0f, label = "chevron")
                        AppleRow(
                            folder,
                            leading = {
                                Box(Modifier.size(29.dp), contentAlignment = Alignment.Center) {
                                    Icon(AppIcons.Folder, null, tint = apple.tint, modifier = Modifier.size(26.dp))
                                }
                            },
                            value = count(members.sumOf { unreadByFeed[it.id] ?: 0 }),
                            chevron = false,
                            showDivider = !isLast || expanded,
                            onClick = { vm.setView(ReaderView.Folder(folder)) },
                            trailing = {
                                Box(
                                    Modifier.size(32.dp).clickable(
                                        interactionSource = remember { MutableInteractionSource() },
                                        indication = null,
                                    ) { vm.toggleFolder(folder) },
                                    contentAlignment = Alignment.Center,
                                ) {
                                    Icon(
                                        AppIcons.ChevronRight,
                                        if (expanded) "Collapse $folder" else "Expand $folder",
                                        tint = apple.tint,
                                        modifier = Modifier.size(18.dp).rotate(rotation),
                                    )
                                }
                            },
                        )
                        if (expanded) {
                            members.forEachIndexed { j, f ->
                                FeedRow(
                                    f, count(unreadByFeed[f.id] ?: 0), indent = true,
                                    showDivider = !(isLast && j == members.lastIndex),
                                ) { vm.setView(ReaderView.OneFeed(f.id)) }
                            }
                        }
                    }
                }
            }
        }

        if (loose.isNotEmpty()) {
            item(key = "feeds") {
                GroupedSection(header = if (folders.isEmpty()) "Feeds" else "Other feeds") {
                    loose.forEachIndexed { i, f ->
                        FeedRow(f, count(unreadByFeed[f.id] ?: 0), indent = false, showDivider = i != loose.lastIndex) {
                            vm.setView(ReaderView.OneFeed(f.id))
                        }
                    }
                }
            }
        }

        item(key = "more") {
            GroupedSection(header = "More") {
                AppleRow("Discover Feeds", leading = { SymbolTile(AppIcons.Compass, apple.purple) }, onClick = vm::openDiscover)
                AppleRow("Keyword Filters", leading = { SymbolTile(AppIcons.Filter, apple.gray) },
                    value = (state.mute.size + state.highlight.size).takeIf { it > 0 }?.toString(), onClick = vm::openFilters)
                if (state.feeds.isNotEmpty()) {
                    AppleRow("Manage Feeds", leading = { SymbolTile(AppIcons.Pencil, apple.gray) }, onClick = vm::openManage)
                }
                AppleRow("Import OPML", leading = { SymbolTile(AppIcons.ImportDoc, apple.blue) }, chevron = false, onClick = onImport)
                AppleRow(
                    "Export OPML",
                    leading = { SymbolTile(AppIcons.ExportDoc, apple.blue) },
                    chevron = false,
                    showDivider = false,
                    onClick = {
                        if (state.feeds.isEmpty()) scope.launch { snackbar.showSnackbar("No feeds to export yet") } else onExport()
                    },
                )
            }
        }
    }
}

@Composable
private fun FeedRow(feed: Feed, unread: String?, indent: Boolean, showDivider: Boolean, onClick: () -> Unit) {
    val apple = LocalApple.current
    AppleRow(
        feed.name,
        modifier = Modifier,
        subtitle = feed.error?.let { "Update failed: $it" },
        leading = {
            Box(Modifier.size(29.dp), contentAlignment = Alignment.Center) { FeedBadge(feed, size = if (indent) 24.dp else 29.dp) }
        },
        value = unread,
        showDivider = showDivider,
        contentStart = if (indent) 44.dp else 16.dp,
        onClick = onClick,
        titleColor = if (feed.error != null) apple.red else null,
    )
}
