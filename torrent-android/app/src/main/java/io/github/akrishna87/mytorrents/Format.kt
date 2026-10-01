package io.github.akrishna87.mytorrents

import java.util.Locale

/** "1.4 GB", "820 KB", "12 B". */
fun formatBytes(bytes: Long): String {
    if (bytes < 1024) return "$bytes B"
    val units = listOf("KB", "MB", "GB", "TB")
    var value = bytes / 1024.0
    var unit = 0
    while (value >= 1024 && unit < units.lastIndex) {
        value /= 1024
        unit++
    }
    return if (value >= 100) String.format(Locale.US, "%.0f %s", value, units[unit])
    else String.format(Locale.US, "%.1f %s", value, units[unit])
}

/** "2.3 MB/s". */
fun formatSpeed(bytesPerSecond: Int): String = formatBytes(bytesPerSecond.toLong()) + "/s"

/** "45 s", "12 min", "3 h 5 min", "2 days". */
fun formatDuration(seconds: Long): String = when {
    seconds < 60 -> "$seconds s"
    seconds < 3600 -> "${seconds / 60} min"
    seconds < 86400 -> "${seconds / 3600} h ${seconds % 3600 / 60} min"
    else -> "${seconds / 86400} days"
}

fun formatPercent(progress: Float): String = "${(progress * 100).toInt()}%"
