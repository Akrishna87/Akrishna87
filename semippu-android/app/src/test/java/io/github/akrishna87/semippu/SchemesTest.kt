package io.github.akrishna87.semippu

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.pow

class SchemesTest {

    // Figures published by banks, the Post Office and fund houses' calculators.

    @Test
    fun ppfMatchesThePublishedFigure() {
        // ₹1.5 lakh a year for 15 years at 7.1% is the widely quoted ₹40,68,209.
        val r = ppf(150000.0, 15, 7.1)
        assertEquals("₹40,68,209", rupees(r.maturity))
        assertEquals(2250000.0, r.invested, 0.0)
        assertEquals(15, r.rows.size)
    }

    @Test
    fun rdMatchesThePostOfficeFigure() {
        // ₹1,000 a month for 5 years at 6.7%, compounded quarterly: ₹71,366.
        assertEquals("₹71,366", rupees(rd(1000.0, 60, 6.7).maturity))
    }

    @Test
    fun sipMatchesTheUsualFigure() {
        // ₹10,000 a month for 10 years at 12%: ₹23,23,391.
        assertEquals("₹23,23,391", rupees(sip(10000.0, 10, 12.0).maturity))
    }

    @Test
    fun nscCompoundsYearlyForFiveYears() {
        assertEquals(1000 * 1.077.pow(5), nsc(1000.0, 7.7).maturity, 1e-6)
        assertEquals("₹1,449", rupees(nsc(1000.0, 7.7).maturity))
    }

    @Test
    fun kvpDoublesIn115Months() {
        assertEquals(115, kvpMonths(7.5))
        val r = kvp(50000.0, 7.5)
        assertEquals(100000.0, r.maturity, 0.0)
        assertEquals(115, r.months)
        assertEquals("Month 115", r.rows.last().label)
        assertEquals(10, r.rows.size)
    }

    @Test
    fun ssyStopsDepositsAfter15YearsAndMaturesAt21() {
        val r = ssy(150000.0, 8.2)
        assertEquals(21, r.rows.size)
        assertEquals(150000.0, r.rows[14].deposited, 0.0)
        assertEquals(0.0, r.rows[15].deposited, 0.0)
        assertEquals(2250000.0, r.invested, 0.0)
        assertEquals(r.rows[14].balance * 1.082.pow(6), r.maturity, 1e-3)
    }

    @Test
    fun scssPaysEveryQuarter() {
        val r = payout(3000000.0, 8.2, perYear = 4, every = "every quarter")
        assertEquals(61500.0, r.payout!!, 1e-9)
        assertEquals(1230000.0, r.interest, 1e-6)
        assertEquals(3000000.0, r.maturity, 0.0)
    }

    @Test
    fun misPaysEveryMonth() {
        assertEquals(5550.0, payout(900000.0, 7.4, perYear = 12, every = "every month").payout!!, 1e-9)
    }

    @Test
    fun fdCompoundsQuarterlyWithSimpleInterestForExtraMonths() {
        assertEquals(100000 * 1.0175.pow(4), fdValue(100000.0, 12, 7.0), 1e-6)
        assertEquals(100000 * 1.0175.pow(4) * (1 + 0.07 / 12), fdValue(100000.0, 13, 7.0), 1e-6)
        val r = fd(100000.0, 13, 7.0)
        assertEquals(listOf("Year 1", "Month 13"), r.rows.map { it.label })
    }

    @Test
    fun everyTableAddsUpToTheResult() {
        for (s in schemes) {
            val r = s.calc(s.fields.associate { it.key to it.default })
            assertEquals(s.id, r.invested, r.rows.sumOf { it.deposited }, 1e-6)
            assertEquals(s.id, r.interest, r.rows.sumOf { it.interest }, 1e-6)
            assertEquals(s.id, r.maturity, r.rows.last().balance, 1e-6)
            for (f in s.fields) assertNull("${s.id} ${f.key}", f.error(f.default))
        }
    }

    @Test
    fun fieldsCheckLimitsAndSteps() {
        val years = schemes.first { it.id == "ppf" }.fields.first { it.key == "years" }
        assertNull(years.error(20.0))
        assertNotNull(years.error(17.0))
        assertNotNull(years.error(10.0))
        assertNotNull(years.error(null))
        val scss = schemes.first { it.id == "scss" }.fields.first()
        assertNull(scss.error(15000.0))
        assertEquals("In multiples of 1000", scss.error(1500.0))
    }

    @Test
    fun rupeesUseIndianGrouping() {
        assertEquals("₹0", rupees(0.0))
        assertEquals("₹999", rupees(999.4))
        assertEquals("₹1,000", rupees(999.5))
        assertEquals("₹1,00,000", rupees(100000.0))
        assertEquals("₹12,34,56,789", rupees(123456789.0))
        assertEquals("40.68 lakh", inWords(4068209.0))
        assertEquals("1.23 crore", inWords(12345678.0))
        assertNull(inWords(99999.0))
        assertEquals("9 years 7 months", term(115))
        assertEquals("15 years", term(180))
        assertEquals("7.1", plain(7.1))
        assertEquals("150000", plain(150000.0))
    }
}
