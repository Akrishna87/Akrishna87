package io.github.akrishna87.semippu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.ceil
import kotlin.math.ln
import kotlin.math.pow

class LoansTest {

    @Test
    fun emiMatchesThePublishedFigure() {
        // ₹10 lakh at 10% for 20 years: EMI ₹9,650, interest ₹13,16,052 (banks' EMI calculators).
        val r = loan(1000000.0, 10.0, 240, 0, 0.0, null, false)
        assertEquals("₹9,650", rupees(r.emi))
        assertEquals("₹13,16,052", rupees(r.totalInterest))
        assertEquals(240, r.asIs.months)
        assertEquals(r.totalInterest, r.asIs.interest, 1e-4)
        assertEquals(0.0, r.asIs.rows.last().balance, 1e-6)
        assertEquals(20, r.asIs.rows.size)
    }

    @Test
    fun balanceAfterMatchesTheSchedule() {
        val e = emi(1000000.0, 10.0, 240)
        val rows = payoff(1000000.0, 10.0, 240, null, false).rows
        assertEquals(balanceAfter(1000000.0, 10.0, e, 12), rows[0].balance, 1e-4)
        assertEquals(balanceAfter(1000000.0, 10.0, e, 60), rows[4].balance, 1e-4)
    }

    @Test
    fun aPartPaymentShortensTheLoanByTheRightNumberOfMonths() {
        val amount = 1000000.0
        val e = emi(amount, 10.0, 240)
        val p = payoff(amount, 10.0, 240, PartPayment(200000.0, 12, Every.ONCE), lowerEmi = false)
        // After month 12 the balance drops by ₹2 lakh; the same EMI then needs this many more months.
        val left = balanceAfter(amount, 10.0, e, 12) - 200000
        val r = 10.0 / 1200
        val more = ceil(-ln(1 - left * r / e) / ln(1 + r)).toInt()
        assertEquals(12 + more, p.months)
        assertEquals(200000.0, p.partPaid, 1e-9)
        assertEquals(e, p.lastEmi, 1e-9)
        val asIs = payoff(amount, 10.0, 240, null, false)
        assertTrue(p.interest < asIs.interest)
        // Everything borrowed is paid back, by EMIs or the part payment.
        assertEquals(amount, p.rows.sumOf { it.principal + it.partPaid }, 1e-4)
    }

    @Test
    fun aPartPaymentCanLowerTheEmiInstead() {
        val amount = 1000000.0
        val e = emi(amount, 10.0, 240)
        val p = payoff(amount, 10.0, 240, PartPayment(200000.0, 12, Every.ONCE), lowerEmi = true)
        assertEquals(240, p.months)
        assertEquals(emi(balanceAfter(amount, 10.0, e, 12) - 200000, 10.0, 228), p.lastEmi, 1e-6)
        assertEquals(0.0, p.rows.last().balance, 1e-6)
        assertEquals(amount, p.rows.sumOf { it.principal + it.partPaid }, 1e-4)
    }

    @Test
    fun repeatedPartPaymentsAreMadeWhenDue() {
        val yearly = PartPayment(50000.0, 6, Every.YEARLY)
        assertEquals(listOf(6, 18, 30), (1..36).filter { yearly.dueIn(it) })
        val monthly = PartPayment(1000.0, 3, Every.MONTHLY)
        assertEquals(listOf(3, 4, 5), (1..5).filter { monthly.dueIn(it) })
        val p = payoff(500000.0, 11.0, 60, monthly, lowerEmi = false)
        assertTrue(p.months < 60)
        assertEquals(500000.0, p.rows.sumOf { it.principal + it.partPaid }, 1e-4)
    }

    @Test
    fun paymentBiggerThanTheBalanceClosesTheLoan() {
        val p = payoff(100000.0, 9.0, 60, PartPayment(1e9, 3, Every.ONCE), lowerEmi = false)
        assertEquals(3, p.months)
        assertEquals(100000.0, p.rows.sumOf { it.principal + it.partPaid }, 1e-4)
    }

    @Test
    fun aRunningLoanStartsFromWhatIsStillOwed() {
        val r = loan(1000000.0, 10.0, 240, 60, 0.0, PartPayment(100000.0, 1, Every.ONCE), false)
        assertEquals(balanceAfter(1000000.0, 10.0, r.emi, 60), r.outstanding, 1e-9)
        assertEquals(180, r.asIs.months)
        assertEquals(r.emi, r.asIs.firstEmi, 1e-6)
        assertTrue(r.withPartPayments!!.months < 180)
    }

    @Test
    fun theFeeRaisesTheEffectiveRate() {
        val e = emi(500000.0, 11.0, 60)
        assertEquals(11.0, effectiveRate(500000.0, 0.0, e, 60), 1e-6)
        val withFee = loan(500000.0, 11.0, 60, 0, 10000.0, null, false)
        assertTrue(withFee.effectiveRate!! > 11.8 && withFee.effectiveRate!! < 12.0)
        assertNull(loan(500000.0, 11.0, 60, 0, 0.0, null, false).effectiveRate)
        assertNull(loan(500000.0, 11.0, 60, 0, 0.0, PartPayment(0.0, 1, Every.ONCE), false).withPartPayments)
    }

    @Test
    fun emiFormulaHoldsForTheExamples() {
        for (t in loanTypes) {
            val n = t.years * 12
            val r = t.rate / 1200
            val e = emi(t.amount, t.rate, n)
            assertEquals(t.id, t.amount, e * (1 - (1 + r).pow(-n)) / r, 1e-4)
        }
    }
}
