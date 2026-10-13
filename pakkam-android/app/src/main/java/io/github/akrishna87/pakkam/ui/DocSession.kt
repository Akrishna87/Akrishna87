package io.github.akrishna87.pakkam.ui

import android.app.Application
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import io.github.akrishna87.pakkam.data.Files
import io.github.akrishna87.pakkam.pdf.AnnotInfo
import io.github.akrishna87.pakkam.pdf.Edit
import io.github.akrishna87.pakkam.pdf.PdfDoc
import io.github.akrishna87.pakkam.pdf.PdfEditor
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * One open document. The app reads and edits a private copy ([workFile]); the original is only
 * written when the person saves. [version] goes up every time the copy changes, so the pages redraw.
 */
class DocSession(
    private val app: Application,
    uri: Uri,
    name: String,
    password: String?,
    workFile: File,
    doc: PdfDoc,
) {
    /** The password the working copy opens with (changes when a password is added or removed). */
    var password by mutableStateOf(password)
    var uri by mutableStateOf(uri)
        private set
    var name by mutableStateOf(name)
        private set
    var workFile by mutableStateOf(workFile)
        private set
    var doc by mutableStateOf(doc)
        private set
    var version by mutableIntStateOf(0)
        private set

    /** The copy has changes that aren't in the original yet. */
    var dirty by mutableStateOf(false)

    var canWriteBack by mutableStateOf(false)

    /** Drawn on the pages but not yet written into the copy. */
    val edits = mutableStateListOf<Edit>()
    val redo = mutableStateListOf<Edit>()

    /** Annotations already in the file (for the Comments list and the eraser). */
    var annotations by mutableStateOf<List<AnnotInfo>>(emptyList())
        private set

    val bitmaps = PageBitmaps()

    suspend fun refreshAnnotations() {
        annotations = try {
            withContext(Dispatchers.IO) { PdfEditor.listAnnotations(workFile, password) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    /** Makes [newFile] the working copy (after an edit or a tool) and redraws. */
    suspend fun replaceWith(newFile: File, markDirty: Boolean = true) {
        val newDoc = PdfDoc.open(newFile, password)
        val oldDoc = doc
        val oldFile = workFile
        doc = newDoc
        workFile = newFile
        bitmaps.clear()
        version++
        if (markDirty) dirty = true
        oldDoc.close()
        if (oldFile != newFile) oldFile.delete()
        refreshAnnotations()
    }

    /** Writes the pending [edits] into the working copy. */
    suspend fun applyEdits() {
        if (edits.isEmpty()) return
        val out = Files.newWorkFile(app)
        val list = edits.toList()
        withContext(Dispatchers.IO) { PdfEditor.applyEdits(workFile, password, list, out) }
        edits.clear()
        redo.clear()
        replaceWith(out)
    }

    fun saved(newUri: Uri? = null, newName: String? = null) {
        if (newUri != null) uri = newUri
        if (newName != null) name = newName
        dirty = false
    }

    fun close() {
        doc.close()
        workFile.delete()
        bitmaps.clear()
    }
}

/** Rendered pages kept in memory, a quarter of what the app may use. */
class PageBitmaps {
    private val maxBytes = (Runtime.getRuntime().maxMemory() / 4).coerceAtMost(400L * 1024 * 1024).toInt()
    private val cache = object : LruCache<String, Bitmap>(maxBytes) {
        override fun sizeOf(key: String, value: Bitmap) = value.byteCount
    }
    private val latest = HashMap<Int, String>()

    fun get(page: Int, width: Int): Bitmap? = cache.get("$page:$width")

    /** Any rendering of [page], to show while a sharper one is made. */
    fun any(page: Int): Bitmap? = latest[page]?.let { cache.get(it) }

    fun put(page: Int, width: Int, bmp: Bitmap) {
        val key = "$page:$width"
        cache.put(key, bmp)
        latest[page] = key
    }

    fun clear() {
        cache.evictAll()
        latest.clear()
    }
}
