package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.akrishna87.rssreader.data.Directory
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.data.stableId
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DiscoverScreen(
    feeds: List<Feed>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: suspend (String) -> String?,
) {
    val scope = rememberCoroutineScope()
    var topic by rememberSaveable { mutableStateOf(Directory.topics.first().name) }
    var query by rememberSaveable { mutableStateOf("") }
    val busy = remember { mutableStateListOf<String>() }
    val subscribed = remember(feeds) { feeds.map { it.id }.toHashSet() }

    fun add(url: String, after: () -> Unit = {}) {
        if (url in busy) return
        busy.add(url)
        scope.launch {
            val err = onAdd(url)
            busy.remove(url)
            if (err != null) snackbar.showSnackbar(err) else after()
        }
    }

    val q = query.trim().lowercase()
    val rows = remember(q, topic) {
        Directory.topics.flatMap { t ->
            t.feeds.filter { e ->
                if (q.isEmpty()) t.name == topic
                else "${e.name} ${e.description} ${t.name}".lowercase().contains(q)
            }.map { t.name to it }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Discover feeds") },
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
            )
        },
        snackbarHost = { SnackbarHost(snackbar) },
    ) { padding ->
        LazyColumn(Modifier.padding(padding).fillMaxSize(), contentPadding = PaddingValues(bottom = 24.dp)) {
            item(key = "quick") {
                QuickAdd(busy = busy.isNotEmpty(), onSubmit = { url, clear -> add(url, clear) })
                HorizontalDivider()
            }
            item(key = "search") {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search the directory") },
                    leadingIcon = { Icon(Icons.Filled.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp),
                )
            }
            if (q.isEmpty()) {
                item(key = "topics") {
                    LazyRow(
                        contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        items(Directory.topics, key = { it.name }) { t ->
                            FilterChip(selected = t.name == topic, onClick = { topic = t.name }, label = { Text(t.name) })
                        }
                    }
                }
            } else {
                item(key = "space") { Spacer(Modifier.height(8.dp)) }
            }
            if (rows.isEmpty()) {
                item(key = "none") {
                    Text(
                        "No feeds match “${query.trim()}”. You can add any site with ➕ Add feed.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp),
                    )
                }
            }
            items(rows, key = { it.second.url }) { (topicName, entry) ->
                val added = stableId(entry.url) in subscribed
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    FeedBadge(Feed(stableId(entry.url), entry.url, entry.name))
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(entry.name, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                        Text(
                            entry.description + if (q.isNotEmpty()) " · $topicName" else "",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    Spacer(Modifier.width(8.dp))
                    when {
                        added -> OutlinedButton(onClick = {}, enabled = false) {
                            Icon(Icons.Filled.Check, null, Modifier.size(16.dp)); Spacer(Modifier.width(4.dp)); Text("Added")
                        }
                        entry.url in busy -> Box(Modifier.width(88.dp), contentAlignment = Alignment.Center) {
                            CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                        }
                        else -> FilledTonalButton(onClick = { add(entry.url) }) { Text("+ Add") }
                    }
                }
                HorizontalDivider(Modifier.padding(start = 52.dp), color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.5f))
            }
        }
    }
}

@Composable
private fun QuickAdd(busy: Boolean, onSubmit: (url: String, clear: () -> Unit) -> Unit) {
    var source by rememberSaveable { mutableStateOf(Directory.Source.YOUTUBE) }
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    fun submit() {
        val url = Directory.feedUrlFor(source, input)
        if (url == null) { error = "That doesn't look right. Try the example in the box."; return }
        error = null
        onSubmit(url) { input = "" }
    }

    Column(Modifier.padding(16.dp)) {
        Text("Add from a site", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { menu = true }) {
                Text(source.label)
                Icon(Icons.Filled.ArrowDropDown, null)
            }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Directory.Source.entries.forEach { s ->
                    DropdownMenuItem(text = { Text(s.label) }, onClick = { source = s; error = null; menu = false })
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            OutlinedTextField(
                value = input,
                onValueChange = { input = it; error = null },
                placeholder = { Text(source.placeholder) },
                singleLine = true,
                isError = error != null,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.weight(1f),
            )
            Spacer(Modifier.width(8.dp))
            Button(onClick = { submit() }, enabled = input.isNotBlank() && !busy) { Text("Add") }
        }
        Spacer(Modifier.height(6.dp))
        Text(
            error ?: source.hint,
            style = MaterialTheme.typography.bodySmall,
            color = if (error != null) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
