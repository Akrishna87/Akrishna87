package io.github.akrishna87.signal

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SignalTest {
    @Test
    fun lteRatings() {
        assertEquals(Rating.EXCELLENT, Ratings.strength(Tech.LTE, -75))
        assertEquals(Rating.EXCELLENT, Ratings.strength(Tech.LTE, -80))
        assertEquals(Rating.GOOD, Ratings.strength(Tech.LTE, -81))
        assertEquals(Rating.FAIR, Ratings.strength(Tech.LTE, -95))
        assertEquals(Rating.POOR, Ratings.strength(Tech.LTE, -110))
        assertEquals(Rating.VERY_POOR, Ratings.strength(Tech.LTE, -111))
        assertEquals(Rating.GOOD, Ratings.quality(Tech.NR, 15))
        assertEquals(Rating.VERY_POOR, Ratings.quality(Tech.LTE, -2))
    }

    @Test
    fun gaugeFractionIsClamped() {
        assertEquals(0f, Ratings.fraction(Tech.LTE, -140), 0f)
        assertEquals(1f, Ratings.fraction(Tech.LTE, -50), 0f)
        assertEquals(0.5f, Ratings.fraction(Tech.LTE, -95), 0.001f)
    }

    @Test
    fun unknownValuesAreDropped() {
        assertNull(Clean.rsrp(Int.MAX_VALUE))
        assertNull(Clean.rsrp(97)) // some phones report the 0..97 index instead of dBm
        assertEquals(-98, Clean.rsrp(-98))
        assertNull(Clean.lteSinr(Int.MAX_VALUE, tenths = false))
        assertEquals(15, Clean.lteSinr(150, tenths = true))
        assertEquals(12, Clean.lteSinr(12, tenths = false))
        assertEquals(-5, Clean.lteSinr(-50, tenths = false)) // tenths on a phone that never updated
    }

    @Test
    fun lteBandsAndFrequencies() {
        assertEquals(3, Bands.lteBand(1650)) // Airtel / Jio 1800
        assertEquals(1850.0, Bands.lteFrequency(1650)!!, 0.001)
        assertEquals(40, Bands.lteBand(38950)) // 2300 TDD
        assertEquals(5, Bands.lteBand(2560)) // Jio 850
        assertEquals(1, Bands.lteBand(100))
        assertNull(Bands.lteBand(70000))
    }

    @Test
    fun nrFrequencies() {
        assertEquals(3500.0, Bands.nrFrequency(633333)!!, 0.01)
        assertEquals(78, Bands.nrBand(3500.0))
        assertEquals(28, Bands.nrBand(Bands.nrFrequency(156510)!!)) // 782.55 MHz
        assertEquals(24250.08, Bands.nrFrequency(2016667)!!, 0.001)
    }

    @Test
    fun describeBands() {
        assertEquals("B3 · 1850 MHz", Bands.describe(listOf("B3"), 1850.0))
        assertEquals("n78 · 3549.6 MHz", Bands.describe(listOf("n78"), 3549.6))
        assertNull(Bands.describe(emptyList(), null))
    }

    @Test
    fun spotsAreSummarisedAndRanked() {
        val samples = listOf(
            SpotSample("SIM 1 · Jio", Tech.LTE, "4G", -90, 10),
            SpotSample("SIM 1 · Jio", Tech.LTE, "4G", -94, 12),
            SpotSample("SIM 1 · Jio", Tech.NR, "5G", -70, 20), // the minority technology is left out
            SpotSample("SIM 2 · airtel", Tech.LTE, "4G+", -100, null),
        )
        val results = SpotMath.summarise(samples)
        assertEquals(2, results.size)
        val jio = results.first { it.sim == "SIM 1 · Jio" }
        assertEquals(-92, jio.avgDbm)
        assertEquals(-94, jio.minDbm)
        assertEquals(-90, jio.maxDbm)
        assertEquals(11, jio.avgSinr)
        assertEquals(2, jio.samples)
        assertNull(results.first { it.sim == "SIM 2 · airtel" }.avgSinr)

        val bedroom = Spot(1, "Bedroom", 1, listOf(jio))
        val balcony = Spot(2, "Balcony", 2, listOf(jio.copy(avgDbm = -78)))
        val kitchen = Spot(3, "Kitchen", 3, listOf(results.last()))
        val ranked = SpotMath.ranked(listOf(bedroom, balcony, kitchen), "SIM 1 · Jio")
        assertEquals(listOf("Balcony", "Bedroom"), ranked.map { it.first.name })
    }
}
