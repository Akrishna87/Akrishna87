package io.github.akrishna87.daybook.ui

import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp
import io.github.akrishna87.daybook.model.Agenda
import io.github.akrishna87.daybook.model.Event
import io.github.akrishna87.daybook.model.Settings
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** A month you can page through (arrows or swipe), and the selected day's events below it. */
@Composable
fun CalendarScreen(
    month: YearMonth,
    selected: LocalDate,
    now: LocalDateTime,
    events: List<Event>,
    settings: Settings,
    is24Hour: Boolean,
    padding: PaddingValues,
    onMonthChange: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
    onEditEvent: (Event) -> Unit,
) {
    val today = now.toLocalDate()
    val dayEvents = Agenda.eventsOn(selected, events)
    val currentMonth by rememberUpdatedState(month)
    val changeMonth by rememberUpdatedState(onMonthChange)
    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 96.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) +
                        if (month.year != today.year) " ${month.year}" else "",
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.headlineMedium,
                )
                if (month != YearMonth.from(today) || selected != today) {
                    TextButton(onClick = { onMonthChange(YearMonth.from(today)); onSelect(today) }) { Text("Today") }
                }
                IconButton(onClick = { onMonthChange(month.minusMonths(1)) }) { Icon(Icons.Rounded.ChevronLeft, "Previous month") }
                IconButton(onClick = { onMonthChange(month.plusMonths(1)) }) { Icon(Icons.Rounded.ChevronRight, "Next month") }
            }
        }
        item {
            GlassCard(
                Modifier
                    .fillMaxWidth()
                    .pointerInput(Unit) {
                        var drag = 0f
                        detectHorizontalDragGestures(
                            onDragStart = { drag = 0f },
                            onDragEnd = {
                                if (drag > 120f) changeMonth(currentMonth.minusMonths(1))
                                if (drag < -120f) changeMonth(currentMonth.plusMonths(1))
                            },
                        ) { _, dx -> drag += dx }
                    },
            ) {
                MonthGrid(
                    month = month,
                    today = today,
                    weekStart = Agenda.weekStart(settings),
                    dots = dotsFor(events, month),
                    selected = selected,
                    onDayClick = onSelect,
                )
            }
        }
        item {
            Text(
                DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(selected),
                Modifier.padding(top = 12.dp),
                style = MaterialTheme.typography.titleLarge,
            )
        }
        if (dayEvents.isEmpty()) {
            item {
                Text("Nothing planned. Tap New event to add something.", style = MaterialTheme.typography.bodyMedium, color = Palette.SubText)
            }
        }
        items(dayEvents, key = { it.id }) { e ->
            GlassCard(Modifier.fillMaxWidth().animateItem(), onClick = { onEditEvent(e) }) {
                EventLine(e, selected, is24Hour, past = Agenda.isPast(e, selected, now))
                if (e.fromDevice) {
                    Text("From your phone's calendar", Modifier.padding(start = 14.dp, top = 4.dp), style = MaterialTheme.typography.labelSmall, color = Palette.Faint)
                }
            }
        }
    }
}
