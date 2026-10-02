package io.github.akrishna87.signal

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import kotlin.math.roundToInt

/** One SIM's result at a measured spot. */
data class SpotResult(
    /** "SIM 1 · Jio": which SIM this was, as the user knows it. */
    val sim: String,
    val tech: Tech,
    val network: String,
    val avgDbm: Int,
    val minDbm: Int,
    val maxDbm: Int,
    val avgSinr: Int?,
    val samples: Int,
) {
    val rating: Rating get() = Ratings.strength(tech, avgDbm)

    /** Comparable across 2G/3G/4G/5G: where the average sits on that technology's scale. */
    val score: Float get() = Ratings.fraction(tech, avgDbm)
}

/** A place in the home or office the user measured, such as "Bedroom". */
data class Spot(val id: Long, val name: String, val atMillis: Long, val results: List<SpotResult>)

/** One second of measurements for one SIM, while a spot is being measured. */
data class SpotSample(val sim: String, val tech: Tech, val network: String, val dbm: Int, val sinr: Int?)

object SpotMath {
    /** Averages a spot's samples per SIM. SIMs with no usable samples are left out. */
    fun summarise(samples: List<SpotSample>): List<SpotResult> =
        samples.groupBy { it.sim }.mapNotNull { (sim, list) ->
            // Use the technology the SIM was on most of the time, so 4G and 5G numbers aren't mixed.
            val tech = list.groupingBy { it.tech }.eachCount().maxByOrNull { it.value }?.key ?: return@mapNotNull null
            val same = list.filter { it.tech == tech }
            val network = same.groupingBy { it.network }.eachCount().maxByOrNull { it.value }?.key.orEmpty()
            val sinrs = same.mapNotNull { it.sinr }
            SpotResult(
                sim = sim,
                tech = tech,
                network = network,
                avgDbm = same.map { it.dbm }.average().roundToInt(),
                minDbm = same.minOf { it.dbm },
                maxDbm = same.maxOf { it.dbm },
                avgSinr = if (sinrs.isEmpty()) null else sinrs.average().roundToInt(),
                samples = same.size,
            )
        }.sortedBy { it.sim }

    /** Spots ranked best first for one SIM; spots without a result for it are left out. */
    fun ranked(spots: List<Spot>, sim: String): List<Pair<Spot, SpotResult>> =
        spots.mapNotNull { s -> s.results.firstOrNull { it.sim == sim }?.let { s to it } }
            .sortedWith(compareByDescending<Pair<Spot, SpotResult>> { it.second.score }.thenByDescending { it.second.avgSinr ?: Int.MIN_VALUE })
}

/** Saved spots, kept on the phone only. */
class SpotStore(context: Context) {
    private val prefs = context.getSharedPreferences("spots", Context.MODE_PRIVATE)

    fun load(): List<Spot> = runCatching {
        val arr = JSONArray(prefs.getString(KEY, "[]"))
        (0 until arr.length()).map { i ->
            val o = arr.getJSONObject(i)
            val rs = o.getJSONArray("results")
            Spot(
                id = o.getLong("id"),
                name = o.getString("name"),
                atMillis = o.getLong("at"),
                results = (0 until rs.length()).map { j ->
                    val r = rs.getJSONObject(j)
                    SpotResult(
                        sim = r.getString("sim"),
                        tech = runCatching { Tech.valueOf(r.getString("tech")) }.getOrDefault(Tech.LTE),
                        network = r.optString("network"),
                        avgDbm = r.getInt("avg"),
                        minDbm = r.getInt("min"),
                        maxDbm = r.getInt("max"),
                        avgSinr = if (r.has("sinr")) r.getInt("sinr") else null,
                        samples = r.optInt("samples"),
                    )
                },
            )
        }
    }.getOrDefault(emptyList())

    fun save(spots: List<Spot>) {
        val arr = JSONArray()
        for (s in spots) {
            val rs = JSONArray()
            for (r in s.results) {
                rs.put(JSONObject().apply {
                    put("sim", r.sim)
                    put("tech", r.tech.name)
                    put("network", r.network)
                    put("avg", r.avgDbm)
                    put("min", r.minDbm)
                    put("max", r.maxDbm)
                    r.avgSinr?.let { put("sinr", it) }
                    put("samples", r.samples)
                })
            }
            arr.put(JSONObject().apply {
                put("id", s.id)
                put("name", s.name)
                put("at", s.atMillis)
                put("results", rs)
            })
        }
        prefs.edit().putString(KEY, arr.toString()).apply()
    }

    private companion object {
        const val KEY = "spots"
    }
}
