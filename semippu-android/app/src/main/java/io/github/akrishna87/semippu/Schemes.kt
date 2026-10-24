package io.github.akrishna87.semippu

import java.util.Locale
import kotlin.math.abs
import kotlin.math.ln
import kotlin.math.pow
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/** Small savings rates notified by the Ministry of Finance. Update these every quarter. */
object Rates {
    const val QUARTER = "1 Oct – 31 Dec 2026"
    const val PPF = 7.1
    const val SSY = 8.2
    const val NSC = 7.7
    const val KVP = 7.5
    const val SCSS = 8.2
    const val MIS = 7.4
    const val RD = 6.7
}

/** A number the user types. A value must be in [min, max] and, when [step] is set, min + a whole number of steps. */
data class Field(
    val key: String,
    val label: String,
    val default: Double,
    val min: Double,
    val max: Double,
    val step: Double? = null,
    val hint: String = "",
) {
    fun error(value: Double?): String? = when {
        value == null -> "Enter a number"
        value < min || value > max -> "From ${plain(min)} to ${plain(max)}"
        step != null && abs((value - min) / step - ((value - min) / step).roundToInt()) > 1e-9 ->
            if (min == step) "In multiples of ${plain(step)}" else "${plain(min)}, then steps of ${plain(step)}"
        else -> null
    }
}

/** One line of the year-by-year table. */
data class Row(val label: String, val deposited: Double, val interest: Double, val balance: Double)

data class Result(
    val invested: Double,
    /** All the interest earned, including any paid out along the way. */
    val interest: Double,
    /** What you get back at the end. */
    val maturity: Double,
    val months: Int,
    /** Interest paid out regularly (SCSS, MIS), with how often. */
    val payout: Double? = null,
    val payoutEvery: String? = null,
    val rows: List<Row>,
)

class Scheme(
    val id: String,
    val name: String,
    val fullName: String,
    val group: String,
    val fields: List<Field>,
    val notes: String,
    val calc: (Map<String, Double>) -> Result,
)

private fun rate(default: Double, hint: String = "") =
    Field("rate", "Interest rate (% a year)", default, 0.1, 30.0, hint = hint)

val schemes = listOf(
    Scheme(
        "ppf", "PPF", "Public Provident Fund", "Government schemes",
        listOf(
            Field("deposit", "Deposit each year (₹)", 150000.0, 500.0, 150000.0),
            Field("years", "Years", 15.0, 15.0, 50.0, step = 5.0, hint = "15, then blocks of 5"),
            rate(Rates.PPF),
        ),
        "15 years, which you can extend in blocks of 5. ₹500 to ₹1.5 lakh a year. Assumes you deposit by 5 April " +
            "each year, so the whole year earns interest. The interest is tax-free.",
    ) { ppf(it.getValue("deposit"), it.getValue("years").toInt(), it.getValue("rate")) },
    Scheme(
        "ssy", "Sukanya Samriddhi", "Sukanya Samriddhi Yojana", "Government schemes",
        listOf(
            Field("deposit", "Deposit each year (₹)", 150000.0, 250.0, 150000.0),
            rate(Rates.SSY),
        ),
        "For a girl under 10. You deposit for 15 years; the account matures 21 years after it was opened and keeps " +
            "earning interest in between. ₹250 to ₹1.5 lakh a year. Assumes you deposit by 5 April each year. " +
            "The interest is tax-free.",
    ) { ssy(it.getValue("deposit"), it.getValue("rate")) },
    Scheme(
        "nsc", "NSC", "National Savings Certificate", "Government schemes",
        listOf(
            Field("amount", "Amount (₹)", 100000.0, 1000.0, 1e9, step = 100.0, hint = "From ₹1,000, in ₹100s"),
            rate(Rates.NSC),
        ),
        "5 years. Interest is added every year and paid at maturity. From ₹1,000, in multiples of ₹100. " +
            "The interest is taxable, but each year's interest counts towards 80C.",
    ) { nsc(it.getValue("amount"), it.getValue("rate")) },
    Scheme(
        "kvp", "KVP", "Kisan Vikas Patra", "Government schemes",
        listOf(
            Field("amount", "Amount (₹)", 100000.0, 1000.0, 1e9, step = 100.0, hint = "From ₹1,000, in ₹100s"),
            rate(Rates.KVP),
        ),
        "Doubles your money; at the current rate that takes 115 months. From ₹1,000, in multiples of ₹100. " +
            "The interest is taxable.",
    ) { kvp(it.getValue("amount"), it.getValue("rate")) },
    Scheme(
        "scss", "SCSS", "Senior Citizens Savings Scheme", "Government schemes",
        listOf(
            Field("amount", "Amount (₹)", 1500000.0, 1000.0, 3000000.0, step = 1000.0, hint = "Up to ₹30 lakh, in ₹1,000s"),
            rate(Rates.SCSS),
        ),
        "For people aged 60 and over (55 for some retirees). ₹1,000 to ₹30 lakh, in multiples of ₹1,000. " +
            "Interest is paid every quarter, and the deposit comes back after 5 years. The interest is taxable.",
    ) { payout(it.getValue("amount"), it.getValue("rate"), perYear = 4, every = "every quarter") },
    Scheme(
        "mis", "Monthly Income Scheme", "Post Office Monthly Income Scheme", "Government schemes",
        listOf(
            Field(
                "amount", "Amount (₹)", 900000.0, 1000.0, 1500000.0, step = 1000.0,
                hint = "Up to ₹9 lakh single, ₹15 lakh joint",
            ),
            rate(Rates.MIS),
        ),
        "Up to ₹9 lakh in a single account or ₹15 lakh in a joint one, in multiples of ₹1,000. Interest is paid " +
            "every month, and the deposit comes back after 5 years. The interest is taxable.",
    ) { payout(it.getValue("amount"), it.getValue("rate"), perYear = 12, every = "every month") },
    Scheme(
        "fd", "Fixed Deposit", "Bank fixed deposit", "Deposits",
        listOf(
            Field("amount", "Amount (₹)", 100000.0, 1.0, 1e9),
            Field("months", "Months", 60.0, 1.0, 360.0, step = 1.0),
            rate(7.0, hint = "Your bank's rate"),
        ),
        "Interest is added every quarter, as most banks do, and paid at maturity. Enter your bank's rate: " +
            "7% is only an example.",
    ) { fd(it.getValue("amount"), it.getValue("months").toInt(), it.getValue("rate")) },
    Scheme(
        "rd", "Recurring Deposit", "Post Office or bank RD", "Deposits",
        listOf(
            Field("monthly", "Deposit each month (₹)", 5000.0, 1.0, 1e8),
            Field("months", "Months", 60.0, 1.0, 360.0, step = 1.0),
            rate(Rates.RD, hint = "Post Office 5-year RD rate"),
        ),
        "Starts at the Post Office 5-year RD rate. Interest is added every quarter, as the Post Office and most banks " +
            "do. Enter your bank's rate if it's different.",
    ) { rd(it.getValue("monthly"), it.getValue("months").toInt(), it.getValue("rate")) },
    Scheme(
        "sip", "SIP", "Mutual fund SIP", "Mutual funds",
        listOf(
            Field("monthly", "Invest each month (₹)", 10000.0, 1.0, 1e8),
            Field("years", "Years", 10.0, 1.0, 50.0, step = 1.0),
            Field("rate", "Expected return (% a year)", 12.0, 0.1, 30.0, hint = "Not guaranteed"),
        ),
        "Returns aren't guaranteed: the expected return is an assumption, not a promise. Assumes you invest at the " +
            "start of each month.",
    ) { sip(it.getValue("monthly"), it.getValue("years").toInt(), it.getValue("rate")) },
)

// ---------- the calculations ----------

/** Rows at the end of each year, and at the last month if that isn't a whole year. */
private fun table(months: Int, deposited: (Int) -> Double, balance: (Int) -> Double): List<Row> {
    val ends = (12..months step 12).toMutableList()
    if (months % 12 != 0) ends += months
    var lastDeposited = 0.0
    var lastBalance = 0.0
    return ends.map { m ->
        val d = deposited(m) - lastDeposited
        val b = balance(m)
        Row(if (m % 12 == 0) "Year ${m / 12}" else "Month $m", d, b - lastBalance - d, b)
            .also { lastDeposited += d; lastBalance = b }
    }
}

private fun result(invested: Double, maturity: Double, months: Int, rows: List<Row>) =
    Result(invested, maturity - invested, maturity, months, rows = rows)

/** A deposit at the start of each of the first [depositYears] years, compounded yearly. */
private fun yearlyBalance(deposit: Double, r: Double, years: Int, depositYears: Int) =
    (1..years).fold(0.0) { b, y -> (b + if (y <= depositYears) deposit else 0.0) * (1 + r) }

fun ppf(deposit: Double, years: Int, ratePct: Double): Result {
    val r = ratePct / 100
    val rows = table(years * 12, { m -> m / 12 * deposit }) { m -> yearlyBalance(deposit, r, m / 12, years) }
    return result(deposit * years, rows.last().balance, years * 12, rows)
}

fun ssy(deposit: Double, ratePct: Double): Result {
    val r = ratePct / 100
    val rows = table(21 * 12, { m -> minOf(m / 12, 15) * deposit }) { m -> yearlyBalance(deposit, r, m / 12, 15) }
    return result(deposit * 15, rows.last().balance, 21 * 12, rows)
}

fun nsc(amount: Double, ratePct: Double): Result {
    val rows = table(60, { amount }) { m -> amount * (1 + ratePct / 100).pow(m / 12) }
    return result(amount, rows.last().balance, 60, rows)
}

/** Months for money to double at [ratePct], compounded yearly: 115 at 7.5%. */
fun kvpMonths(ratePct: Double) = (ln(2.0) / ln(1 + ratePct / 100) * 12).roundToInt()

fun kvp(amount: Double, ratePct: Double): Result {
    val months = kvpMonths(ratePct)
    val rows = table(months, { amount }) { m -> if (m == months) 2 * amount else amount * (1 + ratePct / 100).pow(m / 12.0) }
    return result(amount, 2 * amount, months, rows)
}

/** SCSS and MIS: interest paid out [perYear] times a year for 5 years, then the deposit is returned. */
fun payout(amount: Double, ratePct: Double, perYear: Int, every: String): Result {
    val each = amount * ratePct / 100 / perYear
    val rows = (1..5).map { y -> Row("Year $y", if (y == 1) amount else 0.0, each * perYear, amount) }
    return Result(amount, each * perYear * 5, amount, 60, each, every, rows)
}

/** Compounded every quarter; months beyond the last whole quarter earn simple interest. */
fun fdValue(amount: Double, months: Int, ratePct: Double): Double {
    val r = ratePct / 100
    return amount * (1 + r / 4).pow(months / 3) * (1 + r * (months % 3) / 12)
}

fun fd(amount: Double, months: Int, ratePct: Double): Result {
    val rows = table(months, { amount }) { m -> fdValue(amount, m, ratePct) }
    return result(amount, rows.last().balance, months, rows)
}

/** Value after [months] of deposits made at the start of each month, compounded every quarter. */
fun rdValue(monthly: Double, months: Int, ratePct: Double): Double {
    val q = 1 + ratePct / 100 / 4
    return (1..months).sumOf { k -> monthly * q.pow((months - k + 1) / 3.0) }
}

fun rd(monthly: Double, months: Int, ratePct: Double): Result {
    val rows = table(months, { m -> m * monthly }) { m -> rdValue(monthly, m, ratePct) }
    return result(monthly * months, rows.last().balance, months, rows)
}

fun sip(monthly: Double, years: Int, ratePct: Double): Result {
    val i = ratePct / 100 / 12
    val value = { m: Int -> monthly * ((1 + i).pow(m) - 1) / i * (1 + i) }
    val rows = table(years * 12, { m -> m * monthly }, value)
    return result(monthly * years * 12, rows.last().balance, years * 12, rows)
}

// ---------- showing numbers ----------

/** Rupees with Indian grouping: ₹40,68,209. */
fun rupees(x: Double): String {
    val n = x.roundToLong()
    val s = abs(n).toString()
    val grouped = if (s.length <= 3) s else s.dropLast(3).reversed().chunked(2).joinToString(",").reversed() + "," + s.takeLast(3)
    return (if (n < 0) "-₹" else "₹") + grouped
}

/** "40.68 lakh" or "1.23 crore", for amounts of a lakh or more. */
fun inWords(x: Double): String? = when {
    x >= 1e7 -> String.format(Locale.ROOT, "%.2f crore", x / 1e7)
    x >= 1e5 -> String.format(Locale.ROOT, "%.2f lakh", x / 1e5)
    else -> null
}

fun term(months: Int): String {
    val y = months / 12
    val m = months % 12
    val years = if (y == 1) "1 year" else "$y years"
    val mons = if (m == 1) "1 month" else "$m months"
    return when {
        y == 0 -> mons
        m == 0 -> years
        else -> "$years $mons"
    }
}

/** 150000.0 → "150000", 7.1 → "7.1": how a number is shown in a text field. */
fun plain(x: Double): String = if (x == x.roundToLong().toDouble()) x.roundToLong().toString() else x.toString()
