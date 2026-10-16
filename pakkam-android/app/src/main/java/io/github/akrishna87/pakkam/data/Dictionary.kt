package io.github.akrishna87.pakkam.data

import android.content.Context
import android.database.sqlite.SQLiteDatabase
import io.github.akrishna87.pakkam.pdf.WordForms
import java.io.File
import java.io.FileNotFoundException

/** One meaning of a word. */
data class Meaning(val pos: String, val definition: String, val examples: List<String>, val synonyms: List<String>)

/** What the dictionary says about a word. [looked] is the word as it was in the text. */
data class Definition(val looked: String, val word: String, val pronunciation: String, val meanings: List<Meaning>)

/**
 * The offline English dictionary (Open English WordNet), shipped inside the app as an SQLite
 * file built by ci/build_dictionary.py. It is copied out of the APK on first use.
 */
class Dictionary(private val context: Context) {
    private var db: SQLiteDatabase? = null
    private var missing = false

    /** False when this build was made without the dictionary file. */
    val available: Boolean
        get() = open() != null

    @Synchronized
    private fun open(): SQLiteDatabase? {
        db?.let { return it }
        if (missing) return null
        val version = try {
            context.packageManager.getPackageInfo(context.packageName, 0).let {
                if (android.os.Build.VERSION.SDK_INT >= 28) it.longVersionCode else @Suppress("DEPRECATION") it.versionCode.toLong()
            }
        } catch (e: Exception) {
            0L
        }
        val dir = File(context.noBackupFilesDir, "dictionary").apply { mkdirs() }
        val file = File(dir, "wordnet-$version.db")
        if (!file.exists()) {
            dir.listFiles()?.forEach { it.delete() }
            val tmp = File(dir, "copying.tmp")
            try {
                context.assets.open(ASSET).use { input -> tmp.outputStream().use { input.copyTo(it) } }
            } catch (e: FileNotFoundException) {
                missing = true
                return null
            }
            tmp.renameTo(file)
        }
        return SQLiteDatabase.openDatabase(file.path, null, SQLiteDatabase.OPEN_READONLY).also { db = it }
    }

    /** Looks a word up, trying its plain forms (forgets → forget). Call off the main thread. */
    fun lookup(raw: String): Definition? {
        val d = open() ?: return null
        val looked = WordForms.clean(raw)
        for (c in WordForms.candidates(raw)) {
            val key = entryKey(d, c) ?: continue
            d.rawQuery("SELECT shown, pron, synsets FROM entry WHERE word = ?", arrayOf(key)).use { cur ->
                if (!cur.moveToFirst()) return@use
                val shown = cur.getString(0)
                val pron = cur.getString(1).orEmpty()
                val ids = cur.getString(2).orEmpty().split(',').mapNotNull { it.toIntOrNull() }
                val meanings = ids.mapNotNull { meaning(d, it, shown) }
                if (meanings.isNotEmpty()) return Definition(looked, shown, pron, meanings)
            }
        }
        return null
    }

    /** The headword for [w]: itself if listed, or what an irregular form belongs to. */
    private fun entryKey(d: SQLiteDatabase, w: String): String? {
        d.rawQuery("SELECT 1 FROM entry WHERE word = ?", arrayOf(w)).use { if (it.moveToFirst()) return w }
        d.rawQuery("SELECT word FROM form WHERE form = ?", arrayOf(w)).use { if (it.moveToFirst()) return it.getString(0) }
        return null
    }

    private fun meaning(d: SQLiteDatabase, id: Int, headword: String): Meaning? =
        d.rawQuery("SELECT pos, def, ex, words FROM synset WHERE id = ?", arrayOf(id.toString())).use { c ->
            if (!c.moveToFirst()) return null
            Meaning(
                pos = posName(c.getString(0)),
                definition = c.getString(1).orEmpty(),
                examples = c.getString(2).orEmpty().split('\n').filter { it.isNotBlank() },
                synonyms = c.getString(3).orEmpty().split('\n').filter { it.isNotBlank() && !it.equals(headword, ignoreCase = true) },
            )
        }

    private fun posName(p: String?) = when (p) {
        "n" -> "noun"
        "v" -> "verb"
        "a", "s" -> "adjective"
        "r" -> "adverb"
        else -> "other"
    }

    companion object {
        const val ASSET = "dictionary.db"
        const val CREDIT = "Definitions from Open English WordNet (CC BY 4.0)"
    }
}
