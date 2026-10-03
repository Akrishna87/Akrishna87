package io.github.akrishna87.daybook.ui

import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.model.Agenda
import io.github.akrishna87.daybook.model.Colors
import io.github.akrishna87.daybook.model.Event
import io.github.akrishna87.daybook.model.Task
import java.time.LocalDate
import java.time.LocalTime
import java.time.format.DateTimeFormatter
import java.util.Locale

fun pickDate(context: Context, initial: LocalDate, onPick: (LocalDate) -> Unit) {
    DatePickerDialog(context, { _, y, m, d -> onPick(LocalDate.of(y, m + 1, d)) }, initial.year, initial.monthValue - 1, initial.dayOfMonth).show()
}

fun pickTime(context: Context, initial: LocalTime, is24Hour: Boolean, onPick: (LocalTime) -> Unit) {
    TimePickerDialog(context, { _, h, m -> onPick(LocalTime.of(h, m)) }, initial.hour, initial.minute, is24Hour).show()
}

/** A new event on [day], starting at the next whole hour (or 9:00 on another day) and lasting an hour. */
fun newEventOn(day: LocalDate, now: LocalTime = LocalTime.now()): Event {
    val start = if (day == LocalDate.now() && now.hour < 23) LocalTime.of(now.hour + 1, 0) else LocalTime.of(9, 0)
    return Event(title = "", date = day, start = start, end = start.plusHours(1), color = Colors.Lilac)
}

/** Adds or changes an event: its title, day, time (or all day) and colour. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditor(initial: Event, isNew: Boolean, is24Hour: Boolean, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var date by remember { mutableStateOf(initial.date) }
    var allDay by remember { mutableStateOf(initial.allDay) }
    var start by remember { mutableStateOf(initial.start ?: LocalTime.of(9, 0)) }
    var end by remember { mutableStateOf(initial.end ?: start.plusHours(1)) }
    var color by remember { mutableIntStateOf(initial.color) }
    val time = Agenda.timeFormatter(is24Hour)

    fun save() {
        Store.saveEvent(
            initial.copy(
                title = title.trim(),
                date = date,
                endDate = date,
                start = if (allDay) null else start,
                end = if (allDay) null else end,
                color = color,
            ),
        )
        onDismiss()
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true),
        containerColor = MaterialTheme.colorScheme.surface,
    ) {
        Column(Modifier.verticalScroll(rememberScrollState()).padding(start = 20.dp, end = 20.dp, bottom = 28.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(if (isNew) "New event" else "Edit event", Modifier.weight(1f), style = MaterialTheme.typography.headlineSmall)
                Button(onClick = ::save, Modifier.semantics { contentDescription = "Save" }, enabled = title.isNotBlank()) { Text("Save") }
            }
            OutlinedTextField(
                value = title,
                onValueChange = { title = it },
                modifier = Modifier.fillMaxWidth().padding(top = 16.dp),
                label = { Text("Title") },
                singleLine = true,
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
            )
            FieldRow(Icons.Rounded.Today, "Date", DateTimeFormatter.ofPattern("EEE d MMM yyyy", Locale.getDefault()).format(date)) {
                pickDate(context, date) { date = it }
            }
            Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                Text("All day", Modifier.weight(1f), style = MaterialTheme.typography.bodyLarge)
                Switch(checked = allDay, onCheckedChange = { allDay = it })
            }
            if (!allDay) {
                FieldRow(Icons.Rounded.Schedule, "Starts", time.format(start)) {
                    pickTime(context, start, is24Hour) { s ->
                        // Keep the same length, without running past midnight.
                        val length = java.time.Duration.between(start, end)
                        val moved = s.plus(length)
                        start = s
                        end = if (moved.isBefore(s)) LocalTime.of(23, 59) else moved
                    }
                }
                FieldRow(Icons.Rounded.Schedule, "Ends", time.format(end)) {
                    pickTime(context, end, is24Hour) { e -> end = if (e.isBefore(start)) start else e }
                }
            }
            SectionLabel("Colour", Modifier.padding(top = 16.dp, bottom = 10.dp))
            ColorChoices(Colors.choices, color, { color = it }, Modifier.horizontalScroll(rememberScrollState()))
            if (!isNew) {
                TextButton(
                    onClick = { Store.deleteEvent(initial.id); onDismiss() },
                    modifier = Modifier.padding(top = 20.dp),
                ) { Text("Delete event", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun FieldRow(icon: ImageVector, label: String, value: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = Palette.SubText)
        Text(label, Modifier.padding(start = 12.dp).weight(1f), style = MaterialTheme.typography.bodyLarge)
        Text(value, style = MaterialTheme.typography.titleSmall)
    }
}

/** Changes a task's title, due day and colour, or deletes it. */
@Composable
fun TaskEditor(initial: Task, today: LocalDate, onDismiss: () -> Unit) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(initial.title) }
    var due by remember { mutableStateOf(initial.due) }
    var color by remember { mutableIntStateOf(initial.color) }
    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = MaterialTheme.colorScheme.surface,
        title = { Text("Edit task") },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    modifier = Modifier.fillMaxWidth(),
                    label = { Text("Task") },
                    keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences),
                )
                SectionLabel("Due", Modifier.padding(top = 16.dp, bottom = 4.dp))
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    val chip = FilterChipDefaults.filterChipColors(
                        selectedContainerColor = Color.White,
                        selectedLabelColor = Palette.TodayFill,
                    )
                    FilterChip(selected = due == null, onClick = { due = null }, label = { Text("No date") }, colors = chip)
                    FilterChip(selected = due == today, onClick = { due = today }, label = { Text("Today") }, colors = chip)
                    FilterChip(selected = due == today.plusDays(1), onClick = { due = today.plusDays(1) }, label = { Text("Tomorrow") }, colors = chip)
                    val other = due?.takeIf { it != today && it != today.plusDays(1) }
                    FilterChip(
                        selected = other != null,
                        onClick = { pickDate(context, due ?: today) { due = it } },
                        label = { Text(other?.let { Agenda.dayLabel(it, today) } ?: "Pick a day") },
                        colors = chip,
                    )
                }
                SectionLabel("Colour", Modifier.padding(top = 16.dp, bottom = 10.dp))
                ColorChoices(Colors.choices, color, { color = it }, Modifier.horizontalScroll(rememberScrollState()))
                Spacer(Modifier.size(4.dp))
            }
        },
        confirmButton = {
            TextButton(
                onClick = {
                    Store.saveTask(initial.copy(title = title.trim(), due = due, color = color))
                    onDismiss()
                },
                enabled = title.isNotBlank(),
            ) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = { Store.deleteTask(initial.id); onDismiss() }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
        },
    )
}
