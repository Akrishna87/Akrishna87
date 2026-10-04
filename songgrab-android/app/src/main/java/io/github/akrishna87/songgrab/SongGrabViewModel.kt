package io.github.akrishna87.songgrab

import android.app.Application
import android.content.Context
import android.net.Uri
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class SongGrabViewModel(app: Application) : AndroidViewModel(app) {

    private val prefs = app.getSharedPreferences("settings", Context.MODE_PRIVATE)

    val songs = Library.songs
    val jobs = Jobs.all
    val player = PlayerConnection()

    private val _format = MutableStateFlow(Format.of(prefs.getString("format", null)))
    val format: StateFlow<Format> = _format.asStateFlow()

    /** The engine's version, and whether an update is running or what it found. */
    data class EngineState(val version: String = "…", val updating: Boolean = false, val message: String? = null)

    private val _engine = MutableStateFlow(EngineState())
    val engine: StateFlow<EngineState> = _engine.asStateFlow()

    init {
        viewModelScope.launch(Dispatchers.IO) {
            Library.load(app)
            Library.prune(app)
        }
        viewModelScope.launch(Dispatchers.IO) {
            // Unpack the engine now so the first download starts faster, and keep yt-dlp fresh.
            runCatching { Engine.init(app) }
            _engine.value = _engine.value.copy(version = runCatching { Engine.version(app) }.getOrDefault("built-in"))
            Engine.updateIfStale(app)
            _engine.value = _engine.value.copy(version = runCatching { Engine.version(app) }.getOrDefault("built-in"))
        }
    }

    /** Counts links shared to the app, so the screen can show the download list for each one. */
    private val _shares = MutableStateFlow(0)
    val shares: StateFlow<Int> = _shares.asStateFlow()

    fun shared() {
        _shares.value++
    }

    fun setFormat(format: Format) {
        _format.value = format
        prefs.edit().putString("format", format.name).apply()
    }

    /** True the first time it's called for [permission]: ask once, then carry on without it. */
    fun firstAsk(permission: String): Boolean {
        val key = "asked:$permission"
        if (prefs.getBoolean(key, false)) return false
        prefs.edit().putBoolean(key, true).apply()
        return true
    }

    fun updateEngine() {
        if (_engine.value.updating) return
        val app = getApplication<Application>()
        _engine.value = _engine.value.copy(updating = true, message = null)
        viewModelScope.launch(Dispatchers.IO) {
            val message = try {
                if (Engine.update(app)) "Updated to ${Engine.version(app)}." else "Already up to date."
            } catch (e: Exception) {
                "Couldn't update: ${Grabber.explain(e)}"
            }
            _engine.value = EngineState(version = Engine.version(app), updating = false, message = message)
        }
    }

    fun play(song: Song) {
        val list = songs.value
        player.play(list, list.indexOfFirst { it.uri == song.uri }.coerceAtLeast(0))
    }

    fun shuffleAll() = player.shuffle(songs.value)

    /** Removes the song from the list, and deletes its file too if [deleteFile]. False if the file couldn't be deleted. */
    suspend fun remove(song: Song, deleteFile: Boolean): Boolean = withContext(Dispatchers.IO) {
        val app = getApplication<Application>()
        val deleted = !deleteFile || Saver.delete(app, Uri.parse(song.uri))
        if (deleted) {
            withContext(Dispatchers.Main) { player.removeSong(song) }
            Library.remove(app, song)
        }
        deleted
    }

    override fun onCleared() {
        player.disconnect()
        super.onCleared()
    }
}
