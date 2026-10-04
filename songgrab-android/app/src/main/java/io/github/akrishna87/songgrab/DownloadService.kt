package io.github.akrishna87.songgrab

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.app.ServiceCompat
import androidx.core.content.ContextCompat
import com.yausername.youtubedl_android.YoutubeDL
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File

/**
 * Saves songs one after another in the background, with a progress notification, so a download
 * carries on if you leave the app.
 */
class DownloadService : Service() {

    companion object {
        private const val ACTION_ADD = "io.github.akrishna87.songgrab.ADD"
        private const val ACTION_CANCEL = "io.github.akrishna87.songgrab.CANCEL"
        private const val EXTRA_JOB = "job"
        private const val CHANNEL_PROGRESS = "downloads"
        private const val CHANNEL_DONE = "saved"
        private const val PROGRESS_ID = 1

        /** Queues [url] to be saved as [format] (a video at [quality]). */
        fun add(context: Context, url: String, format: Format, quality: Quality = Quality.P720) {
            val job = Job(url = url, format = format, quality = quality)
            Jobs.add(job)
            ContextCompat.startForegroundService(
                context,
                Intent(context, DownloadService::class.java).setAction(ACTION_ADD).putExtra(EXTRA_JOB, job.id),
            )
        }

        /** Tries a failed download again. */
        fun retry(context: Context, job: Job) {
            Jobs.remove(job.id)
            add(context, job.url, job.format, job.quality)
        }
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<String>(Channel.UNLIMITED)
    private var lastStartId = 0
    private var lastNotified = 0L

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        createChannels()
        scope.launch {
            for (id in queue) {
                val job = Jobs.get(id) ?: continue
                if (job.stage == Stage.Waiting) run(job)
                withContext(Dispatchers.Main) { stopIfIdle() }
            }
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        lastStartId = startId
        // Android requires the notification within seconds of starting, whatever the action.
        ServiceCompat.startForeground(
            this,
            PROGRESS_ID,
            progressNotification(),
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC else 0,
        )
        val id = intent?.getStringExtra(EXTRA_JOB)
        when (intent?.action) {
            ACTION_ADD -> if (id != null) queue.trySend(id)
            ACTION_CANCEL -> if (id != null) Jobs.cancel(id)
        }
        if (id == null || intent?.action == ACTION_CANCEL) stopIfIdle()
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        // Android stopped us: end any yt-dlp still running so it doesn't linger.
        Jobs.active().forEach { Jobs.cancel(it.id) }
        scope.cancel()
        super.onDestroy()
    }

    private fun stopIfIdle() {
        if (Jobs.active().isNotEmpty()) {
            notifyProgress(force = true)
            return
        }
        ServiceCompat.stopForeground(this, ServiceCompat.STOP_FOREGROUND_REMOVE)
        stopSelf(lastStartId)
    }

    /** Changes a job unless it was cancelled meanwhile. */
    private fun change(id: String, block: (Job) -> Job) {
        Jobs.update(id) { if (it.stage == Stage.Cancelled) it else block(it) }
        notifyProgress()
    }

    private fun run(job: Job) {
        val dir = File(cacheDir, "grab/${job.id}")
        try {
            dir.deleteRecursively()
            dir.mkdirs()
            change(job.id) { it.copy(stage = Stage.Starting) }
            val result = try {
                grab(job, dir)
            } catch (e: YoutubeDL.CanceledException) {
                throw e
            } catch (e: Exception) {
                // YouTube often changes in ways only a newer yt-dlp can handle: update and try once more.
                if (Jobs.get(job.id)?.stage == Stage.Cancelled || !Grabber.mightBeFixedByUpdate(e) || !Engine.updateAfterFailure(this)) throw e
                change(job.id) { it.copy(stage = Stage.Starting, progress = -1f, retried = true) }
                dir.deleteRecursively()
                dir.mkdirs()
                grab(job, dir)
            }
            if (Jobs.get(job.id)?.stage == Stage.Cancelled) return
            change(job.id) { it.copy(stage = Stage.Saving) }
            val info = result.info
            val uri = Saver.save(this, result.audio, info.title, info.artist, job.format)
            val art = result.thumbnail?.let { thumb ->
                runCatching { thumb.copyTo(File(Library.artDir(this), "${System.currentTimeMillis()}-${info.id}.${thumb.extension}"), overwrite = true).path }.getOrNull()
            }
            Library.add(
                this,
                Song(
                    id = info.id,
                    title = info.title,
                    artist = info.artist,
                    uri = uri.toString(),
                    durationSec = info.durationSec,
                    format = job.format.name,
                    sourceUrl = job.url,
                    savedAt = System.currentTimeMillis(),
                    art = art,
                    height = if (job.format.isVideo) info.height.takeIf { it > 0 } ?: job.quality.height else 0,
                ),
            )
            change(job.id) { it.copy(stage = Stage.Done, progress = 100f) }
            notifySaved(info, job.format)
        } catch (e: YoutubeDL.CanceledException) {
            Jobs.update(job.id) { it.copy(stage = Stage.Cancelled) }
        } catch (e: Exception) {
            if (Jobs.get(job.id)?.stage != Stage.Cancelled) {
                Jobs.update(job.id) { it.copy(stage = Stage.Failed, error = Grabber.explain(e)) }
            }
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun grab(job: Job, dir: File): Grabber.Result = Engine.use(this) {
        Grabber.grab(job.url, job.format, job.quality, dir, job.id) { event ->
            when (event) {
                is Grabber.Event.Found -> change(job.id) { it.copy(title = event.info.title, artist = event.info.artist) }
                is Grabber.Event.Progress -> change(job.id) { it.copy(stage = Stage.Downloading, progress = event.percent) }
                Grabber.Event.Converting -> change(job.id) { if (it.stage == Stage.Converting) it else it.copy(stage = Stage.Converting, progress = -1f) }
                is Grabber.Event.Part -> change(job.id) { it.copy(part = event.number, progress = 0f) }
            }
        }
    }

    private fun createChannels() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = getSystemService(NotificationManager::class.java)
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_PROGRESS, "Downloads in progress", NotificationManager.IMPORTANCE_LOW),
        )
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_DONE, "Saved songs", NotificationManager.IMPORTANCE_DEFAULT),
        )
    }

    private fun openApp(): PendingIntent = PendingIntent.getActivity(
        this,
        0,
        Intent(this, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
        PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
    )

    private fun progressNotification(): Notification {
        val active = Jobs.active()
        val current = active.firstOrNull { it.stage != Stage.Waiting } ?: active.firstOrNull()
        val builder = NotificationCompat.Builder(this, CHANNEL_PROGRESS)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentIntent(openApp())
            .setOngoing(true)
            .setOnlyAlertOnce(true)
            .setSilent(true)
            .setCategory(NotificationCompat.CATEGORY_PROGRESS)
        if (current == null) return builder.setContentTitle("SongGrab").setContentText("Getting ready…").build()
        val waiting = active.size - 1
        builder.setContentTitle(current.title ?: if (current.format.isVideo) "Saving a video" else "Saving a song")
            .setContentText(statusText(current) + if (waiting > 0) " · $waiting more waiting" else "")
        if (current.stage == Stage.Downloading && current.progress >= 0) {
            builder.setProgress(100, current.progress.toInt(), false)
        } else {
            builder.setProgress(0, 0, true)
        }
        val cancel = PendingIntent.getService(
            this,
            current.id.hashCode(),
            Intent(this, DownloadService::class.java).setAction(ACTION_CANCEL).putExtra(EXTRA_JOB, current.id),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        builder.addAction(0, "Cancel", cancel)
        return builder.build()
    }

    /** At most twice a second, so Android doesn't drop the updates. */
    private fun notifyProgress(force: Boolean = false) {
        val now = System.currentTimeMillis()
        if (!force && now - lastNotified < 500) return
        lastNotified = now
        if (Jobs.active().isEmpty()) return
        runCatching { NotificationManagerCompat.from(this).notify(PROGRESS_ID, progressNotification()) }
    }

    private fun notifySaved(info: Grabber.Info, format: Format) {
        val notification = NotificationCompat.Builder(this, CHANNEL_DONE)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("Saved: ${info.title}")
            .setContentText(listOf(info.artist, Saver.folder(format)).filter { it.isNotBlank() }.joinToString(" · "))
            .setContentIntent(openApp())
            .setAutoCancel(true)
            .build()
        // SecurityException if notifications aren't allowed; the song is saved either way.
        runCatching { NotificationManagerCompat.from(this).notify(info.id.hashCode(), notification) }
    }
}

fun statusText(job: Job): String = when (job.stage) {
    Stage.Waiting -> "Waiting"
    Stage.Starting -> if (job.retried) "Downloader updated, trying again…" else "Finding the audio…"
    Stage.Downloading -> {
        // A video comes as two downloads: the picture, then the sound.
        val what = when {
            !job.format.isVideo -> "Downloading"
            job.part >= 2 -> "Downloading the sound"
            else -> "Downloading the video"
        }
        if (job.progress >= 0) "$what · ${job.progress.toInt()}%" else "$what…"
    }
    Stage.Converting -> when (job.format) {
        Format.MP3 -> "Converting to MP3…"
        Format.MP4 -> "Putting the video together…"
        Format.M4A -> "Adding the cover and tags…"
    }
    Stage.Saving -> "Saving to ${Saver.folder(job.format)}…"
    Stage.Done -> "Saved"
    Stage.Failed -> job.error ?: "Couldn't save it"
    Stage.Cancelled -> "Cancelled"
}
