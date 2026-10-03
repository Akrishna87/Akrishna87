package io.github.akrishna87.weather.data

import org.json.JSONObject
import kotlin.math.asin
import kotlin.math.atan2
import kotlin.math.cos
import kotlin.math.sin
import kotlin.math.sqrt

/** A place to forecast for. [countryCode] is ISO 3166 ("US", "IN"), or empty when unknown. */
data class Place(
    val name: String,
    val region: String,
    val country: String,
    val countryCode: String,
    val lat: Double,
    val lon: Double,
) {
    val label: String
        get() = listOf(name, region.takeIf { it != name }, country)
            .filter { !it.isNullOrBlank() }
            .joinToString(", ")

    /** The US National Weather Service only covers the USA and its territories. */
    val inUsa: Boolean
        get() = countryCode.equals("US", true) ||
            (countryCode.isBlank() && lat in 17.0..72.0 && lon in -180.0..-64.0)

    fun toJson(): String = JSONObject()
        .put("name", name).put("region", region).put("country", country)
        .put("cc", countryCode).put("lat", lat).put("lon", lon)
        .toString()

    companion object {
        fun fromJson(s: String): Place? = runCatching {
            val o = JSONObject(s)
            Place(
                o.getString("name"), o.optString("region"), o.optString("country"), o.optString("cc"),
                o.getDouble("lat"), o.getDouble("lon"),
            )
        }.getOrNull()
    }
}

/**
 * What one source says the weather is right now. Everything is metric (°C, km/h, mm, hPa); the
 * screens convert to the units picked in Settings. Missing values are null: not every model or
 * service reports everything.
 */
data class Reading(
    val sourceId: String,
    val sourceName: String,
    val agency: String,
    val tempC: Double?,
    val code: Int?,
    val windKmh: Double?,
    val windFromDeg: Double?,
    val gustKmh: Double? = null,
    val humidity: Double? = null,
    val pressureHpa: Double? = null,
    val precipMm: Double? = null,
    val condition: String? = null,
) {
    val hasData: Boolean get() = tempC != null || windKmh != null
}

data class Hour(
    val time: String,
    val tempC: Double?,
    val precipProb: Double?,
    val precipMm: Double?,
    val code: Int?,
    val windKmh: Double?,
    val windFromDeg: Double?,
    val gustKmh: Double?,
    val isDay: Boolean,
)

data class Day(
    val date: String,
    val code: Int?,
    val maxC: Double?,
    val minC: Double?,
    val precipMm: Double?,
    val precipProb: Double?,
    val windMaxKmh: Double?,
    val gustMaxKmh: Double?,
    val windFromDeg: Double?,
    val sunrise: String?,
    val sunset: String?,
    val uvMax: Double?,
)

data class Forecast(
    val now: Reading,
    val time: String,
    val isDay: Boolean,
    val feelsLikeC: Double?,
    val cloudPct: Double?,
    val hours: List<Hour>,
    val days: List<Day>,
    val fetchedAt: Long,
)

data class AirQuality(
    val usAqi: Double?,
    val euAqi: Double?,
    val pm25: Double?,
    val pm10: Double?,
    val ozone: Double?,
    val no2: Double?,
    val so2: Double?,
    val co: Double?,
    val dust: Double?,
    val uv: Double?,
)

data class Marine(val waveM: Double?, val waveFromDeg: Double?, val wavePeriodS: Double?, val swellM: Double?)

data class Alert(
    val title: String,
    val severity: String,
    val headline: String,
    val description: String,
    val sender: String,
    val ends: String?,
)

data class LatLon(val lat: Double, val lon: Double)

data class TrackPoint(val lat: Double, val lon: Double, val label: String?, val forecast: Boolean)

/** A storm's path and wind areas, as published by GDACS. */
data class StormTrack(
    val lines: List<List<LatLon>>,
    val points: List<TrackPoint>,
    val areas: List<List<LatLon>>,
)

/**
 * A named tropical cyclone (hurricane, typhoon, cyclone, tropical storm or depression).
 * [windKmh] is the current sustained wind when [windIsPeak] is false (NHC), or the strongest
 * wind so far when it's true (GDACS only reports the peak).
 */
data class Storm(
    val key: String,
    val name: String,
    val kind: String,
    val lat: Double,
    val lon: Double,
    val windKmh: Double?,
    val windIsPeak: Boolean,
    val pressureHpa: Double?,
    val movingTowardDeg: Double?,
    val movingKmh: Double?,
    val alertLevel: String?,
    val updated: String?,
    val sources: List<String>,
    val link: String?,
    val geometryUrl: String?,
    val track: StormTrack? = null,
) {
    val basin: String get() = basinOf(lat, lon)

    /** Saffir–Simpson category 1–5, 0 for a tropical storm, -1 for a depression, null if unknown. */
    val category: Int? get() = windKmh?.let(::categoryFor)

    val regionalName: String get() = regionalName(lat, lon)
}

/** "Hurricane", "Typhoon" or "Cyclone": what a strong tropical cyclone is called in that ocean. */
fun regionalName(lat: Double, lon: Double): String = when {
    lat >= 0 && lon < 0 -> "Hurricane" // North Atlantic, East and Central Pacific
    lat >= 0 && lon > 100 -> "Typhoon" // North-West Pacific
    else -> "Cyclone"
}

fun categoryFor(kmh: Double): Int = when {
    kmh < 63 -> -1
    kmh < 119 -> 0
    kmh < 154 -> 1
    kmh < 178 -> 2
    kmh < 209 -> 3
    kmh < 252 -> 4
    else -> 5
}

/** The ocean basin a storm is in, roughly as the warning centres divide them. */
fun basinOf(lat: Double, lonIn: Double): String {
    val lon = ((lonIn + 540) % 360) - 180
    return if (lat >= 0) when {
        lon < -140 -> "Central Pacific"
        lon < -100 || (lon < -80 && lat < 15) -> "East Pacific"
        lon < 30 -> "North Atlantic"
        lon < 100 -> "North Indian Ocean"
        else -> "West Pacific"
    } else when {
        lon in 20.0..90.0 -> "South-West Indian Ocean"
        lon in 90.0..160.0 -> "Australian region"
        lon < -60 || lon >= 160 -> "South Pacific"
        else -> "South Atlantic"
    }
}

/** Great-circle distance in km. */
fun distanceKm(a: LatLon, b: LatLon): Double {
    val r = 6371.0
    val dLat = Math.toRadians(b.lat - a.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val h = sin(dLat / 2).let { it * it } +
        cos(Math.toRadians(a.lat)) * cos(Math.toRadians(b.lat)) * sin(dLon / 2).let { it * it }
    return 2 * r * asin(sqrt(h.coerceIn(0.0, 1.0)))
}

/** Initial compass bearing from [a] to [b], in degrees. */
fun bearingDeg(a: LatLon, b: LatLon): Double {
    val la = Math.toRadians(a.lat)
    val lb = Math.toRadians(b.lat)
    val dLon = Math.toRadians(b.lon - a.lon)
    val y = sin(dLon) * cos(lb)
    val x = cos(la) * sin(lb) - sin(la) * cos(lb) * cos(dLon)
    return (Math.toDegrees(atan2(y, x)) + 360) % 360
}

enum class SourceState { Loading, Ok, Failed, Skipped }

/** How one data source fared on the last refresh, for the Sources screen. */
data class SourceStatus(
    val id: String,
    val name: String,
    val role: String,
    val state: SourceState,
    val detail: String = "",
    val millis: Long = 0,
    val at: Long = System.currentTimeMillis(),
)
