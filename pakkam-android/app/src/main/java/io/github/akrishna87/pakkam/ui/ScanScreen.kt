@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.akrishna87.pakkam.ui

import android.Manifest
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.util.Size
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.core.resolutionselector.ResolutionStrategy
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.concurrent.futures.await
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Crop
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FlashOff
import androidx.compose.material.icons.filled.FlashOn
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathFillType
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.core.content.ContextCompat
import io.github.akrishna87.pakkam.data.Files
import io.github.akrishna87.pakkam.pdf.PaperSize
import io.github.akrishna87.pakkam.pdf.PdfTools
import io.github.akrishna87.pakkam.scan.DocumentDetector
import io.github.akrishna87.pakkam.scan.Pixels
import io.github.akrishna87.pakkam.scan.Pt
import io.github.akrishna87.pakkam.scan.Quad
import io.github.akrishna87.pakkam.scan.ScanImage
import io.github.akrishna87.pakkam.scan.ScanLook
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.util.concurrent.Executors
import kotlin.math.abs
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min

/** One page of a scan: the photo, where the page is in it, and how it's cleaned up and turned. */
class ScanPage(val photo: File, quad: Quad, look: ScanLook) {
    var quad by mutableStateOf(quad)
    var look by mutableStateOf(look)
    var turn by mutableIntStateOf(0)
}

/** The pages being scanned, kept by the app so they survive the screen turning. */
class ScanSession {
    val pages = mutableStateListOf<ScanPage>()
    var look by mutableStateOf(ScanLook.AUTO)

    fun clear() {
        pages.forEach { it.photo.delete() }
        pages.clear()
    }
}

private fun scanDir(context: Context) = File(context.cacheDir, "scan").apply { mkdirs() }

/** A photo as pixels, turned upright and at most [maxSide] across. */
private fun loadPixels(context: Context, file: File, maxSide: Int): Pixels? {
    val bmp = decodePicture(context, Uri.fromFile(file), maxSide) ?: return null
    val px = IntArray(bmp.width * bmp.height)
    bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
    return Pixels(px, bmp.width, bmp.height)
}

private fun Pixels.toBitmap(): Bitmap = Bitmap.createBitmap(argb, width, height, Bitmap.Config.ARGB_8888)

/** Where the page is in a photo, or the whole photo when no page stands out. */
private fun findPage(px: Pixels): Quad {
    val small = px.shrunk(320)
    return DocumentDetector.detect(small.luminance(), small.width, small.height) ?: Quad.FULL
}

/** The finished page: flattened, cleaned up and turned, at most [maxSide] pixels across. */
private fun renderPage(context: Context, page: ScanPage, maxSide: Int): Bitmap? {
    val src = loadPixels(context, page.photo, maxSide * 4 / 3) ?: return null
    return ScanImage.enhance(ScanImage.flatten(src, page.quad, maxSide), page.look).turned(page.turn).toBitmap()
}

private enum class ScanStage { CAMERA, REVIEW }

/**
 * A document scanner: the camera outlines the page as it's framed (and can take it on its own
 * once it's steady), then each page is flattened from the angle it was shot at, cleaned of
 * shadows and grey paper, and the pages are saved together as a PDF. No AI: geometry and
 * arithmetic on the pixels, all on the phone.
 */
@Composable
fun ScanScreen(vm: AppViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val scan = vm.scan
    var stage by remember { mutableStateOf(if (scan.pages.isEmpty()) ScanStage.CAMERA else ScanStage.REVIEW) }
    var current by remember { mutableIntStateOf(0) }
    var cropAt by remember { mutableStateOf<Int?>(null) }
    var retakeAt by remember { mutableStateOf<Int?>(null) }
    var askDiscard by remember { mutableStateOf(false) }
    var saving by remember { mutableStateOf(false) }

    fun leave() {
        scan.clear()
        vm.back()
    }

    /** Adds (or, when retaking, puts back) a page from a photo file, finding the page in it. */
    suspend fun addPhoto(file: File) {
        val quad = withContext(Dispatchers.Default) { loadPixels(context, file, 1000)?.let { findPage(it) } } ?: run {
            file.delete()
            vm.say("That picture couldn't be read")
            return
        }
        val page = ScanPage(file, quad, scan.look)
        val at = retakeAt
        if (at != null && at in scan.pages.indices) {
            scan.pages[at].photo.delete()
            scan.pages[at] = page
            current = at
            retakeAt = null
            stage = ScanStage.REVIEW
        } else {
            scan.pages += page
            current = scan.pages.lastIndex
        }
    }

    val pickPhotos = rememberLauncherForActivityResult(ActivityResultContracts.PickMultipleVisualMedia(20)) { uris: List<Uri> ->
        if (uris.isEmpty()) return@rememberLauncherForActivityResult
        vm.task("Finding the pages…", {
            for (u in uris) {
                val f = File(scanDir(context), "import-${System.nanoTime()}.jpg")
                withContext(Dispatchers.IO) { context.contentResolver.openInputStream(u)?.use { i -> f.outputStream().use { i.copyTo(it) } } }
                if (f.length() > 0) addPhoto(f)
            }
        }) { stage = ScanStage.REVIEW }
    }
    fun importPhotos() = pickPhotos.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))

    BackHandler {
        when {
            cropAt != null -> cropAt = null
            stage == ScanStage.CAMERA && scan.pages.isNotEmpty() -> {
                retakeAt = null
                stage = ScanStage.REVIEW
            }
            scan.pages.isNotEmpty() -> askDiscard = true
            else -> leave()
        }
    }

    val crop = cropAt
    when {
        crop != null && crop in scan.pages.indices -> CropEditor(scan.pages[crop], onDone = { cropAt = null })
        stage == ScanStage.CAMERA -> ScanCamera(
            pages = scan.pages.size,
            lastPage = scan.pages.lastOrNull(),
            retaking = retakeAt != null,
            onClose = {
                when {
                    scan.pages.isEmpty() -> leave()
                    else -> {
                        retakeAt = null
                        stage = ScanStage.REVIEW
                    }
                }
            },
            onPhoto = { f -> scope.launch { addPhoto(f) } },
            onImport = { importPhotos() },
            onReview = { stage = ScanStage.REVIEW },
        )
        else -> ScanReview(
            scan = scan,
            current = current,
            onCurrent = { current = it },
            onClose = { askDiscard = true },
            onCrop = { cropAt = it },
            onRetake = {
                retakeAt = it
                stage = ScanStage.CAMERA
            },
            onAdd = { stage = ScanStage.CAMERA },
            onImport = { importPhotos() },
            onSave = { saving = true },
        )
    }

    if (askDiscard) {
        AlertDialog(
            onDismissRequest = { askDiscard = false },
            title = { Text("Discard this scan?") },
            text = { Text("The ${scan.pages.size} page${if (scan.pages.size == 1) "" else "s"} scanned so far will be thrown away.") },
            confirmButton = { TextButton(onClick = { askDiscard = false; leave() }) { Text("Discard") } },
            dismissButton = { TextButton(onClick = { askDiscard = false }) { Text("Keep scanning") } },
        )
    }
    if (saving) {
        SaveScanDialog(onDismiss = { saving = false }) { name, paper ->
            saving = false
            val pages = scan.pages.toList()
            vm.task("Making the PDF…", {
                withContext(Dispatchers.IO) {
                    val tmp = Files.newWorkFile(context)
                    PdfTools.imagesToPdf(pages.map { p -> { renderPage(context, p, 2200) } }, paper, 0f, tmp)
                    val uri = saveToDownloads(context, tmp, name)
                    tmp.delete()
                    uri
                }
            }) { uri ->
                scan.clear()
                vm.back()
                vm.say("Saved in Downloads › Pakkam")
                vm.open(uri)
            }
        }
    }
}

/**
 * Saves the PDF where the person can find it with any file manager: Downloads › Pakkam. Phones
 * before Android 10 get the app's own documents folder (no storage permission is needed for it).
 */
private fun saveToDownloads(context: Context, file: File, name: String): Uri {
    val fileName = (name.trim().ifEmpty { "Scan" }.replace(Regex("[\\\\/:*?\"<>|]"), "_")) + ".pdf"
    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, fileName)
            put(MediaStore.MediaColumns.MIME_TYPE, "application/pdf")
            put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/Pakkam")
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val uri = resolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, values) ?: throw java.io.IOException("The PDF couldn't be saved")
        resolver.openOutputStream(uri)?.use { out -> file.inputStream().use { it.copyTo(out) } } ?: throw java.io.IOException("The PDF couldn't be saved")
        resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        return uri
    }
    val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOCUMENTS) ?: context.filesDir, "Scans").apply { mkdirs() }
    var target = File(dir, fileName)
    var n = 2
    while (target.exists()) target = File(dir, fileName.removeSuffix(".pdf") + " ($n).pdf").also { n++ }
    file.copyTo(target)
    return Uri.fromFile(target)
}

// ---- Camera ----

/** The live camera: the page outlined as it's found, a shutter, the torch and taking pages on their own. */
@Composable
private fun ScanCamera(
    pages: Int,
    lastPage: ScanPage?,
    retaking: Boolean,
    onClose: () -> Unit,
    onPhoto: (File) -> Unit,
    onImport: () -> Unit,
    onReview: () -> Unit,
) {
    val context = LocalContext.current
    var granted by remember { mutableStateOf(ContextCompat.checkSelfPermission(context, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) }
    var asked by remember { mutableStateOf(false) }
    val ask = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { ok ->
        granted = ok
        asked = true
    }
    LaunchedEffect(Unit) { if (!granted) ask.launch(Manifest.permission.CAMERA) }

    Box(Modifier.fillMaxSize().background(Color.Black)) {
        if (granted) {
            LiveCamera(pages, lastPage, retaking, onClose, onPhoto, onImport, onReview)
        } else {
            Column(
                Modifier.align(Alignment.Center).padding(32.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Text("The scanner needs the camera", color = Color.White, style = MaterialTheme.typography.titleLarge)
                Text(
                    "Allow Pakkam to use the camera to scan pages, or scan photos you already have.",
                    color = Color.White.copy(alpha = 0.8f),
                )
                if (asked) Button(onClick = { ask.launch(Manifest.permission.CAMERA) }) { Text("Allow the camera") }
                OutlinedButton(onClick = onImport) { Text("Choose photos", color = Color.White) }
                TextButton(onClick = onClose) { Text("Cancel", color = Color.White) }
            }
        }
    }
}

@Composable
private fun LiveCamera(
    pages: Int,
    lastPage: ScanPage?,
    retaking: Boolean,
    onClose: () -> Unit,
    onPhoto: (File) -> Unit,
    onImport: () -> Unit,
    onReview: () -> Unit,
) {
    val context = LocalContext.current
    val lifecycleOwner = androidx.lifecycle.compose.LocalLifecycleOwner.current
    val previewView = remember { PreviewView(context).apply { scaleType = PreviewView.ScaleType.FIT_CENTER } }
    val fourThree = remember { ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build() }
    val imageCapture = remember {
        ImageCapture.Builder()
            .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
            .setResolutionSelector(fourThree)
            .build()
    }
    var camera by remember { mutableStateOf<Camera?>(null) }
    var failed by remember { mutableStateOf(false) }
    // The page found in the latest frame (fractions of the upright frame), and the frame's shape.
    var live by remember { mutableStateOf<Quad?>(null) }
    var frameAspect by remember { mutableStateOf(3f / 4f) }
    var steady by remember { mutableIntStateOf(0) }
    var torch by remember { mutableStateOf(false) }
    var auto by remember { mutableStateOf(true) }
    var busy by remember { mutableStateOf(false) }
    var flash by remember { mutableStateOf(false) }

    val executor = remember { Executors.newSingleThreadExecutor() }
    var provider by remember { mutableStateOf<ProcessCameraProvider?>(null) }
    DisposableEffect(Unit) {
        onDispose {
            try {
                provider?.unbindAll()
            } catch (_: Exception) {
            }
            executor.shutdown()
        }
    }
    LaunchedEffect(lifecycleOwner) {
        val main = ContextCompat.getMainExecutor(context)
            try {
                val cameras = ProcessCameraProvider.getInstance(context).await()
                provider = cameras
                val preview = Preview.Builder().setResolutionSelector(fourThree).build()
                preview.setSurfaceProvider(previewView.surfaceProvider)
                val analysis = ImageAnalysis.Builder()
                    .setResolutionSelector(
                        ResolutionSelector.Builder()
                            .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                            .setResolutionStrategy(ResolutionStrategy(Size(640, 480), ResolutionStrategy.FALLBACK_RULE_CLOSEST_HIGHER_THEN_LOWER))
                            .build(),
                    )
                    .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                    .build()
                analysis.setAnalyzer(executor) { image ->
                    val found = try {
                        findInFrame(image)
                    } catch (e: Exception) {
                        null
                    }
                    val rot = image.imageInfo.rotationDegrees
                    val aspect = if (rot % 180 == 90) image.height.toFloat() / image.width else image.width.toFloat() / image.height
                    image.close()
                    main.execute {
                        frameAspect = aspect
                        val before = live
                        // Steady when the corners hardly move between frames.
                        steady = if (found != null && before != null && found.points.zip(before.points).all { (a, b) -> abs(a.x - b.x) < 0.02f && abs(a.y - b.y) < 0.02f }) steady + 1 else 0
                        live = when {
                            found == null -> null
                            before == null -> found
                            else -> Quad(
                                Pt((found.tl.x + before.tl.x) / 2, (found.tl.y + before.tl.y) / 2),
                                Pt((found.tr.x + before.tr.x) / 2, (found.tr.y + before.tr.y) / 2),
                                Pt((found.br.x + before.br.x) / 2, (found.br.y + before.br.y) / 2),
                                Pt((found.bl.x + before.bl.x) / 2, (found.bl.y + before.bl.y) / 2),
                            )
                        }
                    }
                }
                cameras.unbindAll()
                camera = cameras.bindToLifecycle(lifecycleOwner, CameraSelector.DEFAULT_BACK_CAMERA, preview, imageCapture, analysis)
            } catch (e: Exception) {
                android.util.Log.w("Pakkam", "Camera couldn't start", e)
                failed = true
            }
    }
    LaunchedEffect(torch, camera) {
        camera?.cameraControl?.enableTorch(torch)
        Unit
    }

    fun shoot() {
        if (busy || camera == null) return
        busy = true
        flash = true
        val file = File(scanDir(context), "page-${System.currentTimeMillis()}.jpg")
        imageCapture.takePicture(
            ImageCapture.OutputFileOptions.Builder(file).build(),
            ContextCompat.getMainExecutor(context),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(output: ImageCapture.OutputFileResults) {
                    busy = false
                    steady = 0
                    onPhoto(file)
                }

                override fun onError(exception: ImageCaptureException) {
                    busy = false
                    android.util.Log.w("Pakkam", "Photo failed", exception)
                }
            },
        )
    }
    // Taking the page on its own once it has been held steady for a moment.
    LaunchedEffect(steady, auto) {
        if (auto && steady >= 6 && !busy) shoot()
    }
    LaunchedEffect(flash) {
        if (flash) {
            delay(150)
            flash = false
        }
    }

    Box(Modifier.fillMaxSize()) {
        AndroidView(factory = { previewView }, modifier = Modifier.fillMaxSize())
        // The page as found, drawn over the picture.
        Canvas(Modifier.fillMaxSize()) {
            val q = live ?: return@Canvas
            val viewAspect = size.width / size.height
            val (cw, ch) = if (viewAspect > frameAspect) size.height * frameAspect to size.height else size.width to size.width / frameAspect
            val left = (size.width - cw) / 2
            val top = (size.height - ch) / 2
            val path = Path().apply {
                q.points.forEachIndexed { i, p ->
                    val x = left + p.x * cw
                    val y = top + p.y * ch
                    if (i == 0) moveTo(x, y) else lineTo(x, y)
                }
                close()
            }
            val blue = Color(0xFF2196F3)
            drawPath(path, blue.copy(alpha = 0.22f))
            drawPath(path, blue, style = Stroke(3.dp.toPx()))
        }
        if (flash) Box(Modifier.fillMaxSize().background(Color.White.copy(alpha = 0.6f)))

        // Top: close, torch and auto-capture.
        Row(
            Modifier.align(Alignment.TopCenter).fillMaxWidth().statusBarsPadding().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Filled.Close, if (pages > 0) "Back to the pages" else "Close the scanner", tint = Color.White) }
            Text(if (retaking) "Retake page" else "Scan document", color = Color.White, style = MaterialTheme.typography.titleMedium, modifier = Modifier.weight(1f))
            IconButton(onClick = { torch = !torch }) {
                Icon(if (torch) Icons.Filled.FlashOn else Icons.Filled.FlashOff, if (torch) "Light off" else "Light on", tint = Color.White)
            }
            FilterChip(
                selected = auto,
                onClick = { auto = !auto },
                label = { Text("Auto capture") },
                leadingIcon = { Icon(Icons.Filled.AutoAwesome, null, Modifier.size(18.dp)) },
            )
        }
        // A hint about what's happening.
        Surface(
            Modifier.align(Alignment.Center),
            shape = RoundedCornerShape(16.dp),
            color = Color.Black.copy(alpha = 0.55f),
            contentColor = Color.White,
        ) {
            Text(
                when {
                    failed -> "The camera isn't available. Choose photos instead."
                    busy -> "Taking the page…"
                    live == null -> "Fit the page in the frame"
                    auto -> "Hold steady…"
                    else -> "Page found: tap the button"
                },
                Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                style = MaterialTheme.typography.labelLarge,
            )
        }
        // Bottom: photos, the shutter and the pages taken so far.
        Row(
            Modifier.align(Alignment.BottomCenter).fillMaxWidth().navigationBarsPadding().padding(horizontal = 24.dp, vertical = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterStart) {
                IconButton(onClick = onImport, modifier = Modifier.size(56.dp)) {
                    Icon(Icons.Filled.PhotoLibrary, "Choose photos", tint = Color.White, modifier = Modifier.size(30.dp))
                }
            }
            Box(
                Modifier
                    .size(76.dp)
                    .border(4.dp, Color.White, CircleShape)
                    .padding(8.dp)
                    .background(if (busy) Color.Gray else Color.White, CircleShape)
                    .clickable(enabled = !busy && camera != null) { shoot() }
                    .semantics { contentDescription = "Take photo" },
            )
            Box(Modifier.weight(1f), contentAlignment = Alignment.CenterEnd) {
                if (pages > 0 && lastPage != null) PageStack(lastPage, pages, onReview)
            }
        }
    }
}

/** The newest page, small, with how many there are; tapping it goes to the pages. */
@Composable
private fun PageStack(page: ScanPage, count: Int, onClick: () -> Unit) {
    val context = LocalContext.current
    val thumb by produceState<Bitmap?>(null, page, page.quad, page.look, page.turn) {
        value = withContext(Dispatchers.Default) { renderPage(context, page, 240) }
    }
    Box(Modifier.size(width = 56.dp, height = 72.dp).clickable(onClick = onClick).semantics { contentDescription = "Review $count pages" }) {
        Surface(Modifier.fillMaxSize(), shape = RoundedCornerShape(6.dp), color = Color.White, border = androidx.compose.foundation.BorderStroke(2.dp, Color.White)) {
            thumb?.let { Image(it.asImageBitmap(), null, contentScale = ContentScale.Crop) }
        }
        Surface(Modifier.align(Alignment.TopEnd).padding(2.dp), shape = CircleShape, color = MaterialTheme.colorScheme.primary) {
            Text("$count", Modifier.padding(horizontal = 7.dp, vertical = 1.dp), color = MaterialTheme.colorScheme.onPrimary, style = MaterialTheme.typography.labelMedium)
        }
    }
}

/** Finds the page in a camera frame, from its brightness alone, as fractions of the upright frame. */
private fun findInFrame(image: ImageProxy): Quad? {
    val plane = image.planes[0]
    val buf = plane.buffer
    val row = plane.rowStride
    val ps = plane.pixelStride
    val step = max(1, max(image.width, image.height) / 240)
    val w = image.width / step
    val h = image.height / step
    val lum = IntArray(w * h)
    for (y in 0 until h) for (x in 0 until w) lum[y * w + x] = buf.get(y * step * row + x * step * ps).toInt() and 0xFF
    val q = DocumentDetector.detect(lum, w, h) ?: return null
    // Turn from the sensor's way round to the way the picture is shown.
    val turned = when (image.imageInfo.rotationDegrees) {
        90 -> q.map { Pt(1 - it.y, it.x) }
        180 -> q.map { Pt(1 - it.x, 1 - it.y) }
        270 -> q.map { Pt(it.y, 1 - it.x) }
        else -> q
    }
    return Quad.ordered(turned.points)
}

// ---- Reviewing the pages ----

@Composable
private fun ScanReview(
    scan: ScanSession,
    current: Int,
    onCurrent: (Int) -> Unit,
    onClose: () -> Unit,
    onCrop: (Int) -> Unit,
    onRetake: (Int) -> Unit,
    onAdd: () -> Unit,
    onImport: () -> Unit,
    onSave: () -> Unit,
) {
    val pages = scan.pages
    if (pages.isEmpty()) {
        Column(Modifier.fillMaxSize().padding(32.dp), verticalArrangement = Arrangement.Center, horizontalAlignment = Alignment.CenterHorizontally) {
            Text("No pages yet", style = MaterialTheme.typography.titleLarge)
            Spacer(Modifier.height(16.dp))
            Button(onClick = onAdd) { Text("Scan a page") }
            TextButton(onClick = onImport) { Text("Choose photos") }
        }
        return
    }
    val pager = rememberPagerState(initialPage = current.coerceIn(0, pages.lastIndex)) { pages.size }
    LaunchedEffect(pager.currentPage) { onCurrent(pager.currentPage) }
    LaunchedEffect(current, pages.size) {
        val c = current.coerceIn(0, pages.lastIndex)
        if (c != pager.currentPage) pager.scrollToPage(c)
    }
    val scope = rememberCoroutineScope()
    val page = pages[pager.currentPage.coerceIn(0, pages.lastIndex)]

    Column(Modifier.fillMaxSize().background(MaterialTheme.colorScheme.surfaceContainer)) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onClose) { Icon(Icons.Filled.Close, "Discard the scan") } },
            title = { Text("Page ${pager.currentPage + 1} of ${pages.size}") },
            actions = { Button(onClick = onSave, modifier = Modifier.padding(end = 8.dp)) { Text("Save PDF") } },
        )
        HorizontalPager(state = pager, modifier = Modifier.weight(1f).fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 24.dp), pageSpacing = 12.dp) { i ->
            val p = pages.getOrNull(i) ?: return@HorizontalPager
            PageResult(p, Modifier.fillMaxSize().padding(vertical = 12.dp))
        }
        // How the page is cleaned up.
        LazyRow(Modifier.fillMaxWidth(), contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            itemsIndexed(ScanLook.entries) { _, look ->
                FilterChip(selected = page.look == look, onClick = { page.look = look; scan.look = look }, label = { Text(look.label) })
            }
            item {
                TextButton(onClick = { pages.forEach { it.look = page.look } }) { Text("Use on all pages") }
            }
        }
        // What can be done with the page.
        Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            ScanAction("Crop", Icons.Filled.Crop) { onCrop(pager.currentPage) }
            ScanAction("Rotate", Icons.Filled.RotateRight) { page.turn = (page.turn + 90) % 360 }
            ScanAction("Retake", Icons.Filled.Refresh) { onRetake(pager.currentPage) }
            ScanAction("Delete", Icons.Filled.Delete) {
                val at = pager.currentPage
                pages.removeAt(at).photo.delete()
                onCurrent(at.coerceAtMost(pages.lastIndex).coerceAtLeast(0))
            }
            ScanAction("Add page", Icons.Filled.CameraAlt) { onAdd() }
        }
        // All the pages, small; the chosen one can be moved earlier or later.
        LazyRow(
            Modifier.fillMaxWidth().navigationBarsPadding().padding(bottom = 8.dp),
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            itemsIndexed(pages, key = { _, p -> p.photo.path }) { i, p ->
                val selected = i == pager.currentPage
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Box(
                        Modifier
                            .size(width = 52.dp, height = 68.dp)
                            .border(if (selected) 3.dp else 1.dp, if (selected) MaterialTheme.colorScheme.primary else Color(0x33000000), RoundedCornerShape(6.dp))
                            .clickable { scope.launch { pager.animateScrollToPage(i) } }
                            .semantics { contentDescription = "Page ${i + 1}" },
                    ) { PageResult(p, Modifier.fillMaxSize().padding(3.dp), thumb = true) }
                    if (selected && pages.size > 1) {
                        Row {
                            IconButton(onClick = { if (i > 0) { pages.add(i - 1, pages.removeAt(i)); onCurrent(i - 1) } }, enabled = i > 0, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowBack, "Move page earlier", Modifier.size(18.dp))
                            }
                            IconButton(onClick = { if (i < pages.lastIndex) { pages.add(i + 1, pages.removeAt(i)); onCurrent(i + 1) } }, enabled = i < pages.lastIndex, modifier = Modifier.size(28.dp)) {
                                Icon(Icons.AutoMirrored.Filled.ArrowForward, "Move page later", Modifier.size(18.dp))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ScanAction(label: String, icon: androidx.compose.ui.graphics.vector.ImageVector, onClick: () -> Unit) {
    Column(
        Modifier.clickable(onClick = onClick).padding(horizontal = 6.dp, vertical = 6.dp).width(60.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Icon(icon, null)
        Text(label, style = MaterialTheme.typography.labelSmall, maxLines = 1)
    }
}

/** A finished page, made again whenever its corners, clean-up or turn change. */
@Composable
private fun PageResult(page: ScanPage, modifier: Modifier, thumb: Boolean = false) {
    val context = LocalContext.current
    val bmp by produceState<Bitmap?>(null, page, page.quad, page.look, page.turn) {
        value = withContext(Dispatchers.Default) {
            try {
                renderPage(context, page, if (thumb) 200 else 1400)
            } catch (e: OutOfMemoryError) {
                null
            }
        }
    }
    Box(modifier, contentAlignment = Alignment.Center) {
        val b = bmp
        if (b == null) {
            if (!thumb) CircularProgressIndicator()
        } else {
            Image(
                b.asImageBitmap(),
                contentDescription = if (thumb) null else "Scanned page",
                contentScale = ContentScale.Fit,
                modifier = Modifier.fillMaxSize(),
            )
        }
    }
}

// ---- Adjusting the corners ----

/** The photo with the page's corners to drag into place, and a magnifier to place them exactly. */
@Composable
private fun CropEditor(page: ScanPage, onDone: () -> Unit) {
    val context = LocalContext.current
    val photo by produceState<Bitmap?>(null, page) {
        value = withContext(Dispatchers.Default) { loadPixels(context, page.photo, 1600)?.toBitmap() }
    }
    var quad by remember(page) { mutableStateOf(page.quad) }
    var dragging by remember { mutableStateOf<Int?>(null) }
    var touch by remember { mutableStateOf(Offset.Zero) }
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    Column(Modifier.fillMaxSize().background(Color(0xFF111111))) {
        TopAppBar(
            navigationIcon = { IconButton(onClick = onDone) { Icon(Icons.Filled.Close, "Cancel") } },
            title = { Text("Drag the corners") },
            actions = {
                TextButton(onClick = {
                    if (quad.isConvex) {
                        page.quad = quad
                        onDone()
                    }
                }, enabled = quad.isConvex) { Text("Done") }
            },
        )
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().padding(20.dp)) {
            val bmp = photo
            if (bmp == null) {
                CircularProgressIndicator(Modifier.align(Alignment.Center))
                return@BoxWithConstraints
            }
            val image = remember(bmp) { bmp.asImageBitmap() }
            val boxW = constraints.maxWidth.toFloat()
            val boxH = constraints.maxHeight.toFloat()
            val scale = min(boxW / bmp.width, boxH / bmp.height)
            val shownW = bmp.width * scale
            val shownH = bmp.height * scale
            val left = (boxW - shownW) / 2
            val top = (boxH - shownH) / 2
            fun toView(p: Pt) = Offset(left + p.x * shownW, top + p.y * shownH)
            fun toPhoto(o: Offset) = Pt(((o.x - left) / shownW).coerceIn(0f, 1f), ((o.y - top) / shownH).coerceIn(0f, 1f))
            val grab = with(density) { 40.dp.toPx() }
            Canvas(
                Modifier
                    .fillMaxSize()
                    .semantics { contentDescription = "Page corners" }
                    .pointerInput(bmp) {
                        detectDragGestures(
                            onDragStart = { o ->
                                val i = quad.points.indices.minByOrNull { val v = toView(quad.points[it]); hypot(v.x - o.x, v.y - o.y) }
                                dragging = i?.takeIf { val v = toView(quad.points[it]); hypot(v.x - o.x, v.y - o.y) < grab * 1.5f }
                                touch = o
                            },
                            onDrag = { change, amount ->
                                change.consume()
                                val i = dragging ?: return@detectDragGestures
                                val p = toView(quad.points[i]) + amount
                                touch = p
                                quad = quad.with(i, toPhoto(p))
                            },
                            onDragEnd = { dragging = null },
                            onDragCancel = { dragging = null },
                        )
                    },
            ) {
                drawImage(image, dstOffset = IntOffset(left.toInt(), top.toInt()), dstSize = IntSize(shownW.toInt(), shownH.toInt()))
                val pts = quad.points.map { toView(it) }
                val shape = Path().apply {
                    pts.forEachIndexed { i, p -> if (i == 0) moveTo(p.x, p.y) else lineTo(p.x, p.y) }
                    close()
                }
                // Dim all but the page.
                val outside = Path().apply {
                    fillType = PathFillType.EvenOdd
                    addRect(androidx.compose.ui.geometry.Rect(left, top, left + shownW, top + shownH))
                    addPath(shape)
                }
                drawPath(outside, Color.Black.copy(alpha = 0.5f))
                val accent = if (quad.isConvex) Color(0xFF2196F3) else Color(0xFFE53935)
                drawPath(shape, accent, style = Stroke(2.5.dp.toPx()))
                for ((i, p) in pts.withIndex()) {
                    drawCircle(Color.White, if (dragging == i) 16.dp.toPx() else 12.dp.toPx(), p)
                    drawCircle(accent, if (dragging == i) 16.dp.toPx() else 12.dp.toPx(), p, style = Stroke(3.dp.toPx()))
                }
                // The magnifier: twice as close around the corner being dragged, in the opposite top corner.
                val d = dragging
                if (d != null) {
                    val r = 56.dp.toPx()
                    val centre = if (pts[d].x < size.width / 2) Offset(size.width - r - 8.dp.toPx(), r + 8.dp.toPx()) else Offset(r + 8.dp.toPx(), r + 8.dp.toPx())
                    val zoom = 2.5f
                    val circle = Path().apply { addOval(androidx.compose.ui.geometry.Rect(centre, r)) }
                    clipPath(circle) {
                        drawRect(Color.Black, topLeft = Offset(centre.x - r, centre.y - r), size = androidx.compose.ui.geometry.Size(2 * r, 2 * r))
                        val p = pts[d]
                        drawImage(
                            image,
                            dstOffset = IntOffset((centre.x - (p.x - left) * zoom).toInt(), (centre.y - (p.y - top) * zoom).toInt()),
                            dstSize = IntSize((shownW * zoom).toInt(), (shownH * zoom).toInt()),
                        )
                        drawLine(accent, Offset(centre.x - 12.dp.toPx(), centre.y), Offset(centre.x + 12.dp.toPx(), centre.y), 2.dp.toPx())
                        drawLine(accent, Offset(centre.x, centre.y - 12.dp.toPx()), Offset(centre.x, centre.y + 12.dp.toPx()), 2.dp.toPx())
                    }
                    drawCircle(Color.White, r, centre, style = Stroke(3.dp.toPx()))
                }
            }
        }
        if (!quad.isConvex) {
            Text("The corners cross over: move them back round the page.", Modifier.padding(horizontal = 20.dp), color = Color(0xFFFF8A80))
        }
        Row(Modifier.fillMaxWidth().navigationBarsPadding().padding(12.dp), horizontalArrangement = Arrangement.SpaceEvenly) {
            OutlinedButton(onClick = {
                scope.launch {
                    val found = withContext(Dispatchers.Default) { loadPixels(context, page.photo, 1000)?.let { findPage(it) } }
                    if (found != null) quad = found
                }
            }) { Text("Auto detect", color = Color.White) }
            OutlinedButton(onClick = { quad = Quad.FULL }) { Text("Whole photo", color = Color.White) }
        }
    }
}

// ---- Saving ----

@Composable
private fun SaveScanDialog(onDismiss: () -> Unit, onSave: (String, PaperSize) -> Unit) {
    var name by remember { mutableStateOf("Scan " + java.text.SimpleDateFormat("yyyy-MM-dd HH.mm", java.util.Locale.US).format(java.util.Date())) }
    var paper by remember { mutableStateOf(PaperSize.A4) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Save as PDF") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("File name") }, singleLine = true)
                Text("Page size", style = MaterialTheme.typography.labelLarge)
                androidx.compose.foundation.layout.FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    for ((p, label) in listOf(PaperSize.A4 to "A4", PaperSize.LETTER to "Letter", PaperSize.FIT to "Same as the scan")) {
                        FilterChip(selected = paper == p, onClick = { paper = p }, label = { Text(label) })
                    }
                }
                Text("Saved in Downloads › Pakkam, and opened.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        },
        confirmButton = { TextButton(onClick = { onSave(name, paper) }, enabled = name.isNotBlank()) { Text("Save") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
