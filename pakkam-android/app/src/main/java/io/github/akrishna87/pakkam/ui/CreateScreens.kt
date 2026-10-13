@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.akrishna87.pakkam.ui

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.ColorMatrix
import android.graphics.ColorMatrixColorFilter
import android.graphics.Paint
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.PhotoCamera
import androidx.compose.material.icons.filled.PictureAsPdf
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.FileProvider
import io.github.akrishna87.pakkam.data.Files
import io.github.akrishna87.pakkam.pdf.PaperSize
import io.github.akrishna87.pakkam.pdf.PdfDoc
import io.github.akrishna87.pakkam.pdf.PdfTools
import io.github.akrishna87.pakkam.pdf.WrongPasswordException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

private data class MergeItem(val uri: Uri, val name: String)

@Composable
fun MergeScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val items = remember { mutableStateListOf<MergeItem>() }
    val add = rememberLauncherForActivityResult(ActivityResultContracts.OpenMultipleDocuments()) { uris: List<Uri> ->
        for (u in uris) items += MergeItem(u, Files.displayName(context, u))
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out: Uri? ->
        if (out == null) return@rememberLauncherForActivityResult
        val list = items.toList()
        vm.task("Combining ${list.size} files…", {
            withContext(Dispatchers.IO) {
                val parts = ArrayList<File>()
                try {
                    for (it in list) {
                        val f = Files.copyToWork(context, it.uri)
                        parts += f
                        // PDFium tells us quickly whether a file is locked.
                        try {
                            PdfDoc.open(f, null).close()
                        } catch (e: WrongPasswordException) {
                            throw java.io.IOException("“${it.name}” has a password. Remove it first (open it, then Protect › Remove password).")
                        }
                    }
                    val result = Files.newWorkFile(context)
                    PdfTools.merge(parts, result)
                    Files.writeTo(context, result, out)
                    result.delete()
                } finally {
                    parts.forEach { it.delete() }
                }
            }
        }) {
            vm.back()
            vm.open(out, picked = true)
        }
    }
    LaunchedEffect(Unit) { if (items.isEmpty()) add.launch(arrayOf("application/pdf")) }
    ToolFrame(
        title = "Combine files",
        subtitle = if (items.isEmpty()) "Pick two or more PDFs" else "${items.size} files, in this order",
        onBack = { vm.back() },
        action = "Combine",
        actionEnabled = items.size >= 2,
        onAction = { save.launch("Combined.pdf") },
    ) {
        for ((i, it) in items.withIndex()) {
            ListItem(
                leadingContent = { Icon(Icons.Filled.PictureAsPdf, null, tint = MaterialTheme.colorScheme.primary) },
                headlineContent = { Text(it.name, maxLines = 2, overflow = TextOverflow.Ellipsis) },
                trailingContent = {
                    Row {
                        IconButton(onClick = { items.add(i - 1, items.removeAt(i)) }, enabled = i > 0) { Icon(Icons.Filled.KeyboardArrowUp, "Move up") }
                        IconButton(onClick = { items.add(i + 1, items.removeAt(i)) }, enabled = i < items.lastIndex) { Icon(Icons.Filled.KeyboardArrowDown, "Move down") }
                        IconButton(onClick = { items.removeAt(i) }) { Icon(Icons.Filled.Close, "Remove") }
                    }
                },
            )
        }
        OutlinedButton(onClick = { add.launch(arrayOf("application/pdf")) }) {
            Icon(Icons.Filled.Add, null)
            Text("Add PDFs", Modifier.padding(start = 8.dp))
        }
        Hint("Bookmarks and form fields from each file are kept.")
    }
}

enum class ScanFilter(val label: String) { ORIGINAL("Original"), GRAY("Greyscale"), DOCUMENT("Black & white") }

@Composable
fun ImagesToPdfScreen(vm: AppViewModel, camera: Boolean) {
    val context = LocalContext.current
    val images = remember { mutableStateListOf<Uri>() }
    var paper by rememberSaveable { mutableStateOf(PaperSize.A4) }
    var filter by rememberSaveable { mutableStateOf(if (camera) ScanFilter.DOCUMENT else ScanFilter.ORIGINAL) }
    var margin by rememberSaveable { mutableStateOf(18f) }
    var pendingPhoto by remember { mutableStateOf<Uri?>(null) }

    val pick = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia()) { uris: List<Uri> -> images += uris }
    val shoot = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok: Boolean ->
        val u = pendingPhoto
        if (ok && u != null) images += u
        pendingPhoto = null
    }
    fun takePhoto() {
        val dir = File(context.cacheDir, "camera").apply { mkdirs() }
        val f = File(dir, "scan-${System.currentTimeMillis()}.jpg")
        val u = FileProvider.getUriForFile(context, "${context.packageName}.files", f)
        pendingPhoto = u
        try {
            shoot.launch(u)
        } catch (e: Exception) {
            vm.say("No camera app was found")
        }
    }
    LaunchedEffect(Unit) {
        if (images.isEmpty()) {
            if (camera) takePhoto() else pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        }
    }
    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/pdf")) { out: Uri? ->
        if (out == null) return@rememberLauncherForActivityResult
        val list = images.toList()
        val f = filter
        val p = paper
        val m = margin
        vm.task("Making the PDF…", {
            withContext(Dispatchers.IO) {
                val tmp = Files.newWorkFile(context)
                PdfTools.imagesToPdf(list.map { u -> { decodePicture(context, u, 2400)?.let { applyFilter(it, f) } } }, p, m, tmp)
                Files.writeTo(context, tmp, out)
                tmp.delete()
            }
        }) {
            File(context.cacheDir, "camera").listFiles()?.forEach { it.delete() }
            vm.back()
            vm.open(out, picked = true)
        }
    }

    ToolFrame(
        title = if (camera) "Scan to PDF" else "Photos to PDF",
        subtitle = if (images.isEmpty()) null else "${images.size} page${if (images.size == 1) "" else "s"}",
        onBack = { vm.back() },
        action = "Create PDF",
        actionEnabled = images.isNotEmpty(),
        onAction = { save.launch((if (camera) "Scan " else "Photos ") + java.text.SimpleDateFormat("yyyy-MM-dd HH.mm", java.util.Locale.US).format(java.util.Date()) + ".pdf") },
    ) {
        FlowRow(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            for ((i, u) in images.withIndex()) {
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box {
                        PictureThumb(u, filter)
                        IconButton(onClick = { images.removeAt(i) }, modifier = Modifier.align(Alignment.TopEnd).size(32.dp)) {
                            Surface(shape = RoundedCornerShape(16.dp), color = MaterialTheme.colorScheme.surface.copy(alpha = 0.85f)) {
                                Icon(Icons.Filled.Close, "Remove page ${i + 1}", Modifier.padding(4.dp))
                            }
                        }
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(onClick = { images.add(i - 1, images.removeAt(i)) }, enabled = i > 0, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.KeyboardArrowUp, "Earlier") }
                        Text("${i + 1}", style = MaterialTheme.typography.labelMedium)
                        IconButton(onClick = { images.add(i + 1, images.removeAt(i)) }, enabled = i < images.lastIndex, modifier = Modifier.size(32.dp)) { Icon(Icons.Filled.KeyboardArrowDown, "Later") }
                    }
                }
            }
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { takePhoto() }) {
                Icon(Icons.Filled.PhotoCamera, null)
                Text("Take photo", Modifier.padding(start = 8.dp))
            }
            OutlinedButton(onClick = { pick.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) }) {
                Icon(Icons.Filled.Add, null)
                Text("Add photos", Modifier.padding(start = 8.dp))
            }
        }
        SectionTitle("Look")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (f in ScanFilter.entries) FilterChip(selected = filter == f, onClick = { filter = f }, label = { Text(f.label) })
        }
        SectionTitle("Page size")
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            for (p in PaperSize.entries) FilterChip(selected = paper == p, onClick = { paper = p }, label = { Text(p.label) })
        }
        if (paper != PaperSize.FIT) {
            SectionTitle("Margin")
            FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                for ((m, l) in listOf(0f to "None", 18f to "Small", 36f to "Normal")) FilterChip(selected = margin == m, onClick = { margin = m }, label = { Text(l) })
            }
        }
    }
}

@Composable
private fun PictureThumb(uri: Uri, filter: ScanFilter) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, uri, filter) {
        value = withContext(Dispatchers.IO) { decodePicture(context, uri, 300)?.let { applyFilter(it, filter) } }
    }
    Box(Modifier.size(96.dp, 128.dp).clip(RoundedCornerShape(8.dp))) {
        bmp?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop, modifier = Modifier.fillMaxWidth()) }
    }
}

/** Plain colour adjustments for photographed paper: no AI, just greyscale and contrast. */
fun applyFilter(src: Bitmap, f: ScanFilter): Bitmap {
    if (f == ScanFilter.ORIGINAL) return src
    val out = Bitmap.createBitmap(src.width, src.height, Bitmap.Config.ARGB_8888)
    val cm = ColorMatrix().apply { setSaturation(0f) }
    if (f == ScanFilter.DOCUMENT) {
        // Stretch contrast so grey paper turns white and ink turns black.
        val c = 1.9f
        val t = -0.38f * 255f
        cm.postConcat(ColorMatrix(floatArrayOf(c, 0f, 0f, 0f, t, 0f, c, 0f, 0f, t, 0f, 0f, c, 0f, t, 0f, 0f, 0f, 1f, 0f)))
    }
    Canvas(out).drawBitmap(src, 0f, 0f, Paint(Paint.FILTER_BITMAP_FLAG).apply { colorFilter = ColorMatrixColorFilter(cm) })
    if (out !== src) src.recycle()
    return out
}
