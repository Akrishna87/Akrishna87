package io.github.akrishna87.daybook.model

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime

/** What Quick Add understood in a line of text. Fields are null (or empty) when the text didn't mention them. */
data class ParsedTask(
    val title: String,
    val due: LocalDate? = null,
    val time: LocalTime? = null,
    val priority: Int? = null,
    val projectId: String? = null,
    val labels: List<String> = emptyList(),
    val repeat: Repeat? = null,
    /** The character ranges that were recognised, so the text field can highlight them. */
    val matches: List<IntRange> = emptyList(),
)

/**
 * Reads a task typed in plain words, like Todoist's Quick Add:
 * "Pay rent friday 9am p1 #Home @bills every month".
 *
 * Dates: today, tomorrow (tmr), weekdays (monday or mon), next week, next friday, this weekend,
 * in 3 days, in 2 weeks, 5 jan, jan 5th, 2026-10-05. Times: 9am, 9:30pm, 21:00, at 5, noon, tonight.
 * Priority: p1 to p4. Project: #Name (an existing project; spaces in its name are left out).
 * Labels: @anything. Repeats: daily, every day, every weekday, every week, every monday, every month, every year.
 */
object QuickAdd {
    private val months = mapOf(
        "jan" to 1, "january" to 1, "feb" to 2, "february" to 2, "mar" to 3, "march" to 3,
        "apr" to 4, "april" to 4, "may" to 5, "jun" to 6, "june" to 6, "jul" to 7, "july" to 7,
        "aug" to 8, "august" to 8, "sep" to 9, "sept" to 9, "september" to 9, "oct" to 10, "october" to 10,
        "nov" to 11, "november" to 11, "dec" to 12, "december" to 12,
    )

    // "sat", "sun" and "tom" are left out on purpose: they're everyday words and names.
    private val weekdays = mapOf(
        "mon" to DayOfWeek.MONDAY, "monday" to DayOfWeek.MONDAY,
        "tue" to DayOfWeek.TUESDAY, "tues" to DayOfWeek.TUESDAY, "tuesday" to DayOfWeek.TUESDAY,
        "wed" to DayOfWeek.WEDNESDAY, "weds" to DayOfWeek.WEDNESDAY, "wednesday" to DayOfWeek.WEDNESDAY,
        "thu" to DayOfWeek.THURSDAY, "thur" to DayOfWeek.THURSDAY, "thurs" to DayOfWeek.THURSDAY, "thursday" to DayOfWeek.THURSDAY,
        "fri" to DayOfWeek.FRIDAY, "friday" to DayOfWeek.FRIDAY,
        "saturday" to DayOfWeek.SATURDAY, "sunday" to DayOfWeek.SUNDAY,
    )

    private val clock = Regex("(\\d{1,2})(?::(\\d{2}))?(am|pm)?")
    private val dayNumber = Regex("(\\d{1,2})(st|nd|rd|th)?")

    private class Tok(val raw: String, val word: String, val range: IntRange)

    fun parse(
        input: String,
        today: LocalDate,
        now: LocalTime? = null,
        projects: List<Project> = emptyList(),
        weekStart: DayOfWeek = DayOfWeek.MONDAY,
    ): ParsedTask {
        val toks = Regex("\\S+").findAll(input).map {
            val raw = it.value.trimEnd(',', '.', ';', '!')
            Tok(raw, raw.lowercase(), it.range)
        }.toList()
        val used = BooleanArray(toks.size)
        fun word(i: Int): String? = toks.getOrNull(i)?.word
        fun take(from: Int, count: Int) {
            for (k in from until from + count) used[k] = true
        }

        var due: LocalDate? = null
        var time: LocalTime? = null
        var priority: Int? = null
        var projectId: String? = null
        val labels = mutableListOf<String>()
        var repeat: Repeat? = null

        var i = 0
        while (i < toks.size) {
            val w = toks[i].word
            val raw = toks[i].raw
            var n = 0

            if (priority == null && w.length == 2 && w[0] == 'p' && w[1] in '1'..'4') {
                priority = w[1].digitToInt()
                n = 1
            } else if (projectId == null && raw.length > 1 && raw.startsWith("#")) {
                val name = raw.substring(1)
                val p = projects.firstOrNull { it.name.replace(" ", "").equals(name, true) }
                if (p != null) {
                    projectId = p.id
                    n = 1
                }
            } else if (raw.length > 1 && raw.startsWith("@")) {
                val label = raw.substring(1)
                if (labels.none { it.equals(label, true) }) labels += label
                n = 1
            }

            if (n == 0 && repeat == null) {
                val single = when (w) {
                    "daily", "everyday" -> Repeat.DAILY
                    "weekly" -> Repeat.WEEKLY
                    "monthly" -> Repeat.MONTHLY
                    "yearly", "annually" -> Repeat.YEARLY
                    else -> null
                }
                if (single != null) {
                    repeat = single
                    n = 1
                } else if (w == "every") {
                    val next = word(i + 1)
                    val r = when (next) {
                        "day" -> Repeat.DAILY
                        "weekday", "workday" -> Repeat.WEEKDAYS
                        "week" -> Repeat.WEEKLY
                        "month" -> Repeat.MONTHLY
                        "year" -> Repeat.YEARLY
                        else -> null
                    }
                    val wd = next?.let { weekdays[it] }
                    if (r != null) {
                        repeat = r
                        n = 2
                    } else if (wd != null) {
                        repeat = Repeat.WEEKLY
                        if (due == null) due = onOrAfter(today, wd)
                        n = 2
                    }
                }
            }

            if (n == 0 && due == null) {
                val next = word(i + 1)
                when {
                    w == "today" || w == "tod" -> { due = today; n = 1 }
                    w == "tomorrow" || w == "tmr" || w == "tmrw" -> { due = today.plusDays(1); n = 1 }
                    w == "tonight" -> {
                        due = today
                        if (time == null) time = LocalTime.of(19, 0)
                        n = 1
                    }
                    weekdays.containsKey(w) -> { due = onOrAfter(today, weekdays.getValue(w)); n = 1 }
                    w == "next" && next == "week" -> { due = Dates.startOfWeek(today, weekStart).plusWeeks(1); n = 2 }
                    w == "next" && next == "month" -> { due = today.plusMonths(1).withDayOfMonth(1); n = 2 }
                    w == "next" && next != null && weekdays.containsKey(next) -> {
                        due = onOrAfter(Dates.startOfWeek(today, weekStart).plusWeeks(1), weekdays.getValue(next))
                        n = 2
                    }
                    w == "weekend" -> { due = onOrAfter(today, DayOfWeek.SATURDAY); n = 1 }
                    w == "this" && next == "weekend" -> { due = onOrAfter(today, DayOfWeek.SATURDAY); n = 2 }
                    w == "in" && next != null -> {
                        val amount = if (next == "a" || next == "an") 1 else next.toIntOrNull()
                        val unit = word(i + 2)?.removeSuffix("s")
                        if (amount != null && amount in 1..999) {
                            val d = when (unit) {
                                "day" -> today.plusDays(amount.toLong())
                                "week" -> today.plusWeeks(amount.toLong())
                                "month" -> today.plusMonths(amount.toLong())
                                "year" -> today.plusYears(amount.toLong())
                                else -> null
                            }
                            if (d != null) {
                                due = d
                                n = 3
                            }
                        }
                    }
                    Regex("\\d{4}-\\d{2}-\\d{2}").matches(w) -> {
                        runCatching { LocalDate.parse(w) }.getOrNull()?.let { due = it; n = 1 }
                    }
                    months.containsKey(w) && next != null && dayNumber.matches(next) -> {
                        val d = monthDay(months.getValue(w), dayNumber.matchEntire(next)!!.groupValues[1].toInt(), word(i + 2), today)
                        if (d != null) {
                            due = d.first
                            n = 2 + d.second
                        }
                    }
                    dayNumber.matches(w) && next != null && months.containsKey(next) -> {
                        val d = monthDay(months.getValue(next), dayNumber.matchEntire(w)!!.groupValues[1].toInt(), word(i + 2), today)
                        if (d != null) {
                            due = d.first
                            n = 2 + d.second
                        }
                    }
                }
            }

            if (n == 0 && time == null) {
                val next = word(i + 1)
                when {
                    w == "noon" || w == "midday" -> { time = LocalTime.NOON; n = 1 }
                    w == "at" && next != null -> {
                        val t = clockTime(next, word(i + 2), bare = true)
                        if (t != null) {
                            time = t.first
                            n = 1 + t.second
                        }
                    }
                    else -> {
                        val t = clockTime(w, next, bare = false)
                        if (t != null) {
                            time = t.first
                            n = t.second
                        }
                    }
                }
            }

            if (n > 0) {
                take(i, n)
                i += n
            } else {
                i++
            }
        }

        if (time != null && due == null) {
            due = if (now != null && !time.isAfter(now)) today.plusDays(1) else today
        }
        if (repeat != null && due == null) {
            due = if (repeat == Repeat.WEEKDAYS) onOrAfter(today, DayOfWeek.MONDAY).takeIf { today.dayOfWeek.value >= 6 } ?: today else today
        }

        return ParsedTask(
            title = toks.filterIndexed { k, _ -> !used[k] }.joinToString(" ") { input.substring(it.range) }.trim(),
            due = due,
            time = time,
            priority = priority,
            projectId = projectId,
            labels = labels,
            repeat = repeat,
            matches = toks.filterIndexed { k, _ -> used[k] }.map { it.range },
        )
    }

    /** The first [day] on or after [from]. */
    fun onOrAfter(from: LocalDate, day: DayOfWeek): LocalDate = from.plusDays(((day.value - from.dayOfWeek.value + 7) % 7).toLong())

    /** A month and day, with an optional year after them. Without a year, a date already past means next year. */
    private fun monthDay(month: Int, day: Int, maybeYear: String?, today: LocalDate): Pair<LocalDate, Int>? {
        val year = maybeYear?.takeIf { it.length == 4 }?.toIntOrNull()
        val date = runCatching { LocalDate.of(year ?: today.year, month, day) }.getOrNull() ?: return null
        if (year != null) return date to 1
        return (if (date.isBefore(today)) date.plusYears(1) else date) to 0
    }

    /**
     * A time: "9am", "9:30pm", "21:00", or "9 am" over two words. With [bare] (after "at") a plain
     * hour counts too; 1 to 7 are taken as the afternoon. Returns the time and how many words it used.
     */
    private fun clockTime(w: String, next: String?, bare: Boolean): Pair<LocalTime, Int>? {
        val m = clock.matchEntire(w) ?: return null
        var hour = m.groupValues[1].toInt()
        val minute = m.groupValues[2].ifEmpty { "0" }.toInt()
        var suffix = m.groupValues[3]
        var words = 1
        if (suffix.isEmpty() && (next == "am" || next == "pm")) {
            suffix = next
            words = 2
        }
        val hasMinutes = m.groupValues[2].isNotEmpty()
        when {
            suffix.isNotEmpty() -> {
                if (hour !in 1..12) return null
                hour = when {
                    suffix == "am" && hour == 12 -> 0
                    suffix == "pm" && hour != 12 -> hour + 12
                    else -> hour
                }
            }
            hasMinutes -> if (hour > 23) return null
            bare -> {
                if (hour > 23) return null
                if (hour in 1..7) hour += 12
            }
            else -> return null
        }
        if (minute > 59) return null
        return LocalTime.of(hour, minute) to words
    }
}
