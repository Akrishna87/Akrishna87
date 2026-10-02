package io.github.akrishna87.signal

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** One point on the live graph. */
data class Sample(val atMillis: Long, val tech: Tech?, val dbm: Int?, val sinr: Int?)

data class Permissions(val phone: Boolean, val location: Boolean, val locationOn: Boolean)

/** A spot being measured right now. */
data class Measuring(val name: String, val startedAt: Long, val samples: List<SpotSample>, val latest: List<SpotSample>) {
    fun progress(now: Long) = ((now - startedAt).toFloat() / SPOT_MILLIS).coerceIn(0f, 1f)
    fun secondsLeft(now: Long) = ((SPOT_MILLIS - (now - startedAt) + 999) / 1000).coerceAtLeast(0)
}

const val SPOT_MILLIS = 20_000L
const val HISTORY_MILLIS = 10 * 60_000L

class SignalViewModel(app: Application) : AndroidViewModel(app) {
    private val monitor = SignalMonitor(app)
    private val store = SpotStore(app)

    private val _permissions = MutableStateFlow(readPermissions())
    val permissions: StateFlow<Permissions> = _permissions.asStateFlow()

    /** Per SIM (keyed by subscription id): the last ten minutes, one sample a second. */
    private val _history = MutableStateFlow<Map<Int, List<Sample>>>(emptyMap())
    val history: StateFlow<Map<Int, List<Sample>>> = _history.asStateFlow()

    private val _spots = MutableStateFlow(store.load())
    val spots: StateFlow<List<Spot>> = _spots.asStateFlow()

    private val _measuring = MutableStateFlow<Measuring?>(null)
    val measuring: StateFlow<Measuring?> = _measuring.asStateFlow()

    /** The spot that was just measured, so the screen can point it out. */
    private val _justSaved = MutableStateFlow<Long?>(null)
    val justSaved: StateFlow<Long?> = _justSaved.asStateFlow()

    /** A one-off message for the user, such as a spot that couldn't be measured. */
    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    fun messageShown() {
        _message.value = null
    }

    /** Runs only while the app is on screen (the UI collects it with the lifecycle). */
    val snapshot: StateFlow<Snapshot?> = monitor.snapshots()
        .onEach { record(it); measure(it) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), null)

    fun refreshPermissions() {
        _permissions.value = readPermissions()
    }

    private fun readPermissions() =
        Permissions(monitor.hasPhonePermission(), monitor.hasLocationPermission(), monitor.isLocationOn())

    private fun record(s: Snapshot) {
        val cutoff = s.atMillis - HISTORY_MILLIS
        val old = _history.value
        _history.value = s.sims.associate { sim ->
            val m = sim.main
            val point = Sample(s.atMillis, m?.tech, m?.dbm, m?.sinr)
            sim.subId to (old[sim.subId].orEmpty().dropWhile { it.atMillis < cutoff } + point)
        }
    }

    fun startMeasuring(name: String) {
        _justSaved.value = null
        _measuring.value = Measuring(name.trim().ifEmpty { "Spot" }, System.currentTimeMillis(), emptyList(), emptyList())
    }

    fun cancelMeasuring() {
        _measuring.value = null
    }

    private fun measure(s: Snapshot) {
        val m = _measuring.value ?: return
        val now = s.atMillis
        val latest = s.sims.mapNotNull { sim ->
            val main = sim.main ?: return@mapNotNull null
            val dbm = main.dbm ?: return@mapNotNull null
            SpotSample(sim.label, main.tech, sim.networkLabel, dbm, main.sinr)
        }
        val samples = m.samples + latest
        if (now - m.startedAt < SPOT_MILLIS) {
            _measuring.value = m.copy(samples = samples, latest = latest)
            return
        }
        _measuring.value = null
        val results = SpotMath.summarise(samples)
        if (results.isEmpty()) {
            _message.value = "No signal was picked up at ${m.name}, so it wasn't saved."
            return
        }
        val spot = Spot(now, m.name, now, results)
        _spots.value = _spots.value + spot
        store.save(_spots.value)
        _justSaved.value = spot.id
    }

    fun deleteSpot(id: Long) {
        _spots.value = _spots.value.filterNot { it.id == id }
        store.save(_spots.value)
    }
}
