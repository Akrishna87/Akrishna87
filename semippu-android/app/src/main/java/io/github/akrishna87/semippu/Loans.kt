package io.github.akrishna87.semippu

import kotlin.math.pow

/** Banks set their own loan rates, so these are only starting points the user changes. */
class LoanType(val id: String, val name: String, val amount: Double, val rate: Double, val years: Int, val notes: String)

val loanTypes = listOf(
    LoanType(
        "home", "Home loan", 5000000.0, 8.5, 20,
        "Part payments on a floating-rate home loan carry no charge from banks.",
    ),
    LoanType(
        "car", "Car loan", 800000.0, 9.0, 5,
        "Banks often charge for paying a car loan off early; check your loan agreement.",
    ),
    LoanType(
        "personal", "Personal loan", 500000.0, 11.0, 5,
        "Many lenders allow part payments only after the first 6 or 12 EMIs, and charge a fee for them.",
    ),
)

enum class Every(val label: String) { ONCE("Once"), YEARLY("Every year"), MONTHLY("Every month") }

/** An extra payment of [amount] after the EMI of month [start] (counted from now), repeated as [every] says. */
data class PartPayment(val amount: Double, val start: Int, val every: Every) {
    fun dueIn(month: Int) = when (every) {
        Every.ONCE -> month == start
        Every.YEARLY -> month >= start && (month - start) % 12 == 0
        Every.MONTHLY -> month >= start
    }
}

/** One year of repayments, counted from now. */
data class LoanRow(val label: String, val principal: Double, val interest: Double, val partPaid: Double, val balance: Double)

data class Payoff(
    val months: Int,
    val firstEmi: Double,
    /** The EMI after the last part payment; lower than [firstEmi] when part payments reduce the EMI. */
    val lastEmi: Double,
    val interest: Double,
    val partPaid: Double,
    val rows: List<LoanRow>,
)

data class LoanResult(
    val emi: Double,
    /** Interest over the whole loan, from the first EMI, without part payments. */
    val totalInterest: Double,
    val fee: Double,
    /** The rate once the fee is counted, as banks quote rates (% a year); null when there's no fee. */
    val effectiveRate: Double?,
    /** Still owed today, after the EMIs already paid. */
    val outstanding: Double,
    /** The rest of the loan as it stands, and with the part payments. */
    val asIs: Payoff,
    val withPartPayments: Payoff?,
)

fun emi(amount: Double, ratePct: Double, months: Int): Double {
    val r = ratePct / 1200
    val g = (1 + r).pow(months)
    return amount * r * g / (g - 1)
}

/** What's still owed after [paid] EMIs. */
fun balanceAfter(amount: Double, ratePct: Double, emi: Double, paid: Int): Double {
    val r = ratePct / 1200
    val g = (1 + r).pow(paid)
    return amount * g - emi * (g - 1) / r
}

/**
 * Month by month: interest on the balance, the rest of the EMI off the balance, then any part payment.
 * With [lowerEmi] the EMI is worked out again after each part payment so the loan ends on time;
 * otherwise the EMI stays and the loan ends sooner.
 */
fun payoff(balance: Double, ratePct: Double, months: Int, part: PartPayment?, lowerEmi: Boolean): Payoff {
    val r = ratePct / 1200
    val firstEmi = emi(balance, ratePct, months)
    var emi = firstEmi
    var bal = balance
    var m = 0
    var interest = 0.0
    var partPaid = 0.0
    val rows = ArrayList<LoanRow>()
    var yp = 0.0
    var yi = 0.0
    var yx = 0.0
    while (bal > 0.005 && m < months) {
        m++
        val i = bal * r
        val p = if (m == months) bal else minOf(emi - i, bal)
        bal -= p
        val x = if (part != null && part.dueIn(m)) minOf(part.amount, bal) else 0.0
        bal -= x
        if (lowerEmi && x > 0 && bal > 0.005) emi = emi(bal, ratePct, months - m)
        interest += i
        partPaid += x
        yp += p; yi += i; yx += x
        if (m % 12 == 0 || bal <= 0.005 || m == months) {
            rows += LoanRow("Year ${(m + 11) / 12}", yp, yi, yx, maxOf(bal, 0.0))
            yp = 0.0; yi = 0.0; yx = 0.0
        }
    }
    return Payoff(m, firstEmi, emi, interest, partPaid, rows)
}

/** The yearly rate at which [emi] for [months] repays the [amount] actually received, after the fee. */
fun effectiveRate(amount: Double, fee: Double, emi: Double, months: Int): Double {
    val received = amount - fee
    fun value(i: Double) = emi * (1 - (1 + i).pow(-months)) / i
    var lo = 1e-9
    var hi = 1.0
    repeat(100) {
        val mid = (lo + hi) / 2
        if (value(mid) > received) lo = mid else hi = mid
    }
    return (lo + hi) / 2 * 1200
}

fun loan(
    amount: Double, ratePct: Double, months: Int, paid: Int, fee: Double,
    part: PartPayment?, lowerEmi: Boolean,
): LoanResult {
    val e = emi(amount, ratePct, months)
    val outstanding = if (paid == 0) amount else balanceAfter(amount, ratePct, e, paid)
    return LoanResult(
        emi = e,
        totalInterest = e * months - amount,
        fee = fee,
        effectiveRate = if (fee > 0) effectiveRate(amount, fee, e, months) else null,
        outstanding = outstanding,
        asIs = payoff(outstanding, ratePct, months - paid, null, false),
        withPartPayments = part?.takeIf { it.amount > 0 }?.let { payoff(outstanding, ratePct, months - paid, it, lowerEmi) },
    )
}
