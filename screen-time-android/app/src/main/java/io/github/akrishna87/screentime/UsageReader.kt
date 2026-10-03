package io.github.akrishna87.screentime

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.temporal.ChronoUnit

/** One day's usage: foreground time and opens per app, and screen time per hour. */
class DayUsage {
    val foregroundMs = HashMap<String, Long>()
    val opens = HashMap<String, Int>()
    val hourMs = LongArray(24)
    val total: Long get() = foregroundMs.values.sum()
}

/**
 * Works out how long each app was in use from Android's usage events: an app's time runs from
 * when one of its screens comes to the front until its last one leaves (or the screen turns off).
 * This matches Digital Wellbeing far better than the system's pre-grouped daily stats, whose
 * days don't start at midnight.
 */
class UsageReader(private val context: Context, private val zone: ZoneId) {

    /** Usage for each day from [fromDay] until [now], keyed by epoch day. Days with no use are left out. */
    fun read(fromDay: LocalDate, now: Long = System.currentTimeMillis()): Map<Long, DayUsage> {
        val start = fromDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val usm = context.getSystemService(UsageStatsManager::class.java)
        // Start a few hours early so an app that was already open at midnight counts from midnight.
        val events = usm.queryEvents(start - LOOKBACK_MS, now)
        val homes = homeScreenPackages()

        val days = HashMap<Long, DayUsage>()
        val visible = HashMap<String, MutableSet<String>>() // package -> its activities on screen
        val since = HashMap<String, Long>() // package -> when it came to the front
        var lastApp: String? = null

        fun dayOf(t: Long) = days.getOrPut(Instant.ofEpochMilli(t).atZone(zone).toLocalDate().toEpochDay()) { DayUsage() }

        // Split the time at each hour (and so each midnight) so it lands in the right hour and day.
        fun addTime(pkg: String, from: Long, to: Long) {
            var t = maxOf(from, start)
            while (t < to) {
                val at = Instant.ofEpochMilli(t).atZone(zone)
                val nextHour = at.truncatedTo(ChronoUnit.HOURS).plusHours(1).toInstant().toEpochMilli()
                val end = minOf(to, nextHour)
                val day = dayOf(t)
                day.foregroundMs.merge(pkg, end - t, Long::plus)
                day.hourMs[at.hour] += end - t
                t = end
            }
        }

        fun close(pkg: String, t: Long) {
            visible.remove(pkg)
            since.remove(pkg)?.let { addTime(pkg, it, t) }
        }

        fun closeAll(t: Long) = visible.keys.toList().forEach { close(it, t) }

        val event = UsageEvents.Event()
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val pkg = event.packageName ?: continue
            val t = event.timeStamp
            when (event.eventType) {
                UsageEvents.Event.ACTIVITY_RESUMED -> {
                    // Time on the home screen isn't counted, and leaving it to an app counts as opening that app.
                    if (pkg in homes) {
                        lastApp = null
                        continue
                    }
                    val activities = visible.getOrPut(pkg) { HashSet() }
                    if (activities.isEmpty()) {
                        since[pkg] = t
                        if (pkg != lastApp && t >= start) dayOf(t).opens.merge(pkg, 1, Int::plus)
                    }
                    activities += event.className.orEmpty()
                    lastApp = pkg
                }
                UsageEvents.Event.ACTIVITY_PAUSED, UsageEvents.Event.ACTIVITY_STOPPED -> {
                    val activities = visible[pkg] ?: continue
                    activities -= event.className.orEmpty()
                    if (activities.isEmpty()) close(pkg, t)
                }
                UsageEvents.Event.SCREEN_NON_INTERACTIVE,
                UsageEvents.Event.KEYGUARD_SHOWN,
                UsageEvents.Event.DEVICE_SHUTDOWN -> closeAll(t)
            }
        }
        // Whatever is still on screen (usually this app) counts until now.
        closeAll(now)
        return days
    }

    /**
     * The home screen app(s). Settings has a stand-in home screen that's only used while the phone
     * starts up; it has a very low priority, so it's left out and time in Settings still counts.
     */
    private fun homeScreenPackages(): Set<String> {
        val pm = context.packageManager
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_HOME)
        val homes = pm.queryIntentActivities(intent, PackageManager.MATCH_ALL)
            .filter { it.priority >= 0 }
            .mapTo(HashSet()) { it.activityInfo.packageName }
        pm.resolveActivity(intent, PackageManager.MATCH_DEFAULT_ONLY)?.activityInfo?.packageName
            ?.takeIf { it != "android" } // "android" is the "choose a home app" prompt
            ?.let { homes += it }
        return homes
    }

    private companion object {
        const val LOOKBACK_MS = 6 * 60 * 60 * 1000L
    }
}
