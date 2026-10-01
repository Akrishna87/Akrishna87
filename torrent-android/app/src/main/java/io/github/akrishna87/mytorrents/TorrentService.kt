package io.github.akrishna87.mytorrents

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import android.os.PowerManager
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

/**
 * Keeps the app alive (with a notification) while torrents are downloading or sharing,
 * so they carry on with the screen off or another app open. Stops itself once nothing is running.
 */
class TorrentService : Service() {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main)
    private var watcher: Job? = null
    private var wakeLock: PowerManager.WakeLock? = null

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val app = torrentsApp
        try {
            ServiceCompat.startForeground(
                this,
                Notifications.ONGOING_ID,
                Notifications.ongoing(this, app.engine.torrents.value, app.waitingForWifi.value),
                when {
                    Build.VERSION.SDK_INT >= 34 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
                    Build.VERSION.SDK_INT >= 29 -> ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                    else -> 0
                },
            )
        } catch (e: Exception) {
            // Android didn't allow it right now (e.g. restarted in the background); the app picks up again when opened.
            stopSelf()
            return START_NOT_STICKY
        }
        if (intent?.action == ACTION_PAUSE_ALL) scope.launch { app.ready().pauseAll() }
        if (watcher == null) watcher = scope.launch { watch(app) }
        return START_STICKY
    }

    private suspend fun watch(app: TorrentsApp) {
        app.ready()
        var idleLooks = 0
        while (true) {
            val list = app.engine.torrents.value
            val waiting = app.waitingForWifi.value
            val active = list.any(TorrentsApp::isActive)
            holdWakeLock(active && !waiting)
            // A torrent that was just added takes a moment to show up, so don't stop on the first idle look.
            idleLooks = if (active) 0 else idleLooks + 1
            if (idleLooks >= 3) {
                ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
                stopSelf()
                watcher = null
                return
            }
            try {
                NotificationManagerCompat.from(this).notify(Notifications.ONGOING_ID, Notifications.ongoing(this, list, waiting))
            } catch (e: SecurityException) {
                // Notifications are off; the service still runs.
            }
            delay(2000)
        }
    }

    private fun holdWakeLock(hold: Boolean) {
        if (hold && wakeLock == null) {
            wakeLock = getSystemService(PowerManager::class.java)
                .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, "MyTorrents:downloading")
                .apply {
                    setReferenceCounted(false)
                    acquire()
                }
        } else if (!hold && wakeLock != null) {
            wakeLock?.release()
            wakeLock = null
        }
    }

    override fun onDestroy() {
        holdWakeLock(false)
        scope.cancel()
        super.onDestroy()
    }

    companion object {
        const val ACTION_PAUSE_ALL = "io.github.akrishna87.mytorrents.PAUSE_ALL"

        fun start(context: Context) {
            try {
                ContextCompat.startForegroundService(context, Intent(context, TorrentService::class.java))
            } catch (e: Exception) {
                // Not allowed from the background; it'll be started next time the app is opened.
            }
        }
    }
}
