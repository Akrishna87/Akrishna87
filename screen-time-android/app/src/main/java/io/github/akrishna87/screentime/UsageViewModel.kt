package io.github.akrishna87.screentime

import android.app.Application
import android.text.format.DateFormat
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.time.temporal.TemporalAdjusters
import java.time.temporal.WeekFields
import java.util.Locale

enum class Period(val label: String) { DAY("Day"), WEEK("Week"), MONTH("Month") }

/** The days a period covers, both ends included. */
data class Span(val period: Period, val start: LocalDate, val end: LocalDate) {
    val days: Int get() = (end.toEpochDay() - start.toEpochDay() + 1).toInt()

    fun contains(day: LocalDate) = !day.isBefore(start) && !day.isAfter(end)

    companion object {
        fun of(period: Period, day: LocalDate): Span = when (period) {
            Period.DAY -> Span(period, day, day)
            Period.WEEK -> {
                val firstDayOfWeek = WeekFields.of(Locale.getDefault()).firstDayOfWeek
                val first = day.with(TemporalAdjusters.previousOrSame(firstDayOfWeek))
                Span(period, first, first.plusDays(6))
            }
            Period.MONTH -> Span(period, day.withDayOfMonth(1), day.with(TemporalAdjusters.lastDayOfMonth()))
        }
    }
}

/** One bar of a chart. Tapping a bar that has a [day] opens that day. */
data class Bar(val value: Long, val label: String?, val day: LocalDate? = null, val highlight: Boolean = true)

data class Report(
    val span: Span,
    val total: Long,
    val dailyAverage: Long?,
    val bars: List<Bar>,
    val apps: List<AppTotal>,
    /** Against the day, week or month before; null until that one has been tracked. */
    val trend: Trend?,
)

data class AppReport(
    val total: Long,
    val opens: Int,
    val dailyAverage: Long?,
    val chartTitle: String?,
    val bars: List<Bar>,
)

@OptIn(ExperimentalCoroutinesApi::class)
class UsageViewModel(app: Application) : AndroidViewModel(app) {
    private val dao = UsageDatabase.get(app).usage()

    var hasAccess by mutableStateOf(UsageAccess.granted(app))
        private set
    var syncing by mutableStateOf(false)
        private set
    var today by mutableStateOf(LocalDate.now())
        private set
    var period by mutableStateOf(Period.DAY)

    /** Any day inside the period on screen. */
    var day by mutableStateOf(LocalDate.now())
        private set

    /** The app whose details are open, if any. */
    var selected by mutableStateOf<AppTotal?>(null)

    val span: Span get() = Span.of(period, day)
    val canGoNext: Boolean get() = span.end.isBefore(today)

    val firstDay: StateFlow<LocalDate?> = dao.firstDay()
        .map { it?.let(LocalDate::ofEpochDay) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val report: StateFlow<Report?> = snapshotFlow { span to today }
        .distinctUntilChanged()
        .flatMapLatest { (span, today) ->
            val from = span.start.toEpochDay()
            val to = span.end.toEpochDay()
            val bars = if (span.period == Period.DAY) {
                dao.hours(from).map { hourBars(it) }
            } else {
                dao.dayTotals(from, to).map { dayBars(span, it, today, highlight = today) }
            }
            val earlier = previousWindow(span, today)
            // Whole days of the earlier period, plus (if it's only counted up to now) its last day's hours.
            val earlierWhole = dao.total(
                earlier.start.toEpochDay(),
                earlier.end.toEpochDay() - if (earlier.partial) 1 else 0,
            )
            val earlierLastDay = if (earlier.partial) dao.hours(earlier.end.toEpochDay()) else flowOf(emptyList<HourTotal>())
            combine(dao.appTotals(from, to), bars, firstDay, earlierWhole, earlierLastDay) { apps, chart, first, whole, lastDay ->
                val total = apps.sumOf { it.foregroundMs }
                // "Now" is read here, not above, so it stays current as new usage comes in.
                val previous = whole + if (earlier.partial) usedBefore(lastDay, LocalTime.now()) else 0L
                val tracked = first != null && !first.isAfter(earlier.start)
                val trend = if (tracked) Trend(total, previous, earlier.against) else null
                Report(span, total, dailyAverage(span, total, first, today), chart, apps, trend)
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    val appReport: StateFlow<AppReport?> = snapshotFlow { Triple(selected?.packageName, span, today) }
        .distinctUntilChanged()
        .flatMapLatest { (pkg, span, today) ->
            if (pkg == null) return@flatMapLatest flowOf<AppReport?>(null)
            // A single day has no per-app hourly breakdown, so show the week leading up to it instead.
            val chartSpan = if (span.period == Period.DAY) Span(Period.DAY, span.start.minusDays(6), span.start) else span
            val title = when {
                span.period != Period.DAY -> null
                span.start == today -> "Last 7 days"
                else -> "7 days up to ${span.start.format(DateTimeFormatter.ofPattern("d MMM", Locale.getDefault()))}"
            }
            combine(
                dao.appTotals(span.start.toEpochDay(), span.end.toEpochDay()).map { list -> list.find { it.packageName == pkg } },
                dao.appDays(pkg, chartSpan.start.toEpochDay(), chartSpan.end.toEpochDay()),
                firstDay,
            ) { found, days, first ->
                val total = found?.foregroundMs ?: 0L
                val highlight = if (span.period == Period.DAY) span.start else today
                AppReport(
                    total = total,
                    opens = found?.opens ?: 0,
                    dailyAverage = dailyAverage(span, total, first, today),
                    chartTitle = title,
                    bars = dayBars(chartSpan, days, today, highlight, weekdayLabels = span.period != Period.MONTH),
                )
            }
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    /** Checks for usage access and copies the latest usage in. Called whenever the app comes to the front. */
    fun refresh() {
        hasAccess = UsageAccess.granted(getApplication<Application>())
        val now = LocalDate.now()
        if (now != today) {
            // Midnight passed while the app was in the background: keep showing "today".
            if (day == today) day = now
            today = now
        }
        if (!hasAccess || syncing) return
        syncing = true
        viewModelScope.launch {
            try {
                UsageSync.sync(getApplication<Application>())
            } finally {
                syncing = false
            }
        }
    }

    fun previous() {
        day = when (period) {
            Period.DAY -> day.minusDays(1)
            Period.WEEK -> day.minusWeeks(1)
            Period.MONTH -> day.minusMonths(1)
        }
    }

    fun next() {
        if (!canGoNext) return
        val next = when (period) {
            Period.DAY -> day.plusDays(1)
            Period.WEEK -> day.plusWeeks(1)
            Period.MONTH -> day.plusMonths(1)
        }
        day = if (next.isAfter(today)) today else next
    }

    fun openDay(date: LocalDate) {
        period = Period.DAY
        day = date
    }

    fun canGoBack(firstDay: LocalDate?): Boolean = firstDay != null && span.start.isAfter(firstDay)

    /** Average per day over the days that have both started and been tracked; none for a single day. */
    private fun dailyAverage(span: Span, total: Long, first: LocalDate?, today: LocalDate): Long? {
        if (span.period == Period.DAY) return null
        val from = if (first != null && first.isAfter(span.start)) first else span.start
        val to = if (today.isBefore(span.end)) today else span.end
        val days = to.toEpochDay() - from.toEpochDay() + 1
        return if (days > 0) total / days else null
    }

    private fun hourBars(hours: List<HourTotal>): List<Bar> {
        val byHour = hours.associate { it.hour to it.foregroundMs }
        val pattern = DateTimeFormatter.ofPattern(if (DateFormat.is24HourFormat(getApplication<Application>())) "HH" else "h a")
        return (0 until 24).map { h ->
            Bar(byHour[h] ?: 0L, if (h % 6 == 0) LocalTime.of(h, 0).format(pattern) else null)
        }
    }

    private fun dayBars(
        span: Span,
        totals: List<DayTotal>,
        today: LocalDate,
        highlight: LocalDate,
        weekdayLabels: Boolean = span.period == Period.WEEK,
    ): List<Bar> {
        val byDay = totals.associate { it.day to it.foregroundMs }
        return (0 until span.days).map { i ->
            val date = span.start.plusDays(i.toLong())
            val label = when {
                weekdayLabels -> date.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
                (date.dayOfMonth - 1) % 7 == 0 -> date.dayOfMonth.toString()
                else -> null
            }
            // Pick out the highlighted day when it's in the chart; otherwise no bar is dimmed.
            val bright = !span.contains(highlight) || date == highlight
            Bar(byDay[date.toEpochDay()] ?: 0L, label, date.takeIf { !it.isAfter(today) }, highlight = bright)
        }
    }
}

/** "Today", "Yesterday", "Wed, 1 Oct", "29 Sep – 5 Oct" or "October 2026". */
fun spanLabel(span: Span, today: LocalDate): String {
    val locale = Locale.getDefault()
    val sameYear = span.start.year == today.year && span.end.year == today.year
    return when (span.period) {
        Period.DAY -> when (span.start) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> span.start.format(DateTimeFormatter.ofPattern(if (sameYear) "EEE, d MMM" else "EEE, d MMM yyyy", locale))
        }
        Period.WEEK -> {
            val day = DateTimeFormatter.ofPattern("d MMM", locale)
            val end = if (sameYear) span.end.format(day) else span.end.format(DateTimeFormatter.ofPattern("d MMM yyyy", locale))
            "${span.start.format(day)} – $end"
        }
        Period.MONTH -> span.start.format(DateTimeFormatter.ofPattern("LLLL yyyy", locale))
    }
}
