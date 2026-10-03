package io.github.akrishna87.weather.data

import org.json.JSONObject
import java.util.Locale

/** The Norwegian Meteorological Institute's own forecast (https://api.met.no, CC BY 4.0), for anywhere on Earth. */
object MetNorway {
    suspend fun reading(place: Place): Reading {
        val text = Http.get(
            "https://api.met.no/weatherapi/locationforecast/2.0/compact?lat=${coord(place.lat)}&lon=${coord(place.lon)}",
        )
        val series = JSONObject(text).getJSONObject("properties").getJSONArray("timeseries")
        val data = series.getJSONObject(0).getJSONObject("data")
        val d = data.getJSONObject("instant").getJSONObject("details")
        val next = data.optJSONObject("next_1_hours") ?: data.optJSONObject("next_6_hours")
        val symbol = next?.optJSONObject("summary")?.str("symbol_code")
        return Reading(
            sourceId = "metno",
            sourceName = "MET Norway",
            agency = "Norwegian Meteorological Institute",
            tempC = d.dbl("air_temperature"),
            code = symbol?.let(::codeForWords),
            windKmh = d.dbl("wind_speed")?.times(3.6),
            windFromDeg = d.dbl("wind_from_direction"),
            humidity = d.dbl("relative_humidity"),
            pressureHpa = d.dbl("air_pressure_at_sea_level"),
            precipMm = data.optJSONObject("next_1_hours")?.optJSONObject("details")?.dbl("precipitation_amount"),
        )
    }
}

/** The US National Weather Service (https://api.weather.gov, public domain): forecasts and official alerts for the USA. */
object Nws {
    private const val GEO = "application/geo+json"

    suspend fun reading(place: Place): Reading {
        val point = JSONObject(Http.get("https://api.weather.gov/points/${coord(place.lat)},${coord(place.lon)}", GEO))
        val hourlyUrl = point.getJSONObject("properties").getString("forecastHourly")
        val period = JSONObject(Http.get(hourlyUrl, GEO))
            .getJSONObject("properties").getJSONArray("periods").getJSONObject(0)
        val temp = period.dbl("temperature")
        val tempC = if (period.optString("temperatureUnit") == "F") temp?.let { (it - 32) * 5 / 9 } else temp
        val short = period.str("shortForecast")
        return Reading(
            sourceId = "nws",
            sourceName = "NWS",
            agency = "US National Weather Service",
            tempC = tempC,
            code = short?.let(::codeForWords),
            windKmh = period.str("windSpeed")?.let { parseMph(it) }?.times(1.609344),
            windFromDeg = period.str("windDirection")?.let(::compassToDeg),
            humidity = period.optJSONObject("relativeHumidity")?.dbl("value"),
            condition = short,
        )
    }

    /** Active watches, warnings and advisories covering the place. */
    suspend fun alerts(place: Place): List<Alert> {
        val text = Http.get("https://api.weather.gov/alerts/active?point=${coord(place.lat)},${coord(place.lon)}", GEO)
        val features = JSONObject(text).optJSONArray("features") ?: return emptyList()
        return (0 until features.length()).mapNotNull { i ->
            val p = features.optJSONObject(i)?.optJSONObject("properties") ?: return@mapNotNull null
            Alert(
                title = p.str("event") ?: "Weather alert",
                severity = p.str("severity") ?: "",
                headline = p.str("headline") ?: "",
                description = listOfNotNull(p.str("description"), p.str("instruction")).joinToString("\n\n"),
                sender = p.str("senderName") ?: "National Weather Service",
                ends = p.str("ends") ?: p.str("expires"),
            )
        }
    }

    /** "10 mph" or "5 to 15 mph": the higher figure. */
    private fun parseMph(s: String): Double? =
        Regex("""\d+(\.\d+)?""").findAll(s).mapNotNull { it.value.toDoubleOrNull() }.maxOrNull()
}

private val COMPASS = listOf("N", "NNE", "NE", "ENE", "E", "ESE", "SE", "SSE", "S", "SSW", "SW", "WSW", "W", "WNW", "NW", "NNW")

fun compassToDeg(s: String): Double? = COMPASS.indexOf(s.trim().uppercase(Locale.ROOT)).takeIf { it >= 0 }?.times(22.5)

fun degToCompass(deg: Double): String = COMPASS[(((deg % 360) + 360) % 360 / 22.5 + 0.5).toInt() % 16]

/**
 * Turns a text forecast ("Chance Light Rain", MET Norway's "heavyrainandthunder") into the
 * closest WMO weather code, so every source can share the same icons.
 */
fun codeForWords(words: String): Int {
    val w = words.lowercase(Locale.ROOT).replace(" ", "")
    return when {
        "thunder" in w || "tstorm" in w -> 95
        "freezing" in w || "sleet" in w || "ice" in w -> 66
        "heavysnow" in w -> 75
        "snow" in w || "flurr" in w || "blizzard" in w -> 73
        "heavyrain" in w -> 65
        "shower" in w -> 80
        "drizzle" in w -> 51
        "lightrain" in w -> 61
        "rain" in w -> 63
        "fog" in w || "mist" in w || "haze" in w || "smoke" in w -> 45
        "partly" in w || "mostlysunny" in w || "mostlyclear" in w -> 2
        "fair" in w -> 1
        "cloudy" in w || "overcast" in w -> 3
        "clear" in w || "sunny" in w -> 0
        else -> 2
    }
}
