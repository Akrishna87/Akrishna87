package io.github.akrishna87.weather

import android.app.Application
import android.os.SystemClock
import android.util.Log
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.akrishna87.weather.data.AirQuality
import io.github.akrishna87.weather.data.Alert
import io.github.akrishna87.weather.data.DeviceLocation
import io.github.akrishna87.weather.data.Forecast
import io.github.akrishna87.weather.data.GeoBox
import io.github.akrishna87.weather.data.Marine
import io.github.akrishna87.weather.data.MetNorway
import io.github.akrishna87.weather.data.Nws
import io.github.akrishna87.weather.data.OpenMeteo
import io.github.akrishna87.weather.data.Place
import io.github.akrishna87.weather.data.Prefs
import io.github.akrishna87.weather.data.Reading
import io.github.akrishna87.weather.data.SourceState
import io.github.akrishna87.weather.data.SourceStatus
import io.github.akrishna87.weather.data.Storm
import io.github.akrishna87.weather.data.StormFeeds
import io.github.akrishna87.weather.data.Units
import io.github.akrishna87.weather.data.WindField
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlin.math.roundToInt
import kotlin.math.sqrt

data class WeatherState(
    val loading: Boolean = false,
    val forecast: Forecast? = null,
    /** What every source says right now, keyed by source id. */
    val readings: Map<String, Reading> = emptyMap(),
    val air: AirQuality? = null,
    val marine: Marine? = null,
    val alerts: List<Alert> = emptyList(),
    val error: String? = null,
)

data class StormsState(
    val loading: Boolean = false,
    val storms: List<Storm> = emptyList(),
    val error: String? = null,
    val updatedAt: Long = 0,
)

data class WindState(
    val loading: Boolean = false,
    val field: WindField? = null,
    val error: String? = null,
)

data class SearchState(
    val query: String = "",
    val loading: Boolean = false,
    val results: List<Place> = emptyList(),
    val error: String? = null,
)

/** Where the wind map is looking. Compose state, so the map redraws as it changes; survives tab switches. */
class MapCamera {
    var lon by mutableDoubleStateOf(0.0)
    var lat by mutableDoubleStateOf(20.0)

    /** Pixels per degree; 0 until the map has been laid out once. */
    var ppd by mutableFloatStateOf(0f)

    /** The hour of the wind forecast being shown, as an index into the field's times; -1 means "now". */
    var hour by mutableIntStateOf(-1)
    var gusts by mutableStateOf(false)
    var showStorms by mutableStateOf(true)

    /** The last [MapFocus.seq] the map flew to, so each request is applied once. */
    var handledFocus = 0L
}

/** A request for the map to fly somewhere (a storm, or your place). */
data class MapFocus(val lat: Double, val lon: Double, val spanDeg: Double, val stormKey: String? = null, val seq: Long)

private const val TAG = "Vaanilai"

class WeatherViewModel(app: Application) : AndroidViewModel(app) {
    private val prefs = Prefs(app)

    private val _place = MutableStateFlow(prefs.place)
    val place: StateFlow<Place?> = _place.asStateFlow()

    private val _units = MutableStateFlow(prefs.units)
    val units: StateFlow<Units> = _units.asStateFlow()

    private val _recent = MutableStateFlow(prefs.recent)
    val recent: StateFlow<List<Place>> = _recent.asStateFlow()

    private val _weather = MutableStateFlow(WeatherState())
    val weather: StateFlow<WeatherState> = _weather.asStateFlow()

    private val _storms = MutableStateFlow(StormsState())
    val storms: StateFlow<StormsState> = _storms.asStateFlow()

    private val _wind = MutableStateFlow(WindState())
    val wind: StateFlow<WindState> = _wind.asStateFlow()

    private val _search = MutableStateFlow(SearchState())
    val search: StateFlow<SearchState> = _search.asStateFlow()

    private val _sources = MutableStateFlow<List<SourceStatus>>(emptyList())
    val sources: StateFlow<List<SourceStatus>> = _sources.asStateFlow()

    private val _focus = MutableStateFlow<MapFocus?>(null)
    val focus: StateFlow<MapFocus?> = _focus.asStateFlow()

    private val _locating = MutableStateFlow<String?>(null)

    /** "Finding you…", an error from the last attempt, or null. */
    val locating: StateFlow<String?> = _locating.asStateFlow()

    val camera = MapCamera().apply {
        prefs.place?.let { lon = it.lon; lat = it.lat }
    }

    private var weatherJob: Job? = null
    private var stormsJob: Job? = null
    private var windJob: Job? = null
    private var searchJob: Job? = null
    private var windRequest: GeoBox? = null
    private var weatherFor: Place? = null

    init {
        refreshStorms()
        if (_place.value != null) refreshWeather()
    }

    // ---- Settings ----

    fun setUnits(units: Units) {
        prefs.units = units
        _units.value = units
    }

    // ---- Place ----

    fun choosePlace(p: Place) {
        prefs.place = p
        _place.value = p
        val recent = (listOf(p) + _recent.value.filterNot { it.lat == p.lat && it.lon == p.lon }).take(8)
        prefs.recent = recent
        _recent.value = recent
        _search.value = SearchState()
        refreshWeather()
    }

    fun searchPlaces(query: String) {
        _search.update { it.copy(query = query, error = null) }
        searchJob?.cancel()
        if (query.trim().length < 2) {
            _search.update { it.copy(results = emptyList(), loading = false) }
            return
        }
        searchJob = viewModelScope.launch {
            delay(350) // wait for typing to pause
            _search.update { it.copy(loading = true) }
            try {
                val results = OpenMeteo.search(query)
                _search.update {
                    it.copy(loading = false, results = results, error = if (results.isEmpty()) "No places called “$query”" else null)
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _search.update { it.copy(loading = false, error = e.message ?: "Search failed") }
            }
        }
    }

    fun useDeviceLocation() {
        viewModelScope.launch {
            _locating.value = "Finding you…"
            try {
                choosePlace(DeviceLocation.find(getApplication()))
                _locating.value = null
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                _locating.value = e.message ?: "Couldn't find your location"
            }
        }
    }

    fun locationDenied() {
        _locating.value = "Location permission wasn't given; search for your town instead"
    }

    // ---- Weather for the chosen place ----

    fun refreshIfStale() {
        val f = _weather.value.forecast
        if (_place.value != null && !_weather.value.loading && (f == null || System.currentTimeMillis() - f.fetchedAt > 15 * 60_000)) {
            refreshWeather()
        }
        if (!_storms.value.loading && System.currentTimeMillis() - _storms.value.updatedAt > 30 * 60_000) refreshStorms()
    }

    fun refreshWeather() {
        val p = _place.value ?: return
        weatherJob?.cancel()
        // Keep showing the last figures while refreshing the same place.
        _weather.update { if (weatherFor == p) it.copy(loading = true, error = null) else WeatherState(loading = true) }
        weatherFor = p
        weatherJob = viewModelScope.launch {
            val jobs = mutableListOf<Job>()
            fun reading(r: Reading?) {
                if (r != null && r.hasData) _weather.update { it.copy(readings = it.readings + (r.sourceId to r)) }
            }

            jobs += launch {
                val f = tracked("open-meteo", "Open-Meteo forecast", "Now, hourly and 7-day forecast") { OpenMeteo.forecast(p) }
                _weather.update {
                    it.copy(forecast = f, error = if (f == null) "Couldn't load the forecast. Check your connection." else null)
                }
                reading(f?.now)
            }
            for (m in OpenMeteo.MODELS) jobs += launch {
                val id = "model-${m.id}"
                val r = tracked(id, "${m.name} model", m.agency) { OpenMeteo.modelReading(p, m) }
                if (r != null && !r.hasData) skip(id, "${m.name} model", m.agency, "No data for this place")
                reading(r)
            }
            jobs += launch {
                reading(tracked("metno", "MET Norway", "Norwegian Meteorological Institute's own forecast") { MetNorway.reading(p) })
            }
            jobs += launch {
                if (p.inUsa) {
                    reading(tracked("nws", "US National Weather Service", "Official US forecast") { Nws.reading(p) })
                } else {
                    skip("nws", "US National Weather Service", "Official US forecast", "Only covers the USA")
                }
            }
            jobs += launch {
                if (p.inUsa) {
                    val a = tracked("nws-alerts", "NWS alerts", "Official US watches and warnings") { Nws.alerts(p) }
                    _weather.update { it.copy(alerts = a.orEmpty()) }
                } else {
                    skip("nws-alerts", "NWS alerts", "Official US watches and warnings", "Only covers the USA")
                }
            }
            jobs += launch {
                val a = tracked("air", "Open-Meteo air quality", "Air quality index, pollutants, UV (CAMS)") { OpenMeteo.airQuality(p) }
                _weather.update { it.copy(air = a) }
            }
            jobs += launch {
                val m = tracked("marine", "Open-Meteo marine", "Waves and swell near the coast") { OpenMeteo.marine(p) }
                if (m == null && statusOf("marine")?.state == SourceState.Ok) {
                    skip("marine", "Open-Meteo marine", "Waves and swell near the coast", "No sea near this place")
                }
                _weather.update { it.copy(marine = m) }
            }
            jobs.forEach { it.join() }
            _weather.update { it.copy(loading = false) }
        }
    }

    // ---- Storms ----

    fun refreshStorms() {
        stormsJob?.cancel()
        _storms.update { it.copy(loading = true, error = null) }
        stormsJob = viewModelScope.launch {
            val g = async { tracked("gdacs", "GDACS", "Tropical cyclones in every ocean, with tracks") { StormFeeds.gdacs() } }
            val n = async { tracked("nhc", "NOAA National Hurricane Center", "Atlantic & East/Central Pacific storms") { StormFeeds.nhc() } }
            val gs = g.await()
            val ns = n.await()
            val merged = StormFeeds.merge(gs.orEmpty(), ns.orEmpty())
            _storms.value = StormsState(
                loading = false,
                storms = merged,
                error = if (gs == null && ns == null) "Couldn't reach the storm feeds. Check your connection." else null,
                updatedAt = System.currentTimeMillis(),
            )
            // Then fill in each storm's track.
            val withTracks = merged.map { s ->
                async {
                    val url = s.geometryUrl ?: return@async s
                    try {
                        s.copy(track = StormFeeds.track(url))
                    } catch (e: CancellationException) {
                        throw e
                    } catch (e: Exception) {
                        s
                    }
                }
            }.awaitAll()
            if (withTracks.any { it.track != null }) _storms.update { it.copy(storms = withTracks) }
        }
    }

    fun showOnMap(storm: Storm) {
        _focus.value = MapFocus(storm.lat, storm.lon, 30.0, storm.key, SystemClock.elapsedRealtime())
    }

    fun showPlaceOnMap() {
        val p = _place.value ?: return
        _focus.value = MapFocus(p.lat, p.lon, 40.0, null, SystemClock.elapsedRealtime())
    }

    // ---- Wind field for the map ----

    /**
     * Called when the map settles on a new view. Fetches the wind for it unless what's loaded
     * already covers it at a fine enough spacing. Open-Meteo counts every grid point as a call,
     * so the grid stays around 150 points and is reused while you pan within it.
     */
    fun requestWind(view: GeoBox, force: Boolean = false) {
        val have = _wind.value.field
        val wantSpacing = view.lonSpan / 9
        val fine = have != null && have.spacingDeg <= wantSpacing * 2.2 && have.spacingDeg >= wantSpacing / 3
        if (!force && have != null && fine && have.box.covers(view)) return
        if (!force && _wind.value.loading && windRequest?.covers(view) == true) return

        // Fetch a bit more than is on screen so small pans don't need a new grid.
        val padLon = view.lonSpan * 0.25
        val padLat = view.latSpan * 0.25
        val box = if (view.lonSpan + 2 * padLon >= 300) {
            GeoBox(-180.0, (view.south - padLat).coerceAtLeast(-80.0), 180.0, (view.north + padLat).coerceAtMost(80.0))
        } else {
            GeoBox(view.west - padLon, (view.south - padLat).coerceAtLeast(-80.0), view.east + padLon, (view.north + padLat).coerceAtMost(80.0))
        }
        val aspect = (box.lonSpan / box.latSpan).coerceIn(0.3, 4.0)
        val nx = sqrt(150 * aspect).roundToInt().coerceIn(5, 24)
        val ny = (150.0 / nx).roundToInt().coerceIn(5, 24)

        windRequest = box
        windJob?.cancel()
        _wind.update { it.copy(loading = true, error = null) }
        windJob = viewModelScope.launch {
            val f = tracked("wind", "Open-Meteo wind grid", "Wind map: 48 hours of wind on a ${nx}×$ny grid") {
                OpenMeteo.windField(box, nx, ny)
            }
            _wind.update {
                if (f != null) WindState(field = f) else it.copy(loading = false, error = statusOf("wind")?.detail ?: "Couldn't load the wind")
            }
        }
    }

    // ---- Source bookkeeping ----

    private fun statusOf(id: String) = _sources.value.firstOrNull { it.id == id }

    private fun setStatus(s: SourceStatus) {
        _sources.update { list ->
            val i = list.indexOfFirst { it.id == s.id }
            if (i < 0) list + s else list.toMutableList().also { it[i] = s }
        }
    }

    private fun skip(id: String, name: String, role: String, why: String) =
        setStatus(SourceStatus(id, name, role, SourceState.Skipped, why))

    /** A few words about what came back, for the log (the CI smoke test prints these). */
    private fun summary(result: Any?): String = when (result) {
        is List<*> -> " (${result.size} items${result.filterIsInstance<Storm>().joinToString(prefix = ": ") { it.name }.takeIf { it.length > 2 } ?: ""})"
        is WindField -> " (${result.nx}×${result.ny} points, ${result.times.size} hours)"
        is Forecast -> " (${result.now.tempC}°C, ${result.hours.size} hours, ${result.days.size} days)"
        is Reading -> " (${result.tempC}°C, wind ${result.windKmh} km/h)"
        else -> ""
    }

    /** Runs one source's fetch, recording how it went for the Sources screen. Returns null on failure. */
    private suspend fun <T> tracked(id: String, name: String, role: String, block: suspend () -> T): T? {
        setStatus(SourceStatus(id, name, role, SourceState.Loading))
        val start = SystemClock.elapsedRealtime()
        return try {
            block().also {
                val ms = SystemClock.elapsedRealtime() - start
                setStatus(SourceStatus(id, name, role, SourceState.Ok, millis = ms))
                Log.i(TAG, "$name: OK in $ms ms${summary(it)}")
            }
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            setStatus(SourceStatus(id, name, role, SourceState.Failed, e.message ?: e.javaClass.simpleName))
            Log.w(TAG, "$name: FAILED: ${e.message ?: e.javaClass.simpleName}")
            null
        }
    }
}
