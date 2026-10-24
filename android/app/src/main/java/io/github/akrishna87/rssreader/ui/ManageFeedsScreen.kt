package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.unit.dp
import io.github.akrishna87.rssreader.data.Article
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.hostOf

/** Every subscription with edit (rename / folder) and delete. Tap a row to open that feed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFeedsScreen(
    feeds: List<Feed>,
    articles: List<Article>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (id: String, name: String, folder: String, priority: Boolean, fullText: Boolean) -> Unit,
    onOpen: (String) -> Unit,
) {
    val apple = LocalApple.current
    var confirm by remember { mutableStateOf<Feed?>(null) }
    var editing by remember { mutableStateOf<Feed?>(null) }
    val folders = remember(feeds) { feeds.map { it.folder }.filter { it.isNotEmpty() }.distinct().sorted() }
    val counts = remember(articles) { articles.groupingBy { it.feedId }.eachCount() }
    val starred = remember(articles) { articles.filter { it.starred }.groupingBy { it.feedId }.eachCount() }
    val groups = remember(feeds) {
        feeds.sortedBy { it.name.lowercase() }.groupBy { it.folder }.toList().sortedWith(compareBy<Pair<String, List<Feed>>>({ it.first.isEmpty() }, { it.first.lowercase() }))
    }

    AppleScreen(title = "Manage Feeds", snackbar = snackbar, backLabel = "Feeds", onBack = onBack) {
        if (feeds.isEmpty()) {
            item(key = "empty") { EmptyMessage("No Feeds", "Add some with + or from Discover.") }
        }
        groups.forEach { (folder, members) ->
            item(key = "group-$folder") {
                GroupedSection(header = folder.ifEmpty { if (folders.isEmpty()) "Feeds" else "No folder" }) {
                    members.forEachIndexed { i, f ->
                        AppleRow(
                            f.name,
                            subtitle = (f.error?.let { "⚠ $it" }) ?: "${hostOf(f.url)} · ${counts[f.id] ?: 0} articles",
                            leading = { FeedBadge(f) },
                            chevron = false,
                            showDivider = i != members.lastIndex,
                            onClick = { onOpen(f.id) },
                            trailing = {
                                BarIcon(AppIcons.Pencil, "Rename or move ${f.name}", tint = apple.tint) { editing = f }
                                BarIcon(AppIcons.Trash, "Unsubscribe from ${f.name}", tint = apple.red) { confirm = f }
                            },
                        )
                    }
                }
            }
        }
    }

    confirm?.let { feed ->
        val stars = starred[feed.id] ?: 0
        AppleAlert(
            title = "Unsubscribe from “${feed.name}”?",
            message = "Its saved articles will be removed" + if (stars > 0) ", including $stars starred." else ".",
            confirm = "Unsubscribe",
            destructive = true,
            onConfirm = { onRemove(feed.id); confirm = null },
            onDismiss = { confirm = null },
        )
    }

    editing?.let { feed ->
        val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        var name by remember(feed.id) { mutableStateOf(feed.name) }
        var folder by remember(feed.id) { mutableStateOf(feed.folder) }
        var priority by remember(feed.id) { mutableStateOf(feed.priority) }
        var fullText by remember(feed.id) { mutableStateOf(feed.fullText) }
        ModalBottomSheet(
            onDismissRequest = { editing = null },
            sheetState = sheet,
            containerColor = apple.groupedBackground,
            shape = RoundedCornerShape(topStart = 12.dp, topEnd = 12.dp),
            dragHandle = {
                Box(Modifier.padding(top = 6.dp).size(width = 36.dp, height = 5.dp).clip(RoundedCornerShape(50)).background(apple.tertiaryLabel))
            },
        ) {
            Column(Modifier.fillMaxWidth().imePadding().navigationBarsPadding().padding(bottom = 16.dp)) {
                Box(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 8.dp)) {
                    Box(Modifier.align(Alignment.CenterStart)) { BarText("Cancel") { editing = null } }
                    Text("Edit Feed", style = AppleType.headline, color = apple.label, modifier = Modifier.align(Alignment.Center))
                    Box(Modifier.align(Alignment.CenterEnd)) {
                        BarText("Save", bold = true) {
                            onEdit(feed.id, name.ifBlank { feed.title }, folder, priority, fullText)
                            editing = null
                        }
                    }
                }
                GroupedSection(header = "Name", footer = "Leave as is to keep the feed's own title (${feed.title}).") {
                    Row(Modifier.fillMaxWidth().height(48.dp).padding(horizontal = 16.dp), verticalAlignment = Alignment.CenterVertically) {
                        AppleTextField(name, { name = it }, feed.title, Modifier.weight(1f))
                    }
                }
                FolderPicker(folder, folders) { folder = it }
                GroupedSection(footer = "Priority feeds get their own list in the menu. Full articles are fetched from the website when you open one, for feeds that only send a snippet.") {
                    AppleRow(
                        "Always load full article",
                        chevron = false,
                        onClick = { fullText = !fullText },
                        trailing = { AppleSwitch(fullText) { fullText = it } },
                    )
                    AppleRow(
                        "Priority",
                        leading = { Icon(AppIcons.Bolt, null, tint = apple.tint, modifier = Modifier.size(24.dp)) },
                        chevron = false,
                        showDivider = false,
                        onClick = { priority = !priority },
                        trailing = { AppleSwitch(priority) { priority = it } },
                    )
                }
            }
        }
    }
}
