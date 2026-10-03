package io.github.akrishna87.weather.ui

import io.github.akrishna87.weather.data.Units
import io.github.akrishna87.weather.data.WindUnit
import io.github.akrishna87.weather.data.degToCompass
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.roundToInt

const val DASH = "–"

fun Units.temp(c: Double?): String =
    c?.let { "${(if (fahrenheit) it * 9 / 5 + 32 else it).roundToInt()}°" } ?: DASH

fun Units.windNumber(kmh: Double?): String = kmh?.let {
    when (wind) {
        WindUnit.KMH -> it.roundToInt().toString()
        WindUnit.MPH -> (it / 1.609344).roundToInt().toString()
        WindUnit.MS -> String.format(Locale.getDefault(), "%.1f", it / 3.6)
        WindUnit.KNOTS -> (it / 1.852).roundToInt().toString()
    }
} ?: DASH

fun Units.wind(kmh: Double?): String = if (kmh == null) DASH else "${windNumber(kmh)} ${wind.label}"

fun Units.precip(mm: Double?): String = mm?.let {
    if (fahrenheit) String.format(Locale.getDefault(), "%.2f in", it / 25.4)
    else String.format(Locale.getDefault(), if (it < 10) "%.1f mm" else "%.0f mm", it)
} ?: DASH

fun Units.pressure(hpa: Double?): String = hpa?.let {
    if (fahrenheit) String.format(Locale.getDefault(), "%.2f inHg", it / 33.8639) else "${it.roundToInt()} hPa"
} ?: DASH

fun Units.distance(km: Double): String =
    if (fahrenheit) "${(km / 1.609344).roundToInt()} mi" else "${km.roundToInt()} km"

fun pct(v: Double?): String = v?.let { "${it.roundToInt()}%" } ?: DASH

fun compass(deg: Double?): String = deg?.let(::degToCompass) ?: DASH

/** The Beaufort force and its name for a wind speed. */
fun beaufort(kmh: Double): Pair<Int, String> {
    val limits = doubleArrayOf(1.0, 6.0, 12.0, 20.0, 29.0, 39.0, 50.0, 62.0, 75.0, 89.0, 103.0, 118.0)
    val names = listOf(
        "Calm", "Light air", "Light breeze", "Gentle breeze", "Moderate breeze", "Fresh breeze",
        "Strong breeze", "Near gale", "Gale", "Strong gale", "Storm", "Violent storm", "Hurricane force",
    )
    val force = limits.indexOfFirst { kmh < it }.let { if (it < 0) 12 else it }
    return force to names[force]
}

/** WMO weather code → words. */
fun weatherText(code: Int?): String = when (code) {
    0 -> "Clear sky"
    1 -> "Mainly clear"
    2 -> "Partly cloudy"
    3 -> "Overcast"
    45, 48 -> "Fog"
    51, 53, 55 -> "Drizzle"
    56, 57 -> "Freezing drizzle"
    61 -> "Light rain"
    63 -> "Rain"
    65 -> "Heavy rain"
    66, 67 -> "Freezing rain"
    71 -> "Light snow"
    73 -> "Snow"
    75 -> "Heavy snow"
    77 -> "Snow grains"
    80, 81 -> "Rain showers"
    82 -> "Violent rain showers"
    85, 86 -> "Snow showers"
    95 -> "Thunderstorm"
    96, 99 -> "Thunderstorm with hail"
    null -> DASH
    else -> "Code $code"
}

fun weatherEmoji(code: Int?, isDay: Boolean = true): String = when (code) {
    0 -> if (isDay) "☀️" else "🌙"
    1 -> if (isDay) "🌤️" else "🌙"
    2 -> if (isDay) "⛅" else "☁️"
    3 -> "☁️"
    45, 48 -> "🌫️"
    51, 53, 55, 56, 57 -> "🌦️"
    61, 63, 65, 66, 67, 80, 81, 82 -> "🌧️"
    71, 73, 75, 77, 85, 86 -> "🌨️"
    95, 96, 99 -> "⛈️"
    else -> "🌡️"
}

private val HOUR = DateTimeFormatter.ofPattern("HH:mm")
private val WEEKDAY = DateTimeFormatter.ofPattern("EEE d", Locale.getDefault())
private val WHEN = DateTimeFormatter.ofPattern("EEE d MMM, HH:mm", Locale.getDefault())

/** "2026-10-03T14:00" → "14:00". */
fun hourLabel(iso: String?): String =
    iso?.let { runCatching { LocalDateTime.parse(it).format(HOUR) }.getOrNull() } ?: DASH

/** "2026-10-03" → "Today", "Tomorrow", "Mon 5". */
fun dayLabel(iso: String, index: Int): String = when (index) {
    0 -> "Today"
    1 -> "Tomorrow"
    else -> runCatching { LocalDate.parse(iso).format(WEEKDAY) }.getOrDefault(iso)
}

/** A moment in the phone's own time zone: "Sat 3 Oct, 14:00". */
fun localTime(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(WHEN)

/** An ISO time from a feed ("2026-10-03T12:00:00Z" or without a zone, taken as UTC), in local time. */
fun feedTime(iso: String?): String? = iso?.let {
    runCatching { localTime(Instant.parse(it).toEpochMilli()) }.getOrNull()
        ?: runCatching { localTime(LocalDateTime.parse(it.take(19)).atZone(ZoneId.of("UTC")).toInstant().toEpochMilli()) }.getOrNull()
}

fun ago(millis: Long): String {
    val mins = ((System.currentTimeMillis() - millis) / 60_000).toInt()
    return when {
        mins < 1 -> "just now"
        mins < 60 -> "$mins min ago"
        else -> "${mins / 60} h ago"
    }
}

/** Wind speed colour scale shared by the map and its legend (km/h → ARGB). */
val WIND_STOPS = floatArrayOf(0f, 10f, 20f, 30f, 45f, 60f, 90f, 120f, 160f)
val WIND_COLORS = intArrayOf(
    0xFF3B82F6.toInt(), // calm: blue
    0xFF22D3EE.toInt(), // light: cyan
    0xFF34D399.toInt(), // breeze: green
    0xFFA3E635.toInt(), // fresh: lime
    0xFFFACC15.toInt(), // strong: yellow
    0xFFFB923C.toInt(), // gale: orange
    0xFFEF4444.toInt(), // storm: red
    0xFFD946EF.toInt(), // violent storm: magenta
    0xFFFFFFFF.toInt(), // hurricane force: white
)

fun windColor(kmh: Float): Int {
    if (kmh.isNaN() || kmh <= WIND_STOPS[0]) return WIND_COLORS[0]
    for (i in 1 until WIND_STOPS.size) {
        if (kmh <= WIND_STOPS[i]) {
            val t = (kmh - WIND_STOPS[i - 1]) / (WIND_STOPS[i] - WIND_STOPS[i - 1])
            return mix(WIND_COLORS[i - 1], WIND_COLORS[i], t)
        }
    }
    return WIND_COLORS.last()
}

private fun mix(a: Int, b: Int, t: Float): Int {
    fun ch(shift: Int) = ((a shr shift and 0xFF) * (1 - t) + (b shr shift and 0xFF) * t).roundToInt() shl shift
    return ch(24) or ch(16) or ch(8) or ch(0)
}

/** Colour for a storm's strength: depression → category 5. */
fun stormColor(category: Int?): Int = when (category) {
    null, -1 -> 0xFF60A5FA.toInt()
    0 -> 0xFF34D399.toInt()
    1 -> 0xFFFACC15.toInt()
    2 -> 0xFFFB923C.toInt()
    3 -> 0xFFEF4444.toInt()
    4 -> 0xFFE11D48.toInt()
    else -> 0xFFD946EF.toInt()
}
