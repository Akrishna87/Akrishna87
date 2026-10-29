package io.github.akrishna87.weather.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TrackOrderTest {
    private fun track(vararg lonLat: Pair<Double, Double>) =
        StormTrack(emptyList(), lonLat.map { (lon, lat) -> TrackPoint(lat, lon, null, forecast = false) }, emptyList())

    // Like In Fa: wind areas listed from the far west back to where the storm is now.
    private val westward = track(120.0 to 20.0, 125.0 to 20.0, 130.0 to 20.0)
    private val now = LatLon(20.0, 130.0)

    @Test
    fun startsAtTheStormWhenItMovesAlongThePoints() {
        val t = StormFeeds.inTimeOrder(westward, now, movingTowardDeg = 270.0)
        assertEquals(listOf(130.0, 125.0, 120.0), t.points.map { it.lon })
        assertFalse(t.points.first().forecast)
        assertTrue(t.points.drop(1).all { it.forecast })
    }

    @Test
    fun endsAtTheStormWhenThePointsAreBehindIt() {
        val t = StormFeeds.inTimeOrder(westward, now, movingTowardDeg = 90.0)
        assertEquals(listOf(120.0, 125.0, 130.0), t.points.map { it.lon })
        assertTrue(t.points.none { it.forecast })
    }

    @Test
    fun takesThePointsAsTheForecastWhenTheHeadingIsUnknown() {
        val t = StormFeeds.inTimeOrder(westward, now, movingTowardDeg = null)
        assertEquals(130.0, t.points.first().lon, 0.0)
        assertTrue(t.points.last().forecast)
    }

    @Test
    fun leavesDatedTracksAlone() {
        val dated = westward.copy(points = westward.points.map { it.copy(label = "28/10 12:00") })
        assertEquals(dated, StormFeeds.inTimeOrder(dated, now, movingTowardDeg = 90.0))
    }
}
