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
    onOpen: (String) -> Unit,
) {
    var confirm by remember { mutableStateOf<Feed?>(null) }
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
            items(feeds.sortedBy { it.title.lowercase() }, key = { it.id }) { f ->
                Row(
                    Modifier.fillMaxWidth().clickable { onOpen(f.id) }.padding(start = 16.dp, top = 10.dp, bottom = 10.dp, end = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FeedBadge(f)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(f.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            hostOf(f.url) + " · ${counts[f.id] ?: 0} articles",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        if (f.error != null) {
                            Text("⚠ ${f.error}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.error, maxLines = 2)
                        }
                    }
                    IconButton(onClick = { confirm = f }) {
                        Icon(Icons.Filled.Delete, "Unsubscribe from ${f.title}", tint = MaterialTheme.colorScheme.error)
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
                    "Remove “${feed.title}” and its saved articles" +
                        (if (stars > 0) ", including $stars starred article${if (stars == 1) "" else "s"}?" else "?"),
                )
            },
            confirmButton = { TextButton(onClick = { onRemove(feed.id); confirm = null }) { Text("Unsubscribe") } },
            dismissButton = { TextButton(onClick = { confirm = null }) { Text("Cancel") } },
        )
    }
}
