package io.github.akrishna87.daybook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CalendarMonth
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Inbox
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.TaskAlt
import androidx.compose.material.icons.rounded.EventAvailable
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Dates
import io.github.akrishna87.daybook.model.INBOX_ID
import io.github.akrishna87.daybook.model.Project
import io.github.akrishna87.daybook.model.ProjectColors
import io.github.akrishna87.daybook.model.Task
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

/** Overdue tasks, then today's, with how much of the day's list is done. */
@Composable
fun TodayScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val actions = LocalActions.current
    val extra = LocalExtra.current
    val today = LocalClock.current.today
    val overdue = Dates.overdue(data.tasks, today)
    val due = Dates.dueOn(data.tasks, today)
    val doneToday = Dates.completedOn(data.tasks, today).size

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
    ) {
        item(key = "header") {
            ScreenHeader("Today", DateTimeFormatter.ofPattern("EEEE d MMMM", Locale.getDefault()).format(today)) {
                SearchAction { nav.push(Page.Search) }
            }
        }
        val total = doneToday + due.size + overdue.size
        if (total > 0) {
            item(key = "progress") { DayProgress(doneToday, total) }
        }
        if (overdue.isNotEmpty()) {
            item(key = "overdue") {
                SectionHeader("Overdue", color = extra.overdue) {
                    TextButton(onClick = {
                        val before = overdue.toList()
                        Store.rescheduleOverdue(today)
                        actions.offerUndo(if (before.size == 1) "Moved 1 task to today" else "Moved ${before.size} tasks to today") { Store.restoreTasks(before) }
                    }) { Text("Reschedule") }
                }
            }
            items(overdue, key = { it.id }) { t ->
                SwipeTask(t, Modifier.animateItem()) { TaskRow(t, data) { nav.push(Page.TaskPage(t.id)) } }
            }
            item(key = "today-header") { SectionHeader(Dates.dayHeading(today, today)) }
        }
        items(due, key = { it.id }) { t ->
            SwipeTask(t, Modifier.animateItem()) { TaskRow(t, data, showDue = t.time != null, timeOnly = true) { nav.push(Page.TaskPage(t.id)) } }
        }
        if (overdue.isEmpty() && due.isEmpty()) {
            item(key = "empty") {
                if (doneToday > 0) {
                    EmptyState(
                        Icons.Rounded.TaskAlt,
                        "All done for today",
                        if (doneToday == 1) "You completed 1 task today. Nice work." else "You completed $doneToday tasks today. Nice work.",
                    )
                } else {
                    EmptyState(Icons.Rounded.EventAvailable, "All clear", "Nothing is due today. Tap + to add a task, or plan ahead in Upcoming.")
                }
            }
        }
    }
}

@Composable
private fun DayProgress(done: Int, total: Int) {
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        LinearProgressIndicator(
            progress = { if (total == 0) 0f else done / total.toFloat() },
            modifier = Modifier.weight(1f).height(6.dp).clip(CircleShape),
            color = LocalExtra.current.today,
            trackColor = MaterialTheme.colorScheme.surfaceVariant,
            drawStopIndicator = {},
        )
        Text("$done/$total done", Modifier.padding(start = 12.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

private sealed interface UpRow {
    val key: String

    data object Overdue : UpRow {
        override val key = "overdue"
    }

    data class Day(val day: LocalDate) : UpRow {
        override val key = "day-$day"
    }

    data class Item(val task: Task) : UpRow {
        override val key = "task-${task.id}"
    }
}

/** The next four weeks day by day, with a week strip to jump around. */
@Composable
fun UpcomingScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val extra = LocalExtra.current
    val today = LocalClock.current.today
    val ws = Dates.weekStart(data.settings)
    val firstWeek = Dates.startOfWeek(today, ws)
    var weekStart by remember(ws, today) { mutableStateOf(firstWeek) }
    var selected by remember(today) { mutableStateOf(today) }
    var scrollTo by remember { mutableStateOf<LocalDate?>(null) }
    var picking by remember { mutableStateOf(false) }
    val listState = rememberLazyListState()

    val overdue = Dates.overdue(data.tasks, today)
    val rows: List<UpRow> = buildList {
        if (overdue.isNotEmpty() && weekStart == firstWeek) {
            add(UpRow.Overdue)
            overdue.forEach { add(UpRow.Item(it)) }
        }
        for (k in 0 until 28) {
            val d = weekStart.plusDays(k.toLong())
            if (d.isBefore(today)) continue
            add(UpRow.Day(d))
            Dates.dueOn(data.tasks, d).forEach { add(UpRow.Item(it)) }
        }
    }
    fun jump(day: LocalDate) {
        val d = if (day.isBefore(today)) today else day
        selected = d
        weekStart = Dates.startOfWeek(d, ws)
        scrollTo = d
    }
    LaunchedEffect(scrollTo, rows.size) {
        val target = scrollTo ?: return@LaunchedEffect
        val i = rows.indexOfFirst { it is UpRow.Day && it.day == target }
        if (i >= 0) listState.animateScrollToItem(i)
        scrollTo = null
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        ScreenHeader(
            "Upcoming",
            selected.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + " " + selected.year,
        ) {
            if (weekStart != firstWeek) TextButton(onClick = { jump(today) }) { Text("Today") }
            IconButton(onClick = { picking = true }, Modifier.semantics { contentDescription = "Pick a date" }) { Icon(Icons.Rounded.CalendarMonth, null) }
            SearchAction { nav.push(Page.Search) }
        }
        WeekStrip(weekStart, selected, today, data, ws, onWeek = { weekStart = it }, onDay = { jump(it) })
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
        LazyColumn(
            Modifier.weight(1f),
            state = listState,
            contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            items(rows, key = { it.key }) { row ->
                when (row) {
                    UpRow.Overdue -> SectionHeader("Overdue", color = extra.overdue)
                    is UpRow.Day -> DayHeader(row.day, today) { nav.quickAdd = QuickAddDefaults(due = row.day) }
                    is UpRow.Item -> SwipeTask(row.task, Modifier.animateItem()) {
                        val overdueRow = row.task.due?.isBefore(today) == true
                        TaskRow(row.task, data, showDue = row.task.time != null || overdueRow, timeOnly = !overdueRow) {
                            nav.push(Page.TaskPage(row.task.id))
                        }
                    }
                }
            }
        }
    }
    if (picking) DatePickDialog(selected, onPick = { jump(it) }, onDismiss = { picking = false })
}

@Composable
private fun WeekStrip(
    weekStart: LocalDate,
    selected: LocalDate,
    today: LocalDate,
    data: Data,
    ws: java.time.DayOfWeek,
    onWeek: (LocalDate) -> Unit,
    onDay: (LocalDate) -> Unit,
) {
    val busy = remember(data.tasks, weekStart) {
        (0 until 7).map { weekStart.plusDays(it.toLong()) }.filter { d -> data.tasks.any { !it.done && it.due == d } }.toSet()
    }
    val firstWeek = Dates.startOfWeek(today, ws)
    Row(Modifier.fillMaxWidth().padding(horizontal = 4.dp, vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = { onWeek(weekStart.minusWeeks(1)) },
            enabled = weekStart.isAfter(firstWeek),
            modifier = Modifier.semantics { contentDescription = "Previous week" },
        ) { Icon(Icons.Rounded.ChevronLeft, null) }
        Row(Modifier.weight(1f)) {
            for (k in 0 until 7) {
                val d = weekStart.plusDays(k.toLong())
                val isSelected = d == selected
                val past = d.isBefore(today)
                Column(
                    Modifier
                        .weight(1f)
                        .clip(RoundedCornerShape(14.dp))
                        .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent)
                        .clickable(enabled = !past) { onDay(d) }
                        .padding(vertical = 6.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    val fg = when {
                        isSelected -> MaterialTheme.colorScheme.onPrimary
                        past -> MaterialTheme.colorScheme.outline
                        d == today -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurface
                    }
                    Text(d.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault()), style = MaterialTheme.typography.labelSmall, color = fg)
                    Text(
                        d.dayOfMonth.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = if (d == today || isSelected) FontWeight.Bold else FontWeight.Normal,
                        color = fg,
                    )
                    Box(
                        Modifier
                            .padding(top = 2.dp)
                            .size(4.dp)
                            .clip(CircleShape)
                            .background(if (d in busy) (if (isSelected) MaterialTheme.colorScheme.onPrimary else MaterialTheme.colorScheme.primary) else Color.Transparent),
                    )
                }
            }
        }
        IconButton(
            onClick = { onWeek(weekStart.plusWeeks(1)) },
            modifier = Modifier.semantics { contentDescription = "Next week" },
        ) { Icon(Icons.Rounded.ChevronRight, null) }
    }
}

@Composable
private fun DayHeader(day: LocalDate, today: LocalDate, onAdd: () -> Unit) {
    val heading = Dates.dayHeading(day, today)
    Column {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 4.dp, top = 12.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(
                heading,
                Modifier.weight(1f),
                style = MaterialTheme.typography.titleSmall,
                color = if (day == today) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
            )
            IconButton(onClick = onAdd, Modifier.semantics { contentDescription = "Add task for $heading" }) {
                Icon(Icons.Rounded.Add, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

/** A project's (or the Inbox's) tasks. */
@Composable
fun ProjectPage(id: String, data: Data) {
    val nav = LocalNav.current
    val project = if (id == INBOX_ID) Project.Inbox else data.projects.firstOrNull { it.id == id }
    if (project == null) {
        LaunchedEffect(Unit) { nav.pop() }
        return
    }
    var menu by remember { mutableStateOf(false) }
    var editing by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val open = Dates.inProject(data.tasks, id)
    val done = data.tasks.filter { it.done && it.projectId == id && it.parentId == null }.sortedByDescending { it.doneAt }
    TaskListPage(
        title = project.name,
        color = if (id == INBOX_ID) null else project.color,
        open = open,
        done = done,
        data = data,
        defaults = QuickAddDefaults(projectId = id),
        showProject = false,
        emptyTitle = if (id == INBOX_ID) "Your Inbox is empty" else "No tasks yet",
        emptyMessage = if (id == INBOX_ID) "Tasks without a project land here. Tap + to add one." else "Tap + to add the first task to ${project.name}.",
    ) {
        if (id != INBOX_ID) {
            Box {
                IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "Project options" }) { Icon(Icons.Rounded.MoreVert, null) }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Edit project") }, onClick = { menu = false; editing = true })
                    DropdownMenuItem(
                        text = { Text("Delete project", color = MaterialTheme.colorScheme.error) },
                        onClick = { menu = false; deleting = true },
                    )
                }
            }
        }
    }
    if (editing) {
        NameDialog(
            title = "Edit project",
            initial = project.name,
            colors = ProjectColors.choices,
            initialColor = project.color,
            onSave = { name, color -> Store.saveProject(project.copy(name = name, color = color)) },
            onDismiss = { editing = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete project?",
            text = "This deletes “${project.name}” and its ${open.size + done.size} tasks. It can't be undone.",
            confirm = "Delete",
            onConfirm = {
                Store.deleteProject(project.id)
                nav.pop()
            },
            onDismiss = { deleting = false },
        )
    }
}

/** Tasks with a label. */
@Composable
fun LabelPage(name: String, data: Data) {
    val nav = LocalNav.current
    var menu by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var deleting by remember { mutableStateOf(false) }
    val open = Dates.withLabel(data.tasks, name)
    val done = data.tasks.filter { t -> t.done && t.labels.any { it.equals(name, true) } }.sortedByDescending { it.doneAt }
    TaskListPage(
        title = "@$name",
        color = null,
        open = open,
        done = done,
        data = data,
        defaults = QuickAddDefaults(labels = listOf(name)),
        showProject = true,
        emptyTitle = "Nothing labelled @$name",
        emptyMessage = "Tasks with this label show up here.",
    ) {
        Box {
            IconButton(onClick = { menu = true }, Modifier.semantics { contentDescription = "Label options" }) { Icon(Icons.Rounded.MoreVert, null) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Rename label") }, onClick = { menu = false; renaming = true })
                DropdownMenuItem(text = { Text("Delete label", color = MaterialTheme.colorScheme.error) }, onClick = { menu = false; deleting = true })
            }
        }
    }
    if (renaming) {
        NameDialog(
            title = "Rename label",
            initial = name,
            onSave = { new, _ ->
                val clean = new.removePrefix("@").replace(" ", "-")
                Store.renameLabel(name, clean)
                nav.pop()
                nav.push(Page.LabelPage(clean))
            },
            onDismiss = { renaming = false },
        )
    }
    if (deleting) {
        ConfirmDialog(
            title = "Delete label?",
            text = "@$name is taken off every task. The tasks themselves stay.",
            confirm = "Delete",
            onConfirm = {
                Store.deleteLabel(name)
                nav.pop()
            },
            onDismiss = { deleting = false },
        )
    }
}

@Composable
private fun TaskListPage(
    title: String,
    color: Int?,
    open: List<Task>,
    done: List<Task>,
    data: Data,
    defaults: QuickAddDefaults,
    showProject: Boolean,
    emptyTitle: String,
    emptyMessage: String,
    actions: @Composable RowScope.() -> Unit,
) {
    val nav = LocalNav.current
    var showDone by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = MaterialTheme.colorScheme.surface,
        topBar = {
            Column(Modifier.statusBarsPadding()) {
                PageBar(
                    onBack = { nav.pop() },
                    leading = {
                        if (color != null) {
                            ColorDot(color, 12.dp)
                            Spacer(Modifier.width(10.dp))
                        } else if (title == "Inbox") {
                            Icon(Icons.Rounded.Inbox, null, tint = Color(ProjectColors.Inbox))
                            Spacer(Modifier.width(10.dp))
                        } else {
                            Icon(Icons.Rounded.Label, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.width(10.dp))
                        }
                        Text(title, Modifier.weight(10f, fill = false), style = MaterialTheme.typography.titleLarge, maxLines = 1)
                    },
                    actions = actions,
                )
            }
        },
        floatingActionButton = { Fab("Add task") { nav.quickAdd = defaults } },
    ) { padding ->
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(top = padding.calculateTopPadding(), bottom = padding.calculateBottomPadding() + 88.dp),
        ) {
            if (open.isEmpty()) item(key = "empty") { EmptyState(Icons.Rounded.TaskAlt, emptyTitle, emptyMessage) }
            items(open, key = { it.id }) { t ->
                SwipeTask(t, Modifier.animateItem()) { TaskRow(t, data, showProject = showProject) { nav.push(Page.TaskPage(t.id)) } }
            }
            if (done.isNotEmpty()) {
                item(key = "done-toggle") {
                    TextButton(onClick = { showDone = !showDone }, Modifier.padding(start = 8.dp, top = 8.dp)) {
                        Text(if (showDone) "Hide completed" else "Show completed (${done.size})")
                    }
                }
                if (showDone) {
                    items(done, key = { "done-" + it.id }) { t -> TaskRow(t, data, showProject = showProject) { nav.push(Page.TaskPage(t.id)) } }
                }
            }
        }
    }
}

/** Finished tasks by the day they were finished. Tap a circle to bring a task back. */
@Composable
fun CompletedPage(data: Data) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var clearing by remember { mutableStateOf(false) }
    val zone = ZoneId.systemDefault()
    val byDay = data.tasks.filter { it.done }
        .sortedByDescending { it.doneAt }
        .groupBy { Instant.ofEpochMilli(it.doneAt).atZone(zone).toLocalDate() }
    Column(Modifier.fillMaxSize().statusBarsPadding()) {
        PageBar("Completed", onBack = { nav.pop() }) {
            if (byDay.isNotEmpty()) TextButton(onClick = { clearing = true }) { Text("Clear all") }
        }
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 32.dp)) {
            if (byDay.isEmpty()) item { EmptyState(Icons.Rounded.TaskAlt, "Nothing completed yet", "Tasks you complete are kept here.") }
            for ((day, tasks) in byDay) {
                item(key = "h-$day") { SectionHeader(Dates.dayHeading(day, today)) }
                items(tasks, key = { it.id }) { t -> TaskRow(t, data, showDue = false) { nav.push(Page.TaskPage(t.id)) } }
            }
        }
    }
    if (clearing) {
        ConfirmDialog(
            title = "Clear completed tasks?",
            text = "Every completed task is deleted for good.",
            confirm = "Clear",
            onConfirm = { Store.clearCompleted() },
            onDismiss = { clearing = false },
        )
    }
}
