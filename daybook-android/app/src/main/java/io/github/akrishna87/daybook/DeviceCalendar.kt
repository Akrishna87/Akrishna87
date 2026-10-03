package io.github.akrishna87.daybook

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.provider.CalendarContract.Instances
import androidx.core.content.ContextCompat
import io.github.akrishna87.daybook.model.Agenda
import io.github.akrishna87.daybook.model.Event
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset

/** Reads (never writes) the phone's own calendars, such as Google Calendar and public holidays. */
object DeviceCalendar {

    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) == PackageManager.PERMISSION_GRANTED

    /** Events from the phone's calendars between [from] and [until] (exclusive), if the user turned them on. */
    fun eventsIfEnabled(context: Context, enabled: Boolean, from: LocalDate, until: LocalDate): List<Event> =
        if (enabled) events(context, from, until) else emptyList()

    fun events(context: Context, from: LocalDate, until: LocalDate): List<Event> {
        if (!hasPermission(context)) return emptyList()
        val zone = ZoneId.systemDefault()
        // Widen by a day each way: all-day events are stored in UTC and may sit just outside local midnight.
        val begin = from.minusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val end = until.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli()
        val uri = Instances.CONTENT_URI.buildUpon().also {
            ContentUris.appendId(it, begin)
            ContentUris.appendId(it, end)
        }.build()
        val projection = arrayOf(Instances.EVENT_ID, Instances.TITLE, Instances.BEGIN, Instances.END, Instances.ALL_DAY, Instances.DISPLAY_COLOR)
        val out = ArrayList<Event>()
        runCatching {
            context.contentResolver.query(uri, projection, "${Instances.VISIBLE} = 1", null, "${Instances.BEGIN} ASC")?.use { c ->
                while (c.moveToNext()) {
                    val allDay = c.getInt(4) == 1
                    // All-day events are stored as UTC midnights.
                    val z = if (allDay) ZoneOffset.UTC else zone
                    val startMs = c.getLong(2)
                    val e = Agenda.deviceEvent(
                        id = "device:${c.getLong(0)}:$startMs",
                        title = c.getString(1) ?: "",
                        start = Instant.ofEpochMilli(startMs).atZone(z).toLocalDateTime(),
                        end = Instant.ofEpochMilli(c.getLong(3)).atZone(z).toLocalDateTime(),
                        allDay = allDay,
                        color = c.getInt(5),
                    )
                    if (!e.endDate.isBefore(from) && e.date.isBefore(until)) out += e
                }
            }
        }
        return out
    }
}
