package io.github.akrishna87.weather.data

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.URLEncoder
import java.util.Locale

/**
 * Open-Meteo (https://open-meteo.com, CC BY 4.0): free, keyless access to the forecasts of the
 * world's national weather services, plus air quality, ocean waves and place search.
 */
object OpenMeteo {
    data class Model(val id: String, val name: String, val agency: String)

    /** The global models Open-Meteo serves, each run by a different weather service. */
    val MODELS = listOf(
        Model("ecmwf_ifs025", "ECMWF IFS", "European Centre for Medium-Range Weather Forecasts"),
        Model("gfs_seamless", "GFS", "NOAA (USA)"),
        Model("icon_seamless", "ICON", "Deutscher Wetterdienst (Germany)"),
        Model("gem_seamless", "GEM", "Environment Canada"),
        Model("jma_seamless", "JMA GSM", "Japan Meteorological Agency"),
        Model("meteofrance_seamless", "ARPEGE", "Météo-France"),
        Model("ukmo_seamless", "UKMO", "UK Met Office"),
        Model("cma_grapes_global", "GRAPES", "China Meteorological Administration"),
        Model("bom_access_global", "ACCESS-G", "Bureau of Meteorology (Australia)"),
    )

    private const val NOW = "temperature_2m,relative_humidity_2m,apparent_temperature,is_day,precipitation," +
        "weather_code,cloud_cover,pressure_msl,wind_speed_10m,wind_direction_10m,wind_gusts_10m"

    private fun at(place: Place) = "latitude=${coord(place.lat)}&longitude=${coord(place.lon)}"

    /** The full forecast (now, 48 hours, 7 days) from Open-Meteo's best blend of models for the place. */
    suspend fun forecast(place: Place): Forecast {
        val text = Http.get(
            "https://api.open-meteo.com/v1/forecast?${at(place)}&timezone=auto&forecast_days=7" +
                "&current=$NOW" +
                "&hourly=temperature_2m,precipitation_probability,precipitation,weather_code,wind_speed_10m," +
                "wind_direction_10m,wind_gusts_10m,is_day" +
                "&daily=weather_code,temperature_2m_max,temperature_2m_min,precipitation_sum," +
                "precipitation_probability_max,wind_speed_10m_max,wind_gusts_10m_max," +
                "wind_direction_10m_dominant,sunrise,sunset,uv_index_max",
        )
        return withContext(Dispatchers.Default) {
            val o = JSONObject(text)
            val c = o.getJSONObject("current")
            val now = reading(c, "open-meteo", "Open-Meteo best match", "Blend of the best models for this place")
            val time = c.optString("time")

            val h = o.optJSONObject("hourly")
            val hTimes = h?.optJSONArray("time") ?: JSONArray()
            val currentHour = time.take(13) // "2026-10-03T14"
            val start = (0 until hTimes.length()).firstOrNull { hTimes.optString(it).take(13) >= currentHour } ?: 0
            val hours = (start until minOf(start + 48, hTimes.length())).map { i ->
                Hour(
                    time = hTimes.optString(i),
                    tempC = h?.optJSONArray("temperature_2m").dblAt(i),
                    precipProb = h?.optJSONArray("precipitation_probability").dblAt(i),
                    precipMm = h?.optJSONArray("precipitation").dblAt(i),
                    code = h?.optJSONArray("weather_code").dblAt(i)?.toInt(),
                    windKmh = h?.optJSONArray("wind_speed_10m").dblAt(i),
                    windFromDeg = h?.optJSONArray("wind_direction_10m").dblAt(i),
                    gustKmh = h?.optJSONArray("wind_gusts_10m").dblAt(i),
                    isDay = (h?.optJSONArray("is_day").dblAt(i) ?: 1.0) > 0.5,
                )
            }

            val d = o.optJSONObject("daily")
            val dTimes = d?.optJSONArray("time") ?: JSONArray()
            val days = (0 until dTimes.length()).map { i ->
                Day(
                    date = dTimes.optString(i),
                    code = d?.optJSONArray("weather_code").dblAt(i)?.toInt(),
                    maxC = d?.optJSONArray("temperature_2m_max").dblAt(i),
                    minC = d?.optJSONArray("temperature_2m_min").dblAt(i),
                    precipMm = d?.optJSONArray("precipitation_sum").dblAt(i),
                    precipProb = d?.optJSONArray("precipitation_probability_max").dblAt(i),
                    windMaxKmh = d?.optJSONArray("wind_speed_10m_max").dblAt(i),
                    gustMaxKmh = d?.optJSONArray("wind_gusts_10m_max").dblAt(i),
                    windFromDeg = d?.optJSONArray("wind_direction_10m_dominant").dblAt(i),
                    sunrise = d?.optJSONArray("sunrise").strAt(i),
                    sunset = d?.optJSONArray("sunset").strAt(i),
                    uvMax = d?.optJSONArray("uv_index_max").dblAt(i),
                )
            }

            Forecast(
                now = now,
                time = time,
                isDay = (c.dbl("is_day") ?: 1.0) > 0.5,
                feelsLikeC = c.dbl("apparent_temperature"),
                cloudPct = c.dbl("cloud_cover"),
                hours = hours,
                days = days,
                fetchedAt = System.currentTimeMillis(),
            )
        }
    }

    /**
     * What every global model says right now, in one request: with several models, Open-Meteo
     * names each value after its model ("temperature_2m_ecmwf_ifs025"). One request instead of
     * nine keeps clear of the free tier's limit on simultaneous requests. A model with no data
     * for the place comes back with nulls (and [Reading.hasData] false).
     */
    suspend fun modelReadings(place: Place): List<Reading> {
        val text = Http.get(
            "https://api.open-meteo.com/v1/forecast?${at(place)}&timezone=auto&forecast_days=1" +
                "&models=${MODELS.joinToString(",") { it.id }}&current=$NOW",
        )
        val c = JSONObject(text).getJSONObject("current")
        return MODELS.map { m -> reading(c, m.id, m.name, m.agency, suffix = "_${m.id}") }
    }

    private fun reading(c: JSONObject, id: String, name: String, agency: String, suffix: String = "") = Reading(
        sourceId = id,
        sourceName = name,
        agency = agency,
        tempC = c.dbl("temperature_2m$suffix"),
        code = c.dbl("weather_code$suffix")?.toInt(),
        windKmh = c.dbl("wind_speed_10m$suffix"),
        windFromDeg = c.dbl("wind_direction_10m$suffix"),
        gustKmh = c.dbl("wind_gusts_10m$suffix"),
        humidity = c.dbl("relative_humidity_2m$suffix"),
        pressureHpa = c.dbl("pressure_msl$suffix"),
        precipMm = c.dbl("precipitation$suffix"),
    )

    suspend fun airQuality(place: Place): AirQuality {
        val text = Http.get(
            "https://air-quality-api.open-meteo.com/v1/air-quality?${at(place)}&timezone=auto" +
                "&current=us_aqi,european_aqi,pm2_5,pm10,ozone,nitrogen_dioxide,sulphur_dioxide," +
                "carbon_monoxide,dust,uv_index",
        )
        val c = JSONObject(text).getJSONObject("current")
        return AirQuality(
            usAqi = c.dbl("us_aqi"),
            euAqi = c.dbl("european_aqi"),
            pm25 = c.dbl("pm2_5"),
            pm10 = c.dbl("pm10"),
            ozone = c.dbl("ozone"),
            no2 = c.dbl("nitrogen_dioxide"),
            so2 = c.dbl("sulphur_dioxide"),
            co = c.dbl("carbon_monoxide"),
            dust = c.dbl("dust"),
            uv = c.dbl("uv_index"),
        )
    }

    /** Waves and swell off the coast; null for places inland, where there's no sea to report. */
    suspend fun marine(place: Place): Marine? {
        val text = Http.get(
            "https://marine-api.open-meteo.com/v1/marine?${at(place)}&timezone=auto" +
                "&current=wave_height,wave_direction,wave_period,swell_wave_height",
        )
        val c = JSONObject(text).optJSONObject("current") ?: return null
        val m = Marine(c.dbl("wave_height"), c.dbl("wave_direction"), c.dbl("wave_period"), c.dbl("swell_wave_height"))
        return m.takeIf { it.waveM != null }
    }

    /** Finds places by name, in any language ("Chennai", "சென்னை", "London"). */
    suspend fun search(query: String): List<Place> {
        val q = URLEncoder.encode(query.trim(), "UTF-8")
        val lang = Locale.getDefault().language.ifBlank { "en" }
        val text = Http.get("https://geocoding-api.open-meteo.com/v1/search?name=$q&count=12&language=$lang&format=json")
        val results = JSONObject(text).optJSONArray("results") ?: return emptyList()
        return (0 until results.length()).mapNotNull { i ->
            val r = results.optJSONObject(i) ?: return@mapNotNull null
            Place(
                name = r.optString("name"),
                region = r.str("admin1") ?: "",
                country = r.str("country") ?: "",
                countryCode = r.str("country_code") ?: "",
                lat = r.optDouble("latitude"),
                lon = r.optDouble("longitude"),
            )
        }
    }

    /**
     * Wind on a grid covering [box], for the next 48 hours: the data behind the wind-flow map.
     * Open-Meteo takes many coordinates in one request, so the whole grid comes back in one go.
     */
    suspend fun windField(box: GeoBox, nx: Int, ny: Int): WindField {
        val lats = DoubleArray(ny) { j -> box.south + (box.north - box.south) * j / (ny - 1) }
        val lons = DoubleArray(nx) { i -> box.west + (box.east - box.west) * i / (nx - 1) }
        val latParam = StringBuilder()
        val lonParam = StringBuilder()
        for (j in 0 until ny) for (i in 0 until nx) {
            if (latParam.isNotEmpty()) { latParam.append(','); lonParam.append(',') }
            latParam.append(String.format(Locale.US, "%.2f", lats[j]))
            lonParam.append(String.format(Locale.US, "%.2f", normLon(lons[i])))
        }
        val text = Http.get(
            "https://api.open-meteo.com/v1/forecast?latitude=$latParam&longitude=$lonParam" +
                "&hourly=wind_speed_10m,wind_direction_10m,wind_gusts_10m" +
                "&forecast_hours=48&timeformat=unixtime&timezone=GMT",
            timeoutMs = 40_000,
        )
        return withContext(Dispatchers.Default) {
            val trimmed = text.trimStart()
            val arr = if (trimmed.startsWith("[")) JSONArray(trimmed) else JSONArray().put(JSONObject(trimmed))
            require(arr.length() == nx * ny) { "Wind data came back for ${arr.length()} of ${nx * ny} points" }
            val first = arr.getJSONObject(0).getJSONObject("hourly").getJSONArray("time")
            val nt = first.length()
            val times = LongArray(nt) { first.getLong(it) * 1000 }
            val n = nx * ny
            val u = Array(nt) { FloatArray(n) }
            val v = Array(nt) { FloatArray(n) }
            val speed = Array(nt) { FloatArray(n) }
            val gust = Array(nt) { FloatArray(n) }
            for (k in 0 until n) {
                val hr = arr.getJSONObject(k).getJSONObject("hourly")
                val s = hr.optJSONArray("wind_speed_10m")
                val d = hr.optJSONArray("wind_direction_10m")
                val g = hr.optJSONArray("wind_gusts_10m")
                for (t in 0 until nt) {
                    val sp = s.dblAt(t)
                    val dir = d.dblAt(t)
                    if (sp == null || dir == null) {
                        u[t][k] = Float.NaN; v[t][k] = Float.NaN; speed[t][k] = Float.NaN; gust[t][k] = Float.NaN
                        continue
                    }
                    // Meteorological direction is where the wind blows FROM; u/v point where it goes.
                    val rad = Math.toRadians(dir)
                    u[t][k] = (-sp * Math.sin(rad)).toFloat()
                    v[t][k] = (-sp * Math.cos(rad)).toFloat()
                    speed[t][k] = sp.toFloat()
                    gust[t][k] = (g.dblAt(t) ?: sp).toFloat()
                }
            }
            WindField(box, nx, ny, times, u, v, speed, gust)
        }
    }
}

fun normLon(lon: Double): Double = ((lon + 540) % 360) - 180

/** A lat/lon rectangle. [west] < [east], and either may run past ±180 when the box crosses the date line. */
data class GeoBox(val west: Double, val south: Double, val east: Double, val north: Double) {
    val lonSpan get() = east - west
    val latSpan get() = north - south

    /** True if this box (with its longitudes taken modulo 360) contains all of [other]. */
    fun covers(other: GeoBox): Boolean {
        if (other.south < south || other.north > north) return false
        if (lonSpan >= 359.9) return true
        val w = west + (((other.west - west) % 360) + 360) % 360
        return w + other.lonSpan <= east
    }
}

/**
 * The wind on a regular lat/lon grid, hour by hour. [u]/[v] are the east/north components and
 * [speed]/[gust] the magnitudes, all km/h, indexed `[hour][row * nx + column]` with row 0 at the
 * south edge. Values are NaN where a point had no data.
 */
class WindField(
    val box: GeoBox,
    val nx: Int,
    val ny: Int,
    val times: LongArray,
    val u: Array<FloatArray>,
    val v: Array<FloatArray>,
    val speed: Array<FloatArray>,
    val gust: Array<FloatArray>,
) {
    val spacingDeg: Double get() = box.lonSpan / (nx - 1)

    /** The hour closest to now. */
    fun nowIndex(): Int {
        val now = System.currentTimeMillis()
        return times.indices.minByOrNull { kotlin.math.abs(times[it] - now) } ?: 0
    }

    /**
     * Bilinearly interpolated wind at [lon]/[lat] for hour [t], written to [out] as (u, v, speed).
     * Returns false outside the grid or where data is missing.
     */
    fun sample(t: Int, lon: Double, lat: Double, gusts: Boolean, out: FloatArray): Boolean {
        if (t !in times.indices) return false
        if (lat < box.south || lat > box.north) return false
        val x = box.west + (((lon - box.west) % 360) + 360) % 360
        if (x > box.east && box.lonSpan < 359.9) return false
        val fx = ((x - box.west) / box.lonSpan * (nx - 1)).toFloat().coerceIn(0f, (nx - 1).toFloat())
        val fy = ((lat - box.south) / box.latSpan * (ny - 1)).toFloat().coerceIn(0f, (ny - 1).toFloat())
        val i0 = fx.toInt().coerceAtMost(nx - 2)
        val j0 = fy.toInt().coerceAtMost(ny - 2)
        val ax = fx - i0
        val ay = fy - j0
        val k00 = j0 * nx + i0
        val k10 = k00 + 1
        val k01 = k00 + nx
        val k11 = k01 + 1
        val uu = u[t]
        val vv = v[t]
        val ss = if (gusts) gust[t] else speed[t]
        val a = uu[k00]; val b = uu[k10]; val c = uu[k01]; val d = uu[k11]
        if (a.isNaN() || b.isNaN() || c.isNaN() || d.isNaN()) return false
        fun lerp(p: Float, q: Float, r: Float, s: Float) =
            (p * (1 - ax) + q * ax) * (1 - ay) + (r * (1 - ax) + s * ax) * ay
        out[0] = lerp(a, b, c, d)
        out[1] = lerp(vv[k00], vv[k10], vv[k01], vv[k11])
        out[2] = lerp(ss[k00], ss[k10], ss[k01], ss[k11])
        return true
    }
}
