package io.github.akrishna87.selavu.core

import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale

data class Totals(val income: Long, val expense: Long) {
    val balance: Long get() = income - expense
}

fun List<Transaction>.inPeriod(period: Period) = filter { it.date in period }

fun List<Transaction>.totals() = Totals(
    income = filter { it.type == TxType.INCOME }.sumOf { it.amount },
    expense = filter { it.type == TxType.EXPENSE }.sumOf { it.amount },
)

data class DayGroup(val date: LocalDate, val transactions: List<Transaction>) {
    /** Income minus spending on this day. */
    val net: Long get() = transactions.sumOf { if (it.type == TxType.INCOME) it.amount else -it.amount }
}

/** Newest day first; within a day, the most recently added first. */
fun List<Transaction>.byDay(): List<DayGroup> =
    withIndex()
        .groupBy { it.value.date }
        .toSortedMap(reverseOrder())
        .map { (date, items) -> DayGroup(date, items.sortedByDescending { it.index }.map { it.value }) }

data class CategoryShare(val category: Category, val total: Long, val count: Int, val share: Float)

fun Ledger.byCategory(transactions: List<Transaction>, type: TxType): List<CategoryShare> {
    val ofType = transactions.filter { it.type == type }
    val sum = ofType.sumOf { it.amount }
    if (sum == 0L) return emptyList()
    return ofType.groupBy { it.categoryId }
        .map { (id, items) ->
            val cat = category(id) ?: Category(id, "Deleted category", "❔", type, 0xFF768390)
            val total = items.sumOf { it.amount }
            CategoryShare(cat, total, items.size, total.toFloat() / sum)
        }
        .sortedByDescending { it.total }
}

data class Bucket(val label: String, val start: LocalDate, val end: LocalDate, val total: Long)

/** The period split into bars for the trend chart: days of a week or month, months of a year. */
fun trend(period: Period, transactions: List<Transaction>, type: TxType): List<Bucket> {
    val ofType = transactions.filter { it.type == type && it.date in period }
    return when (period.type) {
        PeriodType.DAY -> emptyList()
        PeriodType.WEEK, PeriodType.MONTH -> (0 until period.days).map { i ->
            val d = period.start.plusDays(i.toLong())
            val label = if (period.type == PeriodType.WEEK) d.format(DateTimeFormatter.ofPattern("EEE", Locale.ENGLISH)).take(2)
            else d.dayOfMonth.toString()
            Bucket(label, d, d, ofType.filter { it.date == d }.sumOf { it.amount })
        }
        PeriodType.YEAR -> (0 until 12).map { m ->
            val s = period.start.plusMonths(m.toLong())
            val e = s.plusMonths(1).minusDays(1)
            Bucket(
                s.format(DateTimeFormatter.ofPattern("MMM", Locale.ENGLISH)).take(1),
                s, e,
                ofType.filter { it.date in Period(PeriodType.MONTH, s, e) }.sumOf { it.amount },
            )
        }
    }
}

/** Spending per day so far: over the days of [period] up to [today], or all of it if it's over. */
fun averagePerDay(period: Period, total: Long, today: LocalDate): Long {
    val last = if (today in period) today else period.end
    if (last.isBefore(period.start)) return 0
    val days = ChronoUnit.DAYS.between(period.start, last) + 1
    return total / days
}

data class BudgetStatus(
    val period: Period,
    val budget: Long,
    val spent: Long,
    /** Days left in the period, counting today; 0 when the period isn't the current one. */
    val daysLeft: Int,
) {
    val left: Long get() = budget - spent
    val fraction: Float get() = if (budget <= 0) 0f else spent.toFloat() / budget
    val perDay: Long get() = if (daysLeft > 0 && left > 0) left / daysLeft else 0
    val over: Boolean get() = spent > budget
}

/**
 * The budget for the week or month containing [date], or null when no budget is set.
 */
fun Ledger.budgetFor(date: LocalDate, today: LocalDate): BudgetStatus? {
    if (settings.budget <= 0) return null
    val type = if (settings.budgetPeriod == BudgetPeriod.WEEKLY) PeriodType.WEEK else PeriodType.MONTH
    val period = Period.of(type, date, settings.weekStart())
    val spent = transactions.inPeriod(period).filter { it.type == TxType.EXPENSE }.sumOf { it.amount }
    val daysLeft = if (today in period) (ChronoUnit.DAYS.between(today, period.end) + 1).toInt() else 0
    return BudgetStatus(period, settings.budget, spent, daysLeft)
}

/** The category used most recently for [type], to pre-select it for the next entry. */
fun Ledger.lastCategory(type: TxType): String? =
    transactions.lastOrNull { it.type == type && category(it.categoryId) != null }?.categoryId
        ?: categories(type).firstOrNull()?.id
