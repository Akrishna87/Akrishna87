package io.github.akrishna87.screentime.ui

/** "3 h 12 min", "45 min" or "<1 min". */
fun formatDuration(ms: Long): String {
    if (ms in 1 until 60_000) return "<1 min"
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "$m min"
        m == 0L -> "$h h"
        else -> "$h h $m min"
    }
}

/** Short form for chart scales: "2h", "45m", "1h 30m". */
fun formatShort(ms: Long): String {
    val minutes = ms / 60_000
    val h = minutes / 60
    val m = minutes % 60
    return when {
        h == 0L -> "${m}m"
        m == 0L -> "${h}h"
        else -> "${h}h ${m}m"
    }
}

fun opensText(opens: Int): String? = when (opens) {
    0 -> null
    1 -> "Opened once"
    else -> "Opened $opens times"
}

/** A round number just above the tallest bar, so the chart's top line reads "2h" rather than "1h 47m". */
fun chartTop(max: Long): Long {
    if (max <= 0) return 0
    val minute = 60_000L
    val hour = 60 * minute
    val steps = listOf(5 * minute, 10 * minute, 15 * minute, 30 * minute, hour, 2 * hour, 3 * hour, 4 * hour,
        6 * hour, 8 * hour, 10 * hour, 12 * hour, 16 * hour, 20 * hour, 24 * hour)
    return steps.firstOrNull { it >= max } ?: ((max + hour - 1) / hour * hour)
}
