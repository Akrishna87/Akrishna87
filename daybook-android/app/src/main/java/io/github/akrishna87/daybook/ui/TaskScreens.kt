package io.github.akrishna87.daybook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.material.icons.rounded.CalendarViewMonth
import androidx.compose.material.icons.rounded.ViewAgenda
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.style.TextAlign
import io.github.akrishna87.daybook.model.Priority
import java.time.YearMonth
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
            SwipeTask(t, Modifier.animateItem()) { TaskRow(t, data, timeOnly = true) { nav.push(Page.TaskPage(t.id)) } }
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

    data class Item(val entry: Dates.Entry) : UpRow {
        override val key = "task-${entry.task.id}-${entry.day}"
    }

    data class Empty(val day: LocalDate) : UpRow {
        override val key = "empty-$day"
    }
}

/**
 * What's coming up: a month calendar with dots on busy days (tap a day for its tasks), or a
 * day-by-day list of the next four weeks. Repeating tasks show on every day they repeat on.
 */
@Composable
fun UpcomingScreen(data: Data, padding: PaddingValues) {
    val nav = LocalNav.current
    val today = LocalClock.current.today
    var calendarView by rememberSaveable { mutableStateOf(true) }
    var month by remember(today) { mutableStateOf(YearMonth.from(today)) }
    var selected by remember(today) { mutableStateOf(today) }
    val thisMonth = YearMonth.from(today)

    fun showMonth(m: YearMonth) {
        if (m.isBefore(thisMonth)) return
        month = m
        selected = if (m == thisMonth) today else m.atDay(1)
    }

    Column(Modifier.fillMaxSize().padding(top = padding.calculateTopPadding())) {
        ScreenHeader(
            "Upcoming",
            if (calendarView) monthName(month) else "Next four weeks",
        ) {
            if (calendarView && selected != today) TextButton(onClick = { showMonth(thisMonth) }) { Text("Today") }
            IconButton(
                onClick = { calendarView = !calendarView },
                Modifier.semantics { contentDescription = if (calendarView) "Show as list" else "Show as calendar" },
            ) { Icon(if (calendarView) Icons.Rounded.ViewAgenda else Icons.Rounded.CalendarViewMonth, null) }
            SearchAction { nav.push(Page.Search) }
        }
        if (calendarView) {
            CalendarView(data, padding, month, selected, today, onMonth = { showMonth(it) }, onSelect = { selected = it })
        } else {
            ListView(data, padding, today)
        }
    }
}

private fun monthName(m: YearMonth): String =
    m.month.getDisplayName(TextStyle.FULL_STANDALONE, Locale.getDefault()) + " " + m.year

@Composable
private fun ColumnScope.CalendarView(
    data: Data,
    padding: PaddingValues,
    month: YearMonth,
    selected: LocalDate,
    today: LocalDate,
    onMonth: (YearMonth) -> Unit,
    onSelect: (LocalDate) -> Unit,
) {
    val nav = LocalNav.current
    val extra = LocalExtra.current
    val ws = Dates.weekStart(data.settings)
    val days = remember(data.tasks, month, today) { Dates.calendar(data.tasks, month.atDay(1), month.atEndOfMonth().plusDays(1), today) }
    val overdue = Dates.overdue(data.tasks, today)
    val entries = days[selected].orEmpty()
    val currentMonth by rememberUpdatedState(month)
    val changeMonth by rememberUpdatedState(onMonth)
    val canGoBack = month.isAfter(YearMonth.from(today))

    // The month: swipe sideways or use the arrows to change it.
    Column(
        Modifier.padding(horizontal = 8.dp).pointerInput(Unit) {
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
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(
                onClick = { onMonth(month.minusMonths(1)) },
                enabled = canGoBack,
                modifier = Modifier.semantics { contentDescription = "Previous month" },
            ) { Icon(Icons.Rounded.ChevronLeft, null) }
            Text(monthName(month), Modifier.weight(1f), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
            IconButton(
                onClick = { onMonth(month.plusMonths(1)) },
                modifier = Modifier.semantics { contentDescription = "Next month" },
            ) { Icon(Icons.Rounded.ChevronRight, null) }
        }
        Row(Modifier.fillMaxWidth().padding(bottom = 4.dp)) {
            for (letter in Dates.weekdayLetters(ws)) {
                Text(
                    letter,
                    Modifier.weight(1f),
                    textAlign = TextAlign.Center,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        for (week in Dates.monthGrid(month, ws)) {
            Row(Modifier.fillMaxWidth()) {
                for (day in week) {
                    Box(Modifier.weight(1f).height(50.dp), contentAlignment = Alignment.Center) {
                        if (day != null) DayCell(day, today, selected, days[day].orEmpty(), overdue.isNotEmpty() && day == today) { onSelect(day) }
                    }
                }
            }
        }
    }
    HorizontalDivider(Modifier.padding(top = 6.dp), color = MaterialTheme.colorScheme.outlineVariant)

    // The selected day's tasks.
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp)) {
        if (selected == today && overdue.isNotEmpty()) {
            item(key = "overdue") { SectionHeader("Overdue", color = extra.overdue) }
            items(overdue, key = { "o-" + it.id }) { t ->
                SwipeTask(t, Modifier.animateItem()) { TaskRow(t, data) { nav.push(Page.TaskPage(t.id)) } }
            }
        }
        item(key = "day-$selected") { DayHeader(selected, today) { nav.quickAdd = QuickAddDefaults(due = selected) } }
        if (entries.isEmpty()) {
            item(key = "empty-$selected") { NothingPlanned() }
        }
        items(entries, key = { "e-${it.task.id}-${it.day}" }) { e -> EntryRow(e, data, Modifier.animateItem()) }
    }
}

@Composable
private fun DayCell(day: LocalDate, today: LocalDate, selected: LocalDate, entries: List<Dates.Entry>, overdue: Boolean, onClick: () -> Unit) {
    val past = day.isBefore(today)
    val isSelected = day == selected
    val fg = when {
        isSelected -> MaterialTheme.colorScheme.onPrimary
        past -> MaterialTheme.colorScheme.outline
        day == today -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.onSurface
    }
    val label = Dates.dayHeading(day, today) + when (entries.size) {
        0 -> ""
        1 -> ", 1 task"
        else -> ", ${entries.size} tasks"
    }
    Column(
        Modifier
            .size(46.dp)
            .clip(RoundedCornerShape(14.dp))
            .clickable(enabled = !past, onClick = onClick)
            .semantics { contentDescription = label },
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center,
    ) {
        Box(
            Modifier
                .size(32.dp)
                .clip(CircleShape)
                .background(if (isSelected) MaterialTheme.colorScheme.primary else Color.Transparent),
            contentAlignment = Alignment.Center,
        ) {
            Text(
                day.dayOfMonth.toString(),
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = if (day == today || isSelected) FontWeight.Bold else FontWeight.Normal,
                color = fg,
            )
        }
        // One dot per task (up to three), in its priority colour; red if there are overdue tasks today.
        Row(Modifier.height(6.dp).padding(top = 2.dp), horizontalArrangement = Arrangement.spacedBy(2.dp)) {
            if (overdue) Box(Modifier.size(4.dp).clip(CircleShape).background(LocalExtra.current.overdue))
            for (e in entries.take(if (overdue) 2 else 3)) {
                val p = e.task.priority
                Box(
                    Modifier
                        .size(4.dp)
                        .clip(CircleShape)
                        .background(if (p < Priority.NONE) Color(Priority.color(p)) else MaterialTheme.colorScheme.onSurfaceVariant),
                )
            }
        }
    }
}

/** A task on the calendar: the real one can be ticked off or swiped; a later repeat just opens the task. */
@Composable
private fun EntryRow(e: Dates.Entry, data: Data, modifier: Modifier = Modifier) {
    val nav = LocalNav.current
    if (e.projected) {
        TaskRow(e.task, data, modifier, timeOnly = true, occurrence = e.day) { nav.push(Page.TaskPage(e.task.id)) }
    } else {
        SwipeTask(e.task, modifier) { TaskRow(e.task, data, timeOnly = true) { nav.push(Page.TaskPage(e.task.id)) } }
    }
}

@Composable
private fun NothingPlanned() {
    Text(
        "Nothing planned. Tap + to add a task for this day.",
        Modifier.padding(horizontal = 20.dp, vertical = 16.dp),
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

/** The next four weeks day by day, with overdue tasks first. */
@Composable
private fun ColumnScope.ListView(data: Data, padding: PaddingValues, today: LocalDate) {
    val nav = LocalNav.current
    val extra = LocalExtra.current
    val overdue = Dates.overdue(data.tasks, today)
    val days = remember(data.tasks, today) { Dates.calendar(data.tasks, today, today.plusDays(28), today) }
    val rows: List<UpRow> = buildList {
        if (overdue.isNotEmpty()) {
            add(UpRow.Overdue)
            overdue.forEach { add(UpRow.Item(Dates.Entry(it, it.due!!, false))) }
        }
        for (k in 0 until 28) {
            val d = today.plusDays(k.toLong())
            add(UpRow.Day(d))
            days[d].orEmpty().forEach { add(UpRow.Item(it)) }
        }
    }
    HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = padding.calculateBottomPadding() + 88.dp)) {
        items(rows, key = { it.key }) { row ->
            when (row) {
                UpRow.Overdue -> SectionHeader("Overdue", color = extra.overdue)
                is UpRow.Day -> DayHeader(row.day, today) { nav.quickAdd = QuickAddDefaults(due = row.day) }
                is UpRow.Empty -> NothingPlanned()
                is UpRow.Item -> if (row.entry.day.isBefore(today)) {
                    SwipeTask(row.entry.task, Modifier.animateItem()) { TaskRow(row.entry.task, data) { nav.push(Page.TaskPage(row.entry.task.id)) } }
                } else {
                    EntryRow(row.entry, data, Modifier.animateItem())
                }
            }
        }
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
