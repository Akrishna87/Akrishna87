package io.github.akrishna87.signal

import android.Manifest
import android.annotation.SuppressLint
import android.content.Context
import android.content.pm.PackageManager
import android.location.LocationManager
import android.os.Build
import android.os.SystemClock
import android.telephony.CellIdentityNr
import android.telephony.CellInfo
import android.telephony.CellInfoCdma
import android.telephony.CellInfoGsm
import android.telephony.CellInfoLte
import android.telephony.CellInfoNr
import android.telephony.CellInfoTdscdma
import android.telephony.CellInfoWcdma
import android.telephony.CellSignalStrength
import android.telephony.CellSignalStrengthCdma
import android.telephony.CellSignalStrengthGsm
import android.telephony.CellSignalStrengthLte
import android.telephony.CellSignalStrengthNr
import android.telephony.CellSignalStrengthTdscdma
import android.telephony.CellSignalStrengthWcdma
import android.telephony.PhoneStateListener
import android.telephony.ServiceState
import android.telephony.SignalStrength
import android.telephony.SubscriptionManager
import android.telephony.TelephonyCallback
import android.telephony.TelephonyDisplayInfo
import android.telephony.TelephonyManager
import androidx.annotation.RequiresApi
import androidx.core.content.ContextCompat
import androidx.core.location.LocationManagerCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.withContext
import java.util.Locale

/** What the phone reported at one moment, for every SIM. */
data class Snapshot(val sims: List<SimReading>, val noSim: Boolean, val atMillis: Long)

/**
 * Reads signal strength, cell details and network type for each active SIM, once a second.
 * Listeners keep the readings fresh between ticks; every few seconds the modem is asked to
 * re-measure the cells around it.
 */
class SignalMonitor(private val context: Context) {
    private val telephony = context.getSystemService(TelephonyManager::class.java)
    private val subscriptions = context.getSystemService(SubscriptionManager::class.java)
    private val location = context.getSystemService(LocationManager::class.java)

    fun hasPhonePermission() = granted(Manifest.permission.READ_PHONE_STATE)
    fun hasLocationPermission() = granted(Manifest.permission.ACCESS_FINE_LOCATION)
    fun isLocationOn() = location != null && LocationManagerCompat.isLocationEnabled(location)

    private fun granted(p: String) = ContextCompat.checkSelfPermission(context, p) == PackageManager.PERMISSION_GRANTED

    /** One SIM's TelephonyManager plus what its listeners last heard. */
    private class Sim(val subId: Int, val slot: Int, val operator: String, val tm: TelephonyManager) {
        @Volatile var signal: SignalStrength? = null
        @Volatile var overrideType: Int? = null
        @Volatile var displayNetworkType: Int? = null
        @Volatile var cells: List<CellInfo>? = null
        val listeners = mutableListOf<Any>()
    }

    fun snapshots(): Flow<Snapshot> = flow {
        var sims = emptyList<Sim>()
        var lastSimCheck = 0L
        var lastCellRequest = 0L
        try {
            while (true) {
                val now = SystemClock.elapsedRealtime()
                if (now - lastSimCheck >= SIM_CHECK_MS || sims.isEmpty() && now - lastSimCheck >= 1000) {
                    lastSimCheck = now
                    val fresh = loadSims()
                    if (fresh.map { it.subId to it.operator } != sims.map { it.subId to it.operator }) {
                        sims.forEach(::stopListening)
                        sims = fresh
                        sims.forEach(::startListening)
                    }
                }
                if (now - lastCellRequest >= CELL_REQUEST_MS) {
                    lastCellRequest = now
                    sims.forEach(::requestCells)
                }
                val readings = withContext(Dispatchers.IO) { sims.map { read(it, sims.size) } }
                val noSim = sims.isEmpty() && telephony?.simState != TelephonyManager.SIM_STATE_READY
                emit(Snapshot(readings, noSim, System.currentTimeMillis()))
                delay(TICK_MS)
            }
        } finally {
            sims.forEach(::stopListening)
        }
    }.flowOn(Dispatchers.Main) // PhoneStateListener needs a Looper thread

    @SuppressLint("MissingPermission")
    private fun loadSims(): List<Sim> {
        val tm = telephony ?: return emptyList()
        val subs = if (hasPhonePermission()) {
            runCatching { subscriptions?.activeSubscriptionInfoList }.getOrNull().orEmpty()
        } else emptyList()
        if (subs.isNotEmpty()) {
            return subs.sortedBy { it.simSlotIndex }.map { sub ->
                val name = sub.carrierName?.toString()?.takeIf { it.isNotBlank() }
                    ?: sub.displayName?.toString().orEmpty()
                Sim(sub.subscriptionId, sub.simSlotIndex, name, tm.createForSubscriptionId(sub.subscriptionId))
            }
        }
        // Without phone permission only the default SIM can be read.
        if (tm.simState != TelephonyManager.SIM_STATE_READY) return emptyList()
        return listOf(Sim(SubscriptionManager.getDefaultSubscriptionId(), 0, tm.networkOperatorName.orEmpty(), tm))
    }

    private fun startListening(sim: Sim) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) listenModern(sim) else listenLegacy(sim)
    }

    @RequiresApi(Build.VERSION_CODES.S)
    private fun listenModern(sim: Sim) {
        val executor = context.mainExecutor
        // Registered one at a time: each listener needs a different permission, and a missing
        // one would otherwise stop all of them.
        val strength = object : TelephonyCallback(), TelephonyCallback.SignalStrengthsListener {
            override fun onSignalStrengthsChanged(s: SignalStrength) { sim.signal = s }
        }
        val display = object : TelephonyCallback(), TelephonyCallback.DisplayInfoListener {
            override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                sim.overrideType = info.overrideNetworkType
                sim.displayNetworkType = info.networkType
            }
        }
        for (cb in listOf(strength, display)) {
            runCatching { sim.tm.registerTelephonyCallback(executor, cb) }.onSuccess { sim.listeners += cb }
        }
    }

    @Suppress("DEPRECATION")
    private fun listenLegacy(sim: Sim) {
        val strength = object : PhoneStateListener() {
            override fun onSignalStrengthsChanged(s: SignalStrength) { sim.signal = s }
        }
        runCatching { sim.tm.listen(strength, PhoneStateListener.LISTEN_SIGNAL_STRENGTHS) }
            .onSuccess { sim.listeners += strength }
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            val display = object : PhoneStateListener() {
                override fun onDisplayInfoChanged(info: TelephonyDisplayInfo) {
                    sim.overrideType = info.overrideNetworkType
                    sim.displayNetworkType = info.networkType
                }
            }
            runCatching { sim.tm.listen(display, PhoneStateListener.LISTEN_DISPLAY_INFO_CHANGED) }
                .onSuccess { sim.listeners += display }
        }
    }

    @Suppress("DEPRECATION")
    private fun stopListening(sim: Sim) {
        for (l in sim.listeners) {
            runCatching {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S && l is TelephonyCallback) {
                    sim.tm.unregisterTelephonyCallback(l)
                } else if (l is PhoneStateListener) {
                    sim.tm.listen(l, PhoneStateListener.LISTEN_NONE)
                }
            }
        }
        sim.listeners.clear()
    }

    /** Asks the modem to measure the cells around it again (Android 10+), for this SIM only. */
    @SuppressLint("MissingPermission")
    private fun requestCells(sim: Sim) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q || !hasLocationPermission()) return
        runCatching {
            sim.tm.requestCellInfoUpdate(context.mainExecutor, object : TelephonyManager.CellInfoCallback() {
                override fun onCellInfo(cellInfo: MutableList<CellInfo>) { sim.cells = cellInfo }
            })
        }
    }

    @SuppressLint("MissingPermission")
    private fun read(sim: Sim, simCount: Int): SimReading {
        val tm = sim.tm
        val signal = sim.signal ?: if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            runCatching { tm.signalStrength }.getOrNull()
        } else null
        val fromSignal = signal?.let { strengths(it) }.orEmpty()

        val plmn = tm.networkOperator.orEmpty()
        val cells: List<CellReading> = if (hasLocationPermission()) {
            // getAllCellInfo() mixes every SIM's cells together; requestCellInfoUpdate() is per SIM.
            val perSim = sim.cells
            val raw = perSim ?: runCatching { tm.allCellInfo }.getOrNull().orEmpty()
            val all = raw.mapNotNull { toReading(it) }
            if (perSim != null || simCount == 1) all
            else all.filter { it.registered && (it.plmn == null || it.plmn == plmn) }
        } else emptyList()

        val registered = cells.filter { it.registered }
        val nrRegistered = registered.firstOrNull { it.tech == Tech.NR }
        val lteRegistered = registered.firstOrNull { it.tech == Tech.LTE }
        val signalLte = fromSignal.firstOrNull { it.tech == Tech.LTE }
        val signalNr = fromSignal.firstOrNull { it.tech == Tech.NR }

        val main: CellReading? = when {
            lteRegistered != null -> lteRegistered.fillFrom(signalLte)
            nrRegistered != null -> nrRegistered.fillFrom(signalNr)
            registered.isNotEmpty() -> registered.first().let { r -> r.fillFrom(fromSignal.firstOrNull { it.tech == r.tech }) }
            signalLte != null -> signalLte.copy(registered = true)
            else -> fromSignal.firstOrNull()?.copy(registered = true)
        }
        // 5G on top of 4G (NSA): the 5G layer shows up in the signal strengths, sometimes as an
        // unregistered 5G cell too.
        val nr: CellReading? = if (main?.tech == Tech.LTE) {
            val nrCell = cells.firstOrNull { it.tech == Tech.NR && !it.registered && it.dbm != null }
            when {
                signalNr != null -> (nrCell ?: signalNr).fillFrom(signalNr)
                else -> nrCell
            }
        } else null

        val shown = setOfNotNull(main, nr)
        val neighbours = cells
            .filter { !it.registered && it.dbm != null && it !in shown && !(nr != null && it.tech == Tech.NR && it.pci == nr.pci) }
            .sortedByDescending { it.dbm }
            .take(MAX_NEIGHBOURS)

        val service = serviceOf(tm, main)
        return SimReading(
            subId = sim.subId,
            slot = sim.slot,
            operator = sim.operator.ifBlank { tm.networkOperatorName.orEmpty() },
            service = service,
            networkLabel = networkLabel(sim, main, nr, service),
            main = main,
            nr = nr,
            neighbours = neighbours,
            systemLevel = signal?.level,
        )
    }

    /** Fills numbers the cell list left out with the ones from the signal-strength report. */
    private fun CellReading.fillFrom(s: CellReading?): CellReading = if (s == null) this else copy(
        dbm = dbm ?: s.dbm,
        rsrq = rsrq ?: s.rsrq,
        sinr = sinr ?: s.sinr,
    )

    @SuppressLint("MissingPermission")
    private fun serviceOf(tm: TelephonyManager, main: CellReading?): Service {
        val state = if (hasPhonePermission()) runCatching { tm.serviceState?.state }.getOrNull() else null
        return when (state) {
            ServiceState.STATE_IN_SERVICE -> Service.IN_SERVICE
            ServiceState.STATE_OUT_OF_SERVICE -> if (main?.dbm != null) Service.IN_SERVICE else Service.NO_SERVICE
            ServiceState.STATE_EMERGENCY_ONLY -> Service.EMERGENCY_ONLY
            ServiceState.STATE_POWER_OFF -> Service.RADIO_OFF
            else -> if (main?.dbm != null) Service.IN_SERVICE else Service.UNKNOWN
        }
    }

    @SuppressLint("MissingPermission")
    private fun networkLabel(sim: Sim, main: CellReading?, nr: CellReading?, service: Service): String {
        if (service == Service.NO_SERVICE || service == Service.RADIO_OFF) return service.label
        when (sim.overrideType) {
            OVERRIDE_NR_NSA -> return "5G (on 4G)"
            OVERRIDE_NR_NSA_MMWAVE, OVERRIDE_NR_ADVANCED -> return "5G+ (on 4G)"
            OVERRIDE_LTE_CA, OVERRIDE_LTE_ADVANCED_PRO -> return "4G+"
        }
        var type = sim.displayNetworkType ?: TelephonyManager.NETWORK_TYPE_UNKNOWN
        if (type == TelephonyManager.NETWORK_TYPE_UNKNOWN && hasPhonePermission()) {
            type = runCatching { sim.tm.dataNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
            if (type == TelephonyManager.NETWORK_TYPE_UNKNOWN) {
                type = runCatching { sim.tm.voiceNetworkType }.getOrDefault(TelephonyManager.NETWORK_TYPE_UNKNOWN)
            }
        }
        var label = networkTypeLabel(type)
        // The reported type can lag behind, or describe the other SIM's data connection; a 4G or
        // 5G cell the phone is registered on is the better witness.
        if (label != null && label != "Wi-Fi calling" && main != null && (main.tech == Tech.LTE || main.tech == Tech.NR) &&
            !label.startsWith(main.tech.label)
        ) label = null
        return when {
            label == "4G" && nr != null -> "5G (on 4G)"
            label != null -> label
            main?.tech == Tech.LTE && nr != null -> "5G (on 4G)"
            main != null -> main.tech.label
            else -> service.label.ifBlank { "Unknown" }
        }
    }

    private companion object {
        const val TICK_MS = 1000L
        const val SIM_CHECK_MS = 5000L
        const val CELL_REQUEST_MS = 3000L
        const val MAX_NEIGHBOURS = 8

        // TelephonyDisplayInfo override types (constants exist from Android 11).
        const val OVERRIDE_LTE_CA = 1
        const val OVERRIDE_LTE_ADVANCED_PRO = 2
        const val OVERRIDE_NR_NSA = 3
        const val OVERRIDE_NR_NSA_MMWAVE = 4
        const val OVERRIDE_NR_ADVANCED = 5

        fun networkTypeLabel(type: Int): String? = when (type) {
            TelephonyManager.NETWORK_TYPE_NR -> "5G"
            TelephonyManager.NETWORK_TYPE_LTE -> "4G"
            TelephonyManager.NETWORK_TYPE_IWLAN -> "Wi-Fi calling"
            TelephonyManager.NETWORK_TYPE_HSPAP, TelephonyManager.NETWORK_TYPE_HSPA,
            TelephonyManager.NETWORK_TYPE_HSDPA, TelephonyManager.NETWORK_TYPE_HSUPA -> "3G (H+)"
            TelephonyManager.NETWORK_TYPE_UMTS, TelephonyManager.NETWORK_TYPE_EVDO_0,
            TelephonyManager.NETWORK_TYPE_EVDO_A, TelephonyManager.NETWORK_TYPE_EVDO_B,
            TelephonyManager.NETWORK_TYPE_EHRPD, TelephonyManager.NETWORK_TYPE_TD_SCDMA -> "3G"
            TelephonyManager.NETWORK_TYPE_EDGE -> "2G (EDGE)"
            TelephonyManager.NETWORK_TYPE_GPRS, TelephonyManager.NETWORK_TYPE_GSM,
            TelephonyManager.NETWORK_TYPE_CDMA, TelephonyManager.NETWORK_TYPE_1xRTT,
            TelephonyManager.NETWORK_TYPE_IDEN -> "2G"
            else -> null
        }

        /** The per-technology strengths in a signal-strength report (Android 10+). */
        fun strengths(s: SignalStrength): List<CellReading> {
            if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
            return s.cellSignalStrengths.mapNotNull { strength(it, registered = false) }
        }

        fun strength(s: CellSignalStrength, registered: Boolean): CellReading? {
            val tenths = Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
            val r = when {
                s is CellSignalStrengthLte -> CellReading(
                    Tech.LTE, registered, Clean.rsrp(s.rsrp), Clean.rsrq(s.rsrq), Clean.lteSinr(s.rssnr, tenths),
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && s is CellSignalStrengthNr -> CellReading(
                    Tech.NR, registered,
                    Clean.rsrp(s.ssRsrp) ?: Clean.rsrp(s.csiRsrp),
                    Clean.nrRsrq(s.ssRsrq) ?: Clean.nrRsrq(s.csiRsrq),
                    Clean.nrSinr(s.ssSinr) ?: Clean.nrSinr(s.csiSinr),
                )
                s is CellSignalStrengthWcdma -> CellReading(
                    Tech.WCDMA, registered, Clean.rscp(s.dbm), null,
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) Clean.ecno(s.ecNo) else null,
                )
                s is CellSignalStrengthGsm -> CellReading(Tech.GSM, registered, Clean.rssi(s.dbm), null, null)
                s is CellSignalStrengthCdma -> CellReading(
                    Tech.CDMA, registered, Clean.rssi(s.cdmaDbm), null,
                    Clean.inRange(s.cdmaEcio, -160..0)?.let { it / 10 },
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && s is CellSignalStrengthTdscdma ->
                    CellReading(Tech.TDSCDMA, registered, Clean.rscp(s.rscp), null, null)
                else -> null
            }
            return r
        }

        @Suppress("DEPRECATION")
        fun toReading(c: CellInfo): CellReading? {
            val base = when {
                c is CellInfoLte -> strength(c.cellSignalStrength, c.isRegistered)?.let { r ->
                    val id = c.cellIdentity
                    val earfcn = Clean.inRange(id.earfcn, 0..262143)
                    val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.toList() else emptyList()
                    r.copy(
                        pci = Clean.inRange(id.pci, 0..503),
                        cellId = Clean.positive(id.ci)?.toLong(),
                        areaCode = Clean.inRange(id.tac, 0..65535),
                        channel = earfcn,
                        bands = bands.ifEmpty { listOfNotNull(earfcn?.let(Bands::lteBand)) }.map { "B$it" },
                        frequencyMhz = earfcn?.let(Bands::lteFrequency),
                        plmn = plmnOf(id.mcc, id.mnc, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString + id.mncString else null),
                    )
                }
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && c is CellInfoNr ->
                    strength(c.cellSignalStrength, c.isRegistered)?.let { r ->
                        val id = c.cellIdentity as CellIdentityNr
                        val arfcn = Clean.inRange(id.nrarfcn, 0..3279165)
                        val mhz = arfcn?.let(Bands::nrFrequency)
                        val bands = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) id.bands.toList() else emptyList()
                        r.copy(
                            pci = Clean.inRange(id.pci, 0..1007),
                            cellId = Clean.positive(id.nci),
                            areaCode = Clean.inRange(id.tac, 0..16777215),
                            channel = arfcn,
                            bands = bands.ifEmpty { listOfNotNull(mhz?.let(Bands::nrBand)) }.map { "n$it" },
                            frequencyMhz = mhz,
                            plmn = joinPlmn(id.mccString, id.mncString),
                        )
                    }
                c is CellInfoWcdma -> strength(c.cellSignalStrength, c.isRegistered)?.let { r ->
                    val id = c.cellIdentity
                    val uarfcn = Clean.inRange(id.uarfcn, 0..16383)
                    r.copy(
                        pci = Clean.inRange(id.psc, 0..511),
                        cellId = Clean.inRange(id.cid, 0..268435455)?.toLong(),
                        areaCode = Clean.inRange(id.lac, 0..65535),
                        channel = uarfcn,
                        bands = listOfNotNull(uarfcn?.let(Bands::umtsBand)).map { "B$it" },
                        frequencyMhz = uarfcn?.let(Bands::umtsFrequency),
                        plmn = plmnOf(id.mcc, id.mnc, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString + id.mncString else null),
                    )
                }
                c is CellInfoGsm -> strength(c.cellSignalStrength, c.isRegistered)?.let { r ->
                    val id = c.cellIdentity
                    val arfcn = Clean.inRange(id.arfcn, 0..1023)
                    r.copy(
                        pci = Clean.inRange(id.bsic, 0..63),
                        cellId = Clean.inRange(id.cid, 0..65535)?.toLong(),
                        areaCode = Clean.inRange(id.lac, 0..65535),
                        channel = arfcn,
                        bands = listOfNotNull(arfcn?.let(Bands::gsmBand)),
                        frequencyMhz = arfcn?.let(Bands::gsmFrequency),
                        plmn = plmnOf(id.mcc, id.mnc, if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) id.mccString + id.mncString else null),
                    )
                }
                c is CellInfoCdma -> strength(c.cellSignalStrength, c.isRegistered)?.copy(
                    cellId = Clean.positive(c.cellIdentity.basestationId)?.toLong(),
                )
                Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && c is CellInfoTdscdma ->
                    strength(c.cellSignalStrength, c.isRegistered)?.let { r ->
                        val id = c.cellIdentity
                        r.copy(
                            cellId = Clean.inRange(id.cid, 0..268435455)?.toLong(),
                            areaCode = Clean.inRange(id.lac, 0..65535),
                            channel = Clean.inRange(id.uarfcn, 0..16383),
                            plmn = joinPlmn(id.mccString, id.mncString),
                        )
                    }
                else -> null
            }
            return base
        }

        private fun joinPlmn(mcc: String?, mnc: String?) =
            if (!mcc.isNullOrEmpty() && !mnc.isNullOrEmpty()) mcc + mnc else null

        /** "nullnull" from the String getters and Int.MAX_VALUE from the old ones both mean unknown. */
        private fun plmnOf(mcc: Int, mnc: Int, strings: String?): String? {
            if (strings != null && strings.all { it.isDigit() } && strings.length in 5..6) return strings
            if (mcc !in 1..999 || mnc !in 0..999) return null
            return String.format(Locale.US, "%03d%02d", mcc, mnc)
        }
    }
}
