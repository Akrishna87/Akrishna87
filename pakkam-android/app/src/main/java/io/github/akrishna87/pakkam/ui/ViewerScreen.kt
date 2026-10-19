@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.akrishna87.pakkam.ui

import android.app.Activity
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.PointF
import android.media.ExifInterface
import android.net.Uri
import android.view.WindowManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowForward
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.BorderColor
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CheckBoxOutlineBlank
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Comment
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CropSquare
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Draw
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.FiberManualRecord
import androidx.compose.material.icons.filled.FormatStrikethrough
import androidx.compose.material.icons.filled.FormatUnderlined
import androidx.compose.material.icons.filled.Gesture
import androidx.compose.material.icons.filled.HorizontalRule
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.List
import androidx.compose.material.icons.filled.MenuBook
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.RadioButtonUnchecked
import androidx.compose.material.icons.filled.Redo
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.TextFields
import androidx.compose.material.icons.filled.Today
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material.icons.filled.ViewModule
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Checkbox
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.pakkam.data.Files
import io.github.akrishna87.pakkam.data.PaperTheme
import io.github.akrishna87.pakkam.data.Signatures
import io.github.akrishna87.pakkam.data.ViewMode
import io.github.akrishna87.pakkam.pdf.MarkupKind
import io.github.akrishna87.pakkam.pdf.NoteEdit
import io.github.akrishna87.pakkam.pdf.PageLink
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.core.content.FileProvider
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.ui.layout.onSizeChanged
import java.io.File
import kotlin.math.abs
import kotlin.math.roundToInt
import java.text.DateFormat
import java.util.Date

private sealed interface ViewerDialog {
    data object GoTo : ViewerDialog
    data class Note(val page: Int, val x: Float, val y: Float) : ViewerDialog
    data class AddText(val page: Int, val ux: Float, val uy: Float) : ViewerDialog
    data class Link(val uri: String) : ViewerDialog
    data object Unsaved : ViewerDialog
    data object Info : ViewerDialog
    data class Sign(val initials: Boolean) : ViewerDialog
    data class Draw(val initials: Boolean) : ViewerDialog
    data class Photo(val initials: Boolean, val uri: Uri) : ViewerDialog
    data object Panels : ViewerDialog
}

@Composable
fun ViewerScreen(vm: AppViewModel, session: DocSession) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val startPage = remember(session) { vm.recentsStore.get(session.uri.toString())?.lastPage ?: 0 }
    val vs = remember(session) { ViewerState(session, scope, vm.settings, startPage) }
    var dialog by remember { mutableStateOf<ViewerDialog?>(null) }
    var defineWord by remember { mutableStateOf<String?>(null) }
    val links = remember(session) { HashMap<Int, List<PageLink>>() }
    LaunchedEffect(session.version) { links.clear() }

    LaunchedEffect(session) {
        vm.startMode?.let {
            vs.mode = it
            vm.startMode = null
        }
    }
    LaunchedEffect(vs.currentPage) {
        delay(800)
        vm.rememberPage(vs.currentPage)
    }
    // Leaving text selection when the selection goes away while reading.
    LaunchedEffect(vs.selection) {
        if (vs.selection == null && vs.mode == ViewerMode.READ && vs.tool == Tool.SELECT) vs.tool = Tool.NONE
    }
    val activity = context as? Activity
    DisposableEffect(vs.keepScreenOn) {
        if (vs.keepScreenOn) activity?.window?.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        onDispose { activity?.window?.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) }
    }

    var readAloud by remember { mutableStateOf<ReadAloud?>(null) }
    DisposableEffect(session) { onDispose { readAloud?.shutdown() } }
    fun readPage(p: Int) {
        val ra = readAloud ?: return
        scope.launch {
            val text = session.doc.pageText(p)
            vs.goTo(p)
            ra.speak(p, text, vm.settings.speechRate)
        }
    }
    fun startReading() {
        if (readAloud == null) {
            readAloud = ReadAloud(context) { done ->
                if (done + 1 < session.doc.pageCount) readPage(done + 1) else readAloud?.stop()
            }
        }
        scope.launch {
            // The voice engine takes a moment to start the first time.
            var tries = 0
            while (readAloud?.isReady != true && tries++ < 30) delay(100)
            readPage(vs.currentPage)
        }
    }

    // ---- Saving ----
    val saver = rememberDocSaver(vm, session) { vs.currentPage }
    fun saveAs(then: (() -> Unit)? = null) = saver.saveAs(then)
    fun save(then: (() -> Unit)? = null) = saver.save(then)
    fun finishEditing() {
        vs.placement = null
        vs.selection = null
        vs.rememberPen()
        // The changes stay as they are until saved, so they can still be undone after Done.
        vs.mode = ViewerMode.READ
        vs.tool = Tool.NONE
    }
    fun openTool(t: ToolKind) {
        vm.task("Getting ready…", { session.applyEdits() }) { vm.go(Screen.Tool(t)) }
    }
    fun share() {
        vm.task("Preparing…", { session.applyEdits() }) {
            val uri = Files.shareUri(context, session.workFile, session.name)
            val send = Intent(Intent.ACTION_SEND).setType("application/pdf").putExtra(Intent.EXTRA_STREAM, uri).addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            context.startActivity(Intent.createChooser(send, "Share ${session.name}"))
        }
    }
    fun openReading() {
        vm.rememberPage(vs.currentPage)
        vm.go(Screen.Reading)
    }
    fun leave() {
        if (session.dirty || session.edits.isNotEmpty()) dialog = ViewerDialog.Unsaved else vm.back()
    }

    // ---- Pictures for "Add image" ----
    val pickImage = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri == null) return@rememberLauncherForActivityResult
        vm.task("Loading the picture…", { withContext(Dispatchers.IO) { loadPictureForPage(context, uri) } }) { f ->
            val bmp = vs.image(f) ?: return@task
            val page = vs.currentPage
            val s = session.doc.pageSizes[page]
            vs.placement = imagePlacement(page, bmp.width, bmp.height, s.width, s.height, f, "Picture", 0.5f)
        }
    }

    // ---- Signatures from a photo of paper ----
    var photoForInitials by remember { mutableStateOf(false) }
    var pendingShot by remember { mutableStateOf<Uri?>(null) }
    val pickSignPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri: Uri? ->
        if (uri != null) dialog = ViewerDialog.Photo(photoForInitials, uri)
    }
    val shootSignPhoto = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean ->
        val u = pendingShot
        pendingShot = null
        if (ok && u != null) dialog = ViewerDialog.Photo(photoForInitials, u)
    }
    fun signFromPhoto(initials: Boolean, camera: Boolean) {
        photoForInitials = initials
        dialog = null
        if (!camera) {
            pickSignPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
            return
        }
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val u = FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "signature-${System.currentTimeMillis()}.jpg"))
        pendingShot = u
        try {
            shootSignPhoto.launch(u)
        } catch (e: Exception) {
            vm.say("No camera app was found")
        }
    }
    fun placeSignature(bmp: android.graphics.Bitmap, initials: Boolean) {
        val f = vm.signatures.add(bmp, initials)
        val page = vs.currentPage
        val s = session.doc.pageSizes[page]
        vs.placement = imagePlacement(page, bmp.width, bmp.height, s.width, s.height, f, if (initials) "Initials" else "Signature", if (initials) 0.12f else 0.3f)
    }

    BackHandler {
        when {
            vs.placement != null -> vs.placement = null
            vs.searchOpen -> vs.closeSearch()
            vs.selection != null -> vs.selection = null
            vs.mode != ViewerMode.READ -> finishEditing()
            else -> leave()
        }
    }

    val actions = remember(vs) {
        object : PageActions {
            override fun onTap(page: Int, x: Float, y: Float) {
                if (vs.selection != null) {
                    vs.selection = null
                    return
                }
                scope.launch {
                    val l = links.getOrPut(page) { session.doc.links(page) }
                    val hit = l.firstOrNull { it.rect.inset(2f).contains(x, y) }
                    when {
                        hit?.page != null -> vs.goTo(hit.page)
                        hit?.uri != null -> dialog = ViewerDialog.Link(hit.uri)
                        else -> vs.chromeVisible = !vs.chromeVisible
                    }
                }
            }

            override fun onLongPress(page: Int, x: Float, y: Float) {
                val layout = vs.layouts[page] ?: return
                val i = layout.charAt(x, y, 8f)
                if (i < 0) return
                vs.selectWord(page, i)
                vs.tool = Tool.SELECT
                vs.chromeVisible = true
            }

            override fun onNoteTap(page: Int, x: Float, y: Float) {
                dialog = ViewerDialog.Note(page, x, y)
            }

            override fun onTextTap(page: Int, unitX: Float, unitY: Float) {
                dialog = ViewerDialog.AddText(page, unitX, unitY)
            }
        }
    }

    val showChrome = vs.chromeVisible || vs.mode != ViewerMode.READ || vs.searchOpen
    Box(Modifier.fillMaxSize()) {
        PageList(vs, actions)

        Column(Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.onSizeChanged { if (it.height > 0 && vs.mode == ViewerMode.READ && !vs.searchOpen) vs.barTop = it.height }) {
                when {
                    vs.searchOpen -> SearchBar(vs)
                    vs.mode != ViewerMode.READ -> EditTopBar(vs, onDone = { finishEditing() })
                    else -> ReadTopBar(
                        vm, vs,
                        onBack = { leave() },
                        onSave = { save() },
                        onMenu = { action ->
                            when (action) {
                                MenuAction.PANELS -> dialog = ViewerDialog.Panels
                                MenuAction.GOTO -> dialog = ViewerDialog.GoTo
                                MenuAction.READING -> openReading()
                                MenuAction.READ_ALOUD -> startReading()
                                MenuAction.SHARE -> share()
                                MenuAction.PRINT -> vm.task("Preparing…", { session.applyEdits() }) { printPdf(context, session.workFile, session.name) }
                                MenuAction.SAVE_COPY -> saveAs()
                                MenuAction.INFO -> dialog = ViewerDialog.Info
                                MenuAction.OPEN_WITH -> openWith(context, session)
                            }
                        },
                        onTool = { openTool(it) },
                    )
                }
            }
            vs.selection?.let { sel ->
                SelectionBar(
                    text = sel.text,
                    onCopy = {
                        val cm = context.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                        cm.setPrimaryClip(ClipData.newPlainText("PDF text", sel.text))
                        vm.say("Copied")
                        vs.selection = null
                    },
                    onMark = { vs.markSelection(it) },
                    highlightColor = vs.highlightColor,
                    onHighlight = { c ->
                        vs.highlightColor = c
                        vs.rememberPen()
                        vs.markSelection(MarkupKind.HIGHLIGHT)
                    },
                    // A single word can be looked up in the dictionary.
                    onDefine = sel.text.trim().takeIf { t -> t.isNotEmpty() && t.length <= 40 && t.none { it.isWhitespace() } }?.let { t -> { defineWord = t } },
                )
            }
        }

        Column(Modifier.align(Alignment.BottomCenter).fillMaxWidth()) {
            Row(Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                if (showChrome || vs.listState.isScrollInProgress) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                        contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                        modifier = Modifier.clickable { dialog = ViewerDialog.GoTo },
                    ) {
                        Text("${vs.currentPage + 1} / ${session.doc.pageCount}", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                    }
                    // Undo and Redo for highlights and other marks made while reading, until they're saved.
                    if (vs.mode == ViewerMode.READ && (session.canUndo || session.canRedo)) {
                        Surface(
                            shape = RoundedCornerShape(20.dp),
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(start = 8.dp),
                        ) {
                            Row {
                                IconButton(onClick = { vs.undo() }, enabled = session.canUndo, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Undo, "Undo") }
                                IconButton(onClick = { vs.redo() }, enabled = session.canRedo, modifier = Modifier.size(40.dp)) { Icon(Icons.Filled.Redo, "Redo") }
                            }
                        }
                    }
                    // How far zoomed in or out; tapping it goes back to the normal size.
                    if (abs(vs.zoom - 1f) > 0.02f) {
                        Surface(
                            shape = RoundedCornerShape(16.dp),
                            color = MaterialTheme.colorScheme.inverseSurface.copy(alpha = 0.85f),
                            contentColor = MaterialTheme.colorScheme.inverseOnSurface,
                            modifier = Modifier.padding(start = 8.dp).clickable { vs.zoomTo(1f) },
                        ) {
                            Text("${(vs.zoom * 100).roundToInt()}%", Modifier.padding(horizontal = 12.dp, vertical = 6.dp), style = MaterialTheme.typography.labelLarge)
                        }
                    }
                }
            }
            readAloud?.takeIf { it.speaking }?.let { ra -> ReadAloudBar(ra.page, onStop = { ra.stop() }) }
            AnimatedVisibility(showChrome, enter = fadeIn(), exit = fadeOut(), modifier = Modifier.onSizeChanged { if (it.height > 0 && vs.mode == ViewerMode.READ) vs.barBottom = it.height }) {
                Surface(tonalElevation = 3.dp, shadowElevation = 6.dp) {
                    Column(Modifier.navigationBarsPadding()) {
                        when (vs.mode) {
                            ViewerMode.READ -> ModeBar(
                                onReading = { openReading() },
                                onMode = { m ->
                                    vs.selection = null
                                    vs.mode = m
                                    vs.tool = Tool.NONE
                                },
                                onOrganize = { openTool(ToolKind.ORGANIZE) },
                            )
                            ViewerMode.COMMENT -> CommentBar(vs)
                            ViewerMode.FILL_SIGN -> FillSignBar(
                                vs,
                                onSign = { dialog = ViewerDialog.Sign(initials = it) },
                                onDate = {
                                    val page = vs.currentPage
                                    val s = session.doc.pageSizes[page]
                                    vs.placement = textPlacement(page, 0.4f, 0.45f, DateFormat.getDateInstance(DateFormat.MEDIUM).format(Date()), 12f, vs.textColor, s.width, s.height)
                                },
                                onMark = { kind ->
                                    val page = vs.currentPage
                                    val s = session.doc.pageSizes[page]
                                    vs.placement = markPlacement(page, kind, s.width, s.height, vs.textColor)
                                },
                            )
                            ViewerMode.EDIT -> EditBar(vs, onImage = { pickImage.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) })
                        }
                    }
                }
            }
        }
    }

    defineWord?.let { w -> DefinitionSheet(vm, w) { defineWord = null } }

    when (val d = dialog) {
        null -> Unit
        ViewerDialog.GoTo -> GoToDialog(session.doc.pageCount, onDismiss = { dialog = null }) {
            dialog = null
            vs.goTo(it)
        }
        is ViewerDialog.Note -> TextInputDialog(
            title = "Add a note",
            label = "Note",
            confirm = "Add",
            onDismiss = { dialog = null },
        ) { text ->
            dialog = null
            if (text.isNotBlank()) vs.addEdit(NoteEdit(d.page, PointF(d.x, d.y), text, 0xFFFFD54F.toInt()))
        }
        is ViewerDialog.AddText -> AddTextDialog(vs, onDismiss = { dialog = null }) { text, size ->
            dialog = null
            val s = session.doc.pageSizes[d.page]
            vs.placement = textPlacement(d.page, d.ux, d.uy, text, size, vs.textColor, s.width, s.height)
        }
        is ViewerDialog.Link -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Open this link?") },
            text = { Text(d.uri) },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    try {
                        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(d.uri)))
                    } catch (e: Exception) {
                        vm.say("No app can open this link")
                    }
                }) { Text("Open") }
            },
            dismissButton = { TextButton(onClick = { dialog = null }) { Text("Cancel") } },
        )
        ViewerDialog.Unsaved -> AlertDialog(
            onDismissRequest = { dialog = null },
            title = { Text("Save your changes?") },
            text = { Text(if (session.canWriteBack) "Your changes to “${session.name}” haven't been saved." else "This file can't be changed where it is. Save your changes as a new copy?") },
            confirmButton = {
                TextButton(onClick = {
                    dialog = null
                    save { vm.back() }
                }) { Text(if (session.canWriteBack) "Save" else "Save a copy") }
            },
            dismissButton = {
                TextButton(onClick = {
                    dialog = null
                    vm.back()
                }) { Text("Don't save") }
            },
        )
        ViewerDialog.Info -> InfoDialog(session, onDismiss = { dialog = null })
        is ViewerDialog.Sign -> SignatureSheet(
            vm,
            initials = d.initials,
            onDismiss = { dialog = null },
            onDraw = { dialog = ViewerDialog.Draw(d.initials) },
            onPhoto = { camera -> signFromPhoto(d.initials, camera) },
            onPick = { f ->
                dialog = null
                val bmp = vs.image(f) ?: return@SignatureSheet
                val page = vs.currentPage
                val s = session.doc.pageSizes[page]
                vs.placement = imagePlacement(page, bmp.width, bmp.height, s.width, s.height, f, if (d.initials) "Initials" else "Signature", if (d.initials) 0.12f else 0.3f)
            },
        )
        is ViewerDialog.Draw -> SignaturePadDialog(
            title = if (d.initials) "Draw your initials" else "Draw your signature",
            onDismiss = { dialog = ViewerDialog.Sign(d.initials) },
        ) { bmp ->
            dialog = null
            placeSignature(bmp, d.initials)
        }
        is ViewerDialog.Photo -> SignaturePhotoDialog(d.uri, d.initials, onDismiss = { dialog = ViewerDialog.Sign(d.initials) }) { bmp ->
            dialog = null
            File(context.cacheDir, "camera").listFiles()?.forEach { it.delete() }
            placeSignature(bmp, d.initials)
        }
        ViewerDialog.Panels -> PanelsSheet(vm, vs, onDismiss = { dialog = null }, onDeleteAnnot = { a ->
            dialog = null
            vs.deleteExisting(a)
            vm.task("Removing…", { session.applyEdits() })
        })
    }
}

enum class MenuAction { PANELS, GOTO, READING, READ_ALOUD, SHARE, PRINT, SAVE_COPY, INFO, OPEN_WITH }

@Composable
private fun ReadTopBar(vm: AppViewModel, vs: ViewerState, onBack: () -> Unit, onSave: () -> Unit, onMenu: (MenuAction) -> Unit, onTool: (ToolKind) -> Unit) {
    val session = vs.session
    var menu by remember { mutableStateOf(false) }
    val marked = vs.currentPage in vm.bookmarks()
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
        navigationIcon = { IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Back") } },
        title = {
            Column {
                Text(session.name, maxLines = 1, overflow = TextOverflow.Ellipsis, style = MaterialTheme.typography.titleMedium)
                if (session.dirty || session.edits.isNotEmpty()) Text("Not saved yet", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
        },
        actions = {
            if (session.dirty || session.edits.isNotEmpty()) IconButton(onClick = onSave) { Icon(Icons.Filled.Save, "Save") }
            IconButton(onClick = { vs.searchOpen = true }) { Icon(Icons.Filled.Search, "Search") }
            IconButton(onClick = { vm.toggleBookmark(vs.currentPage) }) {
                Icon(if (marked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder, if (marked) "Remove bookmark" else "Bookmark this page")
            }
            IconButton(onClick = { onMenu(MenuAction.PANELS) }) { Icon(Icons.Filled.List, "Contents and pages") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Filled.MoreVert, "More options") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Go to page…") }, onClick = { menu = false; onMenu(MenuAction.GOTO) })
                    DropdownMenuItem(
                        text = { Text(if (vs.paged) "Continuous scroll" else "Page by page") },
                        onClick = {
                            menu = false
                            vs.setViewMode(if (vs.paged) ViewMode.CONTINUOUS else ViewMode.SINGLE)
                        },
                    )
                    DropdownMenuItem(text = { Text("Fit page to screen") }, onClick = { menu = false; vs.fitPage() })
                    DropdownMenuItem(text = { Text("Fit to width") }, onClick = { menu = false; vs.fitWidth() })
                    for (p in PaperTheme.entries) {
                        DropdownMenuItem(
                            text = { Text("${p.label} mode") },
                            trailingIcon = { if (vs.paper == p) Icon(Icons.Filled.Check, null) },
                            onClick = { menu = false; vs.choosePaper(p) },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Keep screen on") },
                        trailingIcon = { Checkbox(checked = vs.keepScreenOn, onCheckedChange = null) },
                        onClick = { vs.setScreenOn(!vs.keepScreenOn) },
                    )
                    DropdownMenuItem(text = { Text("Reading mode (like an ebook)") }, onClick = { menu = false; onMenu(MenuAction.READING) })
                    DropdownMenuItem(text = { Text("Read aloud") }, onClick = { menu = false; onMenu(MenuAction.READ_ALOUD) })
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Share") }, onClick = { menu = false; onMenu(MenuAction.SHARE) })
                    DropdownMenuItem(text = { Text("Print") }, onClick = { menu = false; onMenu(MenuAction.PRINT) })
                    DropdownMenuItem(text = { Text("Save a copy") }, onClick = { menu = false; onMenu(MenuAction.SAVE_COPY) })
                    DropdownMenuItem(text = { Text("Open with another app") }, onClick = { menu = false; onMenu(MenuAction.OPEN_WITH) })
                    HorizontalDivider()
                    for (t in ToolKind.entries) {
                        if (t == ToolKind.INFO) continue
                        DropdownMenuItem(text = { Text(t.title) }, onClick = { menu = false; onTool(t) })
                    }
                    HorizontalDivider()
                    DropdownMenuItem(text = { Text("Properties") }, onClick = { menu = false; onMenu(MenuAction.INFO) })
                }
            }
        },
    )
}

@Composable
private fun EditTopBar(vs: ViewerState, onDone: () -> Unit) {
    val session = vs.session
    TopAppBar(
        colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface.copy(alpha = 0.97f)),
        navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.Check, "Done") } },
        title = {
            Column {
                Text(vs.mode.label, style = MaterialTheme.typography.titleMedium)
                val n = session.edits.size
                Text(
                    if (vs.tool == Tool.NONE) "Pick a tool below" else if (n == 0) vs.tool.hint() else "${vs.tool.label} · $n change${if (n == 1) "" else "s"} not saved",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        actions = {
            IconButton(onClick = { vs.undo() }, enabled = session.canUndo) { Icon(Icons.Filled.Undo, "Undo") }
            IconButton(onClick = { vs.redo() }, enabled = session.canRedo) { Icon(Icons.Filled.Redo, "Redo") }
        },
    )
}

private fun Tool.hint() = when (this) {
    Tool.HIGHLIGHT, Tool.UNDERLINE, Tool.STRIKEOUT -> "Drag over text, or tap a word"
    Tool.PEN, Tool.MARKER -> "Draw with one finger, move with two"
    Tool.RECT, Tool.OVAL, Tool.LINE, Tool.ARROW, Tool.WHITEOUT -> "Drag on the page"
    Tool.NOTE -> "Tap where the note goes"
    Tool.TEXT -> "Tap where the text goes"
    Tool.ERASER -> "Tap a mark to remove it"
    else -> label
}

@Composable
private fun SearchBar(vs: ViewerState) {
    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { focus.requestFocus() }
    Surface(tonalElevation = 3.dp, shadowElevation = 4.dp) {
        Column(Modifier.fillMaxWidth().statusBarsPadding()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vs.closeSearch() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, "Close search") }
                TextField(
                    value = vs.query,
                    onValueChange = { vs.query = it },
                    modifier = Modifier.weight(1f).focusRequester(focus),
                    placeholder = { Text("Find in document") },
                    singleLine = true,
                    colors = TextFieldDefaults.colors(focusedContainerColor = Color.Transparent, unfocusedContainerColor = Color.Transparent),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    keyboardActions = KeyboardActions(onSearch = { vs.search(vs.query) }),
                )
                val label = when {
                    vs.searchedFor.isEmpty() -> ""
                    vs.hits.isEmpty() && !vs.searching -> "No matches"
                    vs.hits.isEmpty() -> "…"
                    else -> "${vs.hitIndex + 1} of ${vs.hits.size}${if (vs.searching) "+" else ""}"
                }
                Text(label, style = MaterialTheme.typography.labelMedium, modifier = Modifier.padding(horizontal = 4.dp))
                IconButton(onClick = { vs.nextHit(-1) }, enabled = vs.hits.isNotEmpty()) { Icon(Icons.Filled.KeyboardArrowUp, "Previous match") }
                IconButton(onClick = { vs.nextHit(1) }, enabled = vs.hits.isNotEmpty()) { Icon(Icons.Filled.KeyboardArrowDown, "Next match") }
            }
            if (vs.searching) LinearProgressIndicator(Modifier.fillMaxWidth())
        }
    }
}

@Composable
private fun SelectionBar(
    text: String,
    onCopy: () -> Unit,
    onMark: (MarkupKind) -> Unit,
    highlightColor: Int,
    onHighlight: (Int) -> Unit,
    onDefine: (() -> Unit)? = null,
) {
    Surface(
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
        shape = RoundedCornerShape(16.dp),
        tonalElevation = 6.dp,
        shadowElevation = 6.dp,
    ) {
        Column(Modifier.padding(4.dp)) {
            // Tapping a colour highlights the chosen text in it straight away.
            Row(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 12.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.BorderColor, null, Modifier.size(18.dp))
                Spacer(Modifier.width(12.dp))
                ColorDots(HIGHLIGHT_COLORS, highlightColor, onHighlight)
            }
            Row(Modifier.horizontalScroll(rememberScrollState()), verticalAlignment = Alignment.CenterVertically) {
                if (onDefine != null) {
                    TextButton(onClick = onDefine) { Icon(Icons.Filled.MenuBook, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Define") }
                }
                TextButton(onClick = onCopy) { Icon(Icons.Filled.ContentCopy, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Copy") }
                TextButton(onClick = { onMark(MarkupKind.UNDERLINE) }) { Icon(Icons.Filled.FormatUnderlined, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Underline") }
                TextButton(onClick = { onMark(MarkupKind.STRIKEOUT) }) { Icon(Icons.Filled.FormatStrikethrough, null, Modifier.size(18.dp)); Spacer(Modifier.width(6.dp)); Text("Strike") }
            }
        }
    }
}

@Composable
private fun BarButton(label: String, icon: ImageVector, selected: Boolean = false, onClick: () -> Unit) {
    val tint = if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface
    Column(
        Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 6.dp, vertical = 8.dp)
            .width(66.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Surface(
            shape = RoundedCornerShape(12.dp),
            color = if (selected) MaterialTheme.colorScheme.primaryContainer else Color.Transparent,
        ) {
            Icon(icon, null, Modifier.padding(horizontal = 12.dp, vertical = 4.dp), tint = tint)
        }
        Text(label, style = MaterialTheme.typography.labelSmall, color = tint, maxLines = 1)
    }
}

@Composable
private fun ModeBar(onReading: () -> Unit, onMode: (ViewerMode) -> Unit, onOrganize: () -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly) {
        BarButton("Read mode", Icons.Filled.MenuBook) { onReading() }
        BarButton("Comment", Icons.Filled.Comment) { onMode(ViewerMode.COMMENT) }
        BarButton("Fill & Sign", Icons.Filled.Draw) { onMode(ViewerMode.FILL_SIGN) }
        BarButton("Edit", Icons.Filled.Edit) { onMode(ViewerMode.EDIT) }
        BarButton("Organize", Icons.Filled.ViewModule) { onOrganize() }
    }
}

@Composable
private fun ToolRow(vs: ViewerState, tools: List<Pair<Tool, ImageVector>>) {
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        for ((t, icon) in tools) {
            BarButton(t.label, icon, selected = vs.tool == t) {
                vs.selection = null
                vs.tool = if (vs.tool == t) Tool.NONE else t
            }
        }
    }
}

@Composable
private fun CommentBar(vs: ViewerState) {
    val t = vs.tool
    if (t == Tool.PEN || t == Tool.RECT || t == Tool.OVAL || t == Tool.LINE || t == Tool.ARROW) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp), verticalAlignment = Alignment.CenterVertically) {
            ColorDots(PEN_COLORS, vs.penColor) { vs.penColor = it }
            Spacer(Modifier.width(12.dp))
            Slider(value = vs.penWidth, onValueChange = { vs.penWidth = it }, valueRange = 0.5f..8f, modifier = Modifier.weight(1f))
        }
    } else if (t == Tool.HIGHLIGHT || t == Tool.MARKER) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            ColorDots(HIGHLIGHT_COLORS, vs.highlightColor) { vs.highlightColor = it }
        }
    }
    ToolRow(
        vs,
        listOf(
            Tool.HIGHLIGHT to Icons.Filled.BorderColor,
            Tool.UNDERLINE to Icons.Filled.FormatUnderlined,
            Tool.STRIKEOUT to Icons.Filled.FormatStrikethrough,
            Tool.NOTE to Icons.Filled.Comment,
            Tool.PEN to Icons.Filled.Gesture,
            Tool.MARKER to Icons.Filled.Draw,
            Tool.TEXT to Icons.Filled.TextFields,
            Tool.RECT to Icons.Filled.CropSquare,
            Tool.OVAL to Icons.Filled.RadioButtonUnchecked,
            Tool.LINE to Icons.Filled.HorizontalRule,
            Tool.ARROW to Icons.Filled.ArrowForward,
            Tool.ERASER to Icons.Filled.Delete,
        ),
    )
}

@Composable
private fun FillSignBar(vs: ViewerState, onSign: (initials: Boolean) -> Unit, onDate: () -> Unit, onMark: (Placement.Kind) -> Unit) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
        ColorDots(PEN_COLORS.take(3), vs.textColor) { vs.textColor = it }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState())) {
        BarButton("Text", Icons.Filled.TextFields, selected = vs.tool == Tool.TEXT) { vs.tool = if (vs.tool == Tool.TEXT) Tool.NONE else Tool.TEXT }
        BarButton("Sign", Icons.Filled.Draw) { vs.tool = Tool.NONE; onSign(false) }
        BarButton("Initials", Icons.Filled.Gesture) { vs.tool = Tool.NONE; onSign(true) }
        BarButton("Date", Icons.Filled.Today) { vs.tool = Tool.NONE; onDate() }
        BarButton("Tick", Icons.Filled.Check) { vs.tool = Tool.NONE; onMark(Placement.Kind.CHECK) }
        BarButton("Cross", Icons.Filled.Close) { vs.tool = Tool.NONE; onMark(Placement.Kind.CROSS) }
        BarButton("Dot", Icons.Filled.FiberManualRecord) { vs.tool = Tool.NONE; onMark(Placement.Kind.DOT) }
        BarButton("Erase", Icons.Filled.Delete, selected = vs.tool == Tool.ERASER) { vs.tool = if (vs.tool == Tool.ERASER) Tool.NONE else Tool.ERASER }
    }
}

@Composable
private fun EditBar(vs: ViewerState, onImage: () -> Unit) {
    if (vs.tool == Tool.TEXT) {
        Row(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp)) {
            ColorDots(PEN_COLORS, vs.textColor) { vs.textColor = it }
        }
    }
    Row(Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.SpaceEvenly) {
        BarButton("Add text", Icons.Filled.TextFields, selected = vs.tool == Tool.TEXT) { vs.tool = if (vs.tool == Tool.TEXT) Tool.NONE else Tool.TEXT }
        BarButton("Add image", Icons.Filled.Image) { vs.tool = Tool.NONE; onImage() }
        BarButton("White-out", Icons.Filled.CheckBoxOutlineBlank, selected = vs.tool == Tool.WHITEOUT) { vs.tool = if (vs.tool == Tool.WHITEOUT) Tool.NONE else Tool.WHITEOUT }
        BarButton("Erase", Icons.Filled.Delete, selected = vs.tool == Tool.ERASER) { vs.tool = if (vs.tool == Tool.ERASER) Tool.NONE else Tool.ERASER }
    }
}

@Composable
private fun ReadAloudBar(page: Int, onStop: () -> Unit) {
    Surface(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 6.dp), shape = RoundedCornerShape(16.dp), tonalElevation = 6.dp, shadowElevation = 4.dp) {
        Row(Modifier.padding(start = 16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.Pause, null, tint = MaterialTheme.colorScheme.primary)
            Text("Reading page ${page + 1} aloud", Modifier.weight(1f).padding(horizontal = 12.dp))
            IconButton(onClick = onStop) { Icon(Icons.Filled.Stop, "Stop reading") }
        }
    }
}

@Composable
private fun GoToDialog(pageCount: Int, onDismiss: () -> Unit, onGo: (Int) -> Unit) {
    var text by remember { mutableStateOf("") }
    val n = text.toIntOrNull()
    val ok = n != null && n in 1..pageCount
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Go to page") },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it.filter { c -> c.isDigit() }.take(6) },
                label = { Text("Page (1–$pageCount)") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { if (ok) onGo(n!! - 1) }),
            )
        },
        confirmButton = { TextButton(onClick = { onGo(n!! - 1) }, enabled = ok) { Text("Go") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
fun TextInputDialog(title: String, label: String, confirm: String, initial: String = "", multiline: Boolean = true, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                label = { Text(label) },
                singleLine = !multiline,
                minLines = if (multiline) 3 else 1,
                modifier = Modifier.fillMaxWidth(),
            )
        },
        confirmButton = { TextButton(onClick = { onConfirm(text) }, enabled = text.isNotBlank()) { Text(confirm) } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun AddTextDialog(vs: ViewerState, onDismiss: () -> Unit, onConfirm: (String, Float) -> Unit) {
    var text by remember { mutableStateOf("") }
    var size by remember { mutableFloatStateOf(12f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Add text") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = text, onValueChange = { text = it }, label = { Text("Text") }, minLines = 2, modifier = Modifier.fillMaxWidth())
                Text("Size ${size.toInt()} pt", style = MaterialTheme.typography.labelLarge)
                Slider(value = size, onValueChange = { size = it }, valueRange = 6f..48f)
                ColorDots(PEN_COLORS, vs.textColor) { vs.textColor = it }
            }
        },
        confirmButton = { TextButton(onClick = { onConfirm(text.trimEnd(), size) }, enabled = text.isNotBlank()) { Text("Place it") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

@Composable
private fun InfoDialog(session: DocSession, onDismiss: () -> Unit) {
    val meta by produceState<Map<String, String>>(emptyMap(), session.version) { value = session.doc.metadata() }
    val s = session.doc.pageSizes.firstOrNull()
    val rows = buildList {
        add("File" to session.name)
        add("Size" to Files.humanSize(session.workFile.length()))
        add("Pages" to session.doc.pageCount.toString())
        if (s != null) add("Page size" to "${(s.width / 72f * 25.4f).toInt()} × ${(s.height / 72f * 25.4f).toInt()} mm")
        add("Password" to if (session.password != null) "Yes" else "No")
        addAll(meta.toList())
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Properties") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((k, v) in rows) {
                    Column {
                        Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(v, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}

@Composable
private fun SignatureSheet(vm: AppViewModel, initials: Boolean, onDismiss: () -> Unit, onDraw: () -> Unit, onPhoto: (camera: Boolean) -> Unit, onPick: (File) -> Unit) {
    var list by remember { mutableStateOf(vm.signatures.all().filter { Signatures.isInitials(it) == initials }) }
    androidx.compose.material3.ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(Modifier.padding(horizontal = 16.dp).padding(bottom = 24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            Text(if (initials) "Your initials" else "Your signatures", style = MaterialTheme.typography.titleMedium)
            if (list.isEmpty()) Text("Nothing saved yet. Draw one, or sign on paper and take a photo of it. It will be kept on this phone for next time.", color = MaterialTheme.colorScheme.onSurfaceVariant)
            for (f in list) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Surface(
                        Modifier.weight(1f).height(72.dp).clickable { onPick(f) },
                        shape = RoundedCornerShape(12.dp),
                        color = Color.White,
                        tonalElevation = 1.dp,
                    ) {
                        val bmp = remember(f) { BitmapFactory.decodeFile(f.path) }
                        if (bmp != null) {
                            androidx.compose.foundation.Image(
                                bmp.asImageBitmap(),
                                contentDescription = if (initials) "Saved initials" else "Saved signature",
                                modifier = Modifier.padding(8.dp),
                            )
                        }
                    }
                    IconButton(onClick = {
                        vm.signatures.delete(f)
                        list = list - f
                    }) { Icon(Icons.Filled.Delete, "Delete") }
                }
            }
            TextButton(onClick = onDraw) { Icon(Icons.Filled.Draw, null); Spacer(Modifier.width(8.dp)); Text(if (initials) "Draw new initials" else "Draw a new signature") }
            // Signed on paper: a photo of it, with the paper taken away.
            TextButton(onClick = { onPhoto(true) }) { Icon(Icons.Filled.PhotoCamera, null); Spacer(Modifier.width(8.dp)); Text("Take a photo of it on paper") }
            TextButton(onClick = { onPhoto(false) }) { Icon(Icons.Filled.Image, null); Spacer(Modifier.width(8.dp)); Text("Choose a photo of it on paper") }
        }
    }
}

private fun openWith(context: Context, session: DocSession) {
    val uri = Files.shareUri(context, session.workFile, session.name)
    val view = Intent(Intent.ACTION_VIEW).setDataAndType(uri, "application/pdf").addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    try {
        context.startActivity(Intent.createChooser(view, "Open with"))
    } catch (_: Exception) {
    }
}

/** Reads a picture, turned the right way up and at most 2000 pixels across, into a private file. */
fun loadPictureForPage(context: Context, uri: Uri): File {
    val bmp = decodePicture(context, uri, 2000) ?: throw java.io.IOException("The picture couldn't be read")
    val f = Files.newWorkFile(context, if (bmp.hasAlpha()) "png" else "jpg")
    f.outputStream().use { bmp.compress(if (bmp.hasAlpha()) Bitmap.CompressFormat.PNG else Bitmap.CompressFormat.JPEG, 92, it) }
    return f
}

/** Decodes a picture, scaled down to [maxSide] and turned upright from its camera orientation. */
fun decodePicture(context: Context, uri: Uri, maxSide: Int): Bitmap? {
    val cr = context.contentResolver
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
    if (bounds.outWidth <= 0) return null
    var sample = 1
    while (maxOf(bounds.outWidth, bounds.outHeight) / (sample * 2) >= maxSide) sample *= 2
    val bmp = cr.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample }) } ?: return null
    val orientation = try {
        cr.openInputStream(uri)?.use { ExifInterface(it).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL) } ?: ExifInterface.ORIENTATION_NORMAL
    } catch (e: Exception) {
        ExifInterface.ORIENTATION_NORMAL
    }
    val m = Matrix()
    when (orientation) {
        ExifInterface.ORIENTATION_ROTATE_90 -> m.postRotate(90f)
        ExifInterface.ORIENTATION_ROTATE_180 -> m.postRotate(180f)
        ExifInterface.ORIENTATION_ROTATE_270 -> m.postRotate(270f)
        ExifInterface.ORIENTATION_FLIP_HORIZONTAL -> m.postScale(-1f, 1f)
        else -> Unit
    }
    val turned = if (m.isIdentity) bmp else Bitmap.createBitmap(bmp, 0, 0, bmp.width, bmp.height, m, true)
    val s = minOf(1f, maxSide.toFloat() / maxOf(turned.width, turned.height))
    return if (s < 1f) Bitmap.createScaledBitmap(turned, (turned.width * s).toInt(), (turned.height * s).toInt(), true) else turned
}
