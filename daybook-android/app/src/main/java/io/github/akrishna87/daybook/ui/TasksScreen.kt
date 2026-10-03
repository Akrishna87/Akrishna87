package io.github.akrishna87.daybook.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.model.Agenda
import io.github.akrishna87.daybook.model.Task
import java.time.LocalDate

/** Type a task and press enter to add it; tick it off when it's done. Tap a task to give it a day or colour. */
@Composable
fun TasksScreen(tasks: List<Task>, today: LocalDate, padding: PaddingValues, onEdit: (Task) -> Unit) {
    var draft by rememberSaveable { mutableStateOf("") }
    val open = Agenda.openTasks(tasks)
    val done = Agenda.doneTasks(tasks)

    fun add() {
        val title = draft.trim()
        if (title.isEmpty()) return
        Store.saveTask(Task(title = title))
        draft = ""
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = 20.dp, end = 20.dp,
            top = padding.calculateTopPadding() + 8.dp,
            bottom = padding.calculateBottomPadding() + 32.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        item {
            Column(Modifier.padding(bottom = 8.dp)) {
                Text("Tasks", style = MaterialTheme.typography.headlineMedium)
                Text(
                    when (open.size) {
                        0 -> "Nothing to do"
                        1 -> "1 to do"
                        else -> "${open.size} to do"
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.SubText,
                )
            }
        }
        item {
            OutlinedTextField(
                value = draft,
                onValueChange = { draft = it },
                modifier = Modifier.fillMaxWidth().padding(bottom = 8.dp),
                placeholder = { Text("Add a task") },
                singleLine = true,
                shape = RoundedCornerShape(18.dp),
                keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = ImeAction.Done),
                keyboardActions = KeyboardActions(onDone = { add() }),
                trailingIcon = {
                    IconButton(onClick = ::add, enabled = draft.isNotBlank()) { Icon(Icons.Rounded.Add, "Add task") }
                },
                colors = OutlinedTextFieldDefaults.colors(
                    focusedContainerColor = Palette.Glass,
                    unfocusedContainerColor = Palette.Glass,
                    unfocusedBorderColor = Palette.GlassBorder,
                    focusedBorderColor = Color.White,
                    unfocusedPlaceholderColor = Palette.SubText,
                    focusedPlaceholderColor = Palette.Faint,
                ),
            )
        }
        items(open, key = { it.id }) { t -> TaskRow(t, today, Modifier.animateItem()) { onEdit(t) } }
        if (open.isEmpty() && done.isEmpty()) {
            item {
                Text(
                    "Add groceries, calls, anything you want to remember. Your tasks show on the lock screen and the widget.",
                    Modifier.padding(top = 12.dp),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Palette.SubText,
                )
            }
        }
        if (done.isNotEmpty()) {
            item(key = "done-header") {
                Row(Modifier.padding(top = 16.dp).animateItem(), verticalAlignment = Alignment.CenterVertically) {
                    SectionLabel("Done · ${done.size}", Modifier.weight(1f))
                    TextButton(onClick = Store::clearDoneTasks) { Text("Clear") }
                }
            }
            items(done, key = { it.id }) { t -> TaskRow(t, today, Modifier.animateItem()) { onEdit(t) } }
        }
    }
}

@Composable
private fun TaskRow(task: Task, today: LocalDate, modifier: Modifier = Modifier, onClick: () -> Unit) {
    GlassCard(
        modifier.fillMaxWidth(),
        shape = RoundedCornerShape(20.dp),
        contentPadding = PaddingValues(start = 6.dp, end = 12.dp, top = 6.dp, bottom = 6.dp),
        onClick = onClick,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            TaskCheck(task, onToggle = { Store.toggleTask(task.id) })
            Column(Modifier.weight(1f).padding(start = 4.dp)) {
                TaskTitle(task)
                val due = task.due
                if (due != null && !task.done) {
                    Text(
                        Agenda.dayLabel(due, today),
                        style = MaterialTheme.typography.bodySmall,
                        color = if (due.isBefore(today)) Palette.Overdue else Palette.SubText,
                    )
                }
            }
        }
    }
}
