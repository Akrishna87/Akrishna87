package io.github.akrishna87.daybook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.RowScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.CalendarToday
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SubdirectoryArrowRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import io.github.akrishna87.daybook.model.Data
import io.github.akrishna87.daybook.model.Dates
import io.github.akrishna87.daybook.model.DueTone
import io.github.akrishna87.daybook.model.INBOX_ID
import io.github.akrishna87.daybook.model.Priority
import io.github.akrishna87.daybook.model.Repeat
import io.github.akrishna87.daybook.model.Span
import io.github.akrishna87.daybook.model.Task
import java.time.LocalDateTime

/** The time now, updated each minute, and whether the phone uses a 24-hour clock. */
@Immutable
data class Clock(val now: LocalDateTime, val is24Hour: Boolean) {
    val today: java.time.LocalDate get() = now.toLocalDate()
}

val LocalClock = compositionLocalOf { Clock(LocalDateTime.now(), true) }
val LocalActions = staticCompositionLocalOf<TaskActions> { error("No TaskActions") }

@Composable
fun toneColor(tone: DueTone?): Color {
    val x = LocalExtra.current
    return when (tone) {
        DueTone.OVERDUE -> x.overdue
        DueTone.TODAY -> x.today
        DueTone.TOMORROW -> x.tomorrow
        DueTone.THIS_WEEK -> x.thisWeek
        DueTone.LATER, null -> x.later
    }
}

/** Todoist's round checkbox: the ring takes the priority colour, and a tick appears once done. */
@Composable
fun PriorityCheck(task: Task, onToggle: () -> Unit, size: Dp = 22.dp) {
    val color = Color(Priority.color(task.priority))
    val prioritised = task.priority < Priority.NONE
    Box(
        Modifier
            .size(size + 20.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = if (task.done) "Mark ${task.title} as not done" else "Complete ${task.title}" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size)
                .clip(CircleShape)
                .background(if (task.done) color else if (prioritised) color.copy(alpha = 0.12f) else Color.Transparent)
                .border(if (prioritised) 2.dp else 1.5.dp, color, CircleShape),
            contentAlignment = Alignment.Center,
        ) {
            if (task.done) Icon(Icons.Rounded.Check, null, Modifier.size(size * 0.72f), tint = Color.White)
        }
    }
}

/** A task in a list: its checkbox, title, and a line with its date, repeat, sub-tasks, labels and project. */
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun TaskRow(
    task: Task,
    data: Data,
    modifier: Modifier = Modifier,
    showProject: Boolean = true,
    showDue: Boolean = true,
    /** Under a day's heading, show just the time rather than repeating the day. */
    timeOnly: Boolean = false,
    onClick: () -> Unit,
) {
    val clock = LocalClock.current
    val actions = LocalActions.current
    val muted = MaterialTheme.colorScheme.onSurfaceVariant
    val subs = if (task.parentId == null) data.subtasks(task.id) else emptyList()
    Column(modifier.background(MaterialTheme.colorScheme.surface)) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 6.dp, end = 16.dp, top = 2.dp, bottom = 8.dp),
            verticalAlignment = Alignment.Top,
        ) {
            PriorityCheck(task, onToggle = { actions.toggle(task) })
            Column(Modifier.weight(1f).padding(top = 10.dp)) {
                Text(
                    task.title,
                    style = MaterialTheme.typography.bodyLarge,
                    maxLines = 3,
                    overflow = TextOverflow.Ellipsis,
                    color = if (task.done) muted else MaterialTheme.colorScheme.onSurface,
                    textDecoration = if (task.done) TextDecoration.LineThrough else null,
                )
                if (task.description.isNotBlank()) {
                    Text(task.description.lineSequence().first(), style = MaterialTheme.typography.bodySmall, color = muted, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                val due = when {
                    !showDue -> null
                    timeOnly && task.time != null -> Dates.timeFormatter(clock.is24Hour).format(task.time)
                    else -> Dates.dueLabel(task, clock.today, clock.is24Hour)
                }
                val project = data.project(task.projectId).takeIf { showProject && (task.projectId != INBOX_ID || task.parentId == null) }
                val hasMeta = due != null || subs.isNotEmpty() || task.labels.isNotEmpty() || task.noteId != null || task.parentId != null ||
                    (showProject && task.projectId != INBOX_ID)
                if (hasMeta) {
                    FlowRow(
                        Modifier.padding(top = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                    ) {
                        if (due != null) {
                            val c = toneColor(Dates.tone(task, clock.now))
                            Meta(Icons.Rounded.CalendarToday, due, c)
                            if (task.repeat != Repeat.NONE) Icon(Icons.Rounded.Repeat, task.repeat.label, Modifier.size(14.dp).align(Alignment.CenterVertically), tint = c)
                        }
                        if (subs.isNotEmpty()) Meta(Icons.Rounded.SubdirectoryArrowRight, "${subs.count { it.done }}/${subs.size}", muted)
                        if (task.noteId != null) {
                            Icon(Icons.Rounded.Description, "Linked note", Modifier.size(14.dp).align(Alignment.CenterVertically), tint = muted)
                        }
                        for (l in task.labels) Text("@$l", style = MaterialTheme.typography.labelMedium, color = muted)
                        if (project != null && task.projectId != INBOX_ID) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(project.name, style = MaterialTheme.typography.labelMedium, color = muted)
                                Spacer(Modifier.width(4.dp))
                                Box(Modifier.size(7.dp).clip(CircleShape).background(Color(project.color)))
                            }
                        }
                    }
                }
            }
        }
        HorizontalDivider(Modifier.padding(start = 48.dp), color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
private fun Meta(icon: ImageVector, text: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Icon(icon, null, Modifier.size(13.dp), tint = color)
        Spacer(Modifier.width(3.dp))
        Text(text, style = MaterialTheme.typography.labelMedium, color = color)
    }
}

/** Swipe right to complete, left to delete, with Undo in the snackbar. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeTask(task: Task, modifier: Modifier = Modifier, content: @Composable () -> Unit) {
    val actions = LocalActions.current
    val extra = LocalExtra.current
    val state = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            when (value) {
                SwipeToDismissBoxValue.StartToEnd -> actions.toggle(task)
                SwipeToDismissBoxValue.EndToStart -> actions.delete(task)
                SwipeToDismissBoxValue.Settled -> {}
            }
            // Snap back: the list itself drops the task (or keeps a repeating one with its new date).
            false
        },
    )
    SwipeToDismissBox(
        state = state,
        modifier = modifier,
        backgroundContent = {
            val toStart = state.dismissDirection == SwipeToDismissBoxValue.EndToStart
            Box(
                Modifier.fillMaxSize().background(if (toStart) extra.delete else extra.complete).padding(horizontal = 24.dp),
                contentAlignment = if (toStart) Alignment.CenterEnd else Alignment.CenterStart,
            ) {
                Icon(if (toStart) Icons.Rounded.Delete else Icons.Rounded.Check, null, tint = Color.White)
            }
        },
    ) { content() }
}

/** A big screen title with an optional line under it and action icons on the right. */
@Composable
fun ScreenHeader(title: String, subtitle: String? = null, modifier: Modifier = Modifier, actions: @Composable RowScope.() -> Unit = {}) {
    Row(modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 12.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(title, style = MaterialTheme.typography.headlineMedium, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (subtitle != null) Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        actions()
    }
}

/** A plain top bar for pages: back, a title, and actions. */
@Composable
fun PageBar(title: String = "", onBack: () -> Unit, leading: @Composable RowScope.() -> Unit = {}, actions: @Composable RowScope.() -> Unit = {}) {
    Row(Modifier.fillMaxWidth().height(56.dp).padding(horizontal = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = onBack, Modifier.semantics { contentDescription = "Back" }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, null) }
        leading()
        if (title.isNotEmpty()) {
            Text(title, Modifier.weight(1f).padding(start = 4.dp), style = MaterialTheme.typography.titleLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
        } else {
            Spacer(Modifier.weight(1f))
        }
        actions()
    }
}

@Composable
fun SearchAction(onClick: () -> Unit) {
    IconButton(onClick = onClick, Modifier.semantics { contentDescription = "Search" }) { Icon(Icons.Rounded.Search, null) }
}

@Composable
fun SectionHeader(title: String, modifier: Modifier = Modifier, color: Color = MaterialTheme.colorScheme.onSurface, trailing: @Composable RowScope.() -> Unit = {}) {
    Column(modifier) {
        Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp).height(40.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(title, Modifier.weight(1f), style = MaterialTheme.typography.titleSmall, color = color)
            trailing()
        }
        HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
    }
}

@Composable
fun EmptyState(icon: ImageVector, title: String, message: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(horizontal = 40.dp, vertical = 48.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Box(
            Modifier.size(88.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center,
        ) { Icon(icon, null, Modifier.size(40.dp), tint = MaterialTheme.colorScheme.primary) }
        Text(title, Modifier.padding(top = 20.dp), style = MaterialTheme.typography.titleMedium, textAlign = TextAlign.Center)
        Text(
            message,
            Modifier.padding(top = 6.dp),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
fun ColorDot(color: Int, size: Dp = 10.dp) {
    Box(Modifier.size(size).clip(CircleShape).background(Color(color)))
}

/** A row in Browse and Settings: an icon, a label, and a count or control on the right. */
@Composable
fun ListRow(
    label: String,
    modifier: Modifier = Modifier,
    leading: @Composable () -> Unit = {},
    detail: String? = null,
    trailing: @Composable RowScope.() -> Unit = {},
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .fillMaxWidth()
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(start = 20.dp, end = 12.dp)
            .height(if (detail != null) 64.dp else 52.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(36.dp), contentAlignment = Alignment.CenterStart) { leading() }
        Column(Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyLarge, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (detail != null) Text(detail, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
        }
        trailing()
    }
}

@Composable
fun Count(n: Int) {
    if (n > 0) Text(n.toString(), Modifier.padding(end = 8.dp), style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
}

/** Inline Markdown runs as styled text. */
fun styled(spans: List<Span>): AnnotatedString = buildAnnotatedString {
    for (s in spans) {
        withStyle(
            SpanStyle(
                fontWeight = if (s.bold) FontWeight.Bold else null,
                fontStyle = if (s.italic) FontStyle.Italic else null,
                textDecoration = if (s.strike) TextDecoration.LineThrough else null,
            ),
        ) { append(s.text) }
    }
}
