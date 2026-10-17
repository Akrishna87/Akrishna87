package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.OutlinedTextField
import androidx.compose.foundation.layout.height
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.rssreader.data.Article
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.hostOf

/** Every subscription in one list, each with a delete button. Tap a row to open that feed. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ManageFeedsScreen(
    feeds: List<Feed>,
    articles: List<Article>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onRemove: (String) -> Unit,
    onEdit: (id: String, name: String, folder: String) -> Unit,
    onOpen: (String) -> Unit,
) {
    var confirm by remember { mutableStateOf<Feed?>(null) }
    var editing by remember { mutableStateOf<Feed?>(null) }
    val folders = remember(feeds) { feeds.map { it.folder }.filter { it.isNotEmpty() }.distinct().sorted() }
    val counts = remember(articles) { articles.groupingBy { it.feedId }.eachCount() }
    val starred = remember(articles) { articles.filter { it.starred }.groupingBy { it.feedId }.eachCount() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Manage feeds (${feeds.size})") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            if (feeds.isEmpty()) {
                item(key = "empty") {
                    Text(
                        "No feeds yet. Add some with ➕ or from Discover feeds.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(feeds.sortedWith(compareBy<Feed>({ it.folder.isEmpty() }, { it.folder.lowercase() }, { it.name.lowercase() })), key = { it.id }) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(f.id) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FeedBadge(f)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            (if (f.folder.isNotEmpty()) "📁 ${f.folder} · " else "") + hostOf(f.url) + " · ${counts[f.id] ?: 0} articles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (f.error != null) {
                            Text("⚠ ${f.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                        }
                    }
                    IconButton(onClick = { editing = f }) {
                        Icon(Icons.Filled.Edit, "Rename or move ${f.name}")
                    }
                    IconButton(onClick = { confirm = f }) {
                        Icon(Icons.Filled.Delete, "Unsubscribe from ${f.name}", tint = MaterialTheme.colorScheme.error)
                    }
                }
                HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }

    confirm?.let { feed ->
        val stars = starred[feed.id] ?: 0
        AlertDialog(
            onDismissRequest = { confirm = null },
            title = { Text("Unsubscribe?") },
            text = {
                Text(
                    "Remove “${feed.name}” and its saved articles" +
                        (if (stars > 0) ", including $stars starred article${if (stars == 1) "" else "s"}?" else "?"),
                )
            },
            confirmButton = { TextButton(onClick = { onRemove(feed.id); confirm = null }) { Text("Unsubscribe") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }

    editing?.let { feed ->
        var name by remember(feed.id) { mutableStateOf(feed.name) }
        var folder by remember(feed.id) { mutableStateOf(feed.folder) }
        AlertDialog(
            onDismissRequest = { editing = null },
            title = { Text("Edit feed") },
            text = {
                Column {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Name") }, singleLine = true)
                    Spacer(Modifier.height(12.dp))
                    FolderPicker(folder, folders) { folder = it }
                }
            },
            confirmButton = {
                TextButton(onClick = { onEdit(feed.id, name.ifBlank { feed.title }, folder); editing = null }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { editing = null }) { Text("Cancel") } },
        )
    }
}
