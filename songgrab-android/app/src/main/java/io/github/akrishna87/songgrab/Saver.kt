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

/**
 * Puts finished songs in the phone's shared Music/SongGrab folder, where every music app
 * (and the Files app) can find them, and they stay if SongGrab is uninstalled.
 */
object Saver {
    const val FOLDER = "SongGrab"

    fun save(context: Context, audio: File, title: String, artist: String, format: Format): Uri {
        val base = fileName(if (artist.isNotBlank() && !title.contains(artist, ignoreCase = true)) "$artist - $title" else title)
        val name = "$base.${format.ext}"
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) saveToMediaStore(context, audio, name, format) else saveToFolder(context, audio, name, format)
    }

    private fun saveToMediaStore(context: Context, audio: File, name: String, format: Format): Uri {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Audio.Media.DISPLAY_NAME, name)
            put(MediaStore.Audio.Media.MIME_TYPE, format.mime)
            put(MediaStore.Audio.Media.RELATIVE_PATH, "${Environment.DIRECTORY_MUSIC}/$FOLDER")
            put(MediaStore.Audio.Media.IS_MUSIC, 1)
            put(MediaStore.Audio.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Audio.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: throw IOException("Android wouldn't create the song file.")
        try {
            val out = resolver.openOutputStream(uri) ?: throw IOException("Android wouldn't open the song file.")
            out.use { stream -> audio.inputStream().use { it.copyTo(stream) } }
            resolver.update(uri, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
        } catch (e: Exception) {
            runCatching { resolver.delete(uri, null, null) }
            throw e
        }
        return uri
    }

    /** Android 8 and 9: write straight into the Music folder and tell the media scanner. */
    @Suppress("DEPRECATION")
    private fun saveToFolder(context: Context, audio: File, name: String, format: Format): Uri {
        val dir = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_MUSIC), FOLDER)
        if (!dir.isDirectory && !dir.mkdirs()) throw IOException("Couldn't make the Music/$FOLDER folder. Allow storage access and try again.")
        var target = File(dir, name)
        var n = 1
        while (target.exists()) target = File(dir, "${name.substringBeforeLast('.')} ($n).${format.ext}").also { n++ }
        audio.copyTo(target)
        MediaScannerConnection.scanFile(context, arrayOf(target.path), arrayOf(format.mime), null)
        return Uri.fromFile(target)
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
