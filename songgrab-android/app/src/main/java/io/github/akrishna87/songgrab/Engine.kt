package io.github.akrishna87.songgrab

import android.content.Context
import com.yausername.ffmpeg.FFmpeg
import com.yausername.youtubedl_android.YoutubeDL
import java.util.concurrent.TimeUnit
import java.util.concurrent.locks.ReentrantReadWriteLock
import kotlin.concurrent.read
import kotlin.concurrent.write

/**
 * The download engine: yt-dlp with its own Python, and ffmpeg. YouTube changes often and older
 * yt-dlp versions stop working, so the engine updates itself from yt-dlp's GitHub releases every
 * couple of days, and right away when a download fails in a way a newer version might fix.
 */
object Engine {
    private const val PREFS = "engine"
    private const val LAST_UPDATE = "lastUpdate"
    private val STALE_AFTER = TimeUnit.DAYS.toMillis(2)
    private val RETRY_UPDATE_AFTER = TimeUnit.HOURS.toMillis(1)

    /** Downloads share the engine; an update replaces it, so it waits for them to finish. */
    private val lock = ReentrantReadWriteLock()

    @Volatile
    private var ready = false

    /** Unpacks Python and ffmpeg on first use (a few seconds), then returns at once. */
    fun init(context: Context) {
        if (ready) return
        synchronized(this) {
            if (ready) return
            val app = context.applicationContext
            YoutubeDL.getInstance().init(app)
            FFmpeg.getInstance().init(app)
            ready = true
        }
    }

    fun <T> use(context: Context, block: () -> T): T {
        init(context)
        return lock.read(block)
    }

    /** The yt-dlp version in use, e.g. "2026.09.01", or "built-in" before the first update. */
    fun version(context: Context): String =
        YoutubeDL.getInstance().version(context.applicationContext)?.takeIf { it.isNotBlank() } ?: "built-in"

    fun lastUpdate(context: Context): Long = prefs(context).getLong(LAST_UPDATE, 0)

    /** Fetches the newest yt-dlp. True if it changed. */
    fun update(context: Context): Boolean {
        init(context)
        val status = lock.write {
            YoutubeDL.getInstance().updateYoutubeDL(context.applicationContext, YoutubeDL.UpdateChannel.STABLE)
        }
        prefs(context).edit().putLong(LAST_UPDATE, System.currentTimeMillis()).apply()
        return status == YoutubeDL.UpdateStatus.DONE
    }

    /** Updates quietly if it's been a couple of days. Never throws. */
    fun updateIfStale(context: Context) {
        if (System.currentTimeMillis() - lastUpdate(context) < STALE_AFTER) return
        runCatching { update(context) }
    }

    /** After a failed download: updates unless that was already tried in the last hour. True if a new version arrived. */
    fun updateAfterFailure(context: Context): Boolean {
        if (System.currentTimeMillis() - lastUpdate(context) < RETRY_UPDATE_AFTER) return false
        return runCatching { update(context) }.getOrDefault(false)
    }

    private fun prefs(context: Context) = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
