package io.github.akrishna87.screentime

import io.github.akrishna87.screentime.ui.trendText
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class TrendTest {
    private val today = LocalDate.of(2026, 10, 3) // a Saturday
    private val minute = 60_000L

    @Test
    fun todayIsComparedWithYesterdayUpToNow() {
        val w = previousWindow(Span(Period.DAY, today, today), today)
        assertEquals(PreviousWindow(today.minusDays(1), today.minusDays(1), true, "yesterday at this time"), w)
    }

    @Test
    fun anEarlierDayIsComparedWithTheWholeDayBefore() {
        val day = today.minusDays(3)
        val w = previousWindow(Span(Period.DAY, day, day), today)
        assertEquals(PreviousWindow(day.minusDays(1), day.minusDays(1), false, "the day before"), w)
    }

    @Test
    fun thisWeekIsComparedWithLastWeekUpToTheSameDay() {
        val start = LocalDate.of(2026, 9, 27) // Sunday
        val w = previousWindow(Span(Period.WEEK, start, start.plusDays(6)), today)
        assertEquals(PreviousWindow(LocalDate.of(2026, 9, 20), LocalDate.of(2026, 9, 26), true, "last week at this point"), w)
    }

    @Test
    fun aFinishedWeekIsComparedWithTheWholeWeekBefore() {
        val start = LocalDate.of(2026, 9, 20)
        val w = previousWindow(Span(Period.WEEK, start, start.plusDays(6)), today)
        assertEquals(PreviousWindow(LocalDate.of(2026, 9, 13), LocalDate.of(2026, 9, 19), false, "the week before"), w)
    }

    @Test
    fun thisMonthIsComparedWithLastMonthUpToTheSameDate() {
        val w = previousWindow(Span(Period.MONTH, LocalDate.of(2026, 10, 1), LocalDate.of(2026, 10, 31)), today)
        assertEquals(PreviousWindow(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 3), true, "last month at this point"), w)
    }

    @Test
    fun theLastDayOfALongMonthMatchesTheLastDayOfAShortOne() {
        val march31 = LocalDate.of(2026, 3, 31)
        val w = previousWindow(Span(Period.MONTH, LocalDate.of(2026, 3, 1), march31), march31)
        assertEquals(LocalDate.of(2026, 2, 28), w.end)
    }

    @Test
    fun aFinishedMonthIsComparedWithTheWholeMonthBefore() {
        val w = previousWindow(Span(Period.MONTH, LocalDate.of(2026, 3, 1), LocalDate.of(2026, 3, 31)), today)
        assertEquals(PreviousWindow(LocalDate.of(2026, 2, 1), LocalDate.of(2026, 2, 28), false, "the month before"), w)
    }

    @Test
    fun usedBeforeCountsEarlierHoursAndPartOfTheCurrentOne() {
        val hours = listOf(HourTotal(0, 8, 30 * minute), HourTotal(0, 10, 40 * minute), HourTotal(0, 12, 50 * minute))
        // 10:30 → all of 8 o'clock, half of 10 o'clock, none of 12 o'clock.
        assertEquals(30 * minute + 20 * minute, usedBefore(hours, LocalTime.of(10, 30)))
        assertEquals(0L, usedBefore(hours, LocalTime.MIDNIGHT))
    }

    @Test
    fun trendWording() {
        assertEquals(
            "25 min less than yesterday at this time (−20%)",
            trendText(Trend(100 * minute, 125 * minute, "yesterday at this time")),
        )
        assertEquals("1 h 30 min more than the week before (+50%)", trendText(Trend(270 * minute, 180 * minute, "the week before")))
        assertEquals("About the same as the day before", trendText(Trend(60 * minute + 30_000, 60 * minute, "the day before")))
        assertEquals("10 min more than the day before", trendText(Trend(10 * minute, 0, "the day before")))
    }

    @Test
    fun underAMinuteIsTheSame() {
        assertTrue(Trend(59_000, 0, "x").same)
        assertFalse(Trend(60_000, 0, "x").same)
    }
}
