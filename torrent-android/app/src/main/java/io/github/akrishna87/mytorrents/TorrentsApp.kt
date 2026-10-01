package io.github.akrishna87.mytorrents

import android.app.Application
import android.content.Context
import android.media.MediaScannerConnection
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.os.Environment
import android.util.Log
import io.github.akrishna87.mytorrents.engine.Status
import io.github.akrishna87.mytorrents.engine.Torrent
import io.github.akrishna87.mytorrents.engine.TorrentEngine
import java.io.File
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/** Owns the torrent engine for the whole app, plus what goes around it: settings, network, notifications. */
class TorrentsApp : Application() {

    val engine by lazy { TorrentEngine(File(filesDir, "torrents")) }
    lateinit var settings: Settings
        private set

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val started = MutableStateFlow(false)
    private val metered = MutableStateFlow(false)
    private val _messages = MutableSharedFlow<String>(extraBufferCapacity = 16)

    /** Things to tell the person, shown as snackbars. */
    val messages: SharedFlow<String> = _messages

    private val _waitingForWifi = MutableStateFlow(false)

    /** True while "Wi-Fi only" is on and the phone is on mobile data. */
    val waitingForWifi: StateFlow<Boolean> = _waitingForWifi

    override fun onCreate() {
        super.onCreate()
        settings = Settings(this)
        Notifications.createChannels(this)
        engine.events = object : TorrentEngine.Events {
            override fun onFinished(torrent: Torrent) {
                scanForMedia(torrent)
                Notifications.finished(this@TorrentsApp, torrent)
            }

            override fun onMessage(message: String) {
                say(message)
            }
        }
        scope.launch(Dispatchers.IO) {
            try {
                engine.seedWhenFinished = settings.seedWhenFinished.value
                engine.start(settings.engineSettings())
                Log.i(TAG, "torrent engine started")
                started.value = true
            } catch (e: Throwable) {
                Log.e(TAG, "couldn't start the torrent engine", e)
                say("Couldn't start downloading: ${e.message}")
            }
        }
        watchNetwork()
        scope.launch {
            combine(settings.wifiOnly, metered, started) { wifiOnly, metered, started -> (wifiOnly && metered) to started }
                .collect { (waiting, started) ->
                    _waitingForWifi.value = waiting
                    if (started) engine.setNetworkAllowed(!waiting)
                }
        }
        scope.launch {
            combine(settings.downloadLimitKb, settings.uploadLimitKb) { _, _ -> }.drop(1).collect {
                engine.applySettings(settings.engineSettings())
            }
        }
        scope.launch { settings.seedWhenFinished.collect { engine.seedWhenFinished = it } }
    }

    /** Waits until libtorrent is up (it starts on a background thread when the app starts). */
    suspend fun ready(): TorrentEngine {
        started.first { it }
        return engine
    }

    fun say(message: String) {
        Log.i(TAG, message)
        _messages.tryEmit(message)
    }

    /**
     * Where downloads go: a "Torrents" folder in the phone's Download folder, so they're easy to
     * find in the Files app. If that can't be written to, the app's own folder is used instead.
     */
    fun downloadDir(): File {
        @Suppress("DEPRECATION")
        val shared = File(Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS), "Torrents")
        if (canWrite(shared)) return shared
        return File(getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS) ?: filesDir, "Torrents").also { it.mkdirs() }
    }

    private fun canWrite(dir: File): Boolean = try {
        dir.mkdirs()
        val probe = File(dir, ".mytorrents-probe")
        probe.writeText("ok")
        probe.delete()
        true
    } catch (e: Exception) {
        false
    }

    /** Tells Android about finished files, so music, videos and pictures show up in other apps straight away. */
    private fun scanForMedia(torrent: Torrent) {
        val paths = engine.files(torrent.id).filter { it.wanted }.mapNotNull { engine.filePath(torrent.id, it.index)?.path }
        if (paths.isNotEmpty()) MediaScannerConnection.scanFile(this, paths.toTypedArray(), null, null)
    }

    private fun watchNetwork() {
        val cm = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        metered.value = cm.isActiveNetworkMetered
        cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                metered.value = !caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_NOT_METERED)
            }
        })
    }

    companion object {
        const val TAG = "MyTorrents"

        /** Torrents that need the app to keep running. */
        fun isActive(t: Torrent) = t.status in setOf(Status.GettingInfo, Status.Checking, Status.Queued, Status.Downloading, Status.Seeding)
    }
}

val Context.torrentsApp: TorrentsApp get() = applicationContext as TorrentsApp
