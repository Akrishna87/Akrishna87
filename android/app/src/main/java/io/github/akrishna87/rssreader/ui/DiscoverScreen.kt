package io.github.akrishna87.rssreader.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.rssreader.data.Directory
import io.github.akrishna87.rssreader.data.Feed
import io.github.akrishna87.rssreader.data.Net
import io.github.akrishna87.rssreader.data.ParsedFeed
import io.github.akrishna87.rssreader.data.stableId
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val TopicColors = listOf(0xFF2BB24C, 0xFF3478F6, 0xFFE8590C, 0xFF8E44AD, 0xFFD6336C, 0xFF0CA678, 0xFFF08C00, 0xFF495057)

private class Suggestion(val name: String, val url: String, val description: String, val topic: String)

/** Feedly-style Discover: search anything, browse topics, preview a feed, then Follow. */
@Composable
fun DiscoverScreen(
    feeds: List<Feed>,
    snackbar: SnackbarHostState,
    onBack: () -> Unit,
    onAdd: suspend (url: String, folder: String) -> String?,
) {
    val apple = LocalApple.current
    val scope = rememberCoroutineScope()
    var topic by rememberSaveable { mutableStateOf<String?>(null) }
    var query by rememberSaveable { mutableStateOf("") }
    var previewing by remember { mutableStateOf<Suggestion?>(null) }
    val busy = remember { mutableStateListOf<String>() }
    val subscribed = remember(feeds) { feeds.map { it.id }.toHashSet() }

    BackHandler(enabled = topic != null || query.isNotEmpty()) {
        if (query.isNotEmpty()) query = "" else topic = null
    }

    /** Follow [url]; feeds from the directory go into a folder named after their topic. */
    fun follow(url: String, folder: String = "", after: () -> Unit = {}) {
        if (url in busy) return
        busy.add(url)
        scope.launch {
            val err = onAdd(url, folder)
            busy.remove(url)
            if (err != null) snackbar.showSnackbar(err) else after()
        }
    }

    val q = query.trim()
    val all = remember { Directory.topics.flatMap { t -> t.feeds.map { Suggestion(it.name, it.url, it.description, t.name) } } }
    val results = remember(q) {
        if (q.isEmpty()) emptyList() else all.filter { "${it.name} ${it.description} ${it.topic}".contains(q, ignoreCase = true) }
    }
    val looksLikeAddress = q.isNotEmpty() && ' ' !in q && '.' in q

    @Composable
    fun card(s: Suggestion) {
        FeedCard(
            s,
            following = stableId(s.url) in subscribed,
            busy = s.url in busy,
            onOpen = { previewing = s },
            onFollow = { follow(s.url, folder = s.topic) },
        )
    }

    val title = topic ?: "Discover"
    AppleScreen(
        title = title,
        snackbar = snackbar,
        backLabel = if (topic != null) "Discover" else "Back",
        onBack = { if (topic != null) topic = null else onBack() },
        grouped = false,
        belowTitle = {
            item(key = "search") {
                SearchField(query, { query = it }, "Search topics, sites or paste a feed link", Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
            }
        },
    ) {
        when {
            q.isNotEmpty() -> {
                if (looksLikeAddress) {
                    item(key = "address") {
                        ActionCard(
                            "Follow “$q”", "Add this website or feed link",
                            following = false, busy = q in busy,
                        ) { follow(q, after = { query = "" }) }
                    }
                }
                item(key = "news") {
                    val url = Directory.feedUrlFor(Directory.Source.NEWS, q).orEmpty()
                    ActionCard(
                        "News about “$q”", "Follow articles from news sites across the web",
                        following = url.isNotEmpty() && stableId(url) in subscribed, busy = url in busy,
                    ) { follow(url, after = { query = "" }) }
                }
                if (results.isNotEmpty()) item(key = "rh") { SectionTitle("From our directory") }
                items(results.size, key = { "r-" + results[it].url }) { card(results[it]) }
            }
            topic != null -> {
                val list = all.filter { it.topic == topic }
                items(list.size, key = { "t-" + list[it].url }) { card(list[it]) }
            }
            else -> {
                item(key = "topics-h") { SectionTitle("Explore topics") }
                val rows = Directory.topics.chunked(2)
                items(rows.size, key = { "tr-$it" }) { r ->
                    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 5.dp), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        rows[r].forEach { t ->
                            val index = Directory.topics.indexOf(t)
                            TopicTile(t.name, t.feeds.size, Color(TopicColors[index % TopicColors.size]), Modifier.weight(1f)) { topic = t.name }
                        }
                        if (rows[r].size == 1) Spacer(Modifier.weight(1f))
                    }
                }
                item(key = "featured-h") { SectionTitle("Popular feeds") }
                val featured = Directory.topics.flatMap { t -> t.feeds.take(2).map { Suggestion(it.name, it.url, it.description, t.name) } }
                items(featured.size, key = { "f-" + featured[it].url }) { card(featured[it]) }
                item(key = "quick") { QuickAdd(busy = busy.isNotEmpty()) { url, clear -> follow(url, after = clear) } }
            }
        }
    }

    previewing?.let { s ->
        FeedPreviewSheet(
            s,
            following = stableId(s.url) in subscribed,
            busy = s.url in busy,
            onFollow = { follow(s.url, folder = s.topic, after = { previewing = null }) },
            onDismiss = { previewing = null },
        )
    }
}

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        style = AppleType.title3,
        color = LocalApple.current.label,
        modifier = Modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 6.dp),
    )
}

@Composable
private fun TopicTile(name: String, count: Int, color: Color, modifier: Modifier, onClick: () -> Unit) {
    Column(
        modifier.height(84.dp).clip(RoundedCornerShape(12.dp)).background(color).clickable(onClick = onClick).padding(14.dp),
        verticalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(name, style = AppleType.headline.copy(fontWeight = FontWeight.Bold), color = Color.White, maxLines = 2, overflow = TextOverflow.Ellipsis)
        Text("$count feeds", style = AppleType.footnote, color = Color.White.copy(alpha = 0.85f))
    }
}

@Composable
private fun FollowButton(following: Boolean, busy: Boolean, onClick: () -> Unit) {
    val apple = LocalApple.current
    Box(
        Modifier.width(92.dp).height(34.dp).clip(RoundedCornerShape(8.dp))
            .then(if (following) Modifier.background(apple.tint) else Modifier.border(1.5.dp, apple.tint, RoundedCornerShape(8.dp)))
            .clickable(enabled = !following && !busy, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        when {
            busy -> CircularProgressIndicator(Modifier.size(18.dp), color = apple.tint, strokeWidth = 2.dp)
            following -> Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                Icon(AppIcons.Checkmark, null, tint = Color.White, modifier = Modifier.size(16.dp))
                Text("Following", style = AppleType.footnote.copy(fontWeight = FontWeight.Bold), color = Color.White)
            }
            else -> Text("+ Follow", style = AppleType.subheadline.copy(fontWeight = FontWeight.Bold), color = apple.tint)
        }
    }
}

@Composable
private fun FeedCard(s: Suggestion, following: Boolean, busy: Boolean, onOpen: () -> Unit, onFollow: () -> Unit) {
    val apple = LocalApple.current
    Column(Modifier.fillMaxWidth().clickable(onClick = onOpen)) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            FeedBadge(Feed(stableId(s.url), s.url, s.name), size = 48.dp)
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, style = AppleType.headline, color = apple.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(s.description, style = AppleType.subheadline, color = apple.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(s.topic.uppercase(), style = AppleType.caption2.copy(fontWeight = FontWeight.Bold), color = apple.tint, modifier = Modifier.padding(top = 3.dp))
            }
            Spacer(Modifier.width(12.dp))
            FollowButton(following, busy, onFollow)
        }
        Hairline(start = 78.dp)
    }
}

/** A row for "follow this address" and "follow news about this search". */
@Composable
private fun ActionCard(title: String, subtitle: String, following: Boolean, busy: Boolean, onFollow: () -> Unit) {
    val apple = LocalApple.current
    Column(Modifier.fillMaxWidth()) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(48.dp).clip(RoundedCornerShape(12.dp)).background(apple.tint.copy(alpha = 0.14f)), contentAlignment = Alignment.Center) {
                Icon(AppIcons.Magnifier, null, tint = apple.tint, modifier = Modifier.size(24.dp))
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, style = AppleType.headline, color = apple.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                Text(subtitle, style = AppleType.subheadline, color = apple.secondaryLabel, maxLines = 2)
            }
            Spacer(Modifier.width(12.dp))
            FollowButton(following, busy, onFollow)
        }
        Hairline(start = 78.dp)
    }
}

/** Shows the latest headlines of a feed before you follow it. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FeedPreviewSheet(s: Suggestion, following: Boolean, busy: Boolean, onFollow: () -> Unit, onDismiss: () -> Unit) {
    val apple = LocalApple.current
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var preview by remember(s.url) { mutableStateOf<ParsedFeed?>(null) }
    var failed by remember(s.url) { mutableStateOf(false) }
    LaunchedEffect(s.url) {
        preview = null
        failed = false
        runCatching { withContext(Dispatchers.IO) { Net.loadFeed(s.url).second } }
            .onSuccess { preview = it }
            .onFailure { failed = true }
    }
    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheet,
        containerColor = apple.background,
        shape = RoundedCornerShape(topStart = 16.dp, topEnd = 16.dp),
    ) {
        Column(Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 16.dp)) {
            Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                FeedBadge(Feed(stableId(s.url), s.url, s.name), size = 52.dp)
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = AppleType.title3, color = apple.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(s.description, style = AppleType.subheadline, color = apple.secondaryLabel, maxLines = 2, overflow = TextOverflow.Ellipsis)
                }
                Spacer(Modifier.width(12.dp))
                FollowButton(following, busy, onFollow)
            }
            Hairline(Modifier.padding(top = 8.dp))
            Text("LATEST", style = AppleType.caption1.copy(fontWeight = FontWeight.Bold), color = apple.secondaryLabel, modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp))
            when {
                failed -> Text("Couldn't load a preview, but you can still follow it.", style = AppleType.subheadline, color = apple.secondaryLabel, modifier = Modifier.padding(16.dp))
                preview == null -> Box(Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator(Modifier.size(24.dp), color = apple.tint, strokeWidth = 2.dp)
                }
                else -> preview?.items?.take(8)?.forEach { item ->
                    Column(Modifier.fillMaxWidth()) {
                        Column(Modifier.padding(horizontal = 16.dp, vertical = 10.dp)) {
                            Text(item.title, style = AppleType.body.copy(fontWeight = FontWeight.SemiBold), color = apple.label, maxLines = 2, overflow = TextOverflow.Ellipsis)
                            if (item.date > 0) Text(relativeTime(item.date), style = AppleType.footnote, color = apple.secondaryLabel)
                        }
                        Hairline(start = 16.dp)
                    }
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
