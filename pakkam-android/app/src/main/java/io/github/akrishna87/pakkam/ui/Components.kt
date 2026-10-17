package io.github.akrishna87.pakkam.ui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas as AndroidCanvas
import android.graphics.Paint
import android.os.Bundle
import android.os.CancellationSignal
import android.os.ParcelFileDescriptor
import android.print.PageRange
import android.print.PrintAttributes
import android.print.PrintDocumentAdapter
import android.print.PrintDocumentInfo
import android.print.PrintManager
import android.speech.tts.TextToSpeech
import android.speech.tts.UtteranceProgressListener
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import java.io.File
import java.io.FileOutputStream
import java.util.Locale

val PEN_COLORS = listOf(
    0xFF000000.toInt() to "Black",
    0xFF1E88E5.toInt() to "Blue",
    0xFFD32F2F.toInt() to "Red",
    0xFF43A047.toInt() to "Green",
    0xFFFB8C00.toInt() to "Orange",
    0xFF8E24AA.toInt() to "Purple",
)

val HIGHLIGHT_COLORS = listOf(
    0xFFFFEB3B.toInt() to "Yellow",
    0xFF76FF03.toInt() to "Green",
    0xFF40C4FF.toInt() to "Blue",
    0xFFFF80AB.toInt() to "Pink",
    0xFFFFAB40.toInt() to "Orange",
    0xFFB388FF.toInt() to "Purple",
)

@Composable
fun ColorDots(colors: List<Pair<Int, String>>, selected: Int, onPick: (Int) -> Unit) {
    Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
        for ((c, name) in colors) {
            val isSel = c == selected
            Box(
                Modifier
                    .size(if (isSel) 32.dp else 26.dp)
                    .border(if (isSel) 3.dp else 1.dp, if (isSel) MaterialTheme.colorScheme.primary else Color(0x33000000), CircleShape)
                    .padding(if (isSel) 4.dp else 0.dp)
                    .background(Color(c), CircleShape)
                    .clickable { onPick(c) }
                    .semantics { contentDescription = name },
            )
        }
    }
}

/**
 * A blank card to sign on with a finger. Returns the drawing cropped to the ink, with a
 * see-through background, so it sits on the page like real ink.
 */
@Composable
fun SignaturePadDialog(title: String, onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    val strokes = remember { mutableStateListOf<List<Offset>>() }
    var current by remember { mutableStateOf<List<Offset>>(emptyList()) }
    var size by remember { mutableStateOf(IntSize.Zero) }
    var ink by remember { mutableStateOf(0xFF0D1B6E.toInt()) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(200.dp)
                        .background(Color.White, RoundedCornerShape(12.dp))
                        .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                        .onSizeChanged { size = it }
                        .semantics { contentDescription = "Signature area" }
                        .pointerInput(Unit) {
                            awaitEachGesture {
                                val down = awaitFirstDown()
                                down.consume()
                                val pts = arrayListOf(down.position)
                                current = pts.toList()
                                while (true) {
                                    val e = awaitPointerEvent()
                                    val c = e.changes.firstOrNull { it.id == down.id } ?: break
                                    if (!c.pressed) break
                                    c.consume()
                                    pts += c.position
                                    current = pts.toList()
                                }
                                strokes += pts.toList()
                                current = emptyList()
                            }
                        },
                ) {
                    Canvas(Modifier.matchParentSize()) {
                        drawLine(Color(0xFFBDBDBD), Offset(24f, this.size.height * 0.75f), Offset(this.size.width - 24f, this.size.height * 0.75f), 2f)
                        for (s in strokes + listOf(current)) {
                            if (s.isEmpty()) continue
                            val path = Path().apply {
                                moveTo(s[0].x, s[0].y)
                                for (p in s.drop(1)) lineTo(p.x, p.y)
                            }
                            drawPath(path, Color(ink), style = Stroke(6f, cap = StrokeCap.Round, join = StrokeJoin.Round))
                        }
                    }
                    if (strokes.isEmpty() && current.isEmpty()) {
                        Text("Sign here", Modifier.align(Alignment.Center), color = Color(0xFF9E9E9E))
                    }
                }
                Row(verticalAlignment = Alignment.CenterVertically) {
                    ColorDots(listOf(0xFF0D1B6E.toInt() to "Blue ink", 0xFF000000.toInt() to "Black ink", 0xFFB71C1C.toInt() to "Red ink"), ink) { ink = it }
                    Box(Modifier.weight(1f))
                    TextButton(onClick = { strokes.clear() }) { Text("Clear") }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = strokes.isNotEmpty(),
                onClick = { renderSignature(strokes, ink, size)?.let(onDone) },
            ) { Text("Use it") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

private fun renderSignature(strokes: List<List<Offset>>, ink: Int, size: IntSize): Bitmap? {
    val pts = strokes.flatten()
    if (pts.isEmpty() || size.width == 0) return null
    val pad = 12f
    val minX = (pts.minOf { it.x } - pad).coerceAtLeast(0f)
    val minY = (pts.minOf { it.y } - pad).coerceAtLeast(0f)
    val maxX = (pts.maxOf { it.x } + pad).coerceAtMost(size.width.toFloat())
    val maxY = (pts.maxOf { it.y } + pad).coerceAtMost(size.height.toFloat())
    // Twice the screen resolution, so it stays sharp when printed.
    val scale = 2f
    val w = ((maxX - minX) * scale).toInt().coerceAtLeast(1)
    val h = ((maxY - minY) * scale).toInt().coerceAtLeast(1)
    val bmp = Bitmap.createBitmap(w, h, Bitmap.Config.ARGB_8888)
    val c = AndroidCanvas(bmp)
    val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        color = ink
        style = Paint.Style.STROKE
        strokeWidth = 6f * scale
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    for (s in strokes) {
        if (s.isEmpty()) continue
        val path = android.graphics.Path()
        path.moveTo((s[0].x - minX) * scale, (s[0].y - minY) * scale)
        if (s.size == 1) path.lineTo((s[0].x - minX) * scale + 1, (s[0].y - minY) * scale)
        for (p in s.drop(1)) path.lineTo((p.x - minX) * scale, (p.y - minY) * scale)
        c.drawPath(path, paint)
    }
    return bmp
}

/** Reads pages aloud with the phone's own text-to-speech voice, moving on to the next page by itself. */
class ReadAloud(context: Context, private val onPageDone: (Int) -> Unit) {
    private var ready = false
    var speaking by mutableStateOf(false)
        private set
    var page by mutableStateOf(-1)
        private set
    private val tts: TextToSpeech = TextToSpeech(context.applicationContext) { status ->
        ready = status == TextToSpeech.SUCCESS
    }

    init {
        tts.setOnUtteranceProgressListener(object : UtteranceProgressListener() {
            override fun onStart(utteranceId: String?) {}
            override fun onDone(utteranceId: String?) {
                if (utteranceId?.startsWith("end-") == true) {
                    val p = utteranceId.removePrefix("end-").toIntOrNull() ?: return
                    android.os.Handler(android.os.Looper.getMainLooper()).post { if (speaking) onPageDone(p) }
                }
            }

            @Deprecated("Deprecated in Java")
            override fun onError(utteranceId: String?) {
                android.os.Handler(android.os.Looper.getMainLooper()).post { speaking = false }
            }
        })
    }

    val isReady get() = ready

    fun speak(pageIndex: Int, text: String, rate: Float) {
        page = pageIndex
        speaking = true
        tts.setSpeechRate(rate)
        tts.language = Locale.getDefault()
        val clean = text.replace(Regex("-\\s*\\r?\\n\\s*"), "").replace(Regex("\\s+"), " ").trim()
        val chunks = if (clean.isEmpty()) listOf(" ") else clean.chunked(TextToSpeech.getMaxSpeechInputLength() - 100)
        for ((i, chunk) in chunks.withIndex()) {
            val id = if (i == chunks.lastIndex) "end-$pageIndex" else "part-$pageIndex-$i"
            tts.speak(chunk, if (i == 0) TextToSpeech.QUEUE_FLUSH else TextToSpeech.QUEUE_ADD, Bundle(), id)
        }
    }

    fun stop() {
        speaking = false
        tts.stop()
    }

    fun shutdown() {
        speaking = false
        tts.shutdown()
    }
}

/** Sends the PDF file to Android's print screen as it is. */
fun printPdf(context: Context, file: File, name: String) {
    val pm = context.getSystemService(Context.PRINT_SERVICE) as PrintManager
    val adapter = object : PrintDocumentAdapter() {
        override fun onLayout(
            oldAttributes: PrintAttributes?,
            newAttributes: PrintAttributes,
            cancellationSignal: CancellationSignal,
            callback: LayoutResultCallback,
            extras: Bundle?,
        ) {
            if (cancellationSignal.isCanceled) {
                callback.onLayoutCancelled()
                return
            }
            val info = PrintDocumentInfo.Builder(name).setContentType(PrintDocumentInfo.CONTENT_TYPE_DOCUMENT).build()
            callback.onLayoutFinished(info, oldAttributes != newAttributes)
        }

        override fun onWrite(pages: Array<out PageRange>, destination: ParcelFileDescriptor, cancellationSignal: CancellationSignal, callback: WriteResultCallback) {
            try {
                file.inputStream().use { input -> FileOutputStream(destination.fileDescriptor).use { input.copyTo(it) } }
                callback.onWriteFinished(arrayOf(PageRange.ALL_PAGES))
            } catch (e: Exception) {
                callback.onWriteFailed(e.message)
            }
        }
    }
    pm.print(name, adapter, null)
}
