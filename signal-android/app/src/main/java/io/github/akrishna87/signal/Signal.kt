package io.github.akrishna87.signal

import kotlin.math.roundToInt

/** The radio technology a reading comes from. */
enum class Tech(val label: String, val strengthName: String, val qualityName: String?) {
    NR("5G", "SS-RSRP", "SS-SINR"),
    LTE("4G", "RSRP", "SINR"),
    WCDMA("3G", "RSCP", "Ec/No"),
    GSM("2G", "RSSI", null),
    CDMA("3G (CDMA)", "RSSI", "Ec/Io"),
    TDSCDMA("3G (TD-SCDMA)", "RSCP", null),
}

/** How good a number is, in plain words. [score] runs 0 (very poor) to 4 (excellent). */
enum class Rating(val label: String, val score: Int) {
    EXCELLENT("Excellent", 4),
    GOOD("Good", 3),
    FAIR("Fair", 2),
    POOR("Poor", 1),
    VERY_POOR("Very poor", 0),
}

/**
 * One cell (tower sector) the phone can hear. Any number may be null when the phone doesn't
 * report it; many phones leave some of them out.
 */
data class CellReading(
    val tech: Tech,
    val registered: Boolean,
    /** Strength in dBm: RSRP for 4G/5G, RSCP for 3G, RSSI for 2G. */
    val dbm: Int?,
    /** RSRQ (4G/5G) in dB. */
    val rsrq: Int?,
    /** SINR (4G/5G) or Ec/No (3G) in dB. */
    val sinr: Int?,
    /** Physical cell ID (4G/5G), scrambling code (3G) or BSIC (2G). */
    val pci: Int? = null,
    /** Full cell identity (ECI for 4G, NCI for 5G, CID for 2G/3G). */
    val cellId: Long? = null,
    /** Tracking (4G/5G) or location (2G/3G) area code. */
    val areaCode: Int? = null,
    /** EARFCN / NR-ARFCN / UARFCN / ARFCN. */
    val channel: Int? = null,
    /** Band names such as "B3" or "n78", when known. */
    val bands: List<String> = emptyList(),
    /** Downlink centre frequency in MHz, when it can be worked out from [channel]. */
    val frequencyMhz: Double? = null,
    /** Mobile country + network code, e.g. "40445". */
    val plmn: String? = null,
) {
    val rating: Rating? get() = dbm?.let { Ratings.strength(tech, it) }
    val qualityRating: Rating? get() = sinr?.let { Ratings.quality(tech, it) }

    /** eNB / gNB number: the tower site, worked out from the cell identity. */
    val siteId: Long?
        get() = when (tech) {
            Tech.LTE -> cellId?.let { it shr 8 }
            Tech.NR -> cellId?.let { it shr 12 } // a 24-bit gNB ID is the most common split
            else -> null
        }
}

enum class Service(val label: String) {
    IN_SERVICE("Connected"),
    NO_SERVICE("No service"),
    EMERGENCY_ONLY("Emergency calls only"),
    RADIO_OFF("Radio off (airplane mode?)"),
    UNKNOWN(""),
}

/** Everything known about one SIM at one moment. */
data class SimReading(
    val subId: Int,
    val slot: Int,
    val operator: String,
    val service: Service,
    /** What the status bar would show: "5G", "5G (on 4G)", "4G+", "4G", "3G", "2G" … */
    val networkLabel: String,
    /** The cell the phone is using; for 5G on 4G (NSA) this is the 4G anchor. */
    val main: CellReading?,
    /** The 5G layer of a 5G-on-4G (NSA) connection, when the phone reports it. */
    val nr: CellReading?,
    /** Other cells the phone can hear. */
    val neighbours: List<CellReading>,
    /** Signal bars (0-4) the system itself would show. */
    val systemLevel: Int?,
) {
    val label: String get() = "SIM ${slot + 1}" + if (operator.isNotBlank()) " · $operator" else ""
}

/** Thresholds commonly used by operators and drive-test tools. */
object Ratings {
    fun strength(tech: Tech, dbm: Int): Rating = when (tech) {
        Tech.LTE, Tech.NR -> grade(dbm, -80, -90, -100, -110)
        Tech.WCDMA, Tech.TDSCDMA -> grade(dbm, -75, -85, -95, -105)
        Tech.GSM, Tech.CDMA -> grade(dbm, -70, -80, -90, -100)
    }

    /** Quality: SINR for 4G/5G, Ec/No for 3G. */
    fun quality(tech: Tech, db: Int): Rating = when (tech) {
        Tech.LTE, Tech.NR -> grade(db, 20, 13, 5, 0)
        else -> grade(db, -6, -10, -14, -18)
    }

    private fun grade(v: Int, excellent: Int, good: Int, fair: Int, poor: Int) = when {
        v >= excellent -> Rating.EXCELLENT
        v >= good -> Rating.GOOD
        v >= fair -> Rating.FAIR
        v >= poor -> Rating.POOR
        else -> Rating.VERY_POOR
    }

    /** The span the strength gauge and graph cover, per technology. */
    fun gaugeRange(tech: Tech): IntRange = when (tech) {
        Tech.LTE, Tech.NR -> -130..-60
        Tech.WCDMA, Tech.TDSCDMA -> -120..-50
        Tech.GSM, Tech.CDMA -> -113..-51
    }

    /** Where [dbm] sits on the gauge, 0..1. */
    fun fraction(tech: Tech, dbm: Int): Float {
        val r = gaugeRange(tech)
        return ((dbm - r.first).toFloat() / (r.last - r.first)).coerceIn(0f, 1f)
    }
}

/**
 * Phones report "unknown" in different ways: Int.MAX_VALUE, Int.MIN_VALUE, or a number outside
 * what the standard allows. These turn all of them into null.
 */
object Clean {
    fun inRange(v: Int, range: IntRange): Int? = if (v in range) v else null

    fun rsrp(v: Int) = inRange(v, -140..-44)
    fun rsrq(v: Int) = inRange(v, -34..3)
    fun nrRsrq(v: Int) = inRange(v, -43..20)
    fun rssi(v: Int) = inRange(v, -113..-51)
    fun rscp(v: Int) = inRange(v, -120..-24)
    fun ecno(v: Int) = inRange(v, -24..1)
    fun nrSinr(v: Int) = inRange(v, -23..40)

    /**
     * LTE RSSNR is in dB on Android 10+, but in tenths of a dB on older versions (and on some
     * phones that never caught up).
     */
    fun lteSinr(v: Int, tenths: Boolean): Int? {
        if (v == Int.MAX_VALUE || v == Int.MIN_VALUE) return null
        if (tenths || v !in -20..30) {
            return if (v in -200..300) (v / 10.0).roundToInt() else null
        }
        return v
    }

    fun positive(v: Int) = if (v in 0 until Int.MAX_VALUE) v else null
    fun positive(v: Long) = if (v in 0 until Long.MAX_VALUE) v else null
}

/** Turns radio channel numbers into bands and frequencies (3GPP TS 36.101, 38.104, 25.101). */
object Bands {
    /** band, lowest downlink frequency (MHz), first EARFCN, last EARFCN */
    private class Lte(val band: Int, val fDlLow: Double, val first: Int, val last: Int)

    private val lte = listOf(
        Lte(1, 2110.0, 0, 599), Lte(2, 1930.0, 600, 1199), Lte(3, 1805.0, 1200, 1949),
        Lte(4, 2110.0, 1950, 2399), Lte(5, 869.0, 2400, 2649), Lte(7, 2620.0, 2750, 3449),
        Lte(8, 925.0, 3450, 3799), Lte(12, 729.0, 5010, 5179), Lte(13, 746.0, 5180, 5279),
        Lte(14, 758.0, 5280, 5379), Lte(17, 734.0, 5730, 5849), Lte(18, 860.0, 5850, 5999),
        Lte(19, 875.0, 6000, 6149), Lte(20, 791.0, 6150, 6449), Lte(25, 1930.0, 8040, 8689),
        Lte(26, 859.0, 8690, 9039), Lte(28, 758.0, 9210, 9659), Lte(29, 717.0, 9660, 9769),
        Lte(30, 2350.0, 9770, 9869), Lte(32, 1452.0, 9920, 10359), Lte(34, 2010.0, 36200, 36349),
        Lte(38, 2570.0, 37750, 38249), Lte(39, 1880.0, 38250, 38649), Lte(40, 2300.0, 38650, 39649),
        Lte(41, 2496.0, 39650, 41589), Lte(42, 3400.0, 41590, 43589), Lte(43, 3600.0, 43590, 45589),
        Lte(46, 5150.0, 46790, 54539), Lte(48, 3550.0, 55240, 56739), Lte(66, 2110.0, 66436, 67335),
        Lte(71, 617.0, 68586, 68935),
    )

    fun lteBand(earfcn: Int): Int? = lte.firstOrNull { earfcn in it.first..it.last }?.band

    fun lteFrequency(earfcn: Int): Double? =
        lte.firstOrNull { earfcn in it.first..it.last }?.let { it.fDlLow + 0.1 * (earfcn - it.first) }

    /** NR-ARFCN to MHz (TS 38.104 §5.4.2.1). */
    fun nrFrequency(arfcn: Int): Double? = when (arfcn) {
        in 0..599_999 -> 0.005 * arfcn
        in 600_000..2_016_666 -> 3000.0 + 0.015 * (arfcn - 600_000)
        in 2_016_667..3_279_165 -> 24250.08 + 0.06 * (arfcn - 2_016_667)
        else -> null
    }

    /** The most likely 5G band for a downlink frequency, used when the phone doesn't say. */
    fun nrBand(mhz: Double): Int? = when (mhz) {
        in 617.0..652.0 -> 71
        in 729.0..758.0 -> 12
        in 758.0..803.0 -> 28
        in 791.0..821.0 -> 20
        in 869.0..894.0 -> 5
        in 925.0..960.0 -> 8
        in 1805.0..1880.0 -> 3
        in 2110.0..2200.0 -> 1
        in 2300.0..2400.0 -> 40
        in 2496.0..2690.0 -> 41
        in 3300.0..3800.0 -> 78
        in 3800.0..4200.0 -> 77
        in 4400.0..5000.0 -> 79
        in 24250.0..27500.0 -> 258
        in 26500.0..29500.0 -> 257
        else -> null
    }

    /** UARFCN (downlink) to MHz. */
    fun umtsFrequency(uarfcn: Int): Double? = if (uarfcn in 1..16383) uarfcn / 5.0 else null

    fun umtsBand(uarfcn: Int): Int? = when (uarfcn) {
        in 10562..10838 -> 1
        in 9662..9938 -> 2
        in 1162..1513 -> 3
        in 1537..1738 -> 4
        in 4357..4458 -> 5
        in 2937..3088 -> 8
        else -> null
    }

    fun gsmBand(arfcn: Int): String? = when (arfcn) {
        in 0..124, in 975..1023 -> "900"
        in 128..251 -> "850"
        in 512..885 -> "1800"
        else -> null
    }

    fun gsmFrequency(arfcn: Int): Double? = when (arfcn) {
        in 0..124 -> 935.0 + 0.2 * arfcn
        in 975..1023 -> 935.0 + 0.2 * (arfcn - 1024)
        in 128..251 -> 869.2 + 0.2 * (arfcn - 128)
        in 512..885 -> 1805.2 + 0.2 * (arfcn - 512)
        else -> null
    }

    /** Short words for a band, e.g. "B3 · 1800 MHz". */
    fun describe(bands: List<String>, mhz: Double?): String? {
        val parts = buildList {
            if (bands.isNotEmpty()) add(bands.joinToString("+"))
            if (mhz != null) add(formatMhz(mhz))
        }
        return parts.takeIf { it.isNotEmpty() }?.joinToString(" · ")
    }

    fun formatMhz(mhz: Double): String =
        if (mhz >= 10_000) String.format(java.util.Locale.US, "%.2f GHz", mhz / 1000)
        else if (mhz % 1.0 == 0.0) "${mhz.toInt()} MHz"
        else String.format(java.util.Locale.US, "%.1f MHz", mhz)
}
