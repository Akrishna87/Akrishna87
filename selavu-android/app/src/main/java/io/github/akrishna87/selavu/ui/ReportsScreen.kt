@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.akrishna87.selavu.ui

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import io.github.akrishna87.selavu.AppViewModel
import io.github.akrishna87.selavu.Screen
import io.github.akrishna87.selavu.core.Bucket
import io.github.akrishna87.selavu.core.CategoryShare
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.PeriodType
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.core.averagePerDay
import io.github.akrishna87.selavu.core.byCategory
import io.github.akrishna87.selavu.core.dayHeading
import io.github.akrishna87.selavu.core.inPeriod
import io.github.akrishna87.selavu.core.trend
import java.time.LocalDate
import kotlin.math.roundToInt

@Composable
fun ReportsScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    var type by rememberSaveable { mutableStateOf(TxType.EXPENSE) }
    val period = vm.period()
    val today = LocalDate.now()
    val currency = ledger.settings.currency
    val inPeriod = ledger.transactions.inPeriod(period)
    val shares = ledger.byCategory(inPeriod, type)
    val total = shares.sumOf { it.total }
    val buckets = trend(period, inPeriod, type)

    Column(Modifier.fillMaxSize().padding(padding)) {
        PeriodPicker(vm.periodType, period, vm::choosePeriodType, vm::shiftPeriod, vm::backToToday)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 16.dp)) {
            item {
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp)) {
                    listOf(TxType.EXPENSE to "Spending", TxType.INCOME to "Income").forEachIndexed { i, (t, label) ->
                        SegmentedButton(
                            selected = type == t, onClick = { type = t },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
            }
            if (shares.isEmpty()) {
                item {
                    EmptyState(
                        "📊", if (type == TxType.EXPENSE) "No spending in this period" else "No income in this period",
                        "Charts appear here once there's something to show.",
                    )
                }
                return@LazyColumn
            }
            item {
                Box(Modifier.fillMaxWidth().padding(vertical = 8.dp), contentAlignment = Alignment.Center) {
                    Donut(shares, Modifier.size(200.dp))
                    Column(horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (type == TxType.EXPENSE) "Spent" else "Earned", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        Text(Money.format(total, currency), style = MaterialTheme.typography.headlineSmall)
                        if (period.type != PeriodType.DAY) {
                            Text(
                                "${Money.format(averagePerDay(period, total, today), currency)} a day",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                }
            }
            item { SectionHeader("By category") }
            items(shares, key = { it.category.id }) { s ->
                ShareRow(s, currency) { vm.open(Screen.CategoryDetail(s.category.id)) }
            }
            if (buckets.isNotEmpty()) {
                item { SectionHeader(if (period.type == PeriodType.YEAR) "By month" else "By day") }
                item {
                    Card(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
                        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
                    ) {
                        Bars(buckets, currency, amountColor(type), Modifier.padding(12.dp))
                    }
                }
            }
        }
    }
}

@Composable
private fun Donut(shares: List<CategoryShare>, modifier: Modifier) {
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    Canvas(modifier.semantics { contentDescription = "Chart by category" }) {
        val stroke = size.minDimension * 0.16f
        val d = size.minDimension - stroke
        val topLeft = Offset((size.width - d) / 2, (size.height - d) / 2)
        drawArc(track, 0f, 360f, false, topLeft, Size(d, d), style = Stroke(stroke))
        // A small gap between slices, unless one category is everything.
        val gap = if (shares.size > 1) 1.5f else 0f
        var start = -90f
        shares.forEach { s ->
            val sweep = s.share * 360f
            if (sweep > gap) drawArc(Color(s.category.color), start + gap / 2, sweep - gap, false, topLeft, Size(d, d), style = Stroke(stroke, cap = StrokeCap.Butt))
            start += sweep
        }
    }
}

@Composable
private fun ShareRow(s: CategoryShare, currency: String, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        CategoryIcon(s.category, 36.dp)
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.category.name, style = MaterialTheme.typography.bodyLarge, modifier = Modifier.weight(1f))
                Text(Money.format(s.total, currency), fontWeight = FontWeight.SemiBold)
            }
            Spacer(Modifier.height(4.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                LinearProgressIndicator(
                    progress = { s.share },
                    modifier = Modifier.weight(1f).height(6.dp),
                    color = Color(s.category.color),
                    trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                    strokeCap = StrokeCap.Round,
                    drawStopIndicator = {},
                )
                Text(
                    "${(s.share * 100).roundToInt()}%",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.End,
                    modifier = Modifier.width(44.dp),
                )
            }
        }
    }
}

@Composable
private fun Bars(buckets: List<Bucket>, currency: String, color: Color, modifier: Modifier) {
    val max = buckets.maxOf { it.total }.coerceAtLeast(1)
    val track = MaterialTheme.colorScheme.surfaceContainerHighest
    val today = LocalDate.now()
    Column(modifier) {
        Text(
            "Highest: ${Money.format(max, currency)}",
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Spacer(Modifier.height(8.dp))
        Canvas(Modifier.fillMaxWidth().height(140.dp)) {
            val slot = size.width / buckets.size
            val bar = (slot * 0.62f).coerceAtLeast(2f)
            buckets.forEachIndexed { i, b ->
                val h = size.height * b.total / max
                val x = i * slot + (slot - bar) / 2
                val isNow = !today.isBefore(b.start) && !today.isAfter(b.end)
                drawRoundRect(track, Offset(x, 0f), Size(bar, size.height), CornerRadius(bar / 2))
                if (b.total > 0) {
                    drawRoundRect(
                        if (isNow) color else color.copy(alpha = 0.75f),
                        Offset(x, size.height - h), Size(bar, h), CornerRadius(bar / 2),
                    )
                }
            }
        }
        // Labels: every bar for a week or year, about every fifth day for a month.
        val step = if (buckets.size > 12) 5 else 1
        Row(Modifier.fillMaxWidth().padding(top = 4.dp)) {
            buckets.forEachIndexed { i, b ->
                Text(
                    if (i % step == 0) b.label else "",
                    modifier = Modifier.weight(1f),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }
    }
}

/** Everything in one category for the period being looked at. */
@Composable
fun CategoryDetailScreen(vm: AppViewModel, ledger: Ledger, categoryId: String, padding: PaddingValues) {
    val category = ledger.category(categoryId)
    val period = vm.period()
    val today = LocalDate.now()
    val items = ledger.transactions.inPeriod(period).filter { it.categoryId == categoryId }.sortedByDescending { it.date }
    Column(Modifier.fillMaxSize().padding(padding)) {
        TopAppBar(
            windowInsets = WindowInsets(0),
            title = { Text("${category?.emoji ?: ""} ${category?.name ?: "Category"}".trim()) },
            navigationIcon = {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back") }
            },
        )
        PeriodPicker(vm.periodType, period, vm::choosePeriodType, vm::shiftPeriod, vm::backToToday)
        LazyColumn(Modifier.weight(1f)) {
            item {
                SectionHeader(
                    "${items.size} ${if (items.size == 1) "entry" else "entries"}",
                    Money.format(items.sumOf { it.amount }, ledger.settings.currency),
                )
            }
            if (items.isEmpty()) item { EmptyState("🗂️", "Nothing in this period", "Try another period with the arrows above.") }
            items(items, key = { it.id }) { t ->
                TransactionRow(t, category, ledger.settings.currency, subtitle = dayHeading(t.date, today)) {
                    vm.open(Screen.Edit(t.type, t.id))
                }
            }
        }
    }
}
