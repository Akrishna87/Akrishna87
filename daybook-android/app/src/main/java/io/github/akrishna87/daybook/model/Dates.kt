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

/** How a due date reads and which colour it gets, like Todoist: red overdue, green today, and so on. */
enum class DueTone { OVERDUE, TODAY, TOMORROW, THIS_WEEK, LATER }

/** Date sums and task ordering shared by every screen, the widget and the reminders. Plain Kotlin, so it is unit tested. */
object Dates {

    fun weekStart(settings: Settings): DayOfWeek = if (settings.weekStartsSunday) DayOfWeek.SUNDAY else DayOfWeek.MONDAY

    /** The first day of the week containing [day]. */
    fun startOfWeek(day: LocalDate, weekStart: DayOfWeek): LocalDate = day.minusDays(((day.dayOfWeek.value - weekStart.value + 7) % 7).toLong())

    /** The month as rows of seven days, with nulls before the 1st and after the last day. */
    fun monthGrid(month: YearMonth, weekStart: DayOfWeek): List<List<LocalDate?>> {
        val lead = (month.atDay(1).dayOfWeek.value - weekStart.value + 7) % 7
        val cells = List<LocalDate?>(lead) { null } + (1..month.lengthOfMonth()).map { month.atDay(it) }
        return (cells + List((7 - cells.size % 7) % 7) { null }).chunked(7)
    }

    fun weekdayLetters(weekStart: DayOfWeek, locale: Locale = Locale.getDefault()): List<String> =
        (0 until 7).map { weekStart.plus(it.toLong()).getDisplayName(TextStyle.NARROW, locale) }

    fun timeFormatter(is24Hour: Boolean, locale: Locale = Locale.getDefault()): DateTimeFormatter =
        DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm a", locale)

    /** "Today", "Tomorrow", "Yesterday", a weekday within the next week, otherwise "9 Oct" (with the year if it's another year). */
    fun dayLabel(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val days = ChronoUnit.DAYS.between(today, day)
        return when {
            days == 0L -> "Today"
            days == 1L -> "Tomorrow"
            days == -1L -> "Yesterday"
            days in 2..6 -> day.dayOfWeek.getDisplayName(TextStyle.FULL, locale)
            day.year == today.year -> DateTimeFormatter.ofPattern("d MMM", locale).format(day)
            else -> DateTimeFormatter.ofPattern("d MMM yyyy", locale).format(day)
        }
    }

    /** "Today 09:00", "Friday", "9 Oct"… or null without a due date. */
    fun dueLabel(task: Task, today: LocalDate, is24Hour: Boolean, locale: Locale = Locale.getDefault()): String? {
        val due = task.due ?: return null
        val day = dayLabel(due, today, locale)
        return task.time?.let { "$day ${timeFormatter(is24Hour, locale).format(it)}" } ?: day
    }

    /** A long heading for a day: "Today · Sat 3 Oct". */
    fun dayHeading(day: LocalDate, today: LocalDate, locale: Locale = Locale.getDefault()): String {
        val date = DateTimeFormatter.ofPattern(if (day.year == today.year) "EEE d MMM" else "EEE d MMM yyyy", locale).format(day)
        return when (ChronoUnit.DAYS.between(today, day)) {
            0L -> "Today · $date"
            1L -> "Tomorrow · $date"
            else -> date
        }
    }

    fun tone(task: Task, now: LocalDateTime): DueTone? {
        val due = task.due ?: return null
        val today = now.toLocalDate()
        return when {
            isOverdue(task, now) -> DueTone.OVERDUE
            due == today -> DueTone.TODAY
            due == today.plusDays(1) -> DueTone.TOMORROW
            due.isBefore(today.plusDays(7)) -> DueTone.THIS_WEEK
            else -> DueTone.LATER
        }
    }

    /** Due before today, or due earlier today at a time that has passed. */
    fun isOverdue(task: Task, now: LocalDateTime): Boolean {
        if (task.done) return false
        val due = task.due ?: return false
        val today = now.toLocalDate()
        if (due.isBefore(today)) return true
        val time = task.time ?: return false
        return due == today && time.isBefore(now.toLocalTime())
    }

    /** The next date a repeating task is due after it's completed: at least one step on, and not before today. */
    fun nextDue(due: LocalDate, repeat: Repeat, today: LocalDate): LocalDate {
        if (repeat == Repeat.NONE) return due
        var next = step(due, repeat)
        while (next.isBefore(today)) next = step(next, repeat)
        return next
    }

    private fun step(day: LocalDate, repeat: Repeat): LocalDate = when (repeat) {
        Repeat.NONE -> day
        Repeat.DAILY -> day.plusDays(1)
        Repeat.WEEKDAYS -> {
            var d = day.plusDays(1)
            while (d.dayOfWeek == DayOfWeek.SATURDAY || d.dayOfWeek == DayOfWeek.SUNDAY) d = d.plusDays(1)
            d
        }
        Repeat.WEEKLY -> day.plusWeeks(1)
        Repeat.MONTHLY -> day.plusMonths(1)
        Repeat.YEARLY -> day.plusYears(1)
    }

    /** Todoist order: by date (undated last), timed tasks first within a day, then priority, then the order they were added. */
    val taskOrder: Comparator<Task> = compareBy<Task>(
        { it.due == null },
        { it.due },
        { it.time == null },
        { it.time },
        { it.priority },
        { it.createdAt },
    )

    /** Within one day or project: priority first, then time, then the order they were added. */
    val priorityOrder: Comparator<Task> = compareBy<Task>({ it.priority }, { it.due == null }, { it.due }, { it.time == null }, { it.time }, { it.createdAt })

    /** Open tasks due before today. */
    fun overdue(tasks: List<Task>, today: LocalDate): List<Task> =
        tasks.filter { !it.done && it.due != null && it.due.isBefore(today) }.sortedWith(taskOrder)

    /** Open tasks due on [day]. */
    fun dueOn(tasks: List<Task>, day: LocalDate): List<Task> =
        tasks.filter { !it.done && it.due == day }.sortedWith(taskOrder)

    /** Tasks finished on [day], for the "done today" count. */
    fun completedOn(tasks: List<Task>, day: LocalDate, zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): List<Task> =
        tasks.filter { it.done && it.doneAt > 0 && java.time.Instant.ofEpochMilli(it.doneAt).atZone(zone).toLocalDate() == day }

    /** A project's (or the Inbox's) open top-level tasks. */
    fun inProject(tasks: List<Task>, projectId: String): List<Task> =
        tasks.filter { !it.done && it.parentId == null && it.projectId == projectId }.sortedWith(taskOrder)

    fun withLabel(tasks: List<Task>, label: String): List<Task> =
        tasks.filter { !it.done && it.labels.any { l -> l.equals(label, true) } }.sortedWith(taskOrder)

    /** When a task's reminder should go off, or null if it has none (no time, already done, or switched off). */
    fun reminderAt(task: Task): LocalDateTime? {
        if (task.done || !task.reminder) return null
        val due = task.due ?: return null
        val time = task.time ?: return null
        return LocalDateTime.of(due, time)
    }

    fun shortDate(millis: Long, today: LocalDate, locale: Locale = Locale.getDefault(), zone: java.time.ZoneId = java.time.ZoneId.systemDefault()): String {
        val day = java.time.Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()
        return when (day) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> DateTimeFormatter.ofPattern(if (day.year == today.year) "d MMM" else "d MMM yyyy", locale).format(day)
        }
    }

    /** A sensible default time for a new reminder: the next whole hour. */
    fun nextHour(now: LocalTime): LocalTime = if (now.hour == 23) LocalTime.of(23, 30) else LocalTime.of(now.hour + 1, 0)
}
