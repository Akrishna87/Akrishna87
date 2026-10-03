package io.github.akrishna87.weather.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.github.akrishna87.weather.WeatherViewModel
import io.github.akrishna87.weather.data.AirQuality
import io.github.akrishna87.weather.data.Alert
import io.github.akrishna87.weather.data.Forecast
import io.github.akrishna87.weather.data.LatLon
import io.github.akrishna87.weather.data.Marine
import io.github.akrishna87.weather.data.OpenMeteo
import io.github.akrishna87.weather.data.Place
import io.github.akrishna87.weather.data.Reading
import io.github.akrishna87.weather.data.Storm
import io.github.akrishna87.weather.data.Units
import io.github.akrishna87.weather.data.bearingDeg
import io.github.akrishna87.weather.data.degToCompass
import io.github.akrishna87.weather.data.distanceKm
import kotlin.math.roundToInt

/** Storms closer than this to your place get a warning banner on the Weather screen. */
private const val NEARBY_STORM_KM = 2000.0

@Composable
fun WeatherScreen(vm: WeatherViewModel) {
    val place by vm.place.collectAsState()
    val state by vm.weather.collectAsState()
    val units by vm.units.collectAsState()
    val storms by vm.storms.collectAsState()
    val locating by vm.locating.collectAsState()
    var searching by rememberSaveable { mutableStateOf(false) }

    val context = LocalContext.current
    val askLocation = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) vm.useDeviceLocation() else vm.locationDenied()
    }
    val locate = {
        if (ContextCompat.checkSelfPermission(context, Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED) {
            vm.useDeviceLocation()
        } else {
            askLocation.launch(Manifest.permission.ACCESS_COARSE_LOCATION)
        }
    }

    if (searching) SearchDialog(vm, onDismiss = { searching = false })

    val p = place
    if (p == null) {
        Welcome(locating, onLocate = locate, onSearch = { searching = true })
        return
    }

    val nearby = storms.storms
        .map { it to distanceKm(LatLon(p.lat, p.lon), LatLon(it.lat, it.lon)) }
        .filter { it.second < NEARBY_STORM_KM }
        .sortedBy { it.second }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f).clickable { searching = true }) {
                        Text(p.name, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            listOf(p.region.takeIf { it != p.name }, p.country).filter { !it.isNullOrBlank() }.joinToString(", ")
                                .ifBlank { "%.2f, %.2f".format(p.lat, p.lon) },
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                    IconButton(onClick = { searching = true }) { Icon(Icons.Outlined.Search, "Change place") }
                    IconButton(onClick = locate) { Icon(Icons.Outlined.MyLocation, "Use my location") }
                    IconButton(onClick = { vm.refreshWeather(); vm.refreshStorms() }) { Icon(Icons.Outlined.Refresh, "Refresh") }
                }
                if (state.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 4.dp))
                locating?.let { Text(it, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary) }
            }
        }

        items(nearby, key = { "storm-" + it.first.key }) { (storm, km) ->
            NearbyStormCard(storm, km, p, units) { vm.showOnMap(storm) }
        }
        items(state.alerts, key = { "alert-" + it.title + it.headline }) { AlertCard(it) }

        state.error?.let { err ->
            item { ErrorCard(err) { vm.refreshWeather() } }
        }

        val f = state.forecast
        if (f != null) {
            item { Hero(f, state.readings.values.toList(), units) }
            item { WindCard(f, units) { vm.showPlaceOnMap() } }
            item { HourlyRow(f, units) }
            item { DailyList(f, units) }
            item { DetailsCard(f, units) }
        } else if (state.loading) {
            item { Box(Modifier.fillMaxWidth().padding(48.dp), Alignment.Center) { CircularProgressIndicator() } }
        }

        if (state.readings.isNotEmpty()) item { SourcesCompare(state.readings.values.toList(), units) }
        state.air?.let { item { AirCard(it) } }
        state.marine?.let { item { MarineCard(it, units) } }
        item {
            Text(
                "Forecasts: Open-Meteo (ECMWF, NOAA, DWD, Environment Canada, JMA, Météo-France, UK Met Office, CMA, BoM), " +
                    "MET Norway, US National Weather Service. Air quality: Copernicus CAMS. Storms: GDACS, NOAA NHC.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 8.dp, bottom = 16.dp),
            )
        }
    }
}

@Composable
private fun Welcome(locating: String?, onLocate: () -> Unit, onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxWidth().padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Spacer(Modifier.height(32.dp))
        Text("🌦️", fontSize = 64.sp)
        Text("Vaanilai", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.SemiBold)
        Text(
            "Weather from every free forecast service, side by side, with a live wind map and the world's named storms.",
            style = MaterialTheme.typography.bodyLarge,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Button(onClick = onLocate, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.MyLocation, null)
            Spacer(Modifier.width(8.dp))
            Text("Use my location")
        }
        OutlinedButton(onClick = onSearch, modifier = Modifier.fillMaxWidth()) {
            Icon(Icons.Outlined.Search, null)
            Spacer(Modifier.width(8.dp))
            Text("Search for a place")
        }
        locating?.let { Text(it, color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.bodyMedium) }
    }
}

@Composable
private fun SearchDialog(vm: WeatherViewModel, onDismiss: () -> Unit) {
    val search by vm.search.collectAsState()
    val recent by vm.recent.collectAsState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 560.dp),
            shape = MaterialTheme.shapes.extraLarge,
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(16.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose a place", style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close") }
                }
                OutlinedTextField(
                    value = search.query,
                    onValueChange = vm::searchPlaces,
                    label = { Text("Town or city") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth(),
                )
                if (search.loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp))
                search.error?.let { Text(it, color = MaterialTheme.colorScheme.error, modifier = Modifier.padding(top = 8.dp)) }
                val list = if (search.query.trim().length >= 2) search.results else recent
                if (search.query.trim().length < 2 && recent.isNotEmpty()) {
                    Text("Recent", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 12.dp))
                }
                LazyColumn {
                    items(list, key = { "${it.lat},${it.lon},${it.name}" }) { pl ->
                        PlaceRow(pl, recentIcon = search.query.trim().length < 2) {
                            vm.choosePlace(pl)
                            onDismiss()
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun PlaceRow(p: Place, recentIcon: Boolean, onClick: () -> Unit) {
    ListItem(
        headlineContent = { Text(p.name) },
        supportingContent = {
            Text(listOf(p.region, p.country).filter { it.isNotBlank() && it != p.name }.joinToString(", "))
        },
        leadingContent = { Icon(if (recentIcon) Icons.Outlined.History else Icons.Outlined.Place, null) },
        colors = ListItemDefaults.colors(containerColor = Color.Transparent),
        modifier = Modifier.clickable(onClick = onClick),
    )
}

@Composable
private fun NearbyStormCard(storm: Storm, km: Double, p: Place, units: Units, onShow: () -> Unit) {
    val dir = degToCompass(bearingDeg(LatLon(p.lat, p.lon), LatLon(storm.lat, storm.lon)))
    val color = Color(stormColor(storm.category))
    Card(
        onClick = onShow,
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer),
    ) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Cyclone, null, tint = color, modifier = Modifier.size(36.dp))
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${storm.kind} ${storm.name}", fontWeight = FontWeight.SemiBold, color = MaterialTheme.colorScheme.onErrorContainer)
                Text(
                    buildString {
                        append("${units.distance(km)} $dir of ${p.name}")
                        storm.windKmh?.let { append(" · ${if (storm.windIsPeak) "peak " else ""}winds ${units.wind(it)}") }
                        if (storm.movingTowardDeg != null && storm.movingKmh != null) {
                            append(" · moving ${degToCompass(storm.movingTowardDeg)} at ${units.wind(storm.movingKmh)}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                )
                Text("Tap to see it on the wind map", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onErrorContainer)
            }
        }
    }
}

@Composable
private fun AlertCard(a: Alert) {
    var open by remember { mutableStateOf(false) }
    Card(
        onClick = { open = !open },
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
        modifier = Modifier.animateContentSize(),
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Outlined.Warning, null, tint = MaterialTheme.colorScheme.secondary)
                Spacer(Modifier.width(8.dp))
                Text(a.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                if (a.severity.isNotBlank()) Text(a.severity, style = MaterialTheme.typography.labelMedium)
            }
            if (a.headline.isNotBlank()) Text(a.headline, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 4.dp))
            if (open) {
                Text(a.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
                Text("— ${a.sender}", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 8.dp))
            } else {
                Text("Tap for details", style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(top = 4.dp))
            }
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f), color = MaterialTheme.colorScheme.onErrorContainer)
            TextButton(onClick = onRetry) { Text("Retry") }
        }
    }
}

@Composable
private fun Hero(f: Forecast, readings: List<Reading>, units: Units) {
    val today = f.days.firstOrNull()
    val temps = readings.mapNotNull { it.tempC }.sorted()
    Card {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(weatherEmoji(f.now.code, f.isDay), fontSize = 56.sp)
                Spacer(Modifier.width(16.dp))
                Column {
                    Text(units.temp(f.now.tempC), fontSize = 56.sp, fontWeight = FontWeight.Light, lineHeight = 60.sp)
                    Text(weatherText(f.now.code), style = MaterialTheme.typography.titleMedium)
                }
            }
            Spacer(Modifier.height(8.dp))
            Text(
                "Feels like ${units.temp(f.feelsLikeC)} · High ${units.temp(today?.maxC)} · Low ${units.temp(today?.minC)}",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            if (temps.size >= 3) {
                Text(
                    "${temps.size} sources say ${units.temp(temps.first())} to ${units.temp(temps.last())} " +
                        "(middle: ${units.temp(temps[temps.size / 2])})",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(top = 4.dp),
                )
            }
        }
    }
}

@Composable
fun WindArrow(fromDeg: Double?, modifier: Modifier = Modifier, tint: Color = MaterialTheme.colorScheme.primary) {
    if (fromDeg == null) return
    // The Navigation icon points up (north); the wind blows toward fromDeg + 180.
    Icon(Icons.Filled.Navigation, contentDescription = "from ${degToCompass(fromDeg)}", tint = tint, modifier = modifier.rotate((fromDeg + 180).toFloat()))
}

@Composable
private fun WindCard(f: Forecast, units: Units, onMap: () -> Unit) {
    val now = f.now
    val (force, name) = beaufort(now.windKmh ?: 0.0)
    val peak = f.hours.filter { it.gustKmh != null }.maxByOrNull { it.gustKmh!! }
    Card {
        Column(Modifier.fillMaxWidth().padding(20.dp)) {
            Text("Wind", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                WindArrow(now.windFromDeg, Modifier.size(40.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f)) {
                    Text(units.wind(now.windKmh), style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "From the ${compass(now.windFromDeg)} · gusts ${units.wind(now.gustKmh)}",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text("Force $force", style = MaterialTheme.typography.labelLarge)
                    Text(name, style = MaterialTheme.typography.bodySmall)
                }
            }
            peak?.let { h ->
                val (pf, pn) = beaufort(h.gustKmh!!)
                Text(
                    "Strongest gust in the next 48 h: ${units.wind(h.gustKmh)} at ${hourLabel(h.time)}" +
                        (if (h.time.take(10) != f.time.take(10)) " ${h.time.take(10).substring(5)}" else "") +
                        " ($pn, force $pf)",
                    style = MaterialTheme.typography.bodySmall,
                    color = if (pf >= 8) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 12.dp),
                )
            }
            TextButton(onClick = onMap, contentPadding = PaddingValues(0.dp)) { Text("See the wind flow on the map →") }
        }
    }
}

@Composable
private fun HourlyRow(f: Forecast, units: Units) {
    Card {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text("Next 48 hours", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            LazyRow(contentPadding = PaddingValues(horizontal = 12.dp), modifier = Modifier.padding(top = 8.dp)) {
                items(f.hours, key = { it.time }) { h ->
                    Column(
                        Modifier.widthIn(min = 60.dp).padding(horizontal = 6.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                        verticalArrangement = Arrangement.spacedBy(4.dp),
                    ) {
                        Text(hourLabel(h.time), style = MaterialTheme.typography.labelMedium)
                        Text(weatherEmoji(h.code, h.isDay), fontSize = 22.sp)
                        Text(units.temp(h.tempC), style = MaterialTheme.typography.titleSmall)
                        Text(
                            h.precipProb?.let { "💧${it.roundToInt()}%" } ?: " ",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary,
                        )
                        WindArrow(h.windFromDeg, Modifier.size(16.dp), tint = Color(windColor(h.windKmh?.toFloat() ?: 0f)))
                        Text(units.windNumber(h.windKmh), style = MaterialTheme.typography.labelSmall)
                    }
                }
            }
        }
    }
}

@Composable
private fun DailyList(f: Forecast, units: Units) {
    Card {
        Column(Modifier.padding(vertical = 12.dp)) {
            Text("7 days", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp))
            f.days.forEachIndexed { i, d ->
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(dayLabel(d.date, i), modifier = Modifier.width(84.dp))
                    Text(weatherEmoji(d.code), fontSize = 20.sp, modifier = Modifier.width(32.dp))
                    Text(
                        d.precipProb?.takeIf { it >= 10 }?.let { "💧${it.roundToInt()}%" } ?: "",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.width(48.dp),
                    )
                    Row(Modifier.weight(1f), verticalAlignment = Alignment.CenterVertically) {
                        WindArrow(d.windFromDeg, Modifier.size(14.dp), tint = Color(windColor(d.gustMaxKmh?.toFloat() ?: 0f)))
                        Text(" ${units.windNumber(d.gustMaxKmh)}", style = MaterialTheme.typography.labelSmall)
                    }
                    Text(units.temp(d.minC), color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.width(40.dp))
                    Text(units.temp(d.maxC), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(40.dp))
                }
            }
            Text(
                "Arrows show the wind's direction; the number is the day's strongest gust (${units.wind.label}).",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp, vertical = 4.dp),
            )
        }
    }
}

@Composable
private fun DetailsCard(f: Forecast, units: Units) {
    val today = f.days.firstOrNull()
    val cells = listOf(
        "Humidity" to pct(f.now.humidity),
        "Pressure" to units.pressure(f.now.pressureHpa),
        "Cloud cover" to pct(f.cloudPct),
        "Rain (now)" to units.precip(f.now.precipMm),
        "Rain today" to units.precip(today?.precipMm),
        "UV index (max)" to (today?.uvMax?.let { "%.1f".format(it) } ?: DASH),
        "Sunrise" to hourLabel(today?.sunrise),
        "Sunset" to hourLabel(today?.sunset),
    )
    Card {
        Column(Modifier.padding(20.dp)) {
            Text("Details", style = MaterialTheme.typography.titleMedium)
            cells.chunked(2).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 12.dp)) {
                    row.forEach { (k, v) ->
                        Column(Modifier.weight(1f)) {
                            Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.titleMedium)
                        }
                    }
                }
            }
        }
    }
}

/** Every source's "right now" in one table, so you can see where they agree and where they don't. */
@Composable
private fun SourcesCompare(readings: List<Reading>, units: Units) {
    val order = listOf("open-meteo") + OpenMeteo.MODELS.map { it.id } + listOf("metno", "nws")
    val sorted = readings.sortedBy { order.indexOf(it.sourceId).let { i -> if (i < 0) 99 else i } }
    Card {
        Column(Modifier.padding(vertical = 16.dp)) {
            Text("What each source says now", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(horizontal = 20.dp))
            Text(
                "${sorted.size} forecasts from weather services around the world",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 20.dp),
            )
            Spacer(Modifier.height(8.dp))
            sorted.forEachIndexed { i, r ->
                if (i > 0) HorizontalDivider(Modifier.padding(horizontal = 20.dp), color = MaterialTheme.colorScheme.outlineVariant)
                Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) {
                        Text(r.sourceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                        Text(r.agency, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                    Text(weatherEmoji(r.code), fontSize = 18.sp, modifier = Modifier.width(28.dp))
                    Text(units.temp(r.tempC), style = MaterialTheme.typography.titleMedium, modifier = Modifier.width(44.dp))
                    Row(Modifier.width(92.dp), verticalAlignment = Alignment.CenterVertically) {
                        WindArrow(r.windFromDeg, Modifier.size(14.dp))
                        Column(Modifier.padding(start = 4.dp)) {
                            Text(units.wind(r.windKmh), style = MaterialTheme.typography.labelMedium)
                            r.gustKmh?.let { Text("gust ${units.windNumber(it)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant) }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AirCard(a: AirQuality) {
    val aqi = a.usAqi
    val (label, color) = when {
        aqi == null -> DASH to MaterialTheme.colorScheme.onSurfaceVariant
        aqi <= 50 -> "Good" to Color(0xFF22C55E)
        aqi <= 100 -> "Moderate" to Color(0xFFEAB308)
        aqi <= 150 -> "Unhealthy for sensitive groups" to Color(0xFFF97316)
        aqi <= 200 -> "Unhealthy" to Color(0xFFEF4444)
        aqi <= 300 -> "Very unhealthy" to Color(0xFFA855F7)
        else -> "Hazardous" to Color(0xFF9F1239)
    }
    fun ug(v: Double?) = v?.let { "${it.roundToInt()} µg/m³" } ?: DASH
    Card {
        Column(Modifier.padding(20.dp)) {
            Text("Air quality", style = MaterialTheme.typography.titleMedium)
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 8.dp)) {
                Text(aqi?.roundToInt()?.toString() ?: DASH, style = MaterialTheme.typography.headlineMedium, color = color)
                Spacer(Modifier.width(12.dp))
                Column {
                    Text(label, color = color, fontWeight = FontWeight.SemiBold)
                    Text(
                        "US AQI" + (a.euAqi?.let { " · European AQI ${it.roundToInt()}" } ?: ""),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            val cells = listOf(
                "PM2.5" to ug(a.pm25), "PM10" to ug(a.pm10), "Ozone" to ug(a.ozone),
                "NO₂" to ug(a.no2), "SO₂" to ug(a.so2), "Dust" to ug(a.dust),
            )
            cells.chunked(3).forEach { row ->
                Row(Modifier.fillMaxWidth().padding(top = 10.dp)) {
                    row.forEach { (k, v) ->
                        Column(Modifier.weight(1f)) {
                            Text(k, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Text(v, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MarineCard(m: Marine, units: Units) {
    fun metres(v: Double?) = v?.let { if (units.fahrenheit) "%.1f ft".format(it * 3.28084) else "%.1f m".format(it) } ?: DASH
    Card {
        Column(Modifier.padding(20.dp)) {
            Text("Sea", style = MaterialTheme.typography.titleMedium)
            Row(Modifier.fillMaxWidth().padding(top = 8.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Waves", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(metres(m.waveM), style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text("Swell", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(metres(m.swellM), style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text("Period", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(m.wavePeriodS?.let { "${it.roundToInt()} s" } ?: DASH, style = MaterialTheme.typography.titleMedium)
                }
                Column(Modifier.weight(1f)) {
                    Text("From", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Text(compass(m.waveFromDeg), style = MaterialTheme.typography.titleMedium)
                }
            }
        }
    }
}
