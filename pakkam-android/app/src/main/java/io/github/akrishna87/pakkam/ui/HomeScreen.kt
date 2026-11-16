@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.akrishna87.pakkam.ui

import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.BrandingWatermark
import androidx.compose.material.icons.filled.CallSplit
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.FormatListNumbered
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Button
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.pakkam.data.Files
import io.github.akrishna87.pakkam.data.RecentFile

private data class HomeTool(val label: String, val icon: ImageVector, val run: () -> Unit)
private data class HomeToolGroup(val title: String, val description: String, val tools: List<HomeTool>)

@Composable
fun HomeScreen(vm: AppViewModel) {
    // Tools that work on one PDF ask for it first.
    var pendingTool by remember { mutableStateOf<ToolKind?>(null) }
    var pendingSign by remember { mutableStateOf(false) }
    val openPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri != null) {
            if (pendingSign) vm.startMode = ViewerMode.FILL_SIGN
            vm.open(uri, pendingTool, picked = true)
        }
        pendingTool = null
        pendingSign = false
    }
    fun pick(tool: ToolKind? = null, sign: Boolean = false) {
        pendingTool = tool
        pendingSign = sign
        openPdf.launch(arrayOf("application/pdf"))
    }

    val toolGroups = listOf(
        HomeToolGroup("Create PDFs", "Scan, combine, or turn photos into a PDF", listOf(
            HomeTool("Scan", Icons.Filled.PhotoCamera) { vm.go(Screen.Scan) },
            HomeTool("Photos to PDF", Icons.Filled.Image) { vm.go(Screen.Images(camera = false)) },
            HomeTool("Combine files", Icons.Filled.MergeType) { vm.go(Screen.Merge) },
        )),
        HomeToolGroup("Fill & sign", "Add a signature or complete a PDF form", listOf(
            HomeTool("Fill & Sign", Icons.Filled.Draw) { pick(sign = true) },
            HomeTool("Fill in form", Icons.Filled.EditNote) { pick(ToolKind.FILL_FORM) },
        )),
        HomeToolGroup("Organize & export", "Change pages, protect, compress, or export", listOf(
            HomeTool("Organize pages", Icons.Filled.ViewModule) { pick(ToolKind.ORGANIZE) },
            HomeTool("Compress", Icons.Filled.Compress) { pick(ToolKind.COMPRESS) },
            HomeTool("Protect", Icons.Filled.Lock) { pick(ToolKind.PROTECT) },
            HomeTool("Split", Icons.Filled.CallSplit) { pick(ToolKind.SPLIT) },
            HomeTool("Export", Icons.Filled.FileDownload) { pick(ToolKind.EXPORT) },
            HomeTool("Watermark", Icons.Filled.BrandingWatermark) { pick(ToolKind.WATERMARK) },
            HomeTool("Page numbers", Icons.Filled.FormatListNumbered) { pick(ToolKind.PAGE_NUMBERS) },
        )),
    )

    var starredOnly by rememberSaveable { mutableStateOf(false) }
    // Finding a document by its name, typed or spoken.
    var searching by rememberSaveable { mutableStateOf(false) }
    var find by rememberSaveable { mutableStateOf("") }
    val listen = rememberVoiceInput("Say the document's name") { heard ->
        searching = true
        find = heard
    }
    val scroll = TopAppBarDefaults.exitUntilCollapsedScrollBehavior()
    Scaffold(
        modifier = Modifier.nestedScroll(scroll.nestedScrollConnection),
        topBar = {
            LargeTopAppBar(
                title = {
                    Column {
                        Text("Pakkam", fontWeight = FontWeight.SemiBold)
                        Text("பக்கம் · PDF reader and editor", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                },
                scrollBehavior = scroll,
            )
        },
    ) { pad ->
        val words = find.trim().lowercase().split(Regex("\\s+")).filter { it.isNotEmpty() }
        val shown = vm.recents.filter { f ->
            val name = f.name.lowercase()
            (!starredOnly || f.starred) && (!searching || words.all { name.contains(it) })
        }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                Row(
                    Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    Button(onClick = { pick() }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Icon(Icons.Filled.FolderOpen, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Open PDF")
                    }
                    FilledTonalButton(onClick = { vm.go(Screen.Scan) }, modifier = Modifier.weight(1f).heightIn(min = 56.dp)) {
                        Icon(Icons.Filled.PhotoCamera, null)
                        Spacer(Modifier.width(8.dp))
                        Text("Scan")
                    }
                }
            }
            item {
                Column(Modifier.fillMaxWidth().padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 4.dp)) {
                    Text("Recent documents", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                    Text("Pick up where you left off", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 8.dp, end = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {
                        searching = !searching
                        if (!searching) find = ""
                    }) { Icon(if (searching) Icons.Filled.Close else Icons.Filled.Search, if (searching) "Close search" else "Search documents") }
                    FilterChip(selected = !starredOnly, onClick = { starredOnly = false }, label = { Text("All") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = starredOnly, onClick = { starredOnly = true }, label = { Text("Starred") })
                }
            }
            if (searching) {
                item {
                    OutlinedTextField(
                        value = find,
                        onValueChange = { find = it },
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        placeholder = { Text("Document name") },
                        singleLine = true,
                        leadingIcon = { Icon(Icons.Filled.Search, null) },
                        trailingIcon = if (listen != null) {
                        { IconButton(onClick = listen) { Icon(Icons.Filled.Mic, "Search by voice") } }
                    } else {
                        null
                    },
                    )
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (searching) "No recent PDF matches “${find.trim()}”. Open a PDF above or choose “Open with Pakkam” in another app." else if (starredOnly) "Star a file from its menu to keep it here." else "PDFs you open will appear here. Open one above, or choose “Open with Pakkam” in Files, Gmail or WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(shown, key = { it.uri }) { f -> RecentRow(vm, f) }
            item {
                Column(Modifier.fillMaxWidth().padding(top = 20.dp, bottom = 24.dp)) {
                    Text("All tools", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(horizontal = 16.dp))
                    Text("Every PDF tool, ready when you need it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp))
                    toolGroups.forEach { group ->
                        Column(Modifier.fillMaxWidth().padding(top = 14.dp)) {
                            Text(group.title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Medium, modifier = Modifier.padding(horizontal = 16.dp))
                            Text(group.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(horizontal = 16.dp, vertical = 2.dp))
                            FlowRow(
                                Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp),
                                horizontalArrangement = Arrangement.spacedBy(8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp),
                                maxItemsInEachRow = 3,
                            ) {
                                group.tools.forEach { ToolTile(it, Modifier.weight(1f)) }
                            }
                        }
                    }
                }
            }
            item { Spacer(Modifier.height(16.dp)) }
        }
    }
}

@Composable
private fun ToolTile(t: HomeTool, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = t.run)
            .background(MaterialTheme.colorScheme.surfaceContainerLow, RoundedCornerShape(16.dp))
            .padding(vertical = 12.dp, horizontal = 4.dp)
            .heightIn(min = 96.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(Modifier.size(40.dp).background(MaterialTheme.colorScheme.primaryContainer, RoundedCornerShape(12.dp)), contentAlignment = Alignment.Center) {
            Icon(t.icon, null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
        }
        Spacer(Modifier.height(6.dp))
        Text(t.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2, minLines = 2)
    }
}

@Composable
private fun RecentRow(vm: AppViewModel, f: RecentFile) {
    var menu by remember { mutableStateOf(false) }
    val details = buildList {
        if (f.pages > 0) add(if (f.pages == 1) "1 page" else "${f.pages} pages")
        if (f.size > 0) add(Files.humanSize(f.size))
        if (f.opened > 0) add(DateUtils.getRelativeTimeSpanString(f.opened, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS).toString())
    }.joinToString(" · ")
    ListItem(
        modifier = Modifier.clickable { vm.open(Uri.parse(f.uri)) },
        leadingContent = {
            Surface(shape = RoundedCornerShape(10.dp), color = MaterialTheme.colorScheme.primaryContainer) {
                Icon(Icons.Filled.PictureAsPdf, null, Modifier.padding(10.dp), tint = MaterialTheme.colorScheme.primary)
            }
        },
        headlineContent = { Text(f.name, maxLines = 1, overflow = TextOverflow.Ellipsis) },
        supportingContent = {
            Text(
                details + if (f.lastPage > 0) "\nStopped at page ${f.lastPage + 1}" else "",
                maxLines = 2,
            )
        },
        trailingContent = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                if (f.starred) Icon(Icons.Filled.Star, "Starred", tint = Color(0xFFF9A825), modifier = Modifier.size(20.dp))
                Box {
                    IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More for ${f.name}") }
                    DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                        DropdownMenuItem(
                            text = { Text(if (f.starred) "Remove star" else "Star") },
                            leadingIcon = { Icon(if (f.starred) Icons.Filled.StarBorder else Icons.Filled.Star, null) },
                            onClick = {
                                menu = false
                                vm.recentsStore.update(f.uri) { it.copy(starred = !it.starred) }
                                vm.refreshRecents()
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Remove from list") },
                            onClick = {
                                menu = false
                                vm.recentsStore.remove(f.uri)
                                Files.forget(vm.context, Uri.parse(f.uri))
                                vm.refreshRecents()
                            },
                        )
                    }
                }
            }
        },
    )
}
