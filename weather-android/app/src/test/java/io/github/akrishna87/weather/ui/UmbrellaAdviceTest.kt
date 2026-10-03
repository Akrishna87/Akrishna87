package io.github.akrishna87.weather.ui

import io.github.akrishna87.weather.data.Hour
import io.github.akrishna87.weather.data.Reading
import io.github.akrishna87.weather.data.Units
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UmbrellaAdviceTest {
    private val metric = Units()

    private fun now(code: Int, precipMm: Double = 0.0) = Reading(
        sourceId = "test", sourceName = "Test", agency = "Test",
        tempC = 20.0, code = code, windKmh = 10.0, windFromDeg = 180.0, precipMm = precipMm,
    )

    /** 24 hours from 09:00; [wet] maps an hour index to (chance %, mm, weather code). */
    private fun hours(wet: Map<Int, Triple<Double, Double, Int>> = emptyMap()) = (0 until 24).map { i ->
        val (prob, mm, code) = wet[i] ?: Triple(0.0, 0.0, 1)
        Hour(
            time = "2026-10-03T%02d:00".format((9 + i) % 24),
            tempC = 20.0, precipProb = prob, precipMm = mm, code = code,
            windKmh = 10.0, windFromDeg = 180.0, gustKmh = 20.0, isDay = true,
        )
    }

    @Test fun rainingNowSaysWhenItEases() {
        val a = umbrellaAdvice(now(63, 1.2), hours((0..5).associateWith { Triple(90.0, 1.5, 63) }), metric)
        assertEquals(UmbrellaAdvice.Level.RainingNow, a.level)
        assertEquals("It's raining — take an umbrella", a.title)
        assertEquals("Rain now; should ease around 15:00.", a.detail)
        assertEquals("☂️", a.emoji)
    }

    @Test fun rainingAllDaySaysItContinues() {
        val a = umbrellaAdvice(now(61, 0.4), hours((0..23).associateWith { Triple(80.0, 0.5, 61) }), metric)
        assertEquals(UmbrellaAdvice.Level.RainingNow, a.level)
        assertTrue(a.detail, a.detail.contains("continue"))
    }

    @Test fun drizzleWithNoCodeStillCountsWhenItIsFalling() {
        // Some models report precipitation now with a cloudy code.
        val a = umbrellaAdvice(now(3, 0.3), hours(), metric)
        assertEquals(UmbrellaAdvice.Level.RainingNow, a.level)
    }

    @Test fun rainLaterSaysFromWhenAndHowLikely() {
        val a = umbrellaAdvice(now(2), hours(mapOf(5 to Triple(70.0, 2.0, 63), 6 to Triple(60.0, 1.0, 61))), metric)
        assertEquals(UmbrellaAdvice.Level.Likely, a.level)
        assertEquals("Take an umbrella", a.title)
        assertEquals("Rain likely from 14:00 (70% chance), about 3.0 mm by 20:00.", a.detail)
    }

    @Test fun rainThisHour() {
        val a = umbrellaAdvice(now(3), hours(mapOf(0 to Triple(60.0, 0.1, 80))), metric)
        assertEquals(UmbrellaAdvice.Level.Likely, a.level)
        assertTrue(a.detail, a.detail.startsWith("Rain likely this hour (60% chance)"))
    }

    @Test fun thunderstormsAhead() {
        val a = umbrellaAdvice(now(2), hours(mapOf(3 to Triple(80.0, 5.0, 95))), metric)
        assertEquals(UmbrellaAdvice.Level.Likely, a.level)
        assertTrue(a.thunder)
        assertEquals("⛈️", a.emoji)
        assertTrue(a.title, a.title.contains("storm"))
        assertTrue(a.detail, a.detail.startsWith("Thunderstorms likely from 12:00"))
    }

    @Test fun smallChanceSuggestsMaybe() {
        val a = umbrellaAdvice(now(2), hours(mapOf(7 to Triple(40.0, 0.0, 80))), metric)
        assertEquals(UmbrellaAdvice.Level.Maybe, a.level)
        assertEquals("Maybe pack an umbrella", a.title)
        assertEquals("40% chance of rain around 16:00.", a.detail)
    }

    @Test fun dryDay() {
        val a = umbrellaAdvice(now(0), hours(), metric)
        assertEquals(UmbrellaAdvice.Level.None, a.level)
        assertFalse(a.thunder)
    }

    @Test fun rainAfterTwelveHoursIsIgnored() {
        val a = umbrellaAdvice(now(0), hours(mapOf(15 to Triple(90.0, 4.0, 65))), metric)
        assertEquals(UmbrellaAdvice.Level.None, a.level)
    }

    @Test fun amountFollowsUnits() {
        val a = umbrellaAdvice(now(2), hours(mapOf(2 to Triple(80.0, 25.4, 65))), Units(fahrenheit = true))
        assertTrue(a.detail, a.detail.contains("about 1.00 in by 20:00"))
    }
}
