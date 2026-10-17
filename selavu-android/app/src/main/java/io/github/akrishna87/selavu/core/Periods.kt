package io.github.akrishna87.selavu.core

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.time.temporal.TemporalAdjusters
import java.util.Locale

enum class PeriodType(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month"), YEAR("Year") }

/** A stretch of days, [start] to [end] inclusive. */
data class Period(val type: PeriodType, val start: LocalDate, val end: LocalDate) {
    operator fun contains(date: LocalDate) = !date.isBefore(start) && !date.isAfter(end)

    val days: Int get() = (ChronoUnit.DAYS.between(start, end) + 1).toInt()

    /** The period [n] steps later (or earlier, for negative n). */
    fun shift(n: Long): Period = when (type) {
        PeriodType.DAY -> of(type, start.plusDays(n), weekStart())
        PeriodType.WEEK -> of(type, start.plusWeeks(n), weekStart())
        PeriodType.MONTH -> of(type, start.plusMonths(n), weekStart())
        PeriodType.YEAR -> of(type, start.plusYears(n), weekStart())
    }

    private fun weekStart() = if (type == PeriodType.WEEK) start.dayOfWeek else DayOfWeek.MONDAY

    fun label(today: LocalDate): String = when (type) {
        PeriodType.DAY -> when (start) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            today.plusDays(1) -> "Tomorrow"
            else -> start.format(if (start.year == today.year) DAY_FMT else DAY_YEAR_FMT)
        }
        PeriodType.WEEK -> when {
            today in this -> "This week"
            today.minusWeeks(1) in this -> "Last week"
            else -> rangeLabel(start, end, today)
        }
        PeriodType.MONTH -> start.format(if (start.year == today.year) MONTH_FMT else MONTH_YEAR_FMT)
        PeriodType.YEAR -> start.year.toString()
    }

    /** Smaller label shown under the main one, e.g. the dates of "This week". */
    fun subLabel(today: LocalDate): String? = when (type) {
        PeriodType.DAY -> if (start == today || start == today.minusDays(1) || start == today.plusDays(1)) start.format(DAY_FMT) else null
        PeriodType.WEEK -> if (today in this || today.minusWeeks(1) in this) rangeLabel(start, end, today) else null
        else -> null
    }

    companion object {
        fun of(type: PeriodType, date: LocalDate, weekStart: DayOfWeek): Period = when (type) {
            PeriodType.DAY -> Period(type, date, date)
            PeriodType.WEEK -> {
                val s = date.with(TemporalAdjusters.previousOrSame(weekStart))
                Period(type, s, s.plusDays(6))
            }
            PeriodType.MONTH -> {
                val s = date.withDayOfMonth(1)
                Period(type, s, s.with(TemporalAdjusters.lastDayOfMonth()))
            }
            PeriodType.YEAR -> {
                val s = date.withDayOfYear(1)
                Period(type, s, s.with(TemporalAdjusters.lastDayOfYear()))
            }
        }

        private val DAY_FMT = DateTimeFormatter.ofPattern("EEE, d MMM", Locale.ENGLISH)
        private val DAY_YEAR_FMT = DateTimeFormatter.ofPattern("EEE, d MMM yyyy", Locale.ENGLISH)
        private val MONTH_FMT = DateTimeFormatter.ofPattern("MMMM", Locale.ENGLISH)
        private val MONTH_YEAR_FMT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.ENGLISH)
        private val SHORT_FMT = DateTimeFormatter.ofPattern("d MMM", Locale.ENGLISH)

        private fun rangeLabel(start: LocalDate, end: LocalDate, today: LocalDate): String {
            val tail = if (end.year != today.year) " ${end.year}" else ""
            return if (start.month == end.month) "${start.dayOfMonth} – ${end.format(SHORT_FMT)}$tail"
            else "${start.format(SHORT_FMT)} – ${end.format(SHORT_FMT)}$tail"
        }
    }
}

fun Settings.weekStart(): DayOfWeek = if (weekStartsMonday) DayOfWeek.MONDAY else DayOfWeek.SUNDAY

private val DAY_HEADER_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM", Locale.ENGLISH)
private val DAY_HEADER_YEAR_FMT = DateTimeFormatter.ofPattern("EEEE, d MMMM yyyy", Locale.ENGLISH)

/** Heading for a day in the transaction list: "Today", "Yesterday" or "Monday, 13 October". */
fun dayHeading(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(if (date.year == today.year) DAY_HEADER_FMT else DAY_HEADER_YEAR_FMT)
}

fun shortDate(date: LocalDate, today: LocalDate): String = when (date) {
    today -> "Today"
    today.minusDays(1) -> "Yesterday"
    else -> date.format(DateTimeFormatter.ofPattern(if (date.year == today.year) "d MMM" else "d MMM yyyy", Locale.ENGLISH))
}
