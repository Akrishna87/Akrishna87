package io.github.akrishna87.selavu.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Remove
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.selavu.AppViewModel
import io.github.akrishna87.selavu.Screen
import io.github.akrishna87.selavu.core.BudgetPeriod
import io.github.akrishna87.selavu.core.BudgetStatus
import io.github.akrishna87.selavu.core.Ledger
import io.github.akrishna87.selavu.core.Money
import io.github.akrishna87.selavu.core.PeriodType
import io.github.akrishna87.selavu.core.Totals
import io.github.akrishna87.selavu.core.TxType
import io.github.akrishna87.selavu.core.budgetFor
import io.github.akrishna87.selavu.core.byDay
import io.github.akrishna87.selavu.core.dayHeading
import io.github.akrishna87.selavu.core.inPeriod
import io.github.akrishna87.selavu.core.totals
import java.time.LocalDate

@Composable
fun HomeScreen(vm: AppViewModel, ledger: Ledger, padding: PaddingValues) {
    val period = vm.period()
    val today = LocalDate.now()
    val currency = ledger.settings.currency
    val inPeriod = ledger.transactions.inPeriod(period)
    val totals = inPeriod.totals()
    val days = inPeriod.byDay()
    // The budget card shows when looking at the same kind of period the budget is set for,
    // and on a single day, where it says how much is left for that day.
    val budgetType = if (ledger.settings.budgetPeriod == BudgetPeriod.WEEKLY) PeriodType.WEEK else PeriodType.MONTH
    val budget = when (vm.periodType) {
        budgetType, PeriodType.DAY -> ledger.budgetFor(period.start, today)
        else -> null
    }

    Column(Modifier.fillMaxSize().padding(padding)) {
        PeriodPicker(vm.periodType, period, vm::choosePeriodType, vm::shiftPeriod, vm::backToToday)
        LazyColumn(Modifier.weight(1f), contentPadding = PaddingValues(bottom = 12.dp)) {
            item { SummaryCard(totals, currency) }
            if (budget != null) item { BudgetCard(budget, currency, perDayOnly = vm.periodType == PeriodType.DAY) }
            if (days.isEmpty()) {
                item {
                    EmptyState(
                        "👛", "Nothing logged ${if (today in period) "yet" else "here"}",
                        "Tap Expense or Income below to add one.",
                    )
                }
            }
            days.forEach { day ->
                item(key = "h" + day.date) {
                    SectionHeader(
                        dayHeading(day.date, today),
                        Money.format(day.net, currency, sign = true),
                    )
                }
                items(day.transactions, key = { it.id }) { t ->
                    TransactionRow(t, ledger.category(t.categoryId), currency) {
                        vm.open(Screen.Edit(t.type, t.id))
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val money = LocalMoneyColors.current
            Button(
                onClick = { vm.open(Screen.Edit(TxType.EXPENSE)) },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = money.expense, contentColor = MaterialTheme.colorScheme.surface),
            ) {
                Icon(Icons.Default.Remove, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Expense", style = MaterialTheme.typography.titleMedium)
            }
            Button(
                onClick = { vm.open(Screen.Edit(TxType.INCOME)) },
                modifier = Modifier.weight(1f).height(56.dp),
                colors = ButtonDefaults.buttonColors(containerColor = money.income, contentColor = MaterialTheme.colorScheme.surface),
            ) {
                Icon(Icons.Default.Add, contentDescription = null)
                Spacer(Modifier.size(6.dp))
                Text("Income", style = MaterialTheme.typography.titleMedium)
            }
        }
    }
}

@Composable
private fun SummaryCard(totals: Totals, currency: String) {
    val money = LocalMoneyColors.current
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
    ) {
        Column(Modifier.padding(20.dp)) {
            Text("Balance", style = MaterialTheme.typography.labelLarge, color = MaterialTheme.colorScheme.onPrimaryContainer)
            Text(
                Money.format(totals.balance, currency),
                style = MaterialTheme.typography.displaySmall,
                color = MaterialTheme.colorScheme.onPrimaryContainer,
            )
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text("Income", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(Money.format(totals.income, currency), color = money.income, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.End) {
                    Text("Expenses", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                    Text(Money.format(totals.expense, currency), color = money.expense, fontWeight = FontWeight.Bold, style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}

@Composable
private fun BudgetCard(b: BudgetStatus, currency: String, perDayOnly: Boolean) {
    val money = LocalMoneyColors.current
    val barColor = when {
        b.fraction >= 1f -> money.expense
        b.fraction >= 0.8f -> money.warning
        else -> money.income
    }
    Card(
        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceContainer),
    ) {
        Column(Modifier.padding(16.dp)) {
            val which = if (b.period.type == PeriodType.WEEK) "week" else "month"
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(
                    if (b.over) "Over budget by ${Money.format(-b.left, currency)}" else "${Money.format(b.left, currency)} left",
                    style = MaterialTheme.typography.titleMedium,
                    color = if (b.over) money.expense else MaterialTheme.colorScheme.onSurface,
                )
                Text(
                    "of ${Money.format(b.budget, currency)}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { b.fraction.coerceIn(0f, 1f) },
                modifier = Modifier.fillMaxWidth().height(8.dp),
                color = barColor,
                trackColor = MaterialTheme.colorScheme.surfaceContainerHighest,
                strokeCap = StrokeCap.Round,
                drawStopIndicator = {},
            )
            Spacer(Modifier.height(8.dp))
            val line = when {
                b.over -> "This $which's budget is used up."
                b.daysLeft > 0 -> "You can spend ${Money.format(b.perDay, currency)} a day for the next ${b.daysLeft} ${if (b.daysLeft == 1) "day" else "days"}."
                else -> "Spent ${Money.format(b.spent, currency)} of this $which's budget."
            }
            Text(
                if (perDayOnly && !b.over && b.daysLeft > 0) "${Money.format(b.perDay, currency)} a day keeps you within this $which's budget." else line,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
