@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.akrishna87.pakkam.ui

import android.graphics.Bitmap
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.requiredSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.NoteAdd
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.RotateLeft
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.documentfile.provider.DocumentFile
import io.github.akrishna87.pakkam.data.Files
import io.github.akrishna87.pakkam.pdf.Compression
import io.github.akrishna87.pakkam.pdf.Corner
import io.github.akrishna87.pakkam.pdf.FormField
import io.github.akrishna87.pakkam.pdf.PageEntry
import io.github.akrishna87.pakkam.pdf.PageSize
import io.github.akrishna87.pakkam.pdf.PdfDoc
import io.github.akrishna87.pakkam.pdf.PdfEditor
import io.github.akrishna87.pakkam.pdf.PdfTools
import io.github.akrishna87.pakkam.pdf.WrongPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

@Composable
fun ToolScreen(vm: AppViewModel, session: DocSession, tool: ToolKind) {
    when (tool) {
        ToolKind.ORGANIZE -> OrganizeTool(vm, session)
        ToolKind.COMPRESS -> CompressTool(vm, session)
        ToolKind.PROTECT -> ProtectTool(vm, session)
        ToolKind.SPLIT -> SplitTool(vm, session)
        ToolKind.EXPORT -> ExportTool(vm, session)
        ToolKind.WATERMARK -> WatermarkTool(vm, session)
        ToolKind.PAGE_NUMBERS -> PageNumbersTool(vm, session)
        ToolKind.FILL_FORM -> FillFormTool(vm, session)
        ToolKind.FLATTEN -> FlattenTool(vm, session)
        ToolKind.INFO -> InfoTool(vm, session)
    }
}

/** The frame every tool shares: a title bar, the tool's settings, and one big button at the bottom. */
@Composable
fun ToolFrame(
    title: String,
    subtitle: String?,
    onBack: () -> Unit,
    action: String?,
    actionEnabled: Boolean = true,
    onAction: () -> Unit = {},
    scrollable: Boolean = true,
    content: @Composable ColumnScope.() -> Unit,
) {
    Scaffold(
        topBar = {
            TopAppBar(
                navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
                title = {
                    Column {
                        Text(title)
                        if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                    }
                },
            )
        },
        bottomBar = {
            if (action != null) {
                Surface(tonalElevation = 3.dp) {
                    Button(
                        onClick = onAction,
                        enabled = actionEnabled,
                        modifier = Modifier.fillMaxWidth().navigationBarsPadding().padding(16.dp).height(52.dp),
                    ) { Text(action) }
                }
            }
        },
    ) { pad ->
        val m = Modifier.fillMaxSize().padding(pad)
        Column(
            (if (scrollable) m.verticalScroll(rememberScrollState()) else m).padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
            content = content,
        )
    }
}

@Composable
fun Hint(text: String) {
    Text(text, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

@Composable
fun SectionTitle(text: String) {
    Text(text, style = MaterialTheme.typography.titleSmall, modifier = Modifier.padding(top = 8.dp))
}

/** Runs a tool that rewrites the document, then goes back to it so the result can be checked and saved. */
private fun applyTool(vm: AppViewModel, session: DocSession, label: String, done: String, newPassword: String? = session.password, work: (src: File, out: File) -> Unit) {
    vm.task(label, {
        val out = Files.newWorkFile(vm.context)
        withContext(Dispatchers.IO) { work(session.workFile, out) }
        session.password = newPassword
        session.replaceWith(out)
    }) {
        vm.back()
        vm.say(done)
    }
}

// ---- Organize pages ----

private data class OrgPage(val entry: PageEntry, val id: Long)

@Composable
private fun OrganizeTool(vm: AppViewModel, session: DocSession) {
    val context = LocalContext.current
    var nextId by remember { mutableStateOf(0L) }
    fun id() = nextId++
    val pages = remember { mutableStateListOf<OrgPage>().apply { for (i in 0 until session.doc.pageCount) add(OrgPage(PageEntry(0, i), nextId++)) } }
    val selected = remember { mutableStateListOf<Long>() }
    // Other PDFs inserted here: file, password and an open copy for thumbnails.
    val extras = remember { mutableStateListOf<Pair<File, PdfDoc>>() }
    DisposableEffect(Unit) { onDispose { extras.forEach { it.second.close(); it.first.delete() } } }

    fun selIndexes() = pages.indices.filter { pages[it].id in selected }
    fun rotate(deg: Int) {
        for (i in selIndexes()) pages[i] = pages[i].copy(entry = pages[i].entry.copy(rotation = ((pages[i].entry.rotation + deg) % 360 + 360) % 360))
    }
    fun move(step: Int) {
        val idx = selIndexes()
        if (idx.isEmpty()) return
        if (step < 0 && idx.first() == 0) return
        if (step > 0 && idx.last() == pages.lastIndex) return
        val order = if (step < 0) idx else idx.reversed()
        for (i in order) {
            val p = pages.removeAt(i)
            pages.add(i + step, p)
        }
    }
    fun sizeOf(e: PageEntry): PageSize = when (e.source) {
        PageEntry.BLANK -> e.blankSize ?: PageSize(595.28f, 841.89f)
        0 -> session.doc.pageSizes[e.pageIndex]
        else -> extras[e.source - 1].second.pageSizes[e.pageIndex]
    }

    val insertPdf = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.task("Adding pages…", {
            val f = withContext(Dispatchers.IO) { Files.copyToWork(context, uri) }
            val d = try {
                PdfDoc.open(f, null)
            } catch (e: WrongPasswordException) {
                f.delete()
                throw java.io.IOException("That PDF has a password. Open it and remove the password first (Protect › Remove password).")
            }
            f to d
        }) { pair ->
            extras += pair
            val src = extras.size
            val at = (selIndexes().lastOrNull() ?: pages.lastIndex) + 1
            val added = (0 until pair.second.pageCount).map { OrgPage(PageEntry(src, it), id()) }
            pages.addAll(at, added)
            selected.clear()
            selected.addAll(added.map { it.id })
        }
    }
    val extract = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        val chosen = pages.filter { it.id in selected }.map { it.entry }
        vm.task("Saving the pages…", {
            withContext(Dispatchers.IO) {
                val out = Files.newWorkFile(context)
                PdfTools.organize(session.workFile, session.password, extras.map { it.first to null }, chosen, out)
                Files.writeTo(context, out, uri)
                out.delete()
            }
        }) { vm.say("Saved ${chosen.size} page${if (chosen.size == 1) "" else "s"} as a new PDF") }
    }

    val changed = pages.size != session.doc.pageCount || pages.withIndex().any { (i, p) -> p.entry != PageEntry(0, i) }
    ToolFrame(
        title = "Organize pages",
        subtitle = if (selected.isEmpty()) "${pages.size} pages · tap pages to select" else "${selected.size} selected",
        onBack = { vm.back() },
        action = "Apply changes",
        actionEnabled = changed && pages.isNotEmpty(),
        onAction = {
            val entries = pages.map { it.entry }
            applyTool(vm, session, "Rearranging pages…", "Pages rearranged. Save to keep the changes.") { src, out ->
                PdfTools.organize(src, session.password, extras.map { it.first to null }, entries, out)
            }
        },
        scrollable = false,
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            val any = selected.isNotEmpty()
            SmallAction("Turn left", Icons.Filled.RotateLeft, any) { rotate(270) }
            SmallAction("Turn right", Icons.Filled.RotateRight, any) { rotate(90) }
            SmallAction("Earlier", Icons.Filled.ChevronLeft, any) { move(-1) }
            SmallAction("Later", Icons.Filled.ChevronRight, any) { move(1) }
            SmallAction("Copy", Icons.Filled.ContentCopy, any) {
                val idx = selIndexes()
                val copies = idx.map { pages[it].copy(id = id()) }
                pages.addAll(idx.last() + 1, copies)
            }
            SmallAction("Delete", Icons.Filled.Delete, any && selected.size < pages.size) {
                pages.removeAll { it.id in selected }
                selected.clear()
            }
            SmallAction("Blank page", Icons.Filled.NoteAdd, true) {
                val at = (selIndexes().lastOrNull() ?: pages.lastIndex) + 1
                val near = pages.getOrNull(at - 1)?.entry?.let { sizeOf(it) } ?: PageSize(595.28f, 841.89f)
                pages.add(at, OrgPage(PageEntry(PageEntry.BLANK, 0, blankSize = near), id()))
            }
            SmallAction("Insert PDF", Icons.Filled.PostAdd, true) { insertPdf.launch(arrayOf("application/pdf")) }
            SmallAction("Extract", Icons.Filled.FileDownload, any) { extract.launch(Files.baseName(session.name) + " (pages).pdf") }
        }
        Row {
            androidx.compose.material3.TextButton(onClick = { selected.clear(); selected.addAll(pages.map { it.id }) }) { Text("Select all") }
            androidx.compose.material3.TextButton(onClick = { selected.clear() }, enabled = selected.isNotEmpty()) { Text("Clear selection") }
        }
        LazyVerticalGrid(
            columns = GridCells.Adaptive(100.dp),
            modifier = Modifier.fillMaxWidth().weight(1f),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            itemsIndexed(pages, key = { _, p -> p.id }) { i, p ->
                val sel = p.id in selected
                Column(
                    Modifier.clickable { if (sel) selected.remove(p.id) else selected.add(p.id) },
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Box {
                        when (p.entry.source) {
                            0 -> Thumbnail(session, p.entry.pageIndex, selected = sel, rotation = p.entry.rotation)
                            PageEntry.BLANK -> Box(
                                Modifier.fillMaxWidth().aspectRatio(sizeOf(p.entry).aspect)
                                    .border(if (sel) 3.dp else 1.dp, if (sel) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
                                    .background(Color.White),
                            )
                            else -> DocThumb(extras[p.entry.source - 1].second, p.entry.pageIndex, sel, p.entry.rotation)
                        }
                        if (sel) {
                            Icon(
                                Icons.Filled.CheckCircle, "Selected",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.align(Alignment.TopEnd).padding(4.dp).background(Color.White, CircleShape),
                            )
                        }
                    }
                    val from = when (p.entry.source) {
                        0 -> if (p.entry.pageIndex != i) " (was ${p.entry.pageIndex + 1})" else ""
                        PageEntry.BLANK -> " · blank"
                        else -> " · added"
                    }
                    Text("${i + 1}$from", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
                }
            }
        }
    }
}

@Composable
private fun SmallAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, enabled: Boolean, onClick: () -> Unit) {
    androidx.compose.material3.AssistChip(
        onClick = onClick,
        enabled = enabled,
        label = { Text(label) },
        leadingIcon = { Icon(icon, null, Modifier.size(18.dp)) },
    )
}

/** A thumbnail straight from a [PdfDoc] (for pages of inserted files). */
@Composable
private fun DocThumb(doc: PdfDoc, page: Int, selected: Boolean, rotation: Int) {
    val aspect = doc.pageSizes[page].aspect
    val bmp by produceState<Bitmap?>(null, doc, page) { value = doc.render(page, 240, (240 / aspect).toInt().coerceAtLeast(1)) }
    val turned = rotation % 180 != 0
    androidx.compose.foundation.layout.BoxWithConstraints(
        Modifier.fillMaxWidth().aspectRatio(if (turned) 1f / aspect else aspect)
            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outlineVariant)
            .background(Color.White),
        contentAlignment = Alignment.Center,
    ) {
        val w = maxWidth
        val h = maxHeight
        bmp?.let {
            Image(
                it.asImageBitmap(), null,
                contentScale = ContentScale.FillBounds,
                modifier = Modifier.requiredSize(if (turned) h else w, if (turned) w else h).rotate(rotation.toFloat()),
            )
        }
    }
}

// ---- Compress ----

@Composable
private fun CompressTool(vm: AppViewModel, session: DocSession) {
    var level by remember { mutableStateOf(Compression.MEDIUM) }
    val before = session.workFile.length()
    ToolFrame(
        title = "Compress PDF",
        subtitle = "Now ${Files.humanSize(before)}",
        onBack = { vm.back() },
        action = "Compress",
        onAction = {
            vm.task("Compressing…", {
                val out = Files.newWorkFile(vm.context)
                val r = withContext(Dispatchers.IO) { PdfTools.compress(session.workFile, session.password, level, out) }
                if (r.after < r.before * 0.97) {
                    session.replaceWith(out)
                    r
                } else {
                    out.delete()
                    null
                }
            }) { r ->
                if (r == null) {
                    vm.say("This PDF can't be made noticeably smaller at this setting. Try “Strong”.")
                } else {
                    vm.back()
                    val pct = 100 - (r.after * 100 / r.before.coerceAtLeast(1))
                    vm.say("${Files.humanSize(r.before)} → ${Files.humanSize(r.after)} ($pct% smaller). Save to keep it.")
                }
            }
        },
    ) {
        Hint("Pictures in the PDF are made smaller and re-saved. Text and drawings stay sharp.")
        for (c in Compression.entries) {
            Row(
                Modifier.fillMaxWidth().selectable(selected = level == c, onClick = { level = c }).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = level == c, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(c.label, style = MaterialTheme.typography.bodyLarge)
                    Text(c.detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
    }
}

// ---- Passwords ----

@Composable
private fun ProtectTool(vm: AppViewModel, session: DocSession) {
    val encrypted by produceState<Boolean?>(null, session.version) {
        value = try {
            withContext(Dispatchers.IO) { PdfTools.isEncrypted(session.workFile, session.password) }
        } catch (e: Exception) {
            false
        }
    }
    var pw by remember { mutableStateOf("") }
    var pw2 by remember { mutableStateOf("") }
    var print by remember { mutableStateOf(true) }
    var copy by remember { mutableStateOf(true) }
    var edit by remember { mutableStateOf(true) }
    val ok = pw.length >= 4 && pw == pw2
    ToolFrame(
        title = "Protect with password",
        subtitle = if (encrypted == true) "This PDF is protected now" else null,
        onBack = { vm.back() },
        action = "Protect",
        actionEnabled = ok,
        onAction = {
            applyTool(vm, session, "Adding the password…", "Password added. Save to keep it.", newPassword = pw) { src, out ->
                PdfTools.protect(src, session.password, pw, null, PdfTools.Permissions(print, copy, edit, edit), out)
            }
        },
    ) {
        if (encrypted == true) {
            Surface(shape = RoundedCornerShape(12.dp), color = MaterialTheme.colorScheme.secondaryContainer) {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Remove the password", style = MaterialTheme.typography.titleSmall)
                    Hint("Saves the PDF without its password and without limits on printing or copying.")
                    androidx.compose.material3.FilledTonalButton(onClick = {
                        applyTool(vm, session, "Removing the password…", "Password removed. Save to keep the change.", newPassword = null) { src, out ->
                            PdfTools.unlock(src, session.password, out)
                        }
                    }) { Text("Remove password") }
                }
            }
            SectionTitle("Or set a new password")
        }
        Hint("Anyone opening the PDF will need this password. It's encrypted with AES-256. There's no way to recover a forgotten password, so keep it somewhere safe.")
        OutlinedTextField(
            value = pw, onValueChange = { pw = it }, label = { Text("Password") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            supportingText = { if (pw.isNotEmpty() && pw.length < 4) Text("At least 4 characters") },
        )
        OutlinedTextField(
            value = pw2, onValueChange = { pw2 = it }, label = { Text("Type it again") }, singleLine = true,
            visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
            modifier = Modifier.fillMaxWidth(),
            isError = pw2.isNotEmpty() && pw2 != pw,
            supportingText = { if (pw2.isNotEmpty() && pw2 != pw) Text("The two don't match") },
        )
        SectionTitle("After opening, people can…")
        SwitchRow("Print it", print) { print = it }
        SwitchRow("Copy text and pictures", copy) { copy = it }
        SwitchRow("Change it and add comments", edit) { edit = it }
    }
}

@Composable
fun SwitchRow(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(Modifier.fillMaxWidth().clickable { onChange(!checked) }.padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(label, Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
        Switch(checked = checked, onCheckedChange = onChange)
    }
}

// ---- Split ----

@Composable
private fun SplitTool(vm: AppViewModel, session: DocSession) {
    val context = LocalContext.current
    val count = session.doc.pageCount
    var mode by remember { mutableStateOf(0) }
    var every by remember { mutableStateOf("1") }
    var ranges by remember { mutableStateOf("1-${(count / 2).coerceAtLeast(1)}, ${(count / 2 + 1).coerceAtMost(count)}-$count") }
    val parts: List<IntRange>? = when (mode) {
        0 -> every.toIntOrNull()?.takeIf { it in 1..count }?.let { PdfTools.everyN(it, count) }
        else -> PdfTools.parseRanges(ranges, count)
    }
    val base = Files.baseName(session.name)
    val folder = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        val list = parts
        if (tree == null || list == null) return@rememberLauncherForActivityResult
        vm.task("Splitting into ${list.size} files…", {
            withContext(Dispatchers.IO) {
                val dir = DocumentFile.fromTreeUri(context, tree) ?: throw java.io.IOException("That folder can't be used")
                PdfTools.split(session.workFile, session.password, list) { _, r ->
                    val name = if (r.first == r.last) "$base - page ${r.first + 1}" else "$base - pages ${r.first + 1}-${r.last + 1}"
                    val f = dir.createFile("application/pdf", name) ?: throw java.io.IOException("Couldn't create $name")
                    context.contentResolver.openOutputStream(f.uri) ?: throw java.io.IOException("Couldn't write $name")
                }
            }
        }) { vm.say("Saved ${list.size} PDFs in the folder you chose") }
    }
    ToolFrame(
        title = "Split PDF",
        subtitle = "$count pages",
        onBack = { vm.back() },
        action = if (parts == null) "Split" else "Split into ${parts.size} file${if (parts.size == 1) "" else "s"}",
        actionEnabled = parts != null,
        onAction = { folder.launch(null) },
    ) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            SegmentedButton(selected = mode == 0, onClick = { mode = 0 }, shape = SegmentedButtonDefaults.itemShape(0, 2)) { Text("Every few pages") }
            SegmentedButton(selected = mode == 1, onClick = { mode = 1 }, shape = SegmentedButtonDefaults.itemShape(1, 2)) { Text("Page ranges") }
        }
        if (mode == 0) {
            OutlinedTextField(
                value = every, onValueChange = { every = it.filter(Char::isDigit).take(5) },
                label = { Text("Pages in each file") }, singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
            )
        } else {
            OutlinedTextField(
                value = ranges, onValueChange = { ranges = it },
                label = { Text("Ranges, for example 1-3, 4, 5-10") }, singleLine = true,
                isError = parts == null, modifier = Modifier.fillMaxWidth(),
                supportingText = { Text(if (parts == null) "Use page numbers from 1 to $count" else "Each range becomes its own file") },
            )
        }
        if (parts != null) {
            Hint(parts.take(8).joinToString("\n") { r -> if (r.first == r.last) "• Page ${r.first + 1}" else "• Pages ${r.first + 1}–${r.last + 1}" } + if (parts.size > 8) "\n• …and ${parts.size - 8} more" else "")
        }
        Hint("You'll be asked for a folder to save the files in.")
    }
}

// ---- Export ----

@Composable
private fun ExportTool(vm: AppViewModel, session: DocSession) {
    val context = LocalContext.current
    var format by remember { mutableStateOf(0) }
    var dpi by remember { mutableStateOf(150) }
    var jpeg by remember { mutableStateOf(true) }
    val base = Files.baseName(session.name)
    val saveDocx = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/vnd.openxmlformats-officedocument.wordprocessingml.document")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.task("Making the Word document…", {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { PdfTools.exportDocx(session.workFile, session.password, base, it) } ?: throw java.io.IOException("Couldn't write the file")
            }
        }) { vm.say("Saved as a Word document") }
    }
    val saveTxt = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/plain")) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.task("Saving the text…", {
            withContext(Dispatchers.IO) {
                context.contentResolver.openOutputStream(uri)?.use { PdfTools.exportText(session.workFile, session.password, it) } ?: throw java.io.IOException("Couldn't write the file")
            }
        }) { vm.say("Saved as text") }
    }
    val saveImages = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { tree: Uri? ->
        if (tree == null) return@rememberLauncherForActivityResult
        val doc = session.doc
        vm.task("Saving ${doc.pageCount} pictures…", {
            val dir = DocumentFile.fromTreeUri(context, tree) ?: throw java.io.IOException("That folder can't be used")
            for (p in 0 until doc.pageCount) {
                val s = doc.pageSizes[p]
                val w = (s.width / 72f * dpi).toInt().coerceIn(1, 6000)
                val h = (s.height / 72f * dpi).toInt().coerceIn(1, 6000)
                val bmp = doc.render(p, w, h) ?: continue
                withContext(Dispatchers.IO) {
                    val f = dir.createFile(if (jpeg) "image/jpeg" else "image/png", "$base - page ${p + 1}") ?: throw java.io.IOException("Couldn't create a file")
                    context.contentResolver.openOutputStream(f.uri)?.use { bmp.compress(if (jpeg) Bitmap.CompressFormat.JPEG else Bitmap.CompressFormat.PNG, 90, it) }
                }
                bmp.recycle()
            }
        }) { vm.say("Saved ${doc.pageCount} pictures in the folder you chose") }
    }
    val options = listOf(
        Triple("Word document (.docx)", "The text, in paragraphs, ready to edit in Word or Google Docs. Pictures and exact layout aren't kept.", 0),
        Triple("Plain text (.txt)", "All the text, page by page.", 1),
        Triple("Pictures (JPEG or PNG)", "One picture for every page, exactly as it looks.", 2),
    )
    ToolFrame(
        title = "Export PDF",
        subtitle = session.name,
        onBack = { vm.back() },
        action = "Export",
        onAction = {
            when (format) {
                0 -> saveDocx.launch("$base.docx")
                1 -> saveTxt.launch("$base.txt")
                else -> saveImages.launch(null)
            }
        },
    ) {
        for ((label, detail, i) in options) {
            Row(
                Modifier.fillMaxWidth().selectable(selected = format == i, onClick = { format = i }).padding(vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                RadioButton(selected = format == i, onClick = null)
                Column(Modifier.padding(start = 12.dp)) {
                    Text(label, style = MaterialTheme.typography.bodyLarge)
                    Text(detail, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        }
        if (format == 2) {
            SectionTitle("Picture quality")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((d, l) in listOf(96 to "Screen", 150 to "Good", 300 to "Print")) FilterChip(selected = dpi == d, onClick = { dpi = d }, label = { Text("$l · $d dpi") })
            }
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilterChip(selected = jpeg, onClick = { jpeg = true }, label = { Text("JPEG (smaller)") })
                FilterChip(selected = !jpeg, onClick = { jpeg = false }, label = { Text("PNG (sharper)") })
            }
        }
    }
}

// ---- Watermark ----

@Composable
private fun WatermarkTool(vm: AppViewModel, session: DocSession) {
    var text by remember { mutableStateOf("CONFIDENTIAL") }
    var size by remember { mutableFloatStateOf(60f) }
    var opacity by remember { mutableFloatStateOf(0.25f) }
    var angle by remember { mutableFloatStateOf(45f) }
    var color by remember { mutableStateOf(0xFFC62828.toInt()) }
    var range by remember { mutableStateOf("") }
    val count = session.doc.pageCount
    val pages = if (range.isBlank()) null else PdfTools.parseRanges(range, count)
    ToolFrame(
        title = "Add watermark",
        subtitle = null,
        onBack = { vm.back() },
        action = "Add watermark",
        actionEnabled = text.isNotBlank() && (range.isBlank() || pages != null),
        onAction = {
            val style = PdfTools.WatermarkStyle(text.trim(), size, color, opacity, angle, pages)
            applyTool(vm, session, "Adding the watermark…", "Watermark added. Save to keep it.") { src, out -> PdfTools.watermark(src, session.password, style, out) }
        },
    ) {
        OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (s in listOf("CONFIDENTIAL", "DRAFT", "COPY", "PAID", "SAMPLE")) FilterChip(selected = text == s, onClick = { text = s }, label = { Text(s) })
        }
        // A preview of how it will sit on a page.
        Box(Modifier.fillMaxWidth().height(160.dp).background(Color.White, RoundedCornerShape(8.dp)).border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp)), contentAlignment = Alignment.Center) {
            Text(
                text, color = Color(color).copy(alpha = opacity), fontSize = androidx.compose.ui.unit.TextUnit(size / 3f, androidx.compose.ui.unit.TextUnitType.Sp),
                fontWeight = androidx.compose.ui.text.font.FontWeight.Bold, modifier = Modifier.rotate(-angle), maxLines = 1,
            )
        }
        Text("Size ${size.toInt()} pt", style = MaterialTheme.typography.labelLarge)
        Slider(value = size, onValueChange = { size = it }, valueRange = 16f..120f)
        Text("See-through ${((1 - opacity) * 100).toInt()}%", style = MaterialTheme.typography.labelLarge)
        Slider(value = opacity, onValueChange = { opacity = it }, valueRange = 0.05f..1f)
        Text("Angle ${angle.toInt()}°", style = MaterialTheme.typography.labelLarge)
        Slider(value = angle, onValueChange = { angle = it }, valueRange = -90f..90f, steps = 11)
        ColorDots(PEN_COLORS, color) { color = it }
        OutlinedTextField(
            value = range, onValueChange = { range = it }, label = { Text("Pages (empty for all)") }, singleLine = true,
            isError = range.isNotBlank() && pages == null, modifier = Modifier.fillMaxWidth(),
            supportingText = { Text("For example 1-3, 7") },
        )
    }
}

// ---- Page numbers ----

@Composable
private fun PageNumbersTool(vm: AppViewModel, session: DocSession) {
    var corner by remember { mutableStateOf(Corner.BOTTOM_CENTER) }
    var format by remember { mutableStateOf("{n}") }
    var first by remember { mutableStateOf("1") }
    var skipFirst by remember { mutableStateOf(false) }
    var size by remember { mutableFloatStateOf(11f) }
    val formats = listOf("{n}", "Page {n}", "{n} of {total}", "Page {n} of {total}", "- {n} -")
    ToolFrame(
        title = "Add page numbers",
        subtitle = null,
        onBack = { vm.back() },
        action = "Add page numbers",
        actionEnabled = first.toIntOrNull() != null,
        onAction = {
            val style = PdfTools.NumberStyle(corner, format, first.toIntOrNull() ?: 1, skipFirst, size)
            applyTool(vm, session, "Numbering pages…", "Page numbers added. Save to keep them.") { src, out -> PdfTools.pageNumbers(src, session.password, style, out) }
        },
    ) {
        SectionTitle("Where")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (c in Corner.entries) FilterChip(selected = corner == c, onClick = { corner = c }, label = { Text(c.label) })
        }
        SectionTitle("Style")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in formats) FilterChip(selected = format == f, onClick = { format = f }, label = { Text(f.replace("{n}", "3").replace("{total}", "${session.doc.pageCount}")) })
        }
        OutlinedTextField(
            value = first, onValueChange = { first = it.filter(Char::isDigit).take(5) }, label = { Text("First number") }, singleLine = true,
            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), modifier = Modifier.fillMaxWidth(),
        )
        SwitchRow("Leave the first page (cover) unnumbered", skipFirst) { skipFirst = it }
        Text("Size ${size.toInt()} pt", style = MaterialTheme.typography.labelLarge)
        Slider(value = size, onValueChange = { size = it }, valueRange = 7f..24f)
    }
}

// ---- Forms ----

@Composable
private fun FillFormTool(vm: AppViewModel, session: DocSession) {
    val fields by produceState<List<FormField>?>(null, session.version) {
        value = try {
            withContext(Dispatchers.IO) { PdfEditor.listFormFields(session.workFile, session.password) }
        } catch (e: Exception) {
            emptyList()
        }
    }
    val values = remember { mutableStateMapOf<String, String>() }
    val list = fields
    ToolFrame(
        title = "Fill in form",
        subtitle = list?.let { "${it.size} field${if (it.size == 1) "" else "s"}" },
        onBack = { vm.back() },
        action = if (list.isNullOrEmpty()) null else "Fill in",
        actionEnabled = values.isNotEmpty(),
        onAction = {
            val changes = values.toMap()
            vm.task("Filling in the form…", {
                val out = Files.newWorkFile(vm.context)
                val failed = withContext(Dispatchers.IO) { PdfEditor.fillForm(session.workFile, session.password, changes, out) }
                session.replaceWith(out)
                failed
            }) { failed ->
                vm.back()
                vm.say(if (failed.isEmpty()) "Form filled in. Save to keep it." else "Filled in, except: ${failed.joinToString()}")
            }
        },
    ) {
        when {
            list == null -> Hint("Reading the form…")
            list.isEmpty() -> Hint("This PDF has no fillable fields. Use Fill & Sign to type anywhere on the page instead.")
            else -> {
                Hint("Changes appear on the pages after you tap Fill in.")
                for (f in list) {
                    val v = values[f.name] ?: f.value
                    when (f.kind) {
                        FormField.Kind.TEXT -> OutlinedTextField(
                            value = v, onValueChange = { values[f.name] = it }, label = { Text(f.label) },
                            enabled = !f.readOnly, singleLine = !f.multiline, minLines = if (f.multiline) 3 else 1, modifier = Modifier.fillMaxWidth(),
                        )
                        FormField.Kind.CHECKBOX -> Row(Modifier.fillMaxWidth().clickable(enabled = !f.readOnly) { values[f.name] = if (v == "true") "false" else "true" }, verticalAlignment = Alignment.CenterVertically) {
                            Checkbox(checked = v == "true", onCheckedChange = null, enabled = !f.readOnly)
                            Text(f.label, Modifier.padding(start = 8.dp))
                        }
                        FormField.Kind.RADIO, FormField.Kind.CHOICE -> Column {
                            Text(f.label, style = MaterialTheme.typography.labelLarge)
                            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                for (o in f.options) FilterChip(selected = v == o, onClick = { if (!f.readOnly) values[f.name] = o }, label = { Text(o) })
                            }
                        }
                    }
                }
            }
        }
    }
}

// ---- Flatten ----

@Composable
private fun FlattenTool(vm: AppViewModel, session: DocSession) {
    ToolFrame(
        title = "Flatten",
        subtitle = null,
        onBack = { vm.back() },
        action = "Flatten",
        onAction = {
            applyTool(vm, session, "Flattening…", "Flattened. Save to keep it.") { src, out -> PdfEditor.flatten(src, session.password, out) }
        },
    ) {
        Hint("Paints every comment, highlight, drawing, signature and filled-in form field onto the page itself.")
        Hint("Afterwards nobody can move, change or delete them, and they look the same in every app. Links keep working.")
        Hint("Do this last, before sending a signed form.")
    }
}

// ---- Properties ----

@Composable
private fun InfoTool(vm: AppViewModel, session: DocSession) {
    val info by produceState<Map<String, String>?>(null, session.version) {
        value = try {
            withContext(Dispatchers.IO) { PdfTools.info(session.workFile, session.password) }
        } catch (e: Exception) {
            emptyMap()
        }
    }
    val values = remember { mutableStateMapOf<String, String>() }
    ToolFrame(
        title = "Document properties",
        subtitle = session.name,
        onBack = { vm.back() },
        action = "Save properties",
        actionEnabled = values.isNotEmpty(),
        onAction = {
            val v = values.toMap()
            applyTool(vm, session, "Updating…", "Properties updated. Save to keep them.") { src, out -> PdfTools.setInfo(src, session.password, v, out) }
        },
    ) {
        val i = info
        if (i == null) {
            Hint("Reading…")
        } else {
            for (k in listOf("Title", "Author", "Subject", "Keywords")) {
                OutlinedTextField(value = values[k] ?: i[k].orEmpty(), onValueChange = { values[k] = it }, label = { Text(k) }, singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        }
    }
}

