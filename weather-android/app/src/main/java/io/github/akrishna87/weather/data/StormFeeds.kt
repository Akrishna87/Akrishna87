package io.github.akrishna87.weather.data

import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.util.Locale

/**
 * Named tropical cyclones, from two feeds:
 *  - GDACS (https://www.gdacs.org), the UN / European Commission disaster alert system: every
 *    ocean, with each storm's track and wind areas.
 *  - NOAA's National Hurricane Center (https://www.nhc.noaa.gov): the Atlantic and the East and
 *    Central Pacific, with the latest official intensity, pressure and movement.
 */
object StormFeeds {
    private const val GDACS = "https://www.gdacs.org/gdacsapi/api"

    suspend fun gdacs(): List<Storm> {
        val today = LocalDate.now(ZoneOffset.UTC)
        val search = "$GDACS/events/geteventlist/SEARCH?eventlist=TC&alertlevel=Green;Orange;Red" +
            "&fromDate=${today.minusDays(14)}&toDate=${today.plusDays(1)}"
        val text = try {
            Http.get(search)
        } catch (e: Exception) {
            // The map's own list of current events, in the same format.
            Http.get("$GDACS/events/geteventlist/MAP?eventtypes=TC")
        }
        if (text.isBlank()) return emptyList() // GDACS answers an empty body when nothing matches
        val features = JSONObject(text).optJSONArray("features") ?: return emptyList()
        val cutoff = LocalDateTime.now(ZoneOffset.UTC).minusHours(36)
        val byEvent = LinkedHashMap<String, Storm>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val p = f.optJSONObject("properties") ?: continue
            if (!p.optString("eventtype").equals("TC", true)) continue
            val current = p.optString("iscurrent").equals("true", true)
            val ends = p.str("todate")?.let { runCatching { LocalDateTime.parse(it.take(19)) }.getOrNull() }
            // GDACS can keep "iscurrent" set for days after a storm dies, so go by its last update too.
            if (ends?.isBefore(cutoff) ?: !current) continue
            val coords = f.optJSONObject("geometry")?.optJSONArray("coordinates") ?: continue
            val lon = coords.optDouble(0)
            val lat = coords.optDouble(1)
            if (lat.isNaN() || lon.isNaN()) continue
            val sev = p.optJSONObject("severitydata")
            val sevText = sev?.str("severitytext") ?: ""
            val unit = sev?.optString("severityunit")?.lowercase(Locale.ROOT) ?: ""
            val wind = sev?.dbl("severity")?.let { s ->
                when (unit) {
                    "kt", "knots" -> s * 1.852
                    "mph" -> s * 1.609344
                    else -> s // km/h
                }
            }
            val name = (p.str("eventname") ?: p.str("name")?.substringAfterLast(" ") ?: continue)
                .replace(Regex("""[-\s]\d{2}$"""), "") // "POLO-26" → "POLO"
                .let { titleCase(it) }
            val (kind, level) = kindFromGdacs(sevText, wind, lat, lon)
            val urls = p.optJSONObject("url")
            val eventId = p.optString("eventid")
            val storm = Storm(
                key = "gdacs-$eventId",
                name = name,
                kind = kind,
                level = level,
                lat = lat,
                lon = lon,
                windKmh = wind,
                windIsPeak = true,
                pressureHpa = null,
                movingTowardDeg = null,
                movingKmh = null,
                alertLevel = p.str("alertlevel"),
                updated = p.str("todate"),
                sources = listOf("GDACS"),
                link = urls?.str("report") ?: "https://www.gdacs.org/report.aspx?eventtype=TC&eventid=$eventId",
                geometryUrl = urls?.str("geometry")
                    ?: "$GDACS/polygons/getgeometry?eventtype=TC&eventid=$eventId&episodeid=${p.optString("episodeid")}",
            )
            // Keep the latest episode of each event.
            val old = byEvent[eventId]
            if (old == null || (storm.updated ?: "") >= (old.updated ?: "")) byEvent[eventId] = storm
        }
        return byEvent.values.toList()
    }

    /** A storm's observed and forecast track, plus its wind areas, from GDACS. */
    suspend fun track(geometryUrl: String): StormTrack {
        val fc = JSONObject(Http.get(geometryUrl))
        val features = fc.optJSONArray("features") ?: JSONArray()
        val lines = ArrayList<List<LatLon>>()
        val points = ArrayList<TrackPoint>()
        val areas = ArrayList<List<LatLon>>()
        for (i in 0 until features.length()) {
            val f = features.optJSONObject(i) ?: continue
            val g = f.optJSONObject("geometry") ?: continue
            val c = g.optJSONArray("coordinates") ?: continue
            val props = f.optJSONObject("properties")
            when (g.optString("type")) {
                "Point" -> {
                    val lon = c.optDouble(0); val lat = c.optDouble(1)
                    if (lat.isNaN() || lon.isNaN()) continue
                    val label = props?.let { pr ->
                        listOf("trackdate", "polygondate", "date", "datetime").firstNotNullOfOrNull { pr.str(it) }
                    }
                    val forecast = props?.toString()?.contains("forecast", ignoreCase = true) == true
                    points += TrackPoint(lat, lon, label, forecast)
                }
                "LineString" -> lines += ring(c)
                "MultiLineString" -> for (k in 0 until c.length()) c.optJSONArray(k)?.let { lines += ring(it) }
                "Polygon" -> c.optJSONArray(0)?.let { areas += ring(it, maxPoints = 300) }
                "MultiPolygon" -> for (k in 0 until c.length()) {
                    c.optJSONArray(k)?.optJSONArray(0)?.let { areas += ring(it, maxPoints = 300) }
                }
            }
        }
        // GDACS often sends one position plus a wind area around each position along the track;
        // the centres of those areas mark the track.
        if (points.size <= 1) {
            for (area in areas) {
                val spanLat = area.maxOf { it.lat } - area.minOf { it.lat }
                if (area.size < 3 || spanLat > 15) continue // skip forecast cones and other big shapes
                val c = LatLon(area.sumOf { it.lat } / area.size, area.sumOf { it.lon } / area.size)
                if (points.none { kotlin.math.abs(it.lat - c.lat) < 0.25 && kotlin.math.abs(it.lon - c.lon) < 0.25 }) {
                    points += TrackPoint(c.lat, c.lon, null, forecast = false)
                }
            }
        }
        return StormTrack(lines.filter { it.size > 1 }, points, areas.filter { it.size > 2 })
    }

    private fun ring(c: JSONArray, maxPoints: Int = 2000): List<LatLon> {
        val step = maxOf(1, c.length() / maxPoints)
        return (0 until c.length() step step).mapNotNull { k ->
            val p = c.optJSONArray(k) ?: return@mapNotNull null
            LatLon(p.optDouble(1), p.optDouble(0)).takeIf { !it.lat.isNaN() && !it.lon.isNaN() }
        }
    }

    suspend fun nhc(): List<Storm> {
        val text = Http.get("https://www.nhc.noaa.gov/CurrentStorms.json")
        val active = JSONObject(text).optJSONArray("activeStorms") ?: return emptyList()
        return (0 until active.length()).mapNotNull { i ->
            val s = active.optJSONObject(i) ?: return@mapNotNull null
            val lat = s.dbl("latitudeNumeric") ?: return@mapNotNull null
            val lon = s.dbl("longitudeNumeric") ?: return@mapNotNull null
            val windKt = s.str("intensity")?.toDoubleOrNull()
            val cls = s.optString("classification").uppercase(Locale.ROOT)
            val wind = windKt?.times(1.852)
            Storm(
                key = "nhc-${s.optString("id")}",
                name = titleCase(s.optString("name")),
                kind = when (cls) {
                    "HU" -> wind?.let { "Hurricane · Category ${categoryFor(it).coerceAtLeast(1)}" } ?: "Hurricane"
                    "TS" -> "Tropical Storm"
                    "TD" -> "Tropical Depression"
                    "STS" -> "Subtropical Storm"
                    "STD" -> "Subtropical Depression"
                    "PTC" -> "Post-Tropical Cyclone"
                    "PC" -> "Potential Tropical Cyclone"
                    else -> cls.ifBlank { "Tropical Cyclone" }
                },
                lat = lat,
                lon = lon,
                windKmh = wind,
                windIsPeak = false,
                pressureHpa = s.str("pressure")?.toDoubleOrNull(),
                movingTowardDeg = s.dbl("movementDir"),
                movingKmh = s.dbl("movementSpeed")?.times(1.609344), // NHC gives mph
                alertLevel = null,
                updated = s.str("lastUpdate"),
                sources = listOf("NHC"),
                link = s.optJSONObject("publicAdvisory")?.str("url") ?: "https://www.nhc.noaa.gov/",
                geometryUrl = null,
            )
        }
    }

    /**
     * One entry per storm: NHC's current figures where it has them, with GDACS's track and
     * alert level. Strongest first.
     */
    fun merge(gdacs: List<Storm>, nhc: List<Storm>): List<Storm> {
        val left = gdacs.toMutableList()
        val merged = nhc.map { n ->
            val g = left.firstOrNull {
                it.name.equals(n.name, true) && distanceKm(LatLon(it.lat, it.lon), LatLon(n.lat, n.lon)) < 1500
            }
            if (g == null) n else {
                left.remove(g)
                n.copy(alertLevel = g.alertLevel, geometryUrl = g.geometryUrl, sources = n.sources + g.sources)
            }
        }
        return (merged + left).sortedByDescending { it.windKmh ?: 0.0 }
    }

    /**
     * GDACS describes the storm's current class in words ("Tropical Depression", "Category 3",
     * "Hurricane/Typhoon > 74 mph") but gives only its peak wind; turn that into a clean name and
     * a current level, so a storm that has weakened isn't coloured by its peak.
     */
    private fun kindFromGdacs(severityText: String, wind: Double?, lat: Double, lon: Double): Pair<String, Int?> {
        val label = severityText.substringBefore("(").trim().lowercase(Locale.ROOT)
        val regional = regionalName(lat, lon)
        val category = Regex("""category\s*(\d)""").find(label)?.groupValues?.get(1)?.toIntOrNull()
        return when {
            "depression" in label -> "Tropical Depression" to -1
            "tropical storm" in label -> "Tropical Storm" to 0
            category != null -> "$regional · Category $category" to category
            "hurricane" in label || "typhoon" in label || "cyclone" in label ->
                regional to (wind?.let { categoryFor(it) } ?: 1).coerceAtLeast(1)
            wind == null -> "Tropical Cyclone" to null
            categoryFor(wind) >= 1 -> "$regional · Category ${categoryFor(wind)}" to categoryFor(wind)
            categoryFor(wind) == 0 -> "Tropical Storm" to 0
            else -> "Tropical Depression" to -1
        }
    }

    private fun titleCase(s: String) = s.trim().lowercase(Locale.ROOT).split(" ", "-")
        .joinToString(" ") { w -> w.replaceFirstChar { it.titlecase(Locale.ROOT) } }
}
