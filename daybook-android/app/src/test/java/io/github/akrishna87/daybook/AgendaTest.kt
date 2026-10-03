package io.github.akrishna87.daybook

import io.github.akrishna87.daybook.model.Agenda
import io.github.akrishna87.daybook.model.Colors
import io.github.akrishna87.daybook.model.Event
import io.github.akrishna87.daybook.model.Task
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.YearMonth
import java.util.Locale

class AgendaTest {
    private val oct2 = LocalDate.of(2026, 10, 2)

    private fun event(title: String, start: String?, end: String? = null, date: LocalDate = oct2) =
        Event(title = title, date = date, start = start?.let(LocalTime::parse), end = end?.let(LocalTime::parse))

    @Test
    fun octoberStartsOnThursdayWithMondayFirst() {
        // As on the lock screen in the screenshot: 1 October 2026 is a Thursday.
        val grid = Agenda.monthGrid(YearMonth.of(2026, 10), DayOfWeek.MONDAY)
        assertEquals(5, grid.size)
        assertEquals(listOf(null, null, null), grid[0].take(3))
        assertEquals(1, grid[0][3]?.dayOfMonth)
        assertEquals(31, grid[4][5]?.dayOfMonth)
        assertNull(grid[4][6])
        assertTrue(grid.all { it.size == 7 })
    }

    @Test
    fun sundayFirstShiftsTheGrid() {
        val grid = Agenda.monthGrid(YearMonth.of(2026, 10), DayOfWeek.SUNDAY)
        assertEquals(1, grid[0][4]?.dayOfMonth)
        // February 2026 starts on a Sunday, so it fills exactly four rows.
        assertEquals(4, Agenda.monthGrid(YearMonth.of(2026, 2), DayOfWeek.SUNDAY).size)
    }

    @Test
    fun weekdayLetters() {
        assertEquals(listOf("M", "T", "W", "T", "F", "S", "S"), Agenda.weekdayLetters(DayOfWeek.MONDAY, Locale.ENGLISH))
        assertEquals("S", Agenda.weekdayLetters(DayOfWeek.SUNDAY, Locale.ENGLISH).first())
    }

    @Test
    fun yearProgressMatchesTheScreenshot() {
        assertEquals(75, Agenda.yearPercent(oct2))
        assertEquals(90, Agenda.daysLeft(oct2))
        assertEquals(0, Agenda.daysLeft(LocalDate.of(2026, 12, 31)))
        assertEquals(100, Agenda.yearPercent(LocalDate.of(2026, 12, 31)))
    }

    @Test
    fun dayIsSortedAllDayFirstThenByTime() {
        val events = listOf(
            event("Gym", "18:00", "20:00"),
            event("Meeting with Dr. Conor", "08:45", "09:45"),
            event("Day of German Unity", null, date = oct2.plusDays(1)),
            event("Holiday", null),
            event("Jumah", "13:30", "14:00"),
        )
        assertEquals(listOf("Holiday", "Meeting with Dr. Conor", "Jumah", "Gym"), Agenda.eventsOn(oct2, events).map { it.title })
        assertEquals(listOf("Day of German Unity"), Agenda.eventsOn(oct2.plusDays(1), events).map { it.title })
    }

    @Test
    fun timeLabels() {
        assertEquals("08:45 - 09:45", Agenda.timeLabel(event("a", "08:45", "09:45"), oct2, true, Locale.ENGLISH))
        assertEquals("8:45 AM - 9:45 AM", Agenda.timeLabel(event("a", "08:45", "09:45"), oct2, false, Locale.ENGLISH))
        assertEquals("All day", Agenda.timeLabel(event("a", null), oct2, true, Locale.ENGLISH))
        val overnight = Event(title = "Night shift", date = oct2, endDate = oct2.plusDays(1), start = LocalTime.of(22, 0), end = LocalTime.of(6, 0))
        assertEquals("From 22:00", Agenda.timeLabel(overnight, oct2, true, Locale.ENGLISH))
        assertEquals("Until 06:00", Agenda.timeLabel(overnight, oct2.plusDays(1), true, Locale.ENGLISH))
    }

    @Test
    fun pastEvents() {
        val now = LocalDateTime.of(2026, 10, 2, 15, 36)
        assertTrue(Agenda.isPast(event("Work", "10:00", "11:45"), oct2, now))
        assertFalse(Agenda.isPast(event("Discovery call", "15:00", "16:00"), oct2, now))
        assertFalse(Agenda.isPast(event("Holiday", null), oct2, now))
        assertTrue(Agenda.isPast(event("Yesterday", "09:00", "10:00", oct2.minusDays(1)), oct2.minusDays(1), now))
    }

    @Test
    fun deviceAllDayEventEndsTheDayBefore() {
        // Calendars store a one-day all-day event as midnight to the next midnight.
        val e = Agenda.deviceEvent("1", "Day of German Unity", LocalDateTime.of(2026, 10, 3, 0, 0), LocalDateTime.of(2026, 10, 4, 0, 0), true, 0)
        assertEquals(LocalDate.of(2026, 10, 3), e.date)
        assertEquals(LocalDate.of(2026, 10, 3), e.endDate)
        assertTrue(e.allDay)
        assertTrue(e.fromDevice)
        assertEquals(Colors.Silver, e.color)
    }

    @Test
    fun deviceTimedEventKeepsItsTimes() {
        val e = Agenda.deviceEvent("2", "", LocalDateTime.of(2026, 10, 2, 23, 0), LocalDateTime.of(2026, 10, 3, 0, 0), false, 0x3366CC)
        assertEquals(oct2, e.endDate)
        assertEquals(LocalTime.of(23, 0), e.start)
        assertEquals("(No title)", e.title)
        assertEquals(0xFF3366CC.toInt(), e.color)
    }

    @Test
    fun dotsCoverEveryDayOfALongEvent() {
        val trip = Event(title = "Trip", date = LocalDate.of(2026, 9, 29), endDate = LocalDate.of(2026, 10, 2), color = Colors.Mint)
        val dots = Agenda.eventColorsByDay(listOf(trip), LocalDate.of(2026, 10, 1), LocalDate.of(2026, 11, 1))
        assertEquals(setOf(LocalDate.of(2026, 10, 1), oct2), dots.keys)
        assertEquals(Colors.Mint, dots[oct2])
    }

    @Test
    fun openTasksSoonestDueFirst() {
        val tasks = listOf(
            Task(title = "Groceries", createdAt = 1),
            Task(title = "Language", due = oct2.plusDays(2), createdAt = 2),
            Task(title = "Gym", due = oct2, createdAt = 3),
            Task(title = "Done already", done = true, createdAt = 4),
        )
        assertEquals(listOf("Gym", "Language", "Groceries"), Agenda.openTasks(tasks).map { it.title })
        assertEquals(listOf("Done already"), Agenda.doneTasks(tasks).map { it.title })
    }

    @Test
    fun dayLabels() {
        assertEquals("Today", Agenda.dayLabel(oct2, oct2, Locale.ENGLISH))
        assertEquals("Tomorrow", Agenda.dayLabel(oct2.plusDays(1), oct2, Locale.ENGLISH))
        assertEquals("Yesterday", Agenda.dayLabel(oct2.minusDays(1), oct2, Locale.ENGLISH))
        assertEquals("Fri 9 Oct", Agenda.dayLabel(LocalDate.of(2026, 10, 9), oct2, Locale.ENGLISH))
        assertEquals("5 Jan 2027", Agenda.dayLabel(LocalDate.of(2027, 1, 5), oct2, Locale.ENGLISH))
    }
}
