package io.github.akrishna87.pakkam.data

import android.content.Context
import android.graphics.Bitmap
import java.io.File

/** Signatures and initials the person has drawn, kept as see-through PNG pictures. */
class Signatures(context: Context) {
    private val dir = File(context.filesDir, "signatures").apply { mkdirs() }

    fun all(): List<File> = dir.listFiles { f -> f.extension == "png" }?.sortedByDescending { it.lastModified() } ?: emptyList()

    fun add(bitmap: Bitmap, initials: Boolean): File {
        val f = File(dir, "${if (initials) "initials" else "signature"}-${System.currentTimeMillis()}.png")
        f.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
        return f
    }

    fun delete(f: File) {
        if (f.parentFile == dir) f.delete()
    }

    companion object {
        fun isInitials(f: File) = f.name.startsWith("initials")
    }
}
