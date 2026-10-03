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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.IntrinsicSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.OpenInNew
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.Compress
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.Map
import androidx.compose.material.icons.outlined.NearMe
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
    LaunchedEffect(Unit) { vm.refreshStormsIfNeeded() }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    Box {
        LazyColumn(
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text("Named storms", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                        Text(
                            (if (state.storms.isEmpty()) "Hurricanes, typhoons and cyclones" else "${state.storms.size} active now") +
                                (if (state.updatedAt > 0) " · updated ${ago(state.updatedAt)}" else ""),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    if (state.loading) CircularProgressIndicator(Modifier.size(24.dp))
                    else IconButton(onClick = vm::refreshStorms) { Icon(Icons.Outlined.Refresh, "Refresh storms") }
                }
            }
            state.error?.let { err ->
                item {
                    Surface(shape = RoundedCornerShape(20.dp), color = MaterialTheme.colorScheme.errorContainer) {
                        Row(Modifier.padding(start = 16.dp, end = 8.dp, top = 8.dp, bottom = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text(err, color = MaterialTheme.colorScheme.onErrorContainer, modifier = Modifier.weight(1f))
                            TextButton(onClick = vm::refreshStorms) { Text("Try again") }
                        }
                    }
                }
            }
            if (!state.loading && state.error == null && state.storms.isEmpty()) {
                item {
                    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
                        Column(Modifier.fillMaxWidth().padding(28.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                            Text("🌤️", fontSize = 48.sp)
                            Text("No named storms right now", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(top = 8.dp))
                            Text(
                                "Checked GDACS (every ocean) and the US National Hurricane Center. The wind map still shows strong winds wherever they are.",
                                style = MaterialTheme.typography.bodyMedium,
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
                    modifier = Modifier.padding(horizontal = 4.dp),
                )
            }
        }
        StatusBarScrim(MaterialTheme.colorScheme.background.copy(alpha = 0.92f))
    }
}

@Composable
private fun StormCard(s: Storm, units: Units, you: LatLon?, onMap: () -> Unit) {
    val ctx = LocalContext.current
    val color = Color(stormColor(s.category))
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer, onClick = onMap) {
        Row(Modifier.height(IntrinsicSize.Min)) {
            // A strip down the side in the storm's strength colour.
            Box(Modifier.width(6.dp).fillMaxHeight().background(color))
            Column(Modifier.padding(16.dp)) {
                StormHeader(s)
                StormMetrics(s, units, you, Modifier.padding(top = 14.dp))
                StormFacts(s, units)
                Row(Modifier.padding(top = 10.dp), horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    FilledTonalButton(onClick = onMap) {
                        Icon(Icons.Outlined.Map, null, Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("Show on wind map")
                    }
                    s.link?.let { url ->
                        TextButton(onClick = { openLink(ctx, url) }) {
                            Text("Advisory")
                            Spacer(Modifier.width(4.dp))
                            Icon(Icons.AutoMirrored.Outlined.OpenInNew, null, Modifier.size(16.dp))
                        }
                    }
                }
            }
        }
    }
}

/** Name, kind and category badge; shared by the Storms list and the map's storm card. */
@Composable
fun StormHeader(s: Storm, trailing: @Composable () -> Unit = {}) {
    val color = Color(stormColor(s.category))
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(46.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.18f)), Alignment.Center) {
            Icon(Icons.Outlined.Cyclone, null, tint = color, modifier = Modifier.size(30.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Text(
                "${s.kind.substringBefore(" ·")} · ${s.basin}",
                style = MaterialTheme.typography.bodyMedium,
                color = LocalContentColor.current.copy(alpha = 0.7f),
            )
        }
        Column(horizontalAlignment = Alignment.End, verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Pill(categoryTag(s.category), color)
            s.alertLevel?.let { level ->
                val c = when (level.lowercase()) {
                    "red" -> Color(0xFFEF4444)
                    "orange" -> Color(0xFFF97316)
                    else -> Color(0xFF22C55E)
                }
                Pill("GDACS $level", c, filled = false)
            }
        }
        trailing()
    }
}

/** Wind, pressure and distance as three tiles. */
@Composable
fun StormMetrics(s: Storm, units: Units, you: LatLon?, modifier: Modifier = Modifier) {
    Row(modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        MetricTile(
            Icons.Outlined.Air,
            if (s.windIsPeak) "Peak wind" else "Wind",
            s.windKmh?.let { units.wind(it) } ?: DASH,
            Modifier.weight(1f),
        )
        MetricTile(Icons.Outlined.Compress, "Pressure", s.pressureHpa?.let { units.pressure(it) } ?: DASH, Modifier.weight(1f))
        MetricTile(
            Icons.Outlined.NearMe,
            "From you",
            you?.let {
                val to = LatLon(s.lat, s.lon)
                "${units.distance(distanceKm(it, to))} ${degToCompass(bearingDeg(it, to))}"
            } ?: DASH,
            Modifier.weight(1f),
        )
    }
}

@Composable
private fun MetricTile(icon: androidx.compose.ui.graphics.vector.ImageVector, label: String, value: String, modifier: Modifier) {
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(LocalContentColor.current.copy(alpha = 0.06f))
            .padding(horizontal = 10.dp, vertical = 8.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, Modifier.size(13.dp), tint = LocalContentColor.current.copy(alpha = 0.6f))
            Spacer(Modifier.width(4.dp))
            Text(label, style = MaterialTheme.typography.labelSmall, color = LocalContentColor.current.copy(alpha = 0.7f))
        }
        Text(value, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold, maxLines = 2, modifier = Modifier.padding(top = 2.dp))
    }
}

/** Position, movement, last update and track size, as small lines under the tiles. */
@Composable
fun StormFacts(s: Storm, units: Units) {
    val lines = buildList {
        add("%.1f°%s %.1f°%s".format(abs(s.lat), if (s.lat >= 0) "N" else "S", abs(s.lon), if (s.lon >= 0) "E" else "W"))
        if (s.movingTowardDeg != null && s.movingKmh != null) add("moving ${degToCompass(s.movingTowardDeg)} at ${units.wind(s.movingKmh)}")
        feedTime(s.updated)?.let { add("updated $it") }
        s.track?.points?.size?.takeIf { it > 1 }?.let { add("$it track points") }
        add(s.sources.joinToString(" + "))
    }
    Text(
        lines.joinToString("  ·  "),
        style = MaterialTheme.typography.bodySmall,
        color = LocalContentColor.current.copy(alpha = 0.7f),
        modifier = Modifier.padding(top = 10.dp),
    )
}

fun openLink(context: Context, url: String) {
    try {
        context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    } catch (e: ActivityNotFoundException) {
        // No browser; nothing more we can do.
    }
}
