package io.github.akrishna87.lumen.ui

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.Alignment
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.width
import androidx.compose.material3.Icon
import androidx.compose.ui.unit.dp
import io.github.akrishna87.lumen.Format
import io.github.akrishna87.lumen.LibraryState
import io.github.akrishna87.lumen.MediaItem

private const val RESULT_COLUMNS = 4
private const val MAX_RESULT_ROWS = 60

@Composable
fun SearchScreen(
    state: LibraryState,
    favorites: Set<Long>,
    onAlbum: (AlbumRef) -> Unit,
    onOpen: (AlbumRef, MediaItem) -> Unit,
) {
    val apple = LocalApple.current
    var query by rememberSaveable { mutableStateOf("") }
    var focused by remember { mutableStateOf(false) }
    val list = rememberLazyListState()
    val scrolled by remember { derivedStateOf { list.firstVisibleItemIndex > 0 || list.firstVisibleItemScrollOffset > 60 } }

    val words = remember(query) { AlbumRef.searchWords(query) }
    val albums = remember(state, words) {
        if (words.isEmpty()) state.albums.take(8) else state.albums.filter { a -> words.all { a.name.contains(it, ignoreCase = true) || a.folder.contains(it, ignoreCase = true) } }
    }
    val months = remember(state, words) {
        val all = state.months.asReversed()
        if (words.isEmpty()) all.take(6) else all.filter { m -> words.all { m.title.contains(it, ignoreCase = true) } }
    }
    val results = remember(state, words) { if (words.isEmpty()) emptyList() else state.items.filter { it.matches(words) }.asReversed() }
    val resultsRef = AlbumRef.search(query)

    Column(Modifier.fillMaxSize().background(apple.background)) {
        NavBar("Search", showTitle = scrolled || focused, showBackground = scrolled)
        LazyColumn(Modifier.fillMaxSize(), state = list, contentPadding = PaddingValues(bottom = 24.dp)) {
            if (!focused) item(key = "title") { LargeTitle("Search") }
            item(key = "field") {
                SearchField(
                    query = query,
                    onQueryChange = { query = it },
                    placeholder = "Albums, Months, Files and More",
                    focused = focused,
                    onFocusChange = { focused = it },
                    modifier = Modifier.padding(bottom = 6.dp),
                )
            }
            if (words.isNotEmpty() && albums.isEmpty() && months.isEmpty() && results.isEmpty()) {
                item(key = "none") {
                    EmptyState(AppIcons.Search, "No Results", "There were no results for “${query.trim()}”. Try a new search.")
                }
            }
            if (albums.isNotEmpty()) {
                item(key = "albumsHeader") { Spacer(Modifier.height(10.dp)); SectionHeader("Albums") }
                items(albums, key = { "a:" + it.folder }) { album ->
                    SuggestionRow(album.cover, album.name, Format.counts(album.photoCount, album.videoCount)) {
                        onAlbum(AlbumRef.folder(album.folder, album.name))
                    }
                }
            }
            if (months.isNotEmpty()) {
                item(key = "monthsHeader") { Spacer(Modifier.height(10.dp)); SectionHeader(if (words.isEmpty()) "Recent Months" else "Months") }
                items(months, key = { "m:${it.year}-${it.month}" }) { month ->
                    SuggestionRow(month.items.last(), month.title, Format.counts(month.items.count { !it.isVideo }, month.items.count { it.isVideo })) {
                        onAlbum(AlbumRef.month(month.year, month.month, month.title))
                    }
                }
            }
            if (results.isNotEmpty()) {
                item(key = "photosHeader") {
                    Spacer(Modifier.height(10.dp))
                    SectionHeader("Photos", action = if (results.size > RESULT_COLUMNS * MAX_RESULT_ROWS) "See All" else null) { onAlbum(resultsRef) }
                    Text(
                        Format.plural(results.size, "Result"),
                        style = AppleType.footnote,
                        color = apple.secondaryLabel,
                        modifier = Modifier.padding(start = 16.dp, bottom = 8.dp),
                    )
                }
                items(results.chunked(RESULT_COLUMNS).take(MAX_RESULT_ROWS), key = { "r:" + it.first().id }) { row ->
                    Row(Modifier.fillMaxWidth().padding(bottom = 1.5.dp), horizontalArrangement = Arrangement.spacedBy(1.5.dp)) {
                        row.forEach { item ->
                            Thumb(
                                item = item,
                                favorite = item.id in favorites,
                                selecting = false,
                                selected = false,
                                onClick = { onOpen(resultsRef, item) },
                                modifier = Modifier.weight(1f),
                            )
                        }
                        repeat(RESULT_COLUMNS - row.size) { Spacer(Modifier.weight(1f)) }
                    }
                }
            }
        }
    }
}

/** A suggestion: rounded thumbnail, name, grey count, chevron. */
@Composable
private fun SuggestionRow(cover: MediaItem, title: String, subtitle: String, onClick: () -> Unit) {
    val apple = LocalApple.current
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.size(52.dp).clip(RoundedCornerShape(6.dp)).background(apple.placeholder)) {
            ThumbImage(cover, Modifier.fillMaxSize())
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(title, style = AppleType.body, color = apple.label, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Text(subtitle, style = AppleType.footnote, color = apple.secondaryLabel, maxLines = 1)
        }
        Icon(AppIcons.ChevronRight, null, tint = apple.tertiaryLabel, modifier = Modifier.size(18.dp))
    }
}
