package io.github.akrishna87.screentime

import java.time.LocalDate
import java.time.LocalTime
import java.time.temporal.TemporalAdjusters

/**
 * The earlier period a day, week or month is compared with. While the period on screen is still
 * going, it's compared with the one before *up to the same point* (today until 10:30 against
 * yesterday until 10:30), so a morning check doesn't always read "less than yesterday".
 *
 * Covers [start]..[end]; when [partial], only the time before now on [end] counts.
 */
data class PreviousWindow(val start: LocalDate, val end: LocalDate, val partial: Boolean, val against: String)

fun previousWindow(span: Span, today: LocalDate): PreviousWindow {
    val going = span.contains(today)
    return when (span.period) {
        Period.DAY -> {
            val day = span.start.minusDays(1)
            PreviousWindow(day, day, going, if (going) "yesterday at this time" else "the day before")
        }
        Period.WEEK -> {
            val start = span.start.minusWeeks(1)
            if (going) PreviousWindow(start, today.minusWeeks(1), true, "last week at this point")
            else PreviousWindow(start, start.plusDays(6), false, "the week before")
        }
        Period.MONTH -> {
            val start = span.start.minusMonths(1)
            // On the 31st, "the same point last month" is the last day of a shorter month.
            if (going) PreviousWindow(start, today.minusMonths(1), true, "last month at this point")
            else PreviousWindow(start, start.with(TemporalAdjusters.lastDayOfMonth()), false, "the month before")
        }
    }
}

/** Screen time on a day before [time], from that day's hourly totals (the current hour pro rata). */
fun usedBefore(hours: List<HourTotal>, time: LocalTime): Long = hours.sumOf {
    when {
        it.hour < time.hour -> it.foregroundMs
        it.hour == time.hour -> it.foregroundMs * (time.minute * 60 + time.second) / 3600
        else -> 0L
    }
}

/** This period's screen time against the earlier one's. */
data class Trend(val current: Long, val previous: Long, val against: String) {
    val difference: Long get() = current - previous

    /** Under a minute either way reads as "about the same". */
    val same: Boolean get() = kotlin.math.abs(difference) < 60_000

    /** How big the change is, in percent of the earlier time; null when there was none before. */
    val percent: Long? get() = if (previous > 0) (kotlin.math.abs(difference) * 100 + previous / 2) / previous else null
}
