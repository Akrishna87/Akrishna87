@file:OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)

package io.github.akrishna87.pakkam.ui

import android.graphics.Bitmap
import android.graphics.Matrix
import android.net.Uri
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.RotateRight
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import io.github.akrishna87.pakkam.pdf.InkLifter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min

/** The photo, ready to lift ink from. */
private class SignaturePhoto(val bitmap: Bitmap, val lifter: InkLifter, val found: IntArray?)

private enum class InkChoice(val label: String, val color: Int?) {
    PHOTO("As photographed", null),
    BLUE("Blue", 0xFF0D1B6E.toInt()),
    BLACK("Black", 0xFF000000.toInt()),
}

/**
 * Turns a photo of a signature signed on paper into one to place on PDFs: the person drags a
 * box around it, the paper is taken away so only the ink is left, and the ink can be kept as
 * photographed or made blue or black. Gives a see-through picture trimmed to the ink.
 */
@Composable
fun SignaturePhotoDialog(uri: Uri, initials: Boolean, onDismiss: () -> Unit, onDone: (Bitmap) -> Unit) {
    val context = LocalContext.current
    var turns by remember { mutableStateOf(0) }
    var unreadable by remember { mutableStateOf(false) }
    val photo by produceState<SignaturePhoto?>(null, uri, turns) {
        value = null
        val loaded = withContext(Dispatchers.Default) {
            try {
                val src = decodePicture(context, uri, 1400) ?: return@withContext null
                val bmp = if (turns % 4 == 0) src else Bitmap.createBitmap(src, 0, 0, src.width, src.height, Matrix().apply { postRotate(90f * turns) }, true)
                val px = IntArray(bmp.width * bmp.height)
                bmp.getPixels(px, 0, bmp.width, 0, 0, bmp.width, bmp.height)
                val lifter = InkLifter(px, bmp.width, bmp.height)
                SignaturePhoto(bmp, lifter, lifter.findInk())
            } catch (e: Exception) {
                null
            } catch (e: OutOfMemoryError) {
                null
            }
        }
        unreadable = loaded == null
        value = loaded
    }
    // The box around the signature, in the photo's pixels.
    var crop by remember(photo) { mutableStateOf(photo?.let { it.found ?: intArrayOf(0, 0, it.bitmap.width, it.bitmap.height) }) }
    var strength by remember { mutableFloatStateOf(0.5f) }
    var ink by remember { mutableStateOf(InkChoice.PHOTO) }
    val result by produceState<Bitmap?>(null, photo, crop?.toList(), strength, ink) {
        val p = photo
        val c = crop
        if (p == null || c == null) {
            value = null
            return@produceState
        }
        delay(120)
        value = withContext(Dispatchers.Default) {
            p.lifter.lift(c, strength, ink.color)?.let { Bitmap.createBitmap(it.argb, it.width, it.height, Bitmap.Config.ARGB_8888) }
        }
    }

    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(Modifier.fillMaxSize()) {
            Column(Modifier.fillMaxSize().navigationBarsPadding()) {
                TopAppBar(
                    navigationIcon = { IconButton(onClick = onDismiss) { Icon(Icons.Filled.Close, "Cancel") } },
                    title = { Text(if (initials) "Initials from a photo" else "Signature from a photo") },
                    actions = {
                        TextButton(onClick = { result?.let(onDone) }, enabled = result != null) { Text("Use it") }
                    },
                )
                val p = photo
                if (p == null) {
                    Box(Modifier.weight(1f).fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        if (unreadable) Text("This picture couldn't be read. Try another photo.") else CircularProgressIndicator()
                    }
                } else {
                    SignatureControls(p, crop, { crop = it }, strength, { strength = it }, ink, { ink = it }, result) { turns = (turns + 1) % 4 }
                }
            }
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.ColumnScope.SignatureControls(
    p: SignaturePhoto,
    crop: IntArray?,
    onCrop: (IntArray) -> Unit,
    strength: Float,
    onStrength: (Float) -> Unit,
    ink: InkChoice,
    onInk: (InkChoice) -> Unit,
    result: Bitmap?,
    onTurn: () -> Unit,
) {
    Text(
        "Drag a box around your signature. The paper is taken away, so only the ink goes on the page.",
        Modifier.padding(horizontal = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
    CropArea(p.bitmap, crop, Modifier.weight(1f).fillMaxWidth().padding(16.dp), onCrop)
    Column(Modifier.padding(horizontal = 16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("How it will look", style = MaterialTheme.typography.labelLarge)
        Box(
            Modifier
                .fillMaxWidth()
                .height(96.dp)
                .background(Color.White, RoundedCornerShape(12.dp))
                .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(12.dp))
                .padding(8.dp),
            contentAlignment = Alignment.Center,
        ) {
            if (result != null) {
                Image(result.asImageBitmap(), contentDescription = "Signature preview", contentScale = ContentScale.Fit, modifier = Modifier.fillMaxSize())
            } else {
                Text("No ink found in the box", color = Color(0xFF757575))
            }
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Clean-up", style = MaterialTheme.typography.labelLarge, modifier = Modifier.width(76.dp))
            Slider(value = strength, onValueChange = onStrength, modifier = Modifier.weight(1f))
        }
        FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalArrangement = Arrangement.Center) {
            for (c in InkChoice.entries) {
                FilterChip(selected = ink == c, onClick = { onInk(c) }, label = { Text(c.label) })
            }
            Spacer(Modifier.width(8.dp))
            OutlinedButton(onClick = onTurn) {
                Icon(Icons.Filled.RotateRight, null, Modifier.size(18.dp))
                Spacer(Modifier.width(6.dp))
                Text("Turn")
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The photo fitted to the space, with the chosen box drawn on it; dragging draws a new box. */
@Composable
private fun CropArea(bitmap: Bitmap, crop: IntArray?, modifier: Modifier, onCrop: (IntArray) -> Unit) {
    val image = remember(bitmap) { bitmap.asImageBitmap() }
    BoxWithConstraints(modifier.semantics { contentDescription = "Photo of the signature" }) {
        val boxW = constraints.maxWidth.toFloat()
        val boxH = constraints.maxHeight.toFloat()
        val scale = min(boxW / bitmap.width, boxH / bitmap.height)
        val shownW = bitmap.width * scale
        val shownH = bitmap.height * scale
        val left = (boxW - shownW) / 2
        val top = (boxH - shownH) / 2
        fun toPhoto(o: Offset) = Offset(((o.x - left) / scale).coerceIn(0f, bitmap.width.toFloat()), ((o.y - top) / scale).coerceIn(0f, bitmap.height.toFloat()))
        var dragFrom by remember { mutableStateOf<Offset?>(null) }
        var dragTo by remember { mutableStateOf<Offset?>(null) }
        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(bitmap) {
                    detectDragGestures(
                        onDragStart = { o ->
                            dragFrom = toPhoto(o)
                            dragTo = dragFrom
                        },
                        onDrag = { change, _ ->
                            change.consume()
                            dragTo = toPhoto(change.position)
                        },
                        onDragEnd = {
                            val a = dragFrom
                            val b = dragTo
                            dragFrom = null
                            dragTo = null
                            // A tiny box is more likely a slip than a choice.
                            if (a != null && b != null && abs(a.x - b.x) > 16 && abs(a.y - b.y) > 8) {
                                onCrop(intArrayOf(min(a.x, b.x).toInt(), min(a.y, b.y).toInt(), max(a.x, b.x).toInt(), max(a.y, b.y).toInt()))
                            }
                        },
                        onDragCancel = {
                            dragFrom = null
                            dragTo = null
                        },
                    )
                },
        ) {
            drawImage(
                image,
                dstOffset = androidx.compose.ui.unit.IntOffset(left.toInt(), top.toInt()),
                dstSize = androidx.compose.ui.unit.IntSize(shownW.toInt(), shownH.toInt()),
            )
            val a = dragFrom
            val b = dragTo
            val r = if (a != null && b != null) {
                floatArrayOf(min(a.x, b.x), min(a.y, b.y), max(a.x, b.x), max(a.y, b.y))
            } else {
                crop?.let { floatArrayOf(it[0].toFloat(), it[1].toFloat(), it[2].toFloat(), it[3].toFloat()) }
            } ?: return@Canvas
            val x0 = left + r[0] * scale
            val y0 = top + r[1] * scale
            val x1 = left + r[2] * scale
            val y1 = top + r[3] * scale
            // Dim what's outside the box.
            val shade = Color.Black.copy(alpha = 0.45f)
            drawRect(shade, Offset(left, top), Size(shownW, y0 - top))
            drawRect(shade, Offset(left, y1), Size(shownW, top + shownH - y1))
            drawRect(shade, Offset(left, y0), Size(x0 - left, y1 - y0))
            drawRect(shade, Offset(x1, y0), Size(left + shownW - x1, y1 - y0))
            drawRect(Color.White, Offset(x0, y0), Size(x1 - x0, y1 - y0), style = Stroke(3.dp.toPx()))
        }
    }
}
