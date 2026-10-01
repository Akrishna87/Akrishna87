package io.github.akrishna87.mytorrents

import android.app.Application
import android.content.ActivityNotFoundException
import android.content.ClipData
import android.content.Intent
import android.net.Uri
import android.util.Log
import android.webkit.MimeTypeMap
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.FileProvider
import androidx.core.content.IntentCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.akrishna87.mytorrents.engine.Status
import io.github.akrishna87.mytorrents.engine.Torrent
import io.github.akrishna87.mytorrents.engine.TorrentFile
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.net.URLDecoder

sealed interface Screen {
    data object List : Screen
    data class Details(val id: String) : Screen
    data object Settings : Screen
}

enum class Filter(val label: String) { All("All"), Active("Active"), Done("Done") }

/** A torrent another app sent us, waiting for the person to say "Download". */
sealed interface PendingAdd {
    val name: String

    data class Magnet(val link: String, override val name: String) : PendingAdd

    class File(val bytes: ByteArray, override val name: String) : PendingAdd
}

class TorrentsViewModel(application: Application) : AndroidViewModel(application) {
    private val app = application as TorrentsApp

    val torrents: StateFlow<List<Torrent>> = app.engine.torrents
    val settings = app.settings
    val messages = app.messages
    val waitingForWifi = app.waitingForWifi

    var screen by mutableStateOf<Screen>(Screen.List)
        private set
    var filter by mutableStateOf(Filter.All)
    var showAdd by mutableStateOf(false)

    /** Set while asking "Add this torrent?" about a link or file from another app. */
    var pendingAdd by mutableStateOf<PendingAdd?>(null)
        private set

    init {
        // Opening the app restarts the background service if anything is still running.
        viewModelScope.launch {
            app.ready()
            delay(1500)
            if (torrents.value.any(TorrentsApp::isActive)) TorrentService.start(app)
        }
    }

    fun open(screen: Screen) {
        this.screen = screen
    }

    /** Goes back a screen; false if already on the list. */
    fun back(): Boolean {
        if (screen == Screen.List) return false
        screen = Screen.List
        return true
    }

    /**
     * Magnet links and .torrent files opened from other apps (browser, Files, share sheet), or a
     * notification tap. Anything from another app is shown first and only added once the person
     * taps Download, so no other app can make this phone download (and share) something by itself.
     */
    fun handleIntent(intent: Intent?) {
        intent ?: return
        intent.getStringExtra(MainActivity.EXTRA_TORRENT_ID)?.let {
            screen = Screen.Details(it)
            return
        }
        when (intent.action) {
            Intent.ACTION_VIEW -> {
                val uri = intent.data ?: return
                if (uri.scheme == "magnet") askMagnet(uri.toString()) else askTorrentFile(uri)
            }
            Intent.ACTION_SEND -> {
                val stream = IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
                val text = intent.getStringExtra(Intent.EXTRA_TEXT)
                when {
                    stream != null -> askTorrentFile(stream)
                    text != null -> {
                        val magnet = Regex("magnet:\\?\\S+").find(text)?.value
                        if (magnet != null) askMagnet(magnet) else app.say("That doesn't contain a magnet link")
                    }
                }
            }
        }
        // Don't ask again if the screen is rotated.
        intent.action = null
        intent.data = null
    }

    private fun askMagnet(link: String) {
        if (!link.trim().startsWith("magnet:?")) return
        pendingAdd = PendingAdd.Magnet(link, magnetName(link) ?: "a magnet link")
    }

    /** Reads the file straight away: another app's permission to read it may not last. */
    private fun askTorrentFile(uri: Uri) {
        // Only files shared through Android's content system. file:// paths would be read with this
        // app's own permissions, which isn't something another app should be able to point us at.
        if (uri.scheme != "content") {
            app.say("Can't open that file. Try opening it from your Files app.")
            return
        }
        viewModelScope.launch {
            try {
                val bytes = readTorrentFile(uri)
                val name = app.ready().torrentName(bytes)
                pendingAdd = PendingAdd.File(bytes, name)
            } catch (e: IllegalArgumentException) {
                app.say("That isn't a valid .torrent file")
            } catch (e: Exception) {
                app.say("Couldn't open the .torrent file (${e.message})")
            }
        }
    }

    /** "Download" in the "Add this torrent?" box. */
    fun confirmPendingAdd() {
        when (val p = pendingAdd ?: return) {
            is PendingAdd.Magnet -> addMagnet(p.link)
            is PendingAdd.File -> addTorrentBytes(p.bytes)
        }
        pendingAdd = null
    }

    fun cancelPendingAdd() {
        pendingAdd = null
    }

    fun addMagnet(link: String) {
        Log.i(TorrentsApp.TAG, "adding a magnet link")
        viewModelScope.launch {
            val engine = app.ready()
            try {
                val dir = withContext(Dispatchers.IO) { app.downloadDir() }
                val known = torrents.value.map { it.id }.toSet()
                val id = engine.addMagnet(link, dir)
                Log.i(TorrentsApp.TAG, "added magnet $id into $dir")
                app.say(if (id in known) "Already in your list" else "Added ${magnetName(link) ?: "the magnet link"}")
                showAdd = false
                TorrentService.start(app)
            } catch (e: IllegalArgumentException) {
                Log.w(TorrentsApp.TAG, "bad magnet link", e)
                app.say("That isn't a valid magnet link")
            } catch (e: Exception) {
                Log.w(TorrentsApp.TAG, "couldn't add a magnet link", e)
                app.say("Couldn't add it: ${e.message}")
            }
        }
    }

    /** A .torrent file the person picked in the Add sheet. */
    fun addTorrentFile(uri: Uri) {
        viewModelScope.launch {
            try {
                addTorrentBytes(readTorrentFile(uri))
            } catch (e: Exception) {
                app.say("Couldn't open the .torrent file (${e.message})")
            }
        }
    }

    private fun addTorrentBytes(bytes: ByteArray) {
        viewModelScope.launch {
            val engine = app.ready()
            try {
                val dir = withContext(Dispatchers.IO) { app.downloadDir() }
                val known = torrents.value.map { it.id }.toSet()
                val id = engine.addTorrentFile(bytes, dir)
                app.say(if (id in known) "Already in your list" else "Added the torrent")
                showAdd = false
                TorrentService.start(app)
            } catch (e: IllegalArgumentException) {
                app.say("That isn't a valid .torrent file")
            } catch (e: Exception) {
                app.say("Couldn't add the torrent (${e.message})")
            }
        }
    }

    private suspend fun readTorrentFile(uri: Uri): ByteArray = withContext(Dispatchers.IO) {
        app.contentResolver.openInputStream(uri)?.use { input ->
            // Real .torrent files are at most a few MB; don't read something huge into memory.
            val out = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                out.write(buffer, 0, n)
                if (out.size() > MAX_TORRENT_FILE) throw IllegalArgumentException("too big for a .torrent file")
            }
            out.toByteArray()
        } ?: throw IllegalStateException("couldn't open the file")
    }

    /** The "dn" (display name) of a magnet link. Uri.getQueryParameter doesn't work on them: Android sees them as "opaque". */
    private fun magnetName(link: String): String? =
        Regex("[?&]dn=([^&]*)").find(link)?.groupValues?.get(1)
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.takeIf { it.isNotBlank() }

    fun togglePause(t: Torrent) {
        val engine = app.engine
        when (t.status) {
            Status.Paused, Status.Finished, Status.Error -> {
                engine.resume(t.id)
                TorrentService.start(app)
            }
            else -> engine.pause(t.id)
        }
    }

    fun pauseAll() = app.engine.pauseAll()

    fun resumeAll() {
        app.engine.torrents.value.filter { it.status == Status.Paused || it.status == Status.Error }.forEach { app.engine.resume(it.id) }
        TorrentService.start(app)
    }

    fun remove(t: Torrent, deleteFiles: Boolean) {
        app.engine.remove(t.id, deleteFiles)
        if (screen == Screen.Details(t.id)) screen = Screen.List
        app.say(if (deleteFiles) "Removed ${t.name} and its files" else "Removed ${t.name} (files kept)")
    }

    /** A torrent's files, refreshed every second while the screen shows them. */
    fun files(id: String): Flow<List<TorrentFile>> = flow {
        app.ready()
        while (true) {
            emit(app.engine.files(id))
            delay(1000)
        }
    }.flowOn(Dispatchers.IO)

    fun setWanted(id: String, file: TorrentFile, wanted: Boolean) {
        app.engine.setFileWanted(id, file.index, wanted)
        if (wanted) TorrentService.start(app)
    }

    fun openFile(id: String, file: TorrentFile) {
        if (file.done < file.size) {
            app.say("That file hasn't finished downloading yet")
            return
        }
        val path = app.engine.filePath(id, file.index) ?: return
        // Only files inside the download folders can be shared (see res/xml/file_paths.xml).
        val uri = try {
            FileProvider.getUriForFile(app, "${app.packageName}.files", path)
        } catch (e: IllegalArgumentException) {
            app.say("Open ${path.name} from your Files app instead (it's in ${path.parent})")
            return
        }
        val ext = path.extension.lowercase()
        val mime = MimeTypeMap.getSingleton().getMimeTypeFromExtension(ext) ?: "*/*"
        val view = Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, mime)
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        view.clipData = ClipData.newRawUri(path.name, uri)
        try {
            app.startActivity(Intent.createChooser(view, path.name).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        } catch (e: ActivityNotFoundException) {
            app.say("No app on this phone can open ${path.name}")
        }
    }

    fun shareMagnet(t: Torrent) {
        val link = app.engine.magnetLink(t.id) ?: return
        val send = Intent(Intent.ACTION_SEND).setType("text/plain").putExtra(Intent.EXTRA_TEXT, link)
        app.startActivity(Intent.createChooser(send, "Share magnet link").addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    private companion object {
        const val MAX_TORRENT_FILE = 10 * 1024 * 1024
    }

    /** The folder new downloads go into, for showing in Settings. */
    suspend fun downloadFolder(): String = withContext(Dispatchers.IO) { app.downloadDir().path }
}
