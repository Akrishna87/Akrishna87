package io.github.akrishna87.daybook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.daybook.model.Agenda
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Event
import io.github.akrishna87.daybook.model.Task
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** The app's version of the lock screen: the clock, the month, today's plan, the year and the agenda card. */
@Composable
fun TodayScreen(
    data: Data,
    events: List<Event>,
    now: LocalDateTime,
    is24Hour: Boolean,
    wallpaperActive: Boolean,
    padding: PaddingValues,
    onOpenSettings: () -> Unit,
    onOpenDay: (LocalDate) -> Unit,
    onEditEvent: (Event) -> Unit,
    onAddEvent: () -> Unit,
    onOpenTasks: () -> Unit,
) {
    val today = now.toLocalDate()
    val month = YearMonth.from(today)
    val todays = Agenda.eventsOn(today, events)
    Column(
        Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 24.dp)
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(today),
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleMedium,
                color = Palette.SubText,
            )
            IconButton(onClick = onOpenSettings) { Icon(Icons.Rounded.Lock, "Lock screen and settings") }
        }
        Text(
            DateTimeFormatter.ofPattern(if (is24Hour) "HH:mm" else "h:mm").format(now),
            Modifier.fillMaxWidth(),
            style = MaterialTheme.typography.displayLarge,
            textAlign = TextAlign.Center,
        )

        if (!wallpaperActive) {
            GlassCard(Modifier.fillMaxWidth().padding(top = 8.dp), onClick = onOpenSettings) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Rounded.Lock, null)
                    Column(Modifier.weight(1f).padding(horizontal = 12.dp)) {
                        Text("Put Daybook on your lock screen", style = MaterialTheme.typography.titleSmall)
                        Text("See your day every time you pick up your phone.", style = MaterialTheme.typography.bodySmall, color = Palette.SubText)
                    }
                    Text("Set up", style = MaterialTheme.typography.labelLarge)
                }
            }
        }

        Spacer(Modifier.height(20.dp))
        Row {
            Column(Modifier.weight(0.47f)) {
                Text(
                    month.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()),
                    Modifier.padding(bottom = 6.dp),
                    style = MaterialTheme.typography.titleMedium,
                )
                MonthGrid(
                    month = month,
                    today = today,
                    weekStart = Agenda.weekStart(data.settings),
                    dots = dotsFor(events, month).mapValues { it.value.take(1) },
                    compact = true,
                    onDayClick = onOpenDay,
                )
            }
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(0.53f).padding(top = 2.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                if (todays.isEmpty()) {
                    Text("Nothing planned today", style = MaterialTheme.typography.titleSmall)
                    TextButton(onClick = onAddEvent, contentPadding = PaddingValues(0.dp)) { Text("Add an event") }
                }
                for (e in todays) {
                    EventLine(e, today, is24Hour, past = Agenda.isPast(e, today, now), onClick = { onEditEvent(e) })
                }
            }
        }

        YearProgress(today, Modifier.padding(top = 28.dp, bottom = 20.dp))

        AgendaCard(today, now, events, data.tasks, is24Hour, onEditEvent = onEditEvent, onOpenTasks = onOpenTasks)
    }
}

/** Today and Tomorrow on the left, tasks on the right, as on the lock screen. Tick a task off here. */
@Composable
fun AgendaCard(
    today: LocalDate,
    now: LocalDateTime,
    events: List<Event>,
    tasks: List<Task>,
    is24Hour: Boolean,
    onEditEvent: (Event) -> Unit,
    onOpenTasks: () -> Unit,
) {
    val tomorrow = today.plusDays(1)
    val upcoming = Agenda.eventsOn(today, events).filter { !Agenda.isPast(it, today, now) }
    val tomorrows = Agenda.eventsOn(tomorrow, events)
    val todayShown = upcoming.take(2)
    val tomorrowShown = tomorrows.take(if (todayShown.size < 2) 2 else 1)
    val open = Agenda.openTasks(tasks)
    val tasksShown = open.take(5)

    GlassCard(Modifier.fillMaxWidth()) {
        Row {
            Column(Modifier.weight(1.1f)) {
                DayHeader("Today", Palette.TodayPill, upcoming.size - todayShown.size)
                DayEvents(todayShown, today, is24Hour, "Nothing else today", onEditEvent)
                Spacer(Modifier.height(12.dp))
                DayHeader("Tomorrow", Palette.TomorrowPill, tomorrows.size - tomorrowShown.size)
                DayEvents(tomorrowShown, tomorrow, is24Hour, "Nothing planned", onEditEvent)
            }
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("Tasks", Modifier.clickable(onClick = onOpenTasks), style = MaterialTheme.typography.titleSmall)
                if (tasksShown.isEmpty()) {
                    Text("All done", Modifier.padding(top = 6.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.SubText)
                }
                for (t in tasksShown) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        TaskCheck(t, onToggle = { io.github.akrishna87.daybook.Store.toggleTask(t.id) }, size = 16)
                        Text(t.title, style = MaterialTheme.typography.bodyMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
                if (open.size > tasksShown.size) {
                    Text(
                        "+${open.size - tasksShown.size} more",
                        Modifier.clickable(onClick = onOpenTasks).padding(start = 34.dp),
                        style = MaterialTheme.typography.bodySmall,
                        color = Palette.SubText,
                    )
                }
            }
        }
    }
}

@Composable
private fun DayHeader(label: String, color: androidx.compose.ui.graphics.Color, more: Int) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Pill(label, color)
        if (more > 0) Text("+$more more", Modifier.padding(start = 6.dp), style = MaterialTheme.typography.bodySmall, color = Palette.SubText)
    }
}

@Composable
private fun DayEvents(events: List<Event>, day: LocalDate, is24Hour: Boolean, empty: String, onEditEvent: (Event) -> Unit) {
    if (events.isEmpty()) {
        Text(empty, Modifier.padding(top = 8.dp), style = MaterialTheme.typography.bodyMedium, color = Palette.SubText)
    }
    for (e in events) {
        EventLine(
            e, day, is24Hour,
            Modifier.padding(top = 8.dp),
            titleStyle = MaterialTheme.typography.titleSmall,
            maxLines = 1,
            onClick = { onEditEvent(e) },
        )
    }
}
