package io.github.akrishna87.mytorrents

import android.Manifest
import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import io.github.akrishna87.mytorrents.engine.Status
import io.github.akrishna87.mytorrents.engine.Torrent

object Notifications {
    const val ONGOING_ID = 1
    private const val CHANNEL_ACTIVE = "active"
    private const val CHANNEL_DONE = "done"

    fun createChannels(context: Context) {
        val nm = context.getSystemService(NotificationManager::class.java)
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_ACTIVE, "Downloading", NotificationManager.IMPORTANCE_LOW).apply {
                description = "Shows while torrents are downloading or sharing"
                setShowBadge(false)
            },
        )
        nm.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Finished downloads", NotificationManager.IMPORTANCE_DEFAULT).apply {
                description = "Tells you when a download has finished"
            },
        )
    }

    /** The notification that stays up while torrents are running. */
    fun ongoing(context: Context, torrents: List<Torrent>, waitingForWifi: Boolean): Notification {
        val active = torrents.filter(TorrentsApp::isActive)
        val downloading = active.filter { it.status != Status.Seeding }
        val down = active.sumOf { it.downloadRate }
        val up = active.sumOf { it.uploadRate }
        val title = when {
            waitingForWifi -> "Waiting for Wi-Fi"
            downloading.isEmpty() && active.isNotEmpty() -> "Sharing ${plural(active.size, "torrent")}"
            downloading.isNotEmpty() -> "Downloading ${plural(downloading.size, "torrent")}"
            else -> "My Torrents"
        }
        val text = if (waitingForWifi) "Downloads continue when you're on Wi-Fi"
        else "↓ ${formatSpeed(down)}  ↑ ${formatSpeed(up)}"
        val builder = NotificationCompat.Builder(context, CHANNEL_ACTIVE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle(title)
            .setContentText(text)
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setForegroundServiceBehavior(NotificationCompat.FOREGROUND_SERVICE_IMMEDIATE)
            .setContentIntent(openApp(context, null))
            .addAction(0, "Pause all", pauseAll(context))
        if (downloading.isNotEmpty()) {
            val wanted = downloading.sumOf { it.wantedBytes }
            val done = downloading.sumOf { it.doneBytes }
            if (wanted > 0) builder.setProgress(1000, (done * 1000 / wanted).toInt(), false)
            else builder.setProgress(0, 0, true)
            builder.setStyle(
                NotificationCompat.InboxStyle().also { style ->
                    downloading.take(5).forEach { style.addLine("${formatPercent(it.progress)} · ${it.name}") }
                    style.setSummaryText(text)
                },
            )
        }
        return builder.build()
    }

    fun finished(context: Context, torrent: Torrent) {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED &&
            android.os.Build.VERSION.SDK_INT >= 33
        ) return
        val notification = NotificationCompat.Builder(context, CHANNEL_DONE)
            .setSmallIcon(R.drawable.ic_notification)
            .setContentTitle("Download finished")
            .setContentText(torrent.name)
            .setSubText(formatBytes(torrent.wantedBytes))
            .setAutoCancel(true)
            .setContentIntent(openApp(context, torrent.id))
            .build()
        NotificationManagerCompat.from(context).notify(torrent.id.hashCode(), notification)
    }

    private fun openApp(context: Context, torrentId: String?): PendingIntent {
        val intent = Intent(context, MainActivity::class.java)
            .setFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP)
        if (torrentId != null) intent.putExtra(MainActivity.EXTRA_TORRENT_ID, torrentId)
        return PendingIntent.getActivity(
            context,
            torrentId?.hashCode() ?: 0,
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }

    private fun pauseAll(context: Context): PendingIntent = PendingIntent.getService(
        context,
        1,
        Intent(context, TorrentService::class.java).setAction(TorrentService.ACTION_PAUSE_ALL),
        PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
    )

    private fun plural(n: Int, word: String) = if (n == 1) "1 $word" else "$n ${word}s"
}
