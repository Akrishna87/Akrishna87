@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.akrishna87.pakkam.ui

import android.graphics.Bitmap
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.PrimaryTabRow
import androidx.compose.material3.Tab
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.pakkam.pdf.AnnotInfo
import io.github.akrishna87.pakkam.pdf.OutlineItem

private const val THUMB_WIDTH = 240

@Composable
fun PanelsSheet(vm: AppViewModel, vs: ViewerState, onDismiss: () -> Unit, onDeleteAnnot: (AnnotInfo) -> Unit) {
    val session = vs.session
    val state = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val outline by produceState<List<OutlineItem>?>(null, session.version) { value = session.doc.outline() }
    var tab by rememberSaveable { mutableIntStateOf(-1) }
    // Open on the table of contents when the document has one, otherwise on the pages.
    LaunchedEffect(outline) {
        if (tab == -1 && outline != null) tab = if (outline!!.isNotEmpty()) 0 else 1
    }
    fun go(page: Int) {
        vs.goTo(page)
        onDismiss()
    }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = state) {
        Column(Modifier.fillMaxWidth().fillMaxHeight(0.85f)) {
            val tabs = listOf("Contents", "Pages", "Bookmarks", "Comments")
            PrimaryTabRow(selectedTabIndex = tab.coerceAtLeast(0)) {
                tabs.forEachIndexed { i, t -> Tab(selected = tab == i, onClick = { tab = i }, text = { Text(t) }) }
            }
            when (tab) {
                0 -> {
                    val items = outline.orEmpty()
                    if (items.isEmpty()) {
                        Empty("This document has no table of contents.")
                    } else {
                        LazyColumn {
                            items(items) { o ->
                                ListItem(
                                    modifier = Modifier.clickable { go(o.page) }.padding(start = (o.depth * 16).dp),
                                    headlineContent = { Text(o.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                                    trailingContent = { Text("${o.page + 1}", color = MaterialTheme.colorScheme.onSurfaceVariant) },
                                )
                            }
                        }
                    }
                }
                1 -> {
                    val grid = rememberLazyGridState(initialFirstVisibleItemIndex = vs.currentPage)
                    LazyVerticalGrid(
                        columns = GridCells.Adaptive(104.dp),
                        state = grid,
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(12.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        items(session.doc.pageCount) { i ->
                            Column(Modifier.clickable { go(i) }, horizontalAlignment = Alignment.CenterHorizontally) {
                                Thumbnail(session, i, selected = i == vs.currentPage)
                                Text("${i + 1}", style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(top = 4.dp))
                            }
                        }
                    }
                }
                2 -> {
                    val marks = vm.bookmarks()
                    if (marks.isEmpty()) {
                        Empty("No bookmarks yet. Tap the bookmark at the top while reading to mark a page.")
                    } else {
                        LazyColumn {
                            items(marks) { p ->
                                ListItem(
                                    modifier = Modifier.clickable { go(p) },
                                    leadingContent = { Icon(Icons.Filled.Bookmark, null, tint = MaterialTheme.colorScheme.primary) },
                                    headlineContent = { Text("Page ${p + 1}") },
                                    trailingContent = { IconButton(onClick = { vm.toggleBookmark(p) }) { Icon(Icons.Filled.Delete, "Remove bookmark") } },
                                )
                            }
                        }
                    }
                }
                3 -> {
                    val list = session.annotations
                    if (list.isEmpty()) {
                        Empty("No comments or marks in this document.")
                    } else {
                        LazyColumn {
                            items(list) { a ->
                                ListItem(
                                    modifier = Modifier.clickable { go(a.page) },
                                    leadingContent = {
                                        Box(Modifier.padding(4.dp).aspectRatio(1f).fillMaxHeight(0.5f).background(a.color?.let { Color(it) } ?: Color.Gray, MaterialTheme.shapes.small))
                                    },
                                    overlineContent = { Text("Page ${a.page + 1} · ${a.label}${if (a.author.isNotBlank()) " · ${a.author}" else ""}") },
                                    headlineContent = { Text(a.contents.ifBlank { a.label }, maxLines = 3, overflow = TextOverflow.Ellipsis) },
                                    trailingContent = { IconButton(onClick = { onDeleteAnnot(a) }) { Icon(Icons.Filled.Delete, "Delete") } },
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun Empty(text: String) {
    Text(text, Modifier.fillMaxWidth().padding(32.dp), color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** A small picture of a page, cached with the document's other renderings. */
@Composable
fun Thumbnail(session: DocSession, page: Int, selected: Boolean = false, rotation: Int = 0, modifier: Modifier = Modifier) {
    val size = session.doc.pageSizes.getOrNull(page)
    val aspect = size?.aspect ?: 0.7f
    var bmp by remember(page, session.version) { mutableStateOf<Bitmap?>(session.bitmaps.get(page, THUMB_WIDTH)) }
    LaunchedEffect(page, session.version) {
        if (bmp != null) return@LaunchedEffect
        val h = (THUMB_WIDTH / aspect).toInt().coerceAtLeast(1)
        session.doc.render(page, THUMB_WIDTH, h)?.let {
            session.bitmaps.put(page, THUMB_WIDTH, it)
            bmp = it
        }
    }
    val turned = rotation % 180 != 0
    androidx.compose.foundation.layout.BoxWithConstraints(
        modifier
            .fillMaxWidth()
            .aspectRatio(if (turned) 1f / aspect else aspect)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val boxW = maxWidth
        val boxH = maxHeight
        bmp?.let { b ->
            val img = remember(b) { b.asImageBitmap() }
            Image(
                img,
                contentDescription = "Page ${page + 1}",
                contentScale = ContentScale.FillBounds,
                // A turned page is drawn upright at the swapped size, then turned into place.
                modifier = Modifier
                    .requiredSize(if (turned) boxH else boxW, if (turned) boxW else boxH)
                    .rotate(rotation.toFloat()),
            )
        }
    }
}
