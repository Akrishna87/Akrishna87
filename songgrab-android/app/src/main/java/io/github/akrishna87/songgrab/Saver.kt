package io.github.akrishna87.songgrab

import android.content.ContentValues
import android.content.Context
import android.media.MediaScannerConnection
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import java.io.File
import java.io.IOException
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * Puts finished songs in the phone's shared Music/SongGrab folder and videos in Movies/SongGrab,
 * where music apps, the Gallery and the Files app can find them, and they stay if SongGrab is
 * uninstalled.
 */
object Saver {
    const val FOLDER = "SongGrab"

    /** "Music/SongGrab" or "Movies/SongGrab". */
    fun folder(format: Format): String =
        "${if (format.isVideo) Environment.DIRECTORY_MOVIES else Environment.DIRECTORY_MUSIC}/$FOLDER"

    fun save(context: Context, audio: File, title: String, artist: String, format: Format): Uri {
        val base = fileName(if (artist.isNotBlank() && !title.contains(artist, ignoreCase = true)) "$artist - $title" else title)
        val name = "$base.${format.ext}"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToMediaStore(context, audio, name, format) else saveToFolder(context, audio, name, format)
    }

    private fun saveToMediaStore(context: Context, audio: File, name: String, format: Format): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, name)
            put(MediaStore.MediaColumns.MIME_TYPE, format.mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, folder(format))
            if (!format.isVideo) put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (format.isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val uri = resolver.insert(collection, values) ?: throw IOException("Android wouldn't create the file.")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Android wouldn't open the file.")
            out.use { stream -> audio.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.MediaColumns.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    /**
     * Android 8 and 9: write straight into the folder and tell the media scanner, which hands back
     * a content link other apps can open (a plain file link would be refused).
     */
    @Suppress("DEPRECATION")
    private fun saveToFolder(context: Context, audio: File, name: String, format: Format): Uri {
        val dir = File(Environment.getExternalStorageDirectory(), folder(format))
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Couldn't make the ${folder(format)} folder. Allow storage access and try again.")
        var target = File(dir, name)
        var n = 1
        while (target.exists()) target = File(dir, "${name.substringBeforeLast('.')} ($n).${format.ext}").also { n++ }
        audio.copyTo(target)
        val scanned = CountDownLatch(1)
        var contentUri: Uri? = null
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(format.mime)) { _, uri ->
            contentUri = uri
            scanned.countDown()
        }
        scanned.await(10, TimeUnit.SECONDS)
        return contentUri ?: Uri.fromFile(target)
    }

    /** Whether the song's file is still there (it can be deleted from other apps). */
    fun exists(context: Context, uri: Uri): Boolean = when (uri.scheme) {
        "file" -> uri.path?.let { File(it).exists() } == true
        else -> try {
            context.contentResolver.query(uri, arrayOf(MediaStore.MediaColumns._ID), null, null, null)?.use { it.count > 0 } ?: false
        } catch (e: SecurityException) {
            true // can't look, so assume it's still there
        } catch (e: Exception) {
            false
        }
    }

    /** Deletes the song's file. False if Android didn't allow it. */
    fun delete(context: Context, uri: Uri): Boolean = try {
        when (uri.scheme) {
            "file" -> uri.path?.let { path ->
                File(path).delete().also { MediaScannerConnection.scanFile(context, arrayOf(path), null, null) }
            } == true
            else -> context.contentResolver.delete(uri, null, null) > 0
        }
    } catch (e: Exception) {
        false
    }

    /** A file name without characters Android or other systems refuse. */
    fun fileName(text: String): String =
        text.replace(Regex("""[\\/:*?"<>|\u0000-\u001F]"""), " ")
            .replace(Regex("\\s+"), " ")
            .trim().trim('.')
            .take(120)
            .ifEmpty { "Song" }
}
