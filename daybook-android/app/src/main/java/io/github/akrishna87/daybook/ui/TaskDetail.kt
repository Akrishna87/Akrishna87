package io.github.akrishna87.daybook.ui

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Notifications
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Dates
import io.github.akrishna87.daybook.model.Priority
import io.github.akrishna87.daybook.model.Repeat
import io.github.akrishna87.daybook.model.Task
import kotlinx.coroutines.delay

/** Everything about one task, each part editable in place. Changes save as you go. */
@Composable
fun TaskDetailPage(id: String, data: Data) {
    val nav = LocalNav.current
    val clock = LocalClock.current
    val actions = LocalActions.current
    val ask = LocalAskNotifications.current
    val task = data.task(id)
    if (task == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    var title by remember(id) { mutableStateOf(TextFieldValue(task.title)) }
    var description by remember(id) { mutableStateOf(TextFieldValue(task.description)) }

    fun saveText() {
        val cur = Store.data.value.task(id) ?: return
        val t = title.text.trim().ifEmpty { cur.title }
        val d = description.text.trimEnd()
        if (t != cur.title || d != cur.description) Store.saveTask(cur.copy(title = t, description = d))
    }
    fun update(change: (Task) -> Task) {
        val cur = Store.data.value.task(id) ?: return
        val next = change(cur)
        Store.saveTask(next)
        if (next.time != null && cur.time == null) ask()
    }
    LaunchedEffect(title.text, description.text) {
        delay(600)
        saveText()
    }
    DisposableEffect(id) { onDispose { saveText() } }

    var dueMenu by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    var labelsDialog by remember { mutableStateOf(false) }
    var moreMenu by remember { mutableStateOf(false) }
    val muted = MaterialTheme.colorScheme.onSurfaceVariant

    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().imePadding()) {
        PageBar(
            onBack = { nav.pop() },
            leading = {
                Box {
                    val project = data.project(task.projectId)
                    Row(
                        Modifier
                            .clip(RoundedCornerShape(8.dp))
                            .clickable(enabled = task.parentId == null) { projectMenu = true }
                            .semantics { contentDescription = "Project: ${project.name}" }
                            .padding(horizontal = 8.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        ColorDot(project.color)
                        Spacer(Modifier.width(8.dp))
                        Text(project.name, style = MaterialTheme.typography.titleSmall)
                        if (task.parentId == null) Icon(Icons.Rounded.ArrowDropDown, null)
                    }
                    ProjectMenu(projectMenu, data.allProjects, task.projectId, { projectMenu = false }) { pid ->
                        // Sub-tasks move with their task.
                        Store.restoreTasks(Store.taskFamily(id).map { it.copy(projectId = pid) })
                    }
                }
            },
            actions = {
                Box {
                    IconButton(onClick = { moreMenu = true }, Modifier.semantics { contentDescription = "Task options" }) { Icon(Icons.Rounded.MoreVert, null) }
                    DropdownMenu(expanded = moreMenu, onDismissRequest = { moreMenu = false }) {
                        DropdownMenuItem(
                            text = { Text("Duplicate") },
                            onClick = {
                                moreMenu = false
                                Store.saveTask(task.copy(id = io.github.akrishna87.daybook.model.newId(), done = false, doneAt = 0L, createdAt = System.currentTimeMillis()))
                                actions.message("Task duplicated")
                            },
                        )
                        DropdownMenuItem(
                            text = { Text("Delete task", color = MaterialTheme.colorScheme.error) },
                            onClick = {
                                moreMenu = false
                                nav.pop()
                                actions.delete(task)
                            },
                        )
                    }
                }
            },
        )

        Column(Modifier.weight(1f).verticalScroll(rememberScrollState())) {
            Row(Modifier.padding(start = 6.dp, end = 16.dp), verticalAlignment = Alignment.Top) {
                PriorityCheck(task, onToggle = { actions.toggle(task) }, size = 24.dp)
                PlainField(
                    value = title,
                    onChange = { title = it },
                    placeholder = "Task name",
                    description = "Task title",
                    style = MaterialTheme.typography.titleLarge,
                    modifier = Modifier.padding(top = 9.dp),
                )
            }
            PlainField(
                value = description,
                onChange = { description = it },
                placeholder = "Description",
                description = "Description",
                style = MaterialTheme.typography.bodyMedium,
                modifier = Modifier.padding(start = 50.dp, end = 16.dp, top = 2.dp),
            )
            HorizontalDivider(Modifier.padding(top = 16.dp), color = MaterialTheme.colorScheme.outlineVariant)

            DetailRow(
                Icons.Rounded.CalendarToday,
                Dates.dueLabel(task, clock.today, clock.is24Hour)?.let { if (task.time != null) Dates.dayLabel(task.due!!, clock.today) else it } ?: "Due date",
                if (task.due != null) toneColor(Dates.tone(task, clock.now)) else muted,
                onClick = { dueMenu = true },
                onClear = if (task.due != null) ({ update { it.copy(due = null, time = null, repeat = Repeat.NONE) } }) else null,
            ) {
                DueMenu(dueMenu, { dueMenu = false }, clock.today, Dates.weekStart(data.settings), onPick = { d ->
                    update { if (d == null) it.copy(due = null, time = null, repeat = Repeat.NONE) else it.copy(due = d) }
                }, onPickDate = { pickingDate = true })
            }
            if (task.due != null) {
                DetailRow(
                    Icons.Rounded.Schedule,
                    task.time?.let { Dates.timeFormatter(clock.is24Hour).format(it) } ?: "Add a time",
                    if (task.time != null) MaterialTheme.colorScheme.onSurface else muted,
                    onClick = { pickingTime = true },
                    onClear = if (task.time != null) ({ update { it.copy(time = null) } }) else null,
                )
                DetailRow(
                    Icons.Rounded.Repeat,
                    task.repeat.label,
                    if (task.repeat != Repeat.NONE) MaterialTheme.colorScheme.onSurface else muted,
                    onClick = { repeatMenu = true },
                ) {
                    RepeatMenu(repeatMenu, task.repeat, { repeatMenu = false }) { r -> update { it.copy(repeat = r) } }
                }
                if (task.time != null) {
                    Row(
                        Modifier.fillMaxWidth().clickable { update { it.copy(reminder = !it.reminder) } }.padding(start = 20.dp, end = 16.dp, top = 6.dp, bottom = 6.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Icon(Icons.Rounded.Notifications, null, tint = muted)
                        Text("Remind me at the due time", Modifier.weight(1f).padding(start = 20.dp), style = MaterialTheme.typography.bodyLarge)
                        Switch(checked = task.reminder, onCheckedChange = { on -> update { it.copy(reminder = on) } })
                    }
                }
            }
            DetailRow(
                Icons.Rounded.Flag,
                Priority.label(task.priority),
                if (task.priority < Priority.NONE) Color(Priority.color(task.priority)) else muted,
                onClick = { priorityMenu = true },
            ) {
                PriorityMenu(priorityMenu, task.priority, { priorityMenu = false }) { p -> update { it.copy(priority = p) } }
            }
            DetailRow(
                Icons.Rounded.Label,
                if (task.labels.isEmpty()) "Labels" else task.labels.joinToString("  ") { "@$it" },
                if (task.labels.isEmpty()) muted else MaterialTheme.colorScheme.onSurface,
                onClick = { labelsDialog = true },
            )
            val note = data.note(task.noteId)
            if (note != null && !note.trashed) {
                DetailRow(Icons.Rounded.Description, note.title.ifBlank { "Untitled note" }, MaterialTheme.colorScheme.primary, onClick = {
                    nav.push(Page.NotePage(note.id))
                })
            }
            val parent = data.task(task.parentId)
            if (parent != null) {
                DetailRow(Icons.Rounded.SubdirectoryArrowRight, "Sub-task of “${parent.title}”", MaterialTheme.colorScheme.primary, onClick = {
                    nav.push(Page.TaskPage(parent.id))
                })
            }

            if (task.parentId == null) {
                val subs = data.subtasks(id)
                SectionHeader(if (subs.isEmpty()) "Sub-tasks" else "Sub-tasks  ${subs.count { it.done }}/${subs.size}")
                for (s in subs) TaskRow(s, data, showProject = false) { nav.push(Page.TaskPage(s.id)) }
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable { nav.quickAdd = QuickAddDefaults(projectId = task.projectId, parentId = id) }
                        .semantics { contentDescription = "Add sub-task" }
                        .padding(horizontal = 20.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.primary)
                    Text("Add sub-task", Modifier.padding(start = 20.dp), color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyLarge)
                }
            }
            Text(
                "Added ${Dates.shortDate(task.createdAt, clock.today)}" + if (task.done) " · Completed ${Dates.shortDate(task.doneAt, clock.today)}" else "",
                Modifier.padding(20.dp),
                style = MaterialTheme.typography.bodySmall,
                color = muted,
            )
        }
    }

    if (pickingDate) DatePickDialog(task.due, onPick = { d -> update { it.copy(due = d) } }, onDismiss = { pickingDate = false })
    if (pickingTime) {
        TimePickDialog(task.time ?: defaultTime(task.due, clock.now), clock.is24Hour, onPick = { t -> update { it.copy(time = t) } }, onDismiss = { pickingTime = false })
    }
    if (labelsDialog) LabelsDialog(data.labels, task.labels, onDone = { l -> update { it.copy(labels = l) } }, onDismiss = { labelsDialog = false })
}

@Composable
private fun DetailRow(
    icon: ImageVector,
    text: String,
    tint: Color,
    onClick: () -> Unit,
    onClear: (() -> Unit)? = null,
    menu: @Composable () -> Unit = {},
) {
    Box {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 20.dp, end = 8.dp).padding(vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, null, Modifier.size(22.dp), tint = tint)
            Text(
                text,
                Modifier.weight(1f).padding(start = 20.dp, top = 12.dp, bottom = 12.dp),
                style = MaterialTheme.typography.bodyLarge,
                color = tint,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            if (onClear != null) {
                IconButton(onClick = onClear, Modifier.semantics { contentDescription = "Clear" }) { Icon(Icons.Rounded.Close, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
            }
        }
        menu()
    }
}
