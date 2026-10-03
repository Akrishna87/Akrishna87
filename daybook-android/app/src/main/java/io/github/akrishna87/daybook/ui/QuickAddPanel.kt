package io.github.akrishna87.daybook.ui

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.ime
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.union
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.Send
import androidx.compose.material.icons.rounded.ArrowDropDown
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Flag
import androidx.compose.material.icons.rounded.Label
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TextFieldValue
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.daybook.Store
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Dates
import io.github.akrishna87.daybook.model.Priority
import io.github.akrishna87.daybook.model.QuickAdd
import io.github.akrishna87.daybook.model.Repeat
import io.github.akrishna87.daybook.model.Task
import kotlinx.coroutines.delay
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime

/**
 * Todoist-style Quick Add: a panel above the keyboard where a task is typed in plain words.
 * Recognised dates, times, priorities, #projects, @labels and repeats are highlighted as you
 * type, and the chips underneath can set the same things by hand. It stays open for the next task.
 */
@Composable
fun QuickAddPanel(defaults: QuickAddDefaults, data: Data, onClose: () -> Unit) {
    val clock = LocalClock.current
    val today = clock.today
    val extra = LocalExtra.current
    val ask = LocalAskNotifications.current

    var text by remember { mutableStateOf(TextFieldValue("")) }
    var description by remember { mutableStateOf(TextFieldValue("")) }
    var due by remember { mutableStateOf(defaults.due) }
    var time by remember { mutableStateOf<LocalTime?>(null) }
    var priority by remember { mutableIntStateOf(Priority.NONE) }
    var projectId by remember { mutableStateOf(data.task(defaults.parentId)?.projectId ?: defaults.projectId) }
    var labels by remember { mutableStateOf(defaults.labels) }
    var repeat by remember { mutableStateOf(Repeat.NONE) }
    var added by remember { mutableStateOf<String?>(null) }

    var dueMenu by remember { mutableStateOf(false) }
    var pickingDate by remember { mutableStateOf(false) }
    var pickingTime by remember { mutableStateOf(false) }
    var priorityMenu by remember { mutableStateOf(false) }
    var projectMenu by remember { mutableStateOf(false) }
    var repeatMenu by remember { mutableStateOf(false) }
    var labelsDialog by remember { mutableStateOf(false) }

    val parsed = remember(text.text, data.projects, today) {
        QuickAdd.parse(text.text, today, LocalTime.now(), data.projects, Dates.weekStart(data.settings))
    }
    // What the text says wins over the chips.
    val effDue = parsed.due ?: due
    val effTime = if (effDue == null) null else parsed.time ?: time
    val effPriority = parsed.priority ?: priority
    val effProject = parsed.projectId ?: projectId
    val effLabels = (labels + parsed.labels).distinctBy { it.lowercase() }
    val effRepeat = if (effDue == null) Repeat.NONE else parsed.repeat ?: repeat

    val focus = remember { FocusRequester() }
    LaunchedEffect(Unit) { runCatching { focus.requestFocus() } }
    LaunchedEffect(added) {
        if (added != null) {
            delay(2500)
            added = null
        }
    }
    BackHandler(onBack = onClose)

    fun add() {
        val title = parsed.title
        if (title.isBlank()) return
        val t = Task(
            title = title,
            description = description.text.trim(),
            projectId = effProject,
            labels = effLabels,
            priority = effPriority,
            due = effDue,
            time = effTime,
            repeat = effRepeat,
            parentId = defaults.parentId,
            noteId = defaults.noteId,
        )
        Store.saveTask(t)
        if (t.time != null) ask()
        added = t.title
        text = TextFieldValue("")
        description = TextFieldValue("")
    }

    Box(Modifier.fillMaxSize()) {
        Box(
            Modifier
                .fillMaxSize()
                .background(Color.Black.copy(alpha = 0.32f))
                .clickable(interactionSource = remember { MutableInteractionSource() }, indication = null, onClick = onClose),
        )
        Surface(
            Modifier
                .align(Alignment.BottomCenter)
                .fillMaxWidth()
                .windowInsetsPadding(WindowInsets.ime.union(WindowInsets.navigationBars)),
            shape = RoundedCornerShape(topStart = 20.dp, topEnd = 20.dp),
            color = MaterialTheme.colorScheme.surface,
            shadowElevation = 12.dp,
        ) {
            Column(Modifier.padding(start = 16.dp, end = 12.dp, top = 16.dp, bottom = 8.dp)) {
                val context = listOfNotNull(
                    data.task(defaults.parentId)?.let { "Sub-task of “${it.title}”" },
                    data.note(defaults.noteId)?.let { "For the note “${it.title.ifBlank { "Untitled" }}”" },
                ).firstOrNull()
                if (context != null) {
                    Text(context, Modifier.padding(bottom = 6.dp), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                PlainField(
                    value = text,
                    onChange = { text = it },
                    placeholder = "e.g. Pay rent friday 9am p1 @bills",
                    description = "Task name",
                    style = MaterialTheme.typography.titleMedium,
                    modifier = Modifier.focusRequester(focus),
                    transformation = Highlight(parsed.matches, extra.highlight),
                    imeAction = ImeAction.Done,
                    onDone = ::add,
                )
                PlainField(
                    value = description,
                    onChange = { description = it },
                    placeholder = "Description",
                    description = "Description",
                    style = MaterialTheme.typography.bodyMedium,
                    modifier = Modifier.padding(top = 6.dp),
                )

                Row(
                    Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()).padding(top = 14.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Box {
                        val label = effDue?.let { d ->
                            Dates.dueLabel(Task(title = "", due = d, time = effTime), today, clock.is24Hour)
                        } ?: "Date"
                        QuickChip(
                            Icons.Rounded.CalendarToday, label,
                            if (effDue != null) toneColor(Dates.tone(Task(title = "", due = effDue, time = effTime), clock.now)) else null,
                        ) { dueMenu = true }
                        DueMenu(dueMenu, { dueMenu = false }, today, Dates.weekStart(data.settings), onPick = { due = it }, onPickDate = { pickingDate = true })
                    }
                    if (effDue != null) {
                        QuickChip(Icons.Rounded.Schedule, effTime?.let { Dates.timeFormatter(clock.is24Hour).format(it) } ?: "Time", null) { pickingTime = true }
                        Box {
                            QuickChip(Icons.Rounded.Repeat, if (effRepeat == Repeat.NONE) "Repeat" else effRepeat.label, null) { repeatMenu = true }
                            RepeatMenu(repeatMenu, effRepeat, { repeatMenu = false }) { repeat = it }
                        }
                    }
                    Box {
                        QuickChip(
                            Icons.Rounded.Flag,
                            if (effPriority < Priority.NONE) "P$effPriority" else "Priority",
                            if (effPriority < Priority.NONE) Color(Priority.color(effPriority)) else null,
                        ) { priorityMenu = true }
                        PriorityMenu(priorityMenu, effPriority, { priorityMenu = false }) { priority = it }
                    }
                    QuickChip(Icons.Rounded.Label, if (effLabels.isEmpty()) "Labels" else effLabels.joinToString(" ") { "@$it" }, null) { labelsDialog = true }
                }

                HorizontalDivider(Modifier.padding(top = 12.dp, bottom = 4.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Box {
                        val project = data.project(effProject)
                        Row(
                            Modifier
                                .clip(RoundedCornerShape(8.dp))
                                .clickable(enabled = defaults.parentId == null) { projectMenu = true }
                                .semantics { contentDescription = "Project: ${project.name}" }
                                .padding(horizontal = 8.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                        ) {
                            ColorDot(project.color)
                            Spacer(Modifier.width(8.dp))
                            Text(project.name, style = MaterialTheme.typography.labelLarge)
                            Icon(Icons.Rounded.ArrowDropDown, null)
                        }
                        ProjectMenu(projectMenu, data.allProjects, effProject, { projectMenu = false }) { projectId = it }
                    }
                    Spacer(Modifier.weight(1f))
                    val last = added
                    if (last != null) {
                        Text(
                            "Added",
                            Modifier.padding(end = 10.dp),
                            style = MaterialTheme.typography.labelLarge,
                            color = extra.today,
                        )
                    }
                    FilledIconButton(
                        onClick = ::add,
                        enabled = parsed.title.isNotBlank(),
                        modifier = Modifier.semantics { contentDescription = "Add task" },
                    ) { Icon(Icons.AutoMirrored.Rounded.Send, null) }
                }
            }
        }
    }

    if (pickingDate) DatePickDialog(effDue, onPick = { due = it }, onDismiss = { pickingDate = false })
    if (pickingTime) {
        TimePickDialog(effTime ?: Dates.nextHour(LocalDateTime.now().toLocalTime()), clock.is24Hour, onPick = { time = it }, onDismiss = { pickingTime = false })
    }
    if (labelsDialog) LabelsDialog(data.labels, labels, onDone = { labels = it }, onDismiss = { labelsDialog = false })
}

@Composable
private fun QuickChip(icon: ImageVector, label: String, color: Color?, onClick: () -> Unit) {
    val fg = color ?: MaterialTheme.colorScheme.onSurfaceVariant
    Row(
        Modifier
            .clip(RoundedCornerShape(8.dp))
            .border(1.dp, MaterialTheme.colorScheme.outlineVariant, RoundedCornerShape(8.dp))
            .clickable(onClick = onClick)
            .semantics { contentDescription = label }
            .padding(horizontal = 10.dp, vertical = 7.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(icon, null, Modifier.size(16.dp), tint = fg)
        Spacer(Modifier.width(6.dp))
        Text(label, style = MaterialTheme.typography.labelLarge, color = fg, maxLines = 1)
    }
}

/** A borderless text field with a placeholder, as in Todoist's and Evernote's editors. */
@Composable
fun PlainField(
    value: TextFieldValue,
    onChange: (TextFieldValue) -> Unit,
    placeholder: String,
    description: String,
    style: TextStyle,
    modifier: Modifier = Modifier,
    singleLine: Boolean = false,
    transformation: VisualTransformation = VisualTransformation.None,
    imeAction: ImeAction = ImeAction.Default,
    onDone: (() -> Unit)? = null,
) {
    BasicTextField(
        value = value,
        onValueChange = onChange,
        modifier = modifier.fillMaxWidth().semantics { contentDescription = description },
        textStyle = style.copy(color = MaterialTheme.colorScheme.onSurface),
        singleLine = singleLine || imeAction == ImeAction.Done,
        cursorBrush = SolidColor(MaterialTheme.colorScheme.primary),
        visualTransformation = transformation,
        keyboardOptions = KeyboardOptions(capitalization = KeyboardCapitalization.Sentences, imeAction = imeAction),
        keyboardActions = KeyboardActions(onDone = { onDone?.invoke() }),
        decorationBox = { inner ->
            Box {
                if (value.text.isEmpty()) Text(placeholder, style = style, color = MaterialTheme.colorScheme.outline)
                inner()
            }
        },
    )
}

/** Highlights the words Quick Add understood. */
private class Highlight(private val ranges: List<IntRange>, private val color: Color) : VisualTransformation {
    override fun filter(text: AnnotatedString): TransformedText {
        val b = AnnotatedString.Builder(text)
        for (r in ranges) {
            if (r.first >= 0 && r.last < text.length) b.addStyle(SpanStyle(background = color), r.first, r.last + 1)
        }
        return TransformedText(b.toAnnotatedString(), OffsetMapping.Identity)
    }

    override fun equals(other: Any?) = other is Highlight && other.ranges == ranges && other.color == color
    override fun hashCode() = ranges.hashCode() * 31 + color.hashCode()
}

/** The default for a reminder time on a date that has none. */
fun defaultTime(day: LocalDate?, now: LocalDateTime): LocalTime =
    if (day == null || day == now.toLocalDate()) Dates.nextHour(now.toLocalTime()) else LocalTime.of(9, 0)
