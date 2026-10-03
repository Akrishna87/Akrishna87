package io.github.akrishna87.weather.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.weather.data.Hour
import io.github.akrishna87.weather.data.Reading
import io.github.akrishna87.weather.data.Units
import kotlin.math.roundToInt

/** Whether to take an umbrella, worked out from the next [UMBRELLA_HOURS] hours of forecast. */
data class UmbrellaAdvice(val level: Level, val title: String, val detail: String, val thunder: Boolean = false) {
    enum class Level { RainingNow, Likely, Maybe, None }

    val emoji: String
        get() = when (level) {
            Level.RainingNow, Level.Likely -> if (thunder) "⛈️" else "☂️"
            Level.Maybe, Level.None -> "🌂"
        }
}

const val UMBRELLA_HOURS = 12

private val RAIN_CODES = setOf(51, 53, 55, 56, 57, 61, 63, 65, 66, 67, 80, 81, 82, 95, 96, 99)
private fun isThunder(code: Int?) = code == 95 || code == 96 || code == 99

/** A wet hour: enough rain to want an umbrella, or a good chance of it. */
private fun wet(h: Hour) = (h.precipMm ?: 0.0) >= 0.2 || (h.precipProb ?: 0.0) >= 50

/** An hour that might be wet: a fair chance of rain, or a trace of it. */
private fun maybeWet(h: Hour) = (h.precipProb ?: 0.0) >= 30 || (h.precipMm ?: 0.0) >= 0.1

/**
 * Should you take an umbrella? [hours] starts at the current hour (as in the forecast).
 * Raining now beats rain later, which beats a chance of rain.
 */
fun umbrellaAdvice(now: Reading, hours: List<Hour>, units: Units): UmbrellaAdvice {
    val window = hours.take(UMBRELLA_HOURS)
    val rainingNow = now.code in RAIN_CODES || (now.precipMm ?: 0.0) >= 0.1
    val thunderAhead = isThunder(now.code) || window.any { wet(it) && isThunder(it.code) }

    if (rainingNow) {
        // When it eases: the first dry hour after this one.
        val dry = window.drop(1).firstOrNull { !wet(it) && !maybeWet(it) }
        val what = weatherText(now.code).takeIf { now.code in RAIN_CODES } ?: "Rain"
        val detail = if (dry != null) "$what now; should ease around ${hourLabel(dry.time)}."
        else "$what now, and it looks set to continue for the next $UMBRELLA_HOURS hours."
        return UmbrellaAdvice(
            UmbrellaAdvice.Level.RainingNow,
            if (isThunder(now.code)) "Thunderstorm now — take an umbrella and stay indoors if you can"
            else "It's raining — take an umbrella",
            detail,
            thunder = thunderAhead,
        )
    }

    val firstWet = window.indexOfFirst(::wet)
    if (firstWet >= 0) {
        val h = window[firstWet]
        val total = window.sumOf { it.precipMm ?: 0.0 }
        val chance = h.precipProb?.let { " (${it.roundToInt()}% chance)" } ?: ""
        val start = if (firstWet == 0) "this hour" else "from ${hourLabel(h.time)}"
        val amount = if (total >= 0.5) ", about ${units.precip(total)} by ${hourLabel(window.last().time)}" else ""
        val what = if (thunderAhead) "Thunderstorms" else "Rain"
        return UmbrellaAdvice(
            UmbrellaAdvice.Level.Likely,
            if (thunderAhead) "Take an umbrella — and stay indoors during the storm" else "Take an umbrella",
            "$what likely $start$chance$amount.",
            thunder = thunderAhead,
        )
    }

    val firstMaybe = window.indexOfFirst(::maybeWet)
    if (firstMaybe >= 0) {
        val h = window[firstMaybe]
        val chance = h.precipProb?.let { "${it.roundToInt()}% chance of rain" } ?: "A chance of rain"
        val time = if (firstMaybe == 0) "this hour" else "around ${hourLabel(h.time)}"
        return UmbrellaAdvice(UmbrellaAdvice.Level.Maybe, "Maybe pack an umbrella", "$chance $time.")
    }

    return UmbrellaAdvice(
        UmbrellaAdvice.Level.None,
        "No umbrella needed",
        "No rain expected in the next $UMBRELLA_HOURS hours.",
    )
}

/** The umbrella reminder on the Weather screen: prominent when it's wet, a quiet line when it's dry. */
@Composable
fun UmbrellaCard(advice: UmbrellaAdvice) {
    val strong = advice.level == UmbrellaAdvice.Level.RainingNow || advice.level == UmbrellaAdvice.Level.Likely
    if (advice.level == UmbrellaAdvice.Level.None) {
        Row(
            Modifier.padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.Center,
        ) {
            Text(advice.emoji, fontSize = 16.sp)
            Spacer(Modifier.width(8.dp))
            Text(
                "${advice.detail.removeSuffix(".")} — ${advice.title.lowercase()}.",
                style = MaterialTheme.typography.bodyMedium,
                color = OnSkyDim,
            )
        }
        return
    }
    GlassCard(tint = if (strong) Color(0xFF3B82F6) else Color.White, alpha = if (strong) 0.38f else 0.13f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(advice.emoji, fontSize = if (strong) 36.sp else 28.sp)
            Spacer(Modifier.width(14.dp))
            Column {
                Text(
                    advice.title,
                    style = if (strong) MaterialTheme.typography.titleMedium else MaterialTheme.typography.titleSmall,
                    fontWeight = FontWeight.SemiBold,
                )
                Text(advice.detail, style = MaterialTheme.typography.bodyMedium, color = OnSkyDim, modifier = Modifier.padding(top = 2.dp))
            }
        }
    }
}
