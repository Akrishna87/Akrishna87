package io.github.akrishna87.myvideos

import android.content.Context

/** How far into a video someone got, and when. */
data class Progress(val positionMs: Long, val durationMs: Long, val updatedAt: Long) {
    val fraction: Float get() = if (durationMs > 0) (positionMs.toFloat() / durationMs).coerceIn(0f, 1f) else 0f

    /** Close enough to the end (the credits) to count as watched. */
    val finished: Boolean
        get() = durationMs > 0 && (positionMs >= durationMs * 0.95 || durationMs - positionMs < 15_000)

    /** Where to pick up from, or 0 to start at the beginning. */
    val resumeAt: Long get() = if (!finished && positionMs > 5_000) positionMs else 0L
}

/** Remembers where each video was left, in the app's SharedPreferences. Keys come from [Video.key]. */
class WatchProgress(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("progress", Context.MODE_PRIVATE)

    fun get(key: String): Progress? = parse(prefs.getString(key, null))

    fun all(): Map<String, Progress> =
        prefs.all.mapNotNull { (k, v) -> parse(v as? String)?.let { k to it } }.toMap()

    fun save(key: String, positionMs: Long, durationMs: Long) {
        if (durationMs <= 0) return
        prefs.edit().putString(key, "${positionMs.coerceIn(0, durationMs)}|$durationMs|${System.currentTimeMillis()}").apply()
    }

    fun markWatched(key: String, durationMs: Long) = save(key, durationMs, durationMs.coerceAtLeast(1))

    fun clear(key: String) = prefs.edit().remove(key).apply()

    private fun parse(s: String?): Progress? {
        val parts = s?.split('|') ?: return null
        if (parts.size != 3) return null
        return Progress(parts[0].toLongOrNull() ?: return null, parts[1].toLongOrNull() ?: return null, parts[2].toLongOrNull() ?: 0L)
    }
}
