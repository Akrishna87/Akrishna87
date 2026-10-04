package io.github.akrishna87.songgrab

import android.content.Context
import android.net.Uri
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * A saved song or video. [uri] is the file in Music/SongGrab (or Movies/SongGrab); [art] a copy
 * of its cover in app storage; [height] a video's picture height, e.g. 720.
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val uri: String,
    val durationSec: Int,
    val format: String,
    val sourceUrl: String,
    val savedAt: Long,
    val art: String?,
    val height: Int = 0,
) {
    val isVideo: Boolean get() = Format.of(format).isVideo
}

/** The songs and videos SongGrab has saved, newest first, kept in a small JSON file. */
object Library {
    private const val FILE = "songs.json"
    private val _songs = MutableStateFlow<List<Song>>(emptyList())
    val songs: StateFlow<List<Song>> = _songs.asStateFlow()
    private var loaded = false

    @Synchronized
    fun load(context: Context) {
        if (loaded) return
        val file = File(context.filesDir, FILE)
        _songs.value = if (file.exists()) runCatching { fromJson(file.readText()) }.getOrDefault(emptyList()) else emptyList()
        loaded = true
    }

    @Synchronized
    fun add(context: Context, song: Song) {
        load(context)
        save(context, listOf(song) + _songs.value.filterNot { it.uri == song.uri })
    }

    /** Takes the song off the list and deletes its cover copy. The audio file is left alone. */
    @Synchronized
    fun remove(context: Context, song: Song) {
        load(context)
        song.art?.let { File(it).delete() }
        save(context, _songs.value.filterNot { it.uri == song.uri })
    }

    /** Drops songs whose file was deleted from another app. */
    fun prune(context: Context) {
        load(context)
        val gone = _songs.value.filterNot { Saver.exists(context, Uri.parse(it.uri)) }
        gone.forEach { remove(context, it) }
    }

    fun artDir(context: Context) = File(context.filesDir, "art").apply { mkdirs() }

    private fun save(context: Context, list: List<Song>) {
        _songs.value = list
        val file = File(context.filesDir, FILE)
        val tmp = File(context.filesDir, "$FILE.tmp")
        tmp.writeText(toJson(list))
        tmp.renameTo(file)
    }

    fun toJson(list: List<Song>): String = JSONArray().apply {
        list.forEach { s ->
            put(
                JSONObject()
                    .put("id", s.id).put("title", s.title).put("artist", s.artist).put("uri", s.uri)
                    .put("duration", s.durationSec).put("format", s.format).put("source", s.sourceUrl)
                    .put("savedAt", s.savedAt).put("art", s.art ?: JSONObject.NULL).put("height", s.height),
            )
        }
    }.toString()

    fun fromJson(text: String): List<Song> {
        val array = JSONArray(text)
        return (0 until array.length()).mapNotNull { i ->
            val o = array.optJSONObject(i) ?: return@mapNotNull null
            val uri = o.optString("uri").takeIf { it.isNotEmpty() } ?: return@mapNotNull null
            Song(
                id = o.optString("id"),
                title = o.optString("title"),
                artist = o.optString("artist"),
                uri = uri,
                durationSec = o.optInt("duration"),
                format = o.optString("format", Format.MP3.name),
                sourceUrl = o.optString("source"),
                savedAt = o.optLong("savedAt"),
                art = if (o.isNull("art")) null else o.optString("art").takeIf { it.isNotEmpty() },
                height = o.optInt("height"),
            )
        }
    }
}
