package io.github.akrishna87.selavu.core

import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MoneyTest {
    @Test fun parsesTypedAmounts() {
        assertEquals(25000L, Money.parse("250"))
        assertEquals(25050L, Money.parse("250.5"))
        assertEquals(9999L, Money.parse("₹ 99.99"))
        assertEquals(120000L, Money.parse("1,200"))
        assertEquals(50L, Money.parse(".5"))
        assertNull(Money.parse(""))
        assertNull(Money.parse("0"))
        assertNull(Money.parse("1.234"))
        assertNull(Money.parse("1.2.3"))
    }

    @Test fun formatsWithIndianGroupingForRupees() {
        assertEquals("₹1,25,000", Money.format(1_25_000_00, "₹"))
        assertEquals("₹999", Money.format(999_00, "₹"))
        assertEquals("₹12,34,56,789.05", Money.format(12_34_56_789_05, "₹"))
        assertEquals("$125,000.50", Money.format(125_000_50, "$"))
        assertEquals("−₹40", Money.format(-40_00, "₹"))
        assertEquals("+₹40", Money.format(40_00, "₹", sign = true))
    }

    @Test fun plainAmountsRoundTrip() {
        for (a in listOf(1L, 10L, 50L, 99L, 100L, 25050L, 123456789L)) assertEquals(a, Money.parse(Money.plain(a)))
        assertEquals("250.5", Money.plain(25050))
    }

    @Test fun compactAmounts() {
        assertEquals("₹1.2L", Money.compact(1_20_000_00, "₹"))
        assertEquals("₹45k", Money.compact(45_000_00, "₹"))
        assertEquals("₹800", Money.compact(800_00, "₹"))
        assertEquals("$3.4M", Money.compact(3_400_000_00, "$"))
    }
}

class PeriodTest {
    private val today = LocalDate.of(2026, 10, 17) // a Saturday

    @Test fun weeksFollowTheChosenFirstDay() {
        val mon = Period.of(PeriodType.WEEK, today, DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 10, 12), mon.start)
        assertEquals(LocalDate.of(2026, 10, 18), mon.end)
        val sun = Period.of(PeriodType.WEEK, today, DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 10, 11), sun.start)
        assertEquals(LocalDate.of(2026, 10, 4), sun.shift(-1).start)
    }

    @Test fun monthsAndYears() {
        val feb = Period.of(PeriodType.MONTH, LocalDate.of(2028, 2, 10), DayOfWeek.MONDAY)
        assertEquals(29, feb.days)
        assertEquals(LocalDate.of(2028, 3, 31), feb.shift(1).end)
        assertEquals("2026", Period.of(PeriodType.YEAR, today, DayOfWeek.MONDAY).label(today))
    }

    @Test fun labels() {
        val day = Period.of(PeriodType.DAY, today, DayOfWeek.MONDAY)
        assertEquals("Today", day.label(today))
        assertEquals("Yesterday", day.shift(-1).label(today))
        assertEquals("Mon, 5 Oct", day.shift(-12).label(today))
        val week = Period.of(PeriodType.WEEK, today, DayOfWeek.MONDAY)
        assertEquals("This week", week.label(today))
        assertEquals("12 – 18 Oct", week.subLabel(today))
        assertEquals("Last week", week.shift(-1).label(today))
        assertEquals("28 Sep – 4 Oct", week.shift(-2).label(today))
        assertEquals("October", Period.of(PeriodType.MONTH, today, DayOfWeek.MONDAY).label(today))
        assertEquals("December 2025", Period.of(PeriodType.MONTH, today, DayOfWeek.MONDAY).shift(-10).label(today))
    }
}

class LedgerTest {
    private val today = LocalDate.of(2026, 10, 17)
    private var n = 0
    private val id = { "id${n++}" }

    private fun tx(amount: Long, date: LocalDate, cat: String = "food", type: TxType = TxType.EXPENSE) =
        Transaction(id(), type, amount, cat, date)

    @Test fun totalsAndGrouping() {
        val l = Ledger(transactions = listOf(
            tx(100_00, today), tx(50_00, today.minusDays(1)), tx(1000_00, today, "salary", TxType.INCOME),
            tx(30_00, today.minusDays(40)),
        ))
        val month = Period.of(PeriodType.MONTH, today, DayOfWeek.MONDAY)
        val t = l.transactions.inPeriod(month).totals()
        assertEquals(150_00L, t.expense)
        assertEquals(850_00L, t.balance)
        val days = l.transactions.inPeriod(month).byDay()
        assertEquals(listOf(today, today.minusDays(1)), days.map { it.date })
        assertEquals(900_00L, days.first().net)
        // The entry added last comes first within its day.
        assertEquals(TxType.INCOME, days.first().transactions.first().type)
    }

    @Test fun categoryShares() {
        val l = Ledger(transactions = listOf(tx(300_00, today), tx(100_00, today, "groceries"), tx(100_00, today)))
        val shares = l.byCategory(l.transactions, TxType.EXPENSE)
        assertEquals("food", shares[0].category.id)
        assertEquals(0.8f, shares[0].share, 0.001f)
        assertEquals(2, shares[0].count)
    }

    @Test fun budgetLeftAndPerDay() {
        val l = Ledger(
            transactions = listOf(tx(4000_00, today), tx(1000_00, today.minusDays(3)), tx(999_00, today.minusMonths(1))),
            settings = Settings(budget = 20000_00, budgetPeriod = BudgetPeriod.MONTHLY),
        )
        val b = l.budgetFor(today, today)!!
        assertEquals(5000_00L, b.spent)
        assertEquals(15000_00L, b.left)
        assertEquals(15, b.daysLeft) // 17th..31st
        assertEquals(1000_00L, b.perDay)
        assertEquals(0, l.budgetFor(today.minusMonths(1), today)!!.daysLeft)
        assertNull(l.copy(settings = Settings()).budgetFor(today, today))
    }

    @Test fun trendBuckets() {
        val month = Period.of(PeriodType.MONTH, today, DayOfWeek.MONDAY)
        val b = trend(month, listOf(tx(10_00, today), tx(5_00, today)), TxType.EXPENSE)
        assertEquals(31, b.size)
        assertEquals(15_00L, b[16].total)
        val year = trend(Period.of(PeriodType.YEAR, today, DayOfWeek.MONDAY), listOf(tx(10_00, today)), TxType.EXPENSE)
        assertEquals(12, year.size)
        assertEquals(10_00L, year[9].total)
        assertEquals(1_00L, averagePerDay(month, 17_00, today))
    }

    @Test fun recurringMonthlyLogsEachMonthWithoutDrift() {
        val start = LocalDate.of(2026, 5, 31)
        val l = Ledger().save(Transaction("t", TxType.EXPENSE, 500_00, "rent", start, "Rent"), Repeat.MONTHLY, today, id)
        val dates = l.transactions.map { it.date }
        assertEquals(
            listOf("2026-05-31", "2026-06-30", "2026-07-31", "2026-08-31", "2026-09-30").map(LocalDate::parse),
            dates,
        )
        assertTrue(l.transactions.all { it.recurringId == l.recurring.single().id })
        assertEquals(LocalDate.of(2026, 10, 31), l.recurring.single().nextDate)
        // Nothing new until the next one is due, then exactly one.
        assertEquals(5, l.logDueRecurring(today).transactions.size)
        assertEquals(6, l.logDueRecurring(LocalDate.of(2026, 10, 31)).transactions.size)
        assertEquals(5, l.stopRecurring(l.recurring.single().id).logDueRecurring(LocalDate.of(2027, 1, 1)).transactions.size)
    }

    @Test fun futureRecurringWaitsForItsDate() {
        val l = Ledger().save(Transaction("t", TxType.INCOME, 1_00, "salary", today.plusDays(3)), Repeat.WEEKLY, today, id)
        assertEquals(0, l.transactions.size)
        assertEquals(1, l.logDueRecurring(today.plusDays(3)).transactions.size)
    }

    @Test fun catchUpIsCapped() {
        val l = Ledger().save(Transaction("t", TxType.EXPENSE, 1_00, "food", today.minusYears(5)), Repeat.DAILY, today, id)
        assertEquals(MAX_CATCH_UP + 1, l.transactions.size)
    }

    @Test fun editingKeepsTheSameEntry() {
        val l = Ledger().save(Transaction("t", TxType.EXPENSE, 1_00, "food", today), Repeat.NONE, today, id)
        val edited = l.save(l.transactions.single().copy(amount = 2_00), Repeat.NONE, today, id)
        assertEquals(2_00L, edited.transactions.single().amount)
        assertEquals(0, edited.delete("t").transactions.size)
    }

    @Test fun deletingACategoryMovesItsEntries() {
        val l = Ledger(transactions = listOf(tx(1_00, today, "fuel")))
        val after = l.deleteCategory("fuel", "transport")
        assertNull(after.category("fuel"))
        assertEquals("transport", after.transactions.single().categoryId)
        val onlyOne = Ledger(categories = listOf(DefaultCategories.all.first()))
        assertEquals(onlyOne, onlyOne.deleteCategory("food", "x"))
    }

    @Test fun lastCategoryIsPreselected() {
        val l = Ledger(transactions = listOf(tx(1, today, "groceries"), tx(1, today, "salary", TxType.INCOME)))
        assertEquals("groceries", l.lastCategory(TxType.EXPENSE))
        assertEquals("salary", l.lastCategory(TxType.INCOME))
        assertEquals("food", Ledger().lastCategory(TxType.EXPENSE))
    }
}

class CsvTest {
    private val today = LocalDate.of(2026, 10, 17)

    @Test fun exportAndImportRoundTrip() {
        val l = Ledger(transactions = listOf(
            Transaction("a", TxType.EXPENSE, 250_50, "food", today, "Lunch, with \"team\""),
            Transaction("b", TxType.INCOME, 50000_00, "salary", today.minusDays(2), "Line one\nline two"),
        ))
        val csv = Csv.export(l)
        assertTrue(csv.startsWith(Csv.HEADER))
        val back = Csv.import(Ledger(), csv)
        assertEquals(2, back.added)
        assertEquals(
            l.transactions.map { listOf(it.type, it.amount, it.categoryId, it.date, it.note) }.toSet(),
            back.ledger.transactions.map { listOf(it.type, it.amount, it.categoryId, it.date, it.note) }.toSet(),
        )
        // The same file again adds nothing.
        val again = Csv.import(back.ledger, csv)
        assertEquals(0, again.added)
        assertEquals(2, again.skipped)
    }

    @Test fun unknownCategoriesAreCreatedAndBadRowsCounted() {
        val csv = "Date,Type,Category,Amount,Note\n2026-10-01,Expense,Pets,1200,Vet\nnot a date,Expense,Food,10,\n2026-10-02,Expense,Food,abc,\n"
        val r = Csv.import(Ledger(), csv, { "new" })
        assertEquals(1, r.added)
        assertEquals(2, r.failed)
        assertEquals("Pets", r.ledger.category(r.ledger.transactions.single().categoryId)!!.name)
    }
}
