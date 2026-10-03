package io.github.akrishna87.daybook.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.Schedule
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.daybook.model.Agenda
import io.github.akrishna87.daybook.model.Event
import io.github.akrishna87.daybook.model.Task
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth

/** A frosted-glass card, like the widgets on the lock screen. */
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(26.dp),
    tint: Color = Palette.Glass,
    contentPadding: PaddingValues = PaddingValues(16.dp),
    onClick: (() -> Unit)? = null,
    content: @Composable ColumnScope.() -> Unit,
) {
    Column(
        modifier
            .clip(shape)
            .background(tint)
            .border(1.dp, Palette.GlassBorder, shape)
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(contentPadding),
        content = content,
    )
}

@Composable
fun Pill(text: String, color: Color) {
    Text(
        text,
        Modifier.clip(RoundedCornerShape(8.dp)).background(color).padding(horizontal = 8.dp, vertical = 1.dp),
        color = Color.White,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** An event with its coloured bar on the left, its title, and a clock with its time. */
@Composable
fun EventLine(
    event: Event,
    day: LocalDate,
    is24Hour: Boolean,
    modifier: Modifier = Modifier,
    past: Boolean = false,
    titleStyle: TextStyle = MaterialTheme.typography.titleMedium,
    maxLines: Int = 2,
    onClick: (() -> Unit)? = null,
) {
    Row(
        modifier
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .height(IntrinsicSize.Min)
            .alpha(if (past) 0.62f else 1f),
    ) {
        Box(Modifier.width(4.dp).fillMaxHeight().clip(RoundedCornerShape(2.dp)).background(Color(event.color)))
        Column(Modifier.padding(start = 10.dp, top = 1.dp, bottom = 1.dp)) {
            Text(event.title, style = titleStyle, maxLines = maxLines, overflow = TextOverflow.Ellipsis)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Rounded.Schedule, null, Modifier.size(13.dp), tint = Palette.SubText)
                Text(
                    Agenda.timeLabel(event, day, is24Hour),
                    Modifier.padding(start = 4.dp),
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.SubText,
                )
            }
        }
    }
}

/** Up to three event colours per day, for the dots under the calendar's numbers. */
fun dotsFor(events: List<Event>, month: YearMonth): Map<LocalDate, List<Int>> =
    (1..month.lengthOfMonth()).associate { d ->
        val day = month.atDay(d)
        day to Agenda.eventsOn(day, events).map { it.color }.distinct().take(3)
    }.filterValues { it.isNotEmpty() }

/**
 * A month of days. Compact is the small calendar on Today; the full size fills the Calendar tab,
 * where a day can be selected.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    today: LocalDate,
    weekStart: DayOfWeek,
    dots: Map<LocalDate, List<Int>>,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
    selected: LocalDate? = null,
    onDayClick: ((LocalDate) -> Unit)? = null,
) {
    val cellHeight = if (compact) 24.dp else 48.dp
    val circle = if (compact) 21.dp else 36.dp
    val numberSize = if (compact) 12.sp else 16.sp
    Column(modifier) {
        Row(Modifier.fillMaxWidth()) {
            for (letter in Agenda.weekdayLetters(weekStart)) {
                Text(
                    letter,
                    Modifier.weight(1f).padding(bottom = if (compact) 2.dp else 6.dp),
                    textAlign = TextAlign.Center,
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = FontWeight.Bold,
                    color = Palette.SubText,
                )
            }
        }
        for (week in Agenda.monthGrid(month, weekStart)) {
            Row(Modifier.fillMaxWidth()) {
                for (day in week) {
                    Box(
                        Modifier
                            .weight(1f)
                            .height(cellHeight)
                            .then(
                                if (day != null && onDayClick != null) {
                                    Modifier.clip(RoundedCornerShape(12.dp)).clickable(role = Role.Button) { onDayClick(day) }
                                } else {
                                    Modifier
                                },
                            ),
                        contentAlignment = Alignment.TopCenter,
                    ) {
                        if (day == null) return@Box
                        val isToday = day == today
                        val isSelected = day == selected && !compact
                        Box(
                            Modifier
                                .padding(top = if (compact) 0.dp else 2.dp)
                                .size(circle)
                                .clip(CircleShape)
                                .background(
                                    when {
                                        isToday -> Palette.TodayFill
                                        isSelected -> Color.White.copy(alpha = 0.22f)
                                        else -> Color.Transparent
                                    },
                                )
                                .then(if (isSelected) Modifier.border(1.5.dp, Color.White, CircleShape) else Modifier),
                            contentAlignment = Alignment.Center,
                        ) {
                            Text(
                                day.dayOfMonth.toString(),
                                fontSize = numberSize,
                                fontWeight = if (isToday || isSelected) FontWeight.Bold else FontWeight.Medium,
                                color = Color.White,
                            )
                        }
                        val colors = dots[day].orEmpty().take(if (compact) 1 else 3)
                        if (colors.isNotEmpty()) {
                            Row(
                                Modifier.align(Alignment.BottomCenter).padding(bottom = if (compact) 0.dp else 3.dp),
                                horizontalArrangement = Arrangement.spacedBy(2.dp),
                            ) {
                                for (c in colors) Box(Modifier.size(if (compact) 3.dp else 5.dp).clip(CircleShape).background(Color(c)))
                            }
                        }
                    }
                }
            }
        }
    }
}

/** "2026 … 75%" with a progress line, like the lock screen. */
@Composable
fun YearProgress(today: LocalDate, modifier: Modifier = Modifier) {
    val percent = Agenda.yearPercent(today)
    val left = Agenda.daysLeft(today)
    Column(modifier) {
        Row(verticalAlignment = Alignment.Bottom) {
            Text(today.year.toString(), fontSize = 18.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.weight(1f))
            Text("$percent%", fontSize = 20.sp, fontWeight = FontWeight.Bold)
        }
        Box(Modifier.padding(vertical = 6.dp).fillMaxWidth().height(3.dp).clip(CircleShape).background(Color.White.copy(alpha = 0.35f))) {
            Box(Modifier.fillMaxWidth(percent / 100f).fillMaxHeight().clip(CircleShape).background(Color.White))
        }
        Row {
            Text("$percent% of this year has passed", style = MaterialTheme.typography.bodySmall, color = Palette.SubText)
            Spacer(Modifier.weight(1f))
            Text(if (left == 1) "1 day left" else "$left days left", style = MaterialTheme.typography.bodySmall, color = Palette.SubText)
        }
    }
}

/** A rounded checkbox in the task's colour. */
@Composable
fun TaskCheck(task: Task, onToggle: () -> Unit, size: Int = 22) {
    val color = Color(task.color)
    Box(
        Modifier
            .size(size.dp + 18.dp)
            .clip(CircleShape)
            .clickable(role = Role.Checkbox, onClick = onToggle)
            .semantics { contentDescription = if (task.done) "Mark ${task.title} as not done" else "Mark ${task.title} as done" },
        contentAlignment = Alignment.Center,
    ) {
        Box(
            Modifier
                .size(size.dp)
                .clip(RoundedCornerShape((size / 3.5f).dp))
                .background(if (task.done) color else Color.Transparent)
                .border(2.dp, color, RoundedCornerShape((size / 3.5f).dp)),
            contentAlignment = Alignment.Center,
        ) {
            if (task.done) Icon(Icons.Rounded.Check, null, Modifier.size((size - 6).dp), tint = Color.White)
        }
    }
}

@Composable
fun TaskTitle(task: Task, modifier: Modifier = Modifier, style: TextStyle = MaterialTheme.typography.bodyLarge) {
    Text(
        task.title,
        modifier,
        style = style,
        maxLines = 2,
        overflow = TextOverflow.Ellipsis,
        color = if (task.done) Palette.Faint else Color.White,
        textDecoration = if (task.done) TextDecoration.LineThrough else null,
    )
}

/** A row of colour swatches; [none] adds a "no colour" choice first (used by notes). */
@Composable
fun ColorChoices(colors: List<Int>, selected: Int, onPick: (Int) -> Unit, modifier: Modifier = Modifier, none: Boolean = false) {
    Row(modifier, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
        val all = if (none) listOf(0) + colors else colors
        for (c in all) {
            val isSelected = c == selected
            Box(
                Modifier
                    .size(32.dp)
                    .clip(CircleShape)
                    .background(if (c == 0) Palette.Glass else Color(c))
                    .border(if (isSelected) 2.5.dp else 1.dp, if (isSelected) Color.White else Palette.GlassBorder, CircleShape)
                    .clickable(role = Role.RadioButton, onClickLabel = "Choose colour") { onPick(c) },
                contentAlignment = Alignment.Center,
            ) {
                if (isSelected) Icon(Icons.Rounded.Check, "Selected", Modifier.size(18.dp), tint = if (c == 0) Color.White else Palette.TodayFill)
            }
        }
    }
}

@Composable
fun SectionLabel(text: String, modifier: Modifier = Modifier) {
    Text(text, modifier, style = MaterialTheme.typography.titleSmall, color = Palette.SubText)
}
