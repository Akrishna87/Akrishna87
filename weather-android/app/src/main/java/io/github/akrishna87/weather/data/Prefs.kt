package io.github.akrishna87.weather.data

import android.content.Context
import org.json.JSONArray

enum class WindUnit(val label: String) { KMH("km/h"), MPH("mph"), MS("m/s"), KNOTS("kn") }

data class Units(val fahrenheit: Boolean = false, val wind: WindUnit = WindUnit.KMH)

/** The chosen place, recent places and units. Kept on this phone only (left out of backups). */
class Prefs(context: Context) {
    private val sp = context.getSharedPreferences("vaanilai", Context.MODE_PRIVATE)

    var place: Place?
        get() = sp.getString("place", null)?.let(Place::fromJson)
        set(value) = sp.edit().putString("place", value?.toJson()).apply()

    var recent: List<Place>
        get() = runCatching {
            val a = JSONArray(sp.getString("recent", "[]"))
            (0 until a.length()).mapNotNull { Place.fromJson(a.getString(it)) }
        }.getOrDefault(emptyList())
        set(value) = sp.edit().putString("recent", JSONArray(value.map { it.toJson() }).toString()).apply()

    var units: Units
        get() = Units(
            fahrenheit = sp.getBoolean("fahrenheit", false),
            wind = runCatching { WindUnit.valueOf(sp.getString("wind", "KMH")!!) }.getOrDefault(WindUnit.KMH),
        )
        set(value) = sp.edit().putBoolean("fahrenheit", value.fahrenheit).putString("wind", value.wind.name).apply()
}
