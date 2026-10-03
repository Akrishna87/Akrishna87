package io.github.akrishna87.daybook

import io.github.akrishna87.daybook.model.Project
import io.github.akrishna87.daybook.model.QuickAdd
import io.github.akrishna87.daybook.model.Repeat
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.time.LocalDate
import java.time.LocalTime

class QuickAddTest {
    // Saturday 3 October 2026.
    private val today = LocalDate.of(2026, 10, 3)
    private val home = Project(id = "home", name = "Home")
    private val work = Project(id = "work", name = "Side Project")

    private fun parse(s: String, now: LocalTime? = null) = QuickAdd.parse(s, today, now, listOf(home, work))

    @Test
    fun theWholeExample() {
        val p = parse("Pay rent friday 9am p1 #Home @bills every month")
        assertEquals("Pay rent", p.title)
        assertEquals(LocalDate.of(2026, 10, 9), p.due)
        assertEquals(LocalTime.of(9, 0), p.time)
        assertEquals(1, p.priority)
        assertEquals("home", p.projectId)
        assertEquals(listOf("bills"), p.labels)
        assertEquals(Repeat.MONTHLY, p.repeat)
    }

    @Test
    fun plainTextIsLeftAlone() {
        val p = parse("Call Tom about the sun cream")
        assertEquals("Call Tom about the sun cream", p.title)
        assertNull(p.due)
        assertNull(p.time)
        assertNull(p.priority)
        assertEquals(emptyList<IntRange>(), p.matches)
    }

    @Test
    fun relativeDays() {
        assertEquals(today, parse("x today").due)
        assertEquals(today.plusDays(1), parse("x tomorrow").due)
        assertEquals(today.plusDays(1), parse("x tmr").due)
        assertEquals(today.plusDays(3), parse("x in 3 days").due)
        assertEquals(today.plusWeeks(2), parse("x in 2 weeks").due)
        assertEquals(today.plusWeeks(1), parse("x in a week").due)
    }

    @Test
    fun weekdays() {
        assertEquals(LocalDate.of(2026, 10, 5), parse("x monday").due)
        assertEquals(LocalDate.of(2026, 10, 7), parse("x wed").due)
        assertEquals(today, parse("x saturday").due)
        // "next" means in the coming week, as in Todoist.
        assertEquals(LocalDate.of(2026, 10, 5), parse("x next monday").due)
        assertEquals(LocalDate.of(2026, 10, 9), parse("x next friday").due)
        assertEquals(LocalDate.of(2026, 10, 5), parse("x next week").due)
        assertEquals(today, parse("x this weekend").due)
    }

    @Test
    fun calendarDates() {
        assertEquals(LocalDate.of(2026, 12, 25), parse("Gifts dec 25").due)
        assertEquals(LocalDate.of(2026, 12, 25), parse("Gifts 25 December").due)
        assertEquals(LocalDate.of(2026, 11, 1), parse("x nov 1st").due)
        // A date already past this year means next year.
        assertEquals(LocalDate.of(2027, 1, 5), parse("x jan 5").due)
        assertEquals(LocalDate.of(2028, 3, 2), parse("x 2 mar 2028").due)
        assertEquals(LocalDate.of(2026, 10, 20), parse("x 2026-10-20").due)
    }

    @Test
    fun times() {
        assertEquals(LocalTime.of(21, 30), parse("x tomorrow 9:30pm").time)
        assertEquals(LocalTime.of(9, 0), parse("x tomorrow 9 am").time)
        assertEquals(LocalTime.of(17, 0), parse("x tomorrow at 5").time)
        assertEquals(LocalTime.of(21, 0), parse("x tomorrow 21:00").time)
        assertEquals(LocalTime.NOON, parse("x tomorrow noon").time)
        assertEquals(LocalTime.of(0, 15), parse("x tomorrow 12:15am").time)
        assertEquals("Meet at the office", parse("Meet at the office").title)
    }

    @Test
    fun aTimeAloneMeansTodayOrTomorrow() {
        assertEquals(today, parse("Call mum 6pm", LocalTime.of(10, 0)).due)
        assertEquals(today.plusDays(1), parse("Call mum 6pm", LocalTime.of(19, 0)).due)
    }

    @Test
    fun priorityLabelsAndProjects() {
        val p = parse("Write report p2 @work @deep-focus #SideProject")
        assertEquals("Write report", p.title)
        assertEquals(2, p.priority)
        assertEquals(listOf("work", "deep-focus"), p.labels)
        assertEquals("work", p.projectId)
        // An unknown project stays in the title.
        assertEquals("Plan #Garden", parse("Plan #Garden").title)
    }

    @Test
    fun repeats() {
        assertEquals(Repeat.DAILY, parse("Stretch every day").repeat)
        assertEquals(today, parse("Stretch every day").due)
        assertEquals(Repeat.WEEKLY, parse("Bins every thursday").repeat)
        assertEquals(LocalDate.of(2026, 10, 8), parse("Bins every thursday").due)
        assertEquals(Repeat.WEEKDAYS, parse("Standup every weekday 9:15am").repeat)
        // On a Saturday, "every weekday" starts on Monday.
        assertEquals(LocalDate.of(2026, 10, 5), parse("Standup every weekday").due)
        assertEquals(Repeat.YEARLY, parse("Renew passport yearly").repeat)
    }

    @Test
    fun recognisedWordsAreMarkedForHighlighting() {
        val text = "Buy milk tomorrow p1"
        val p = parse(text)
        assertEquals(listOf("tomorrow", "p1"), p.matches.map { text.substring(it) })
    }

    @Test
    fun punctuationAfterAWordStillCounts() {
        val p = parse("Dentist tomorrow, 3pm.")
        assertEquals("Dentist", p.title)
        assertEquals(LocalTime.of(15, 0), p.time)
    }
}
