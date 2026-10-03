package io.github.akrishna87.daybook.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale

/** Calendar sums shared by the app, the lock screen and the widget. Plain Kotlin, so it is unit tested. */
object Agenda {

    fun weekStart(settings: Settings): DayOfWeek = if (settings.weekStartsSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY

    /** The month as rows of seven days, with nulls before the 1st and after the last day. */
    fun monthGrid(month: YearMonth, weekStart: DayOfWeek): List<List<LocalDate?>> {
        val lead = (month.atDay(1).dayOfWeek.value - weekStart.value + 7) % 7
        val cells = List<LocalDate?>(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        val padded = cells + List((7 - cells.size % 7) % 7) { null }
        return padded.chunked(7)
    }

    /** "M T W T F S S", starting on [weekStart]. */
    fun weekdayLetters(weekStart: DayOfWeek, locale: Locale = Locale.getDefault()): List<String> =
        (0 until 7).map { weekStart.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale) }

    /** A day's events: all-day ones first, then by start time, then by title. */
    fun eventsOn(day: LocalDate, events: List<Event>): List<Event> =
        events.filter { it.occursOn(day) }.sortedWith(
            compareBy<Event>({ !(it.allDay || it.date.isBefore(day)) }, { if (it.date.isBefore(day)) LocalTime.MIN else it.start }, { it.title.lowercase() }),
        )

    /** Days in the range that have at least one event, for the dots under the calendar's numbers. */
    fun eventColorsByDay(events: List<Event>, from: LocalDate, until: LocalDate): Map<LocalDate, Int> {
        val out = HashMap<LocalDate, Int>()
        for (e in events.sortedWith(compareBy<Event>({ it.date }, { it.start }))) {
            var d = maxOf(e.date, from)
            val last = minOf(e.endDate, until.minusDays(1))
            while (!d.isAfter(last)) {
                out.putIfAbsent(d, e.color)
                d = d.plusDays(1)
            }
        }
        return out
    }

    fun timeFormatter(is24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)

    /** "08:45 - 09:45", "All day", or for an event that runs over midnight, "From 22:00" and "Until 02:00". */
    fun timeLabel(e: Event, day: LocalDate, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String {
        val f = timeFormatter(is24Hour, locale)
        val start = e.start
        if (start == null) return "All day"
        val startsToday = e.date == day
        val endsToday = e.endDate == day
        return when {
            startsToday && endsToday -> e.end?.let { "${f.format(start)} - ${f.format(it)}" } ?: f.format(start)
            startsToday -> "From ${f.format(start)}"
            endsToday && e.end != null -> "Until ${f.format(e.end)}"
            else -> "All day"
        }
    }

    /** True once the event is over, so the day's list can fade it. */
    fun isPast(e: Event, day: LocalDate, now: LocalDateTime): Boolean {
        if (e.endDate.isBefore(now.toLocalDate())) return true
        if (e.endDate.isAfter(now.toLocalDate()) || day != now.toLocalDate()) return false
        val end = e.end ?: return false
        return end != LocalTime.MIDNIGHT && !end.isAfter(now.toLocalTime())
    }

    /** How much of the year has gone, counting today: 75 on 2 October 2026. */
    fun yearPercent(day: LocalDate): Int = day.dayOfYear * 100 / day.lengthOfYear()

    /** Days after today until the year ends: 90 on 2 October 2026. */
    fun daysLeft(day: LocalDate): Int = day.lengthOfYear() - day.dayOfYear

    /** Tasks still to do, soonest due first, then the ones without a date in the order they were added. */
    fun openTasks(tasks: List<Task>): List<Task> =
        tasks.filter { !it.done }.sortedWith(compareBy<Task>({ it.due == null }, { it.due }, { it.createdAt }))

    fun doneTasks(tasks: List<Task>): List<Task> = tasks.filter { it.done }.sortedByDescending { it.doneAt }

    /** "Today", "Tomorrow", "Yesterday", "Fri 9 Oct", or the date with the year when it's another year. */
    fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String =
        when (ChronoUnit.DAYS.between(today, day)) {
            0L -> "Today"
            1L -> "Tomorrow"
            -1L -> "Yesterday"
            else -> DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE d MMM" else "d MMM yyyy", locale).format(day)
        }

    /**
     * Turns one occurrence from the phone's calendar into an [Event]. An end at exactly midnight
     * belongs to the day before, so a one-day all-day event doesn't spill into the next day.
     */
    fun deviceEvent(id: String, title: String, start: LocalDateTime, end: LocalDateTime, allDay: Boolean, color: Int): Event {
        val finish = maxOf(start, end)
        val endDay = if (finish.toLocalTime() == LocalTime.MIDNIGHT && finish.isAfter(start)) finish.toLocalDate().minusDays(1) else finish.toLocalDate()
        return Event(
            id = id,
            title = title.ifBlank { "(No title)" },
            date = start.toLocalDate(),
            endDate = endDay,
            start = if (allDay) null else start.toLocalTime(),
            end = if (allDay) null else finish.toLocalTime(),
            color = if (color == 0) Colors.Silver else color or 0xFF000000.toInt(),
            fromDevice = true,
        )
    }
}
