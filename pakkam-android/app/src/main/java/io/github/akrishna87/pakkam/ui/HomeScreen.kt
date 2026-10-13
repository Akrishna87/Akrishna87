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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LargeTopAppBar
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
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

private data class HomeTool(val label: String, val icon: ImageVector, val tint: Color, val run: () -> Unit)

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

    val tools = listOf(
        HomeTool("Combine files", Icons.Filled.MergeType, Color(0xFF1565C0)) { vm.go(Screen.Merge) },
        HomeTool("Photos to PDF", Icons.Filled.Image, Color(0xFF2E7D32)) { vm.go(Screen.Images(camera = false)) },
        HomeTool("Scan", Icons.Filled.PhotoCamera, Color(0xFF00838F)) { vm.go(Screen.Images(camera = true)) },
        HomeTool("Fill & Sign", Icons.Filled.Draw, Color(0xFF6A1B9A)) { pick(sign = true) },
        HomeTool("Organize pages", Icons.Filled.ViewModule, Color(0xFFEF6C00)) { pick(ToolKind.ORGANIZE) },
        HomeTool("Compress", Icons.Filled.Compress, Color(0xFF5D4037)) { pick(ToolKind.COMPRESS) },
        HomeTool("Protect", Icons.Filled.Lock, Color(0xFFC62828)) { pick(ToolKind.PROTECT) },
        HomeTool("Split", Icons.Filled.CallSplit, Color(0xFF283593)) { pick(ToolKind.SPLIT) },
        HomeTool("Export", Icons.Filled.FileDownload, Color(0xFF00695C)) { pick(ToolKind.EXPORT) },
        HomeTool("Watermark", Icons.Filled.BrandingWatermark, Color(0xFFAD1457)) { pick(ToolKind.WATERMARK) },
        HomeTool("Page numbers", Icons.Filled.FormatListNumbered, Color(0xFF4527A0)) { pick(ToolKind.PAGE_NUMBERS) },
        HomeTool("Fill in form", Icons.Filled.EditNote, Color(0xFF558B2F)) { pick(ToolKind.FILL_FORM) },
    )

    var starredOnly by rememberSaveable { mutableStateOf(false) }
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
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { pick() },
                icon = { Icon(Icons.Filled.FolderOpen, null) },
                text = { Text("Open a PDF") },
            )
        },
    ) { pad ->
        val shown = vm.recents.filter { !starredOnly || it.starred }
        LazyColumn(Modifier.fillMaxSize(), contentPadding = pad) {
            item {
                Text("Tools", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(start = 16.dp, top = 8.dp, bottom = 8.dp))
                FlowRow(
                    Modifier.fillMaxWidth().padding(horizontal = 8.dp),
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    verticalArrangement = Arrangement.spacedBy(4.dp),
                    maxItemsInEachRow = 4,
                ) {
                    tools.forEach { ToolTile(it, Modifier.weight(1f)) }
                }
            }
            item {
                Row(Modifier.fillMaxWidth().padding(start = 16.dp, end = 8.dp, top = 20.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Recent", style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
                    FilterChip(selected = !starredOnly, onClick = { starredOnly = false }, label = { Text("All") })
                    Spacer(Modifier.width(8.dp))
                    FilterChip(selected = starredOnly, onClick = { starredOnly = true }, label = { Text("Starred") })
                }
            }
            if (shown.isEmpty()) {
                item {
                    Text(
                        if (starredOnly) "Star a file from its menu to keep it here." else "PDFs you open will be listed here.\nOpen one with the button below, or choose “Open with Pakkam” in Files, Gmail or WhatsApp.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(32.dp),
                    )
                }
            }
            items(shown, key = { it.uri }) { f -> RecentRow(vm, f) }
            item { Spacer(Modifier.height(96.dp)) }
        }
    }
}

@Composable
private fun ToolTile(t: HomeTool, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .clickable(onClick = t.run)
            .padding(vertical = 10.dp, horizontal = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(Modifier.size(48.dp).background(t.tint.copy(alpha = 0.14f), RoundedCornerShape(14.dp)), contentAlignment = Alignment.Center) {
            Icon(t.icon, null, tint = t.tint)
        }
        Spacer(Modifier.height(6.dp))
        Text(t.label, style = MaterialTheme.typography.labelMedium, textAlign = TextAlign.Center, maxLines = 2)
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
