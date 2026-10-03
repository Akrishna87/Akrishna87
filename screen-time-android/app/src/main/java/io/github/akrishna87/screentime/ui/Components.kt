package io.github.akrishna87.screentime.ui

import android.content.Context
import android.content.pm.PackageManager
import android.util.LruCache
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material.icons.automirrored.rounded.TrendingDown
import androidx.compose.material.icons.automirrored.rounded.TrendingFlat
import androidx.compose.material.icons.automirrored.rounded.TrendingUp
import androidx.compose.material.icons.rounded.ChevronLeft
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.core.graphics.drawable.toBitmap
import io.github.akrishna87.screentime.AppTotal
import io.github.akrishna87.screentime.Bar
import io.github.akrishna87.screentime.Period
import io.github.akrishna87.screentime.Trend
import io.github.akrishna87.screentime.UsageViewModel
import io.github.akrishna87.screentime.spanLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PeriodPicker(vm: UsageViewModel, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier.fillMaxWidth()) {
        Period.entries.forEachIndexed { i, period ->
            SegmentedButton(
                selected = vm.period == period,
                onClick = { vm.period = period },
                shape = SegmentedButtonDefaults.itemShape(i, Period.entries.size),
            ) { Text(period.label) }
        }
    }
}

/** ‹ Today › — steps back and forward through days, weeks or months. */
@Composable
fun SpanNavigator(vm: UsageViewModel, firstDay: LocalDate?, modifier: Modifier = Modifier) {
    val previous = when (vm.period) {
        Period.DAY -> "Previous day"
        Period.WEEK -> "Previous week"
        Period.MONTH -> "Previous month"
    }
    val next = when (vm.period) {
        Period.DAY -> "Next day"
        Period.WEEK -> "Next week"
        Period.MONTH -> "Next month"
    }
    Row(modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
        IconButton(onClick = vm::previous, enabled = vm.canGoBack(firstDay)) {
            Icon(Icons.Rounded.ChevronLeft, contentDescription = previous)
        }
        Text(
            spanLabel(vm.span, vm.today),
            modifier = Modifier.weight(1f),
            style = MaterialTheme.typography.titleMedium,
            textAlign = TextAlign.Center,
        )
        IconButton(onClick = vm::next, enabled = vm.canGoNext) {
            Icon(Icons.Rounded.ChevronRight, contentDescription = next)
        }
    }
}

/** The big number at the top: total time, with the daily average (or something else) under it. */
@Composable
fun TotalHeader(total: Long, subtitle: String, modifier: Modifier = Modifier) {
    Column(modifier.fillMaxWidth().padding(vertical = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(formatDuration(total), style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(subtitle, style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
    }
}

/** "↓ 25 min less than yesterday at this time (−18%)", in a pill under the total. */
@Composable
fun TrendPill(trend: Trend, modifier: Modifier = Modifier) {
    val colors = MaterialTheme.colorScheme
    val icon = when {
        trend.same -> Icons.AutoMirrored.Rounded.TrendingFlat
        trend.difference < 0 -> Icons.AutoMirrored.Rounded.TrendingDown
        else -> Icons.AutoMirrored.Rounded.TrendingUp
    }
    val (background, content) = when {
        trend.same -> colors.surfaceVariant to colors.onSurfaceVariant
        trend.difference < 0 -> colors.primaryContainer to colors.onPrimaryContainer
        else -> colors.secondaryContainer to colors.onSecondaryContainer
    }
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
        Row(
            Modifier
                .clip(RoundedCornerShape(50))
                .background(background)
                .padding(horizontal = 14.dp, vertical = 7.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(icon, contentDescription = null, tint = content, modifier = Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text(trendText(trend), style = MaterialTheme.typography.labelLarge, color = content, textAlign = TextAlign.Center)
        }
    }
}

/** A simple bar chart. Bars with a day can be tapped to open that day. */
@Composable
fun BarChart(bars: List<Bar>, modifier: Modifier = Modifier, onDayClick: ((LocalDate) -> Unit)? = null) {
    val top = chartTop(bars.maxOfOrNull { it.value } ?: 0L)
    val colors = MaterialTheme.colorScheme
    val gap = if (bars.size > 12) 2.dp else 6.dp
    Column(modifier.fillMaxWidth()) {
        Text(
            if (top > 0) formatShort(top) else "",
            style = MaterialTheme.typography.labelSmall,
            color = colors.onSurfaceVariant,
            modifier = Modifier.align(Alignment.End),
        )
        Row(
            Modifier
                .fillMaxWidth()
                .height(140.dp)
                .drawBehind {
                    val stroke = 1.dp.toPx()
                    listOf(0f, size.height / 2, size.height - stroke / 2).forEach { y ->
                        drawLine(colors.outlineVariant, Offset(0f, y), Offset(size.width, y), stroke)
                    }
                },
            horizontalArrangement = Arrangement.spacedBy(gap),
            verticalAlignment = Alignment.Bottom,
        ) {
            bars.forEach { bar ->
                val day = bar.day
                val tap = if (onDayClick != null && day != null) Modifier.clickable { onDayClick(day) } else Modifier
                Box(Modifier.weight(1f).fillMaxHeight().then(tap), contentAlignment = Alignment.BottomCenter) {
                    if (bar.value > 0 && top > 0) {
                        val fraction = (bar.value.toFloat() / top).coerceIn(0.02f, 1f)
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .fillMaxHeight(fraction)
                                .clip(RoundedCornerShape(topStart = 3.dp, topEnd = 3.dp))
                                .background(if (bar.highlight) colors.primary else colors.primary.copy(alpha = 0.45f))
                        )
                    }
                }
            }
        }
        Row(Modifier.fillMaxWidth().padding(top = 4.dp), horizontalArrangement = Arrangement.spacedBy(gap)) {
            bars.forEach { bar ->
                Box(Modifier.weight(1f)) {
                    bar.label?.let {
                        Text(
                            it,
                            style = MaterialTheme.typography.labelSmall,
                            color = colors.onSurfaceVariant,
                            maxLines = 1,
                            softWrap = false,
                            overflow = TextOverflow.Visible,
                        )
                    }
                }
            }
        }
    }
}

/** One app in the list: icon, name, a bar for its share, how often it was opened, and its time. */
@Composable
fun AppRow(app: AppTotal, longest: Long, onClick: () -> Unit) {
    val colors = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        AppIcon(app.packageName, 40.dp)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                app.label ?: app.packageName,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box(
                Modifier
                    .padding(vertical = 6.dp)
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(CircleShape)
                    .background(colors.surfaceVariant)
            ) {
                val share = if (longest > 0) (app.foregroundMs.toFloat() / longest).coerceIn(0.01f, 1f) else 0f
                Box(Modifier.fillMaxWidth(share).fillMaxHeight().clip(CircleShape).background(colors.primary))
            }
            opensText(app.opens)?.let {
                Text(it, style = MaterialTheme.typography.labelMedium, color = colors.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(14.dp))
        Text(formatDuration(app.foregroundMs), style = MaterialTheme.typography.bodyMedium)
    }
}

private val iconCache = LruCache<String, ImageBitmap>(150)

@Composable
fun AppIcon(packageName: String, size: Dp) {
    val context = LocalContext.current
    val icon by produceState<ImageBitmap?>(iconCache.get(packageName), packageName) {
        if (value == null) value = withContext(Dispatchers.IO) { loadIcon(context, packageName) }
    }
    val bitmap = icon
    if (bitmap != null) {
        Image(bitmap, contentDescription = null, modifier = Modifier.size(size))
    } else {
        Box(Modifier.size(size).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant))
    }
}

private fun loadIcon(context: Context, packageName: String): ImageBitmap? = try {
    context.packageManager.getApplicationIcon(packageName).toBitmap(128, 128).asImageBitmap()
        .also { iconCache.put(packageName, it) }
} catch (e: PackageManager.NameNotFoundException) {
    null // uninstalled since
}
