package io.github.akrishna87.pakkam.data

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.OpenableColumns
import java.io.File
import java.io.FileInputStream
import java.io.IOException
import java.util.UUID

/** Moving documents between other apps (content:// links) and the private working files the app edits. */
object Files {

    fun workDir(context: Context) = File(context.cacheDir, "work").apply { mkdirs() }

    fun newWorkFile(context: Context, ext: String = "pdf") = File(workDir(context), "${UUID.randomUUID()}.$ext")

    fun displayName(context: Context, uri: Uri): String {
        if (uri.scheme == "file") return uri.lastPathSegment ?: "Document.pdf"
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                    if (i >= 0) c.getString(i)?.takeIf { it.isNotBlank() }?.let { return it }
                }
            }
        } catch (e: Exception) {
            // Some apps hand over links that can't be asked about; fall back to the link itself.
        }
        return uri.lastPathSegment?.substringAfterLast('/')?.takeIf { it.isNotBlank() } ?: "Document.pdf"
    }

    fun size(context: Context, uri: Uri): Long {
        if (uri.scheme == "file") return uri.path?.let { File(it).length() } ?: -1
        try {
            context.contentResolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst()) {
                    val i = c.getColumnIndex(OpenableColumns.SIZE)
                    if (i >= 0 && !c.isNull(i)) return c.getLong(i)
                }
            }
        } catch (e: Exception) {
        }
        return -1
    }

    /** Copies a document into a private working file. */
    fun copyToWork(context: Context, uri: Uri): File {
        val out = newWorkFile(context)
        val input = if (uri.scheme == "file") FileInputStream(File(uri.path ?: throw IOException("Bad file link"))) else context.contentResolver.openInputStream(uri)
        (input ?: throw IOException("The file couldn't be opened")).use { i -> out.outputStream().use { o -> i.copyTo(o) } }
        if (out.length() == 0L) {
            out.delete()
            throw IOException("The file is empty")
        }
        return out
    }

    /** Whether the edited file can be saved back over the original. Doesn't change the original. */
    fun canWrite(context: Context, uri: Uri): Boolean {
        if (uri.scheme == "file") return uri.path?.let { File(it).canWrite() || File(it).parentFile?.canWrite() == true } ?: false
        return try {
            context.contentResolver.openFileDescriptor(uri, "rw")?.close()
            true
        } catch (e: Exception) {
            false
        }
    }

    fun writeTo(context: Context, file: File, uri: Uri) {
        if (uri.scheme == "file") {
            val target = File(uri.path ?: throw IOException("Bad file link"))
            // A file someone else put in a folder we own may be read-only to us; replace it instead.
            if (target.exists() && !target.canWrite()) target.delete()
            file.copyTo(target, overwrite = true)
            return
        }
        val out = try {
            context.contentResolver.openOutputStream(uri, "wt")
        } catch (e: Exception) {
            context.contentResolver.openOutputStream(uri, "w")
        } ?: throw IOException("Couldn't write the file")
        out.use { o -> file.inputStream().use { it.copyTo(o) } }
    }

    /** Keeps access to a picked document after the app restarts, so it can stay in Recent. */
    fun keepAccess(context: Context, uri: Uri) {
        if (uri.scheme != "content") return
        try {
            context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (e: Exception) {
            try {
                context.contentResolver.takePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION)
            } catch (_: Exception) {
                // Links shared from other apps can't be kept; they still open now.
            }
        }
    }

    /** Whether the app may still open [uri] later, after this visit (Files, Drive, its own files). */
    fun keepsAccess(context: Context, uri: Uri): Boolean =
        uri.scheme != "content" || context.contentResolver.persistedUriPermissions.any { it.uri == uri && it.isReadPermission }

    /**
     * A copy of a file shared from another app (WhatsApp, Gmail…), which only lets it be read
     * this once, kept in the app's own storage so it can be opened again from Recent.
     * The same file shared again reuses its copy.
     */
    fun keepReceived(context: Context, work: File, name: String): Uri {
        val dir = File(context.filesDir, "received").apply { mkdirs() }
        val safe = safeName(name)
        var target = File(dir, safe)
        var n = 2
        while (target.exists() && target.length() != work.length()) {
            target = File(dir, safe.substringBeforeLast('.') + " ($n)." + safe.substringAfterLast('.', "pdf"))
            n++
        }
        work.copyTo(target, overwrite = true)
        return Uri.fromFile(target)
    }

    fun forget(context: Context, uri: Uri) {
        try {
            context.contentResolver.releasePersistableUriPermission(uri, Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION)
        } catch (_: Exception) {
        }
    }

    /** A link other apps can read, for sharing [file] under [name]. */
    fun shareUri(context: Context, file: File, name: String): Uri {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        dir.listFiles()?.forEach { if (System.currentTimeMillis() - it.lastModified() > 3_600_000) it.deleteRecursively() }
        val sub = File(dir, UUID.randomUUID().toString().take(8)).apply { mkdirs() }
        val copy = File(sub, safeName(name))
        file.copyTo(copy, overwrite = true)
        return androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", copy)
    }

    fun safeName(name: String) = name.replace(Regex("[\\\\/:*?\"<>|\\x00-\\x1f]"), "_").take(120).ifBlank { "Document.pdf" }

    fun baseName(name: String) = name.substringBeforeLast('.', name)

    fun cleanWork(context: Context, keep: Set<File>) {
        workDir(context).listFiles()?.forEach { if (it !in keep && System.currentTimeMillis() - it.lastModified() > 60_000) it.delete() }
    }

    fun humanSize(bytes: Long): String = when {
        bytes < 0 -> ""
        bytes < 1024 -> "$bytes B"
        bytes < 1024 * 1024 -> "${(bytes + 512) / 1024} KB"
        else -> String.format(java.util.Locale.US, "%.1f MB", bytes / 1048576.0)
    }
}
