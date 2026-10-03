package io.github.akrishna87.weather.ui

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.weather.WeatherViewModel
import io.github.akrishna87.weather.data.LatLon
import io.github.akrishna87.weather.data.Storm
import io.github.akrishna87.weather.data.Units
import io.github.akrishna87.weather.data.bearingDeg
import io.github.akrishna87.weather.data.degToCompass
import io.github.akrishna87.weather.data.distanceKm
import kotlin.math.abs

@Composable
fun StormsScreen(vm: WeatherViewModel) {
    val state by vm.storms.collectAsState()
    val units by vm.units.collectAsState()
    val place by vm.place.collectAsState()
    val you = place?.let { LatLon(it.lat, it.lon) }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Named storms", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                    Text(
                        if (state.updatedAt > 0) "Hurricanes, typhoons and cyclones active now · updated ${ago(state.updatedAt)}"
                        else "Hurricanes, typhoons and cyclones active now",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                if (state.loading) CircularProgressIndicator(Modifier.size(24.dp))
                else IconButton(onClick = vm::refreshStorms) { Icon(Icons.Outlined.Refresh, "Refresh storms") }
            }
        }
        state.error?.let { err ->
            item {
                Column {
                    Text(err, color = MaterialTheme.colorScheme.error)
                    TextButton(onClick = vm::refreshStorms) { Text("Try again") }
                }
            }
        }
        if (!state.loading && state.error == null && state.storms.isEmpty()) {
            item {
                Card {
                    Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text("🌤️", style = MaterialTheme.typography.displaySmall)
                        Text("No named storms are active anywhere right now.", style = MaterialTheme.typography.titleMedium)
                        Text(
                            "Checked GDACS (every ocean) and the US National Hurricane Center. The Wind map still shows strong winds wherever they are.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(top = 8.dp),
                        )
                    }
                }
            }
        }
        items(state.storms, key = { it.key }) { s -> StormCard(s, units, you) { vm.showOnMap(s) } }
        item {
            Text(
                "Sources: GDACS (UN / European Commission) for every ocean, with tracks and wind areas; NOAA National Hurricane Center " +
                    "for the latest official figures in the Atlantic and East/Central Pacific. Categories use the Saffir–Simpson scale. " +
                    "Always follow your national weather service's warnings.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun StormCard(s: Storm, units: Units, you: LatLon?, onMap: () -> Unit) {
    val ctx = LocalContext.current
    val color = Color(stormColor(s.category))
    Card(onClick = onMap) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(Modifier.size(44.dp).background(color.copy(alpha = 0.18f), RoundedCornerShape(50)), Alignment.Center) {
                    Icon(Icons.Outlined.Cyclone, null, tint = color, modifier = Modifier.size(30.dp))
                }
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(s.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                    Text(s.kind, color = color, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                }
                s.alertLevel?.let { AlertChip(it) }
            }
            StormFacts(s, units, you)
            Row(Modifier.padding(top = 8.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                FilledTonalButton(onClick = onMap) { Text("Show on wind map") }
                s.link?.let { url -> TextButton(onClick = { openLink(ctx, url) }) { Text("Advisory") } }
            }
        }
    }
}

@Composable
private fun AlertChip(level: String) {
    val c = when (level.lowercase()) {
        "red" -> Color(0xFFEF4444)
        "orange" -> Color(0xFFF97316)
        else -> Color(0xFF22C55E)
    }
    Box(Modifier.background(c.copy(alpha = 0.2f), RoundedCornerShape(8.dp)).padding(horizontal = 8.dp, vertical = 4.dp)) {
        Text("GDACS $level", color = c, style = MaterialTheme.typography.labelMedium)
    }
}

/** The numbers for one storm, shared by the Storms list and the map's storm card. */
@Composable
fun StormFacts(s: Storm, units: Units, you: LatLon?) {
    val facts = buildList {
        add("Where" to "%.1f°%s %.1f°%s · %s".format(abs(s.lat), if (s.lat >= 0) "N" else "S", abs(s.lon), if (s.lon >= 0) "E" else "W", s.basin))
        s.windKmh?.let { add((if (s.windIsPeak) "Peak wind" else "Sustained wind") to units.wind(it)) }
        s.pressureHpa?.let { add("Central pressure" to units.pressure(it)) }
        if (s.movingTowardDeg != null && s.movingKmh != null) {
            add("Moving" to "${degToCompass(s.movingTowardDeg)} at ${units.wind(s.movingKmh)}")
        }
        if (you != null) {
            val km = distanceKm(you, LatLon(s.lat, s.lon))
            add("From your place" to "${units.distance(km)} ${degToCompass(bearingDeg(you, LatLon(s.lat, s.lon)))}")
        }
        feedTime(s.updated)?.let { add("Updated" to it) }
        s.track?.let { t ->
            val n = t.points.size
            if (n > 0) add("Track" to "$n points${if (t.areas.isNotEmpty()) " · ${t.areas.size} wind areas" else ""}")
        }
    }
    Column(Modifier.padding(top = 8.dp)) {
        facts.forEach { (k, v) ->
            Row(Modifier.padding(vertical = 2.dp)) {
                Text(k, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(120.dp))
                Text(v, style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No browser; nothing more we can do.
    }
}
