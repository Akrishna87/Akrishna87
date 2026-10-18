package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import io.github.akrishna87.rssreader.data.Directory
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.data.stableId
import kotlinx.coroutines.launch

@Composable
fun DiscoverScreen(
    feeds: List<Feed>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: suspend (url: String, folder: String) -> String?,
) {
    val apple = LocalApple.current
    val scope = rememberCoroutineScope()
    var topic by rememberSaveable { mutableStateOf(Directory.topics.first().name) }
    var query by rememberSaveable { mutableStateOf("") }
    var useFolders by rememberSaveable { mutableStateOf(true) }
    val busy = remember { mutableStateListOf<String>() }
    val subscribed = remember(feeds) { feeds.map { it.id }.toHashSet() }

    fun add(url: String, folder: String = "", after: () -> Unit = {}) {
        if (url in busy) return
        busy.add(url)
        scope.launch {
            val err = onAdd(url, folder)
            busy.remove(url)
            if (err != null) snackbar.showSnackbar(err) else after()
        }
    }

    val q = query.trim().lowercase()
    val rows = remember(q, topic) {
        Directory.topics.flatMap { t ->
            t.feeds.filter { e ->
                if (q.isEmpty()) t.name == topic else "${e.name} ${e.description} ${t.name}".lowercase().contains(q)
            }.map { t.name to it }
        }
    }

    AppleScreen(
        title = "Discover",
        snackbar = snackbar,
        backLabel = "Feeds",
        onBack = onBack,
        belowTitle = {
            item(key = "search") {
                SearchField(query, { query = it }, "Search feeds", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        },
    ) {
        item(key = "quick") { QuickAdd(busy = busy.isNotEmpty()) { url, clear -> add(url, after = clear) } }

        if (q.isEmpty()) {
            item(key = "topics") {
                LazyRow(
                    contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 22.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    items(Directory.topics, key = { it.name }) { t ->
                        val selected = t.name == topic
                        Text(
                            t.name,
                            style = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold),
                            color = if (selected) Color.White else apple.label,
                            modifier = Modifier.clip(RoundedCornerShape(50)).background(if (selected) apple.tint else apple.cell)
                                .clickable { topic = t.name }.padding(horizontal = 14.dp, vertical = 7.dp),
                        )
                    }
                }
            }
        }

        item(key = "list") {
            GroupedSection(header = if (q.isEmpty()) topic else "Results") {
                AppleRow(
                    "Put in topic folders",
                    chevron = false,
                    onClick = { useFolders = !useFolders },
                    trailing = { AppleSwitch(useFolders) { useFolders = it } },
                )
                if (rows.isEmpty()) {
                    Text(
                        "No feeds match “${query.trim()}”. You can add any site with +.",
                        style = AppleType.subheadline,
                        color = apple.secondaryLabel,
                        modifier = Modifier.padding(16.dp),
                    )
                }
                rows.forEachIndexed { i, (topicName, entry) ->
                    val added = stableId(entry.url) in subscribed
                    AppleRow(
                        entry.name,
                        subtitle = entry.description + if (q.isNotEmpty()) " · $topicName" else "",
                        leading = { FeedBadge(Feed(stableId(entry.url), entry.url, entry.name), size = 40.dp) },
                        chevron = false,
                        showDivider = i != rows.lastIndex,
                        dividerStart = 72.dp,
                        trailing = {
                            Box(Modifier.padding(start = 8.dp), contentAlignment = Alignment.Center) {
                                when {
                                    added -> Icon(AppIcons.Checkmark, "Added", tint = apple.green, modifier = Modifier.padding(horizontal = 18.dp).size(22.dp))
                                    entry.url in busy -> CircularProgressIndicator(Modifier.padding(horizontal = 22.dp).size(20.dp), color = apple.tint, strokeWidth = 2.dp)
                                    else -> CapsuleButton("Add") { add(entry.url, folder = if (useFolders) topicName else "") }
                                }
                            }
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun QuickAdd(busy: Boolean, onSubmit: (url: String, clear: () -> Unit) -> Unit) {
    val apple = LocalApple.current
    var source by rememberSaveable { mutableStateOf(Directory.Source.YOUTUBE) }
    var input by rememberSaveable { mutableStateOf("") }
    var error by remember { mutableStateOf<String?>(null) }
    var menu by remember { mutableStateOf(false) }

    fun submit() {
        val url = Directory.feedUrlFor(source, input)
        if (url == null) { error = "That doesn't look right. Try the example shown."; return }
        error = null
        onSubmit(url) { input = "" }
    }

    GroupedSection(header = "Add from a site", footer = error ?: source.hint) {
        Box {
            AppleRow("Source", value = source.label, onClick = { menu = true })
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                Directory.Source.entries.forEach { s ->
                    DropdownMenuItem(
                        text = { Text(s.label, style = AppleType.body) },
                        trailingIcon = { if (s == source) Icon(AppIcons.Checkmark, null, tint = apple.tint, modifier = Modifier.size(18.dp)) },
                        onClick = { source = s; error = null; menu = false },
                    )
                }
            }
        }
        Row(Modifier.fillMaxWidth().height(48.dp).padding(start = 16.dp, end = 8.dp), verticalAlignment = Alignment.CenterVertically) {
            AppleTextField(
                value = input,
                onValueChange = { input = it; error = null },
                placeholder = source.placeholder,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go, autoCorrectEnabled = false),
                keyboardActions = KeyboardActions(onGo = { submit() }),
                modifier = Modifier.weight(1f),
            )
            BarText("Add", bold = true, enabled = input.isNotBlank() && !busy) { submit() }
        }
    }
}
