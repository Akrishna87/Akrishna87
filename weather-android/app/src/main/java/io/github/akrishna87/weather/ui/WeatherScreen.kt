package io.github.akrishna87.weather.ui

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Navigation
import androidx.compose.material.icons.outlined.Air
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Close
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.Cyclone
import androidx.compose.material.icons.outlined.Hub
import androidx.compose.material.icons.outlined.History
import androidx.compose.material.icons.outlined.KeyboardArrowDown
import androidx.compose.material.icons.outlined.Masks
import androidx.compose.material.icons.outlined.MyLocation
import androidx.compose.material.icons.outlined.NightsStay
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Speed
import androidx.compose.material.icons.outlined.Thermostat
import androidx.compose.material.icons.outlined.Umbrella
import androidx.compose.material.icons.outlined.Warning
import androidx.compose.material.icons.outlined.WaterDrop
import androidx.compose.material.icons.outlined.Waves
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material.icons.outlined.WbTwilight
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.ListItemDefaults
import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.core.content.ContextCompat
import io.github.akrishna87.weather.R
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

    val f = state.forecast
    val sky = skyBrush(f?.now?.code, f?.isDay ?: true)

    Box(Modifier.fillMaxSize().background(sky)) {
        CompositionLocalProvider(LocalContentColor provides OnSky) {
            val p = place
            if (p == null) {
                Welcome(locating, onLocate = locate, onSearch = { searching = true })
                return@CompositionLocalProvider
            }

            val nearby = storms.storms
                .map { it to distanceKm(LatLon(p.lat, p.lon), LatLon(it.lat, it.lon)) }
                .filter { it.second < NEARBY_STORM_KM }
                .sortedBy { it.second }
            val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

            LazyColumn(
                contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top, bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    TopBar(
                        p = p,
                        updated = f?.fetchedAt,
                        loading = state.loading,
                        locating = locating,
                        onSearch = { searching = true },
                        onLocate = locate,
                        onRefresh = { vm.refreshWeather(); vm.refreshStorms() },
                    )
                }

                if (f != null) {
                    item { Hero(f, state.readings.values.toList(), units) }
                } else if (state.loading) {
                    item { Box(Modifier.fillMaxWidth().padding(64.dp), Alignment.Center) { CircularProgressIndicator(color = OnSky) } }
                }

                items(nearby, key = { "storm-" + it.first.key }) { (storm, km) ->
                    NearbyStormCard(storm, km, p, units) { vm.showOnMap(storm) }
                }
                items(state.alerts, key = { "alert-" + it.title + it.headline }) { AlertCard(it) }
                state.error?.let { err -> item { ErrorCard(err) { vm.refreshWeather() } } }

                if (f != null) {
                    item { HourlyCard(f, units) }
                    item { DailyCard(f, units) }
                    item { WindCard(f, units) { vm.showPlaceOnMap() } }
                    item { DetailsGrid(f, units) }
                }
                if (state.readings.isNotEmpty()) item { SourcesCompare(state.readings.values.toList(), units) }
                state.air?.let { item { AirCard(it) } }
                state.marine?.let { item { MarineCard(it, units) } }
                item {
                    Text(
                        "Forecasts: Open-Meteo (ECMWF, NOAA, DWD, Environment Canada, JMA, Météo-France, UK Met Office, CMA, BoM), " +
                            "MET Norway, US National Weather Service. Air quality: Copernicus CAMS. Storms: GDACS, NOAA NHC.",
                        style = MaterialTheme.typography.labelSmall,
                        color = OnSkyDim,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.fillMaxWidth().padding(top = 8.dp, start = 8.dp, end = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun TopBar(
    p: Place,
    updated: Long?,
    loading: Boolean,
    locating: String?,
    onSearch: () -> Unit,
    onLocate: () -> Unit,
    onRefresh: () -> Unit,
) {
    Column(Modifier.padding(top = 4.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Row(
                Modifier
                    .weight(1f, fill = false)
                    .clip(RoundedCornerShape(50))
                    .clickable(onClick = onSearch)
                    .padding(horizontal = 8.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Place, null, Modifier.size(20.dp))
                Spacer(Modifier.width(4.dp))
                Text(p.name, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Icon(Icons.Outlined.KeyboardArrowDown, "Change place")
            }
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onLocate) { Icon(Icons.Outlined.MyLocation, "Use my location") }
            IconButton(onClick = onRefresh) { Icon(Icons.Outlined.Refresh, "Refresh") }
        }
        Text(
            listOf(p.region.takeIf { it != p.name }, p.country).filter { !it.isNullOrBlank() }.joinToString(", ")
                .ifBlank { "%.2f, %.2f".format(p.lat, p.lon) } +
                (updated?.let { " · updated ${ago(it)}" } ?: ""),
            style = MaterialTheme.typography.bodySmall,
            color = OnSkyDim,
            modifier = Modifier.padding(start = 12.dp),
        )
        if (loading) LinearProgressIndicator(Modifier.fillMaxWidth().padding(top = 8.dp), color = OnSky, trackColor = Color.White.copy(alpha = 0.2f))
        locating?.let { Text(it, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(start = 12.dp, top = 4.dp)) }
    }
}

@Composable
private fun Welcome(locating: String?, onLocate: () -> Unit, onSearch: () -> Unit) {
    Column(
        Modifier.fillMaxSize().statusBarsPadding().padding(28.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically),
    ) {
        Box(
            Modifier
                .size(112.dp)
                .clip(RoundedCornerShape(32.dp))
                .background(Brush.linearGradient(listOf(Color(0xFF38BDF8), Color(0xFF2563EB), Color(0xFF312E81)))),
        ) {
            Image(painterResource(R.drawable.ic_launcher_foreground), null, Modifier.fillMaxSize())
        }
        Text("Vaanilai", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold)
        Text(
            "Weather from every free forecast service, side by side, with a live wind map and the world's named storms.",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSkyDim,
            textAlign = TextAlign.Center,
        )
        Spacer(Modifier.height(8.dp))
        Button(
            onClick = onLocate,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Color.White, contentColor = Color(0xFF0B5FC9)),
        ) {
            Icon(Icons.Outlined.MyLocation, null)
            Spacer(Modifier.width(8.dp))
            Text("Use my location", fontWeight = FontWeight.SemiBold)
        }
        OutlinedButton(
            onClick = onSearch,
            modifier = Modifier.fillMaxWidth().height(52.dp),
            colors = ButtonDefaults.outlinedButtonColors(contentColor = OnSky),
            border = androidx.compose.foundation.BorderStroke(1.dp, Color.White.copy(alpha = 0.6f)),
        ) {
            Icon(Icons.Outlined.Search, null)
            Spacer(Modifier.width(8.dp))
            Text("Search for a place")
        }
        locating?.let { Text(it, style = MaterialTheme.typography.bodyMedium, textAlign = TextAlign.Center) }
    }
}

@Composable
private fun SearchDialog(vm: WeatherViewModel, onDismiss: () -> Unit) {
    val search by vm.search.collectAsState()
    val recent by vm.recent.collectAsState()
    Dialog(onDismissRequest = onDismiss, properties = DialogProperties(usePlatformDefaultWidth = false)) {
        Surface(
            Modifier.fillMaxWidth().padding(16.dp).heightIn(max = 560.dp),
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainerHigh,
        ) {
            Column(Modifier.padding(20.dp)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text("Choose a place", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
                    IconButton(onClick = onDismiss) { Icon(Icons.Outlined.Close, "Close") }
                }
                OutlinedTextField(
                    value = search.query,
                    onValueChange = vm::searchPlaces,
                    label = { Text("Town or city") },
                    leadingIcon = { Icon(Icons.Outlined.Search, null) },
                    singleLine = true,
                    shape = RoundedCornerShape(16.dp),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                    modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
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
        modifier = Modifier.clip(RoundedCornerShape(16.dp)).clickable(onClick = onClick),
    )
}

@Composable
private fun Hero(f: Forecast, readings: List<Reading>, units: Units) {
    val today = f.days.firstOrNull()
    val temps = readings.mapNotNull { it.tempC }.sorted()
    Column(Modifier.fillMaxWidth().padding(top = 8.dp, bottom = 12.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Text(weatherEmoji(f.now.code, f.isDay), fontSize = 56.sp)
        Text(
            units.temp(f.now.tempC),
            fontSize = 96.sp,
            lineHeight = 100.sp,
            fontWeight = FontWeight.Thin,
            modifier = Modifier.padding(start = 24.dp), // balances the degree sign
        )
        Text(weatherText(f.now.code), style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Medium)
        Text(
            "H ${units.temp(today?.maxC)}  ·  L ${units.temp(today?.minC)}  ·  Feels like ${units.temp(f.feelsLikeC)}",
            style = MaterialTheme.typography.bodyLarge,
            color = OnSkyDim,
            modifier = Modifier.padding(top = 4.dp),
        )
        if (temps.size >= 3) {
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.16f))
                    .padding(horizontal = 14.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(Icons.Outlined.Hub, null, Modifier.size(14.dp))
                Spacer(Modifier.width(6.dp))
                Text(
                    "${temps.size} forecasts: ${units.temp(temps.first())} – ${units.temp(temps.last())}",
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }
    }
}

@Composable
private fun NearbyStormCard(storm: Storm, km: Double, p: Place, units: Units, onShow: () -> Unit) {
    val dir = degToCompass(bearingDeg(LatLon(p.lat, p.lon), LatLon(storm.lat, storm.lon)))
    val color = Color(stormColor(storm.category))
    GlassCard(tint = Color(0xFFEF4444), alpha = 0.28f, onClick = onShow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(Modifier.size(44.dp).clip(RoundedCornerShape(50)).background(color.copy(alpha = 0.25f)), Alignment.Center) {
                Icon(Icons.Outlined.Cyclone, null, tint = color, modifier = Modifier.size(28.dp))
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text("${storm.kind} ${storm.name}", fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    buildString {
                        append("${units.distance(km)} $dir of ${p.name}")
                        storm.windKmh?.let { append(" · ${if (storm.windIsPeak) "peak " else ""}winds ${units.wind(it)}") }
                        if (storm.movingTowardDeg != null && storm.movingKmh != null) {
                            append(" · moving ${degToCompass(storm.movingTowardDeg)} at ${units.wind(storm.movingKmh)}")
                        }
                    },
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("Tap to see it on the wind map", style = MaterialTheme.typography.labelSmall, color = OnSkyDim)
            }
        }
    }
}

@Composable
private fun AlertCard(a: Alert) {
    var open by remember { mutableStateOf(false) }
    GlassCard(tint = Color(0xFFF59E0B), alpha = 0.26f, onClick = { open = !open }, modifier = Modifier.animateContentSize()) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Outlined.Warning, null, tint = Color(0xFFFDE68A))
            Spacer(Modifier.width(8.dp))
            Text(a.title, fontWeight = FontWeight.SemiBold, modifier = Modifier.weight(1f))
            if (a.severity.isNotBlank()) Pill(a.severity, Color(0xFFFDE68A), filled = false)
        }
        if (a.headline.isNotBlank()) Text(a.headline, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.padding(top = 6.dp))
        if (open) {
            Text(a.description, style = MaterialTheme.typography.bodySmall, modifier = Modifier.padding(top = 8.dp))
            Text("— ${a.sender}", style = MaterialTheme.typography.labelSmall, color = OnSkyDim, modifier = Modifier.padding(top = 8.dp))
        } else {
            Text("Tap for details", style = MaterialTheme.typography.labelSmall, color = OnSkyDim, modifier = Modifier.padding(top = 4.dp))
        }
    }
}

@Composable
private fun ErrorCard(message: String, onRetry: () -> Unit) {
    GlassCard(tint = Color(0xFFEF4444), alpha = 0.25f) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(message, modifier = Modifier.weight(1f))
            TextButton(onClick = onRetry) { Text("Retry", color = OnSky, fontWeight = FontWeight.SemiBold) }
        }
    }
}

@Composable
fun WindArrow(fromDeg: Double?, modifier: Modifier = Modifier, tint: Color = LocalContentColor.current) {
    if (fromDeg == null) return
    // The Navigation icon points up (north); the wind blows toward fromDeg + 180.
    Icon(Icons.Filled.Navigation, contentDescription = "from ${degToCompass(fromDeg)}", tint = tint, modifier = modifier.rotate((fromDeg + 180).toFloat()))
}

/** The next 48 hours, with a temperature curve running through them. */
@Composable
private fun HourlyCard(f: Forecast, units: Units) {
    val hours = f.hours
    if (hours.isEmpty()) return
    val itemW = 58.dp
    val curveH = 72.dp
    val measurer = rememberTextMeasurer()
    val labelStyle = TextStyle(color = OnSky, fontSize = 14.sp, fontWeight = FontWeight.Medium)
    GlassCard(contentPadding = PaddingValues(vertical = 16.dp)) {
        SectionLabel(Icons.Outlined.Schedule, "Next 48 hours", Modifier.padding(horizontal = 16.dp))
        Column(Modifier.horizontalScroll(rememberScrollState()).padding(horizontal = 8.dp)) {
            Row {
                hours.forEachIndexed { i, h ->
                    Column(Modifier.width(itemW), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(if (i == 0) "Now" else hourLabel(h.time), style = MaterialTheme.typography.labelMedium, color = if (i == 0) OnSky else OnSkyDim)
                        Text(weatherEmoji(h.code, h.isDay), fontSize = 22.sp, modifier = Modifier.padding(top = 6.dp))
                    }
                }
            }
            val temps = hours.map { it.tempC }
            val known = temps.filterNotNull()
            Canvas(Modifier.width(itemW * hours.size).height(curveH)) {
                if (known.isEmpty()) return@Canvas
                val lo = known.min()
                val hi = known.max()
                val span = (hi - lo).coerceAtLeast(1.0)
                val labelSpace = 22.dp.toPx()
                val pad = 6.dp.toPx()
                val w = itemW.toPx()
                fun y(t: Double) = labelSpace + pad + ((1 - (t - lo) / span) * (size.height - labelSpace - 2 * pad)).toFloat()
                val pts = temps.mapIndexedNotNull { i, t -> t?.let { Offset(w * (i + 0.5f), y(it)) } }
                if (pts.size < 2) return@Canvas
                val line = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    for (i in 1 until pts.size) {
                        val a = pts[i - 1]
                        val b = pts[i]
                        val mx = (a.x + b.x) / 2
                        cubicTo(mx, a.y, mx, b.y, b.x, b.y)
                    }
                }
                val fill = Path().apply {
                    addPath(line)
                    lineTo(pts.last().x, size.height)
                    lineTo(pts.first().x, size.height)
                    close()
                }
                drawPath(fill, Brush.verticalGradient(listOf(Color.White.copy(alpha = 0.22f), Color.Transparent), startY = labelSpace, endY = size.height))
                drawPath(line, Color.White, style = Stroke(width = 2.dp.toPx()))
                temps.forEachIndexed { i, t ->
                    if (t == null) return@forEachIndexed
                    val p = Offset(w * (i + 0.5f), y(t))
                    drawCircle(Color.White, radius = 3.dp.toPx(), center = p)
                    val text = measurer.measure(units.temp(t), labelStyle)
                    drawText(text, topLeft = Offset(p.x - text.size.width / 2f, p.y - text.size.height - 4.dp.toPx()))
                }
            }
            Row {
                hours.forEach { h ->
                    Column(Modifier.width(itemW), horizontalAlignment = Alignment.CenterHorizontally) {
                        Text(
                            h.precipProb?.takeIf { it >= 5 }?.let { "${it.roundToInt()}%" } ?: " ",
                            style = MaterialTheme.typography.labelSmall,
                            color = RainBlue,
                            fontWeight = FontWeight.SemiBold,
                        )
                        Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.padding(top = 4.dp)) {
                            WindArrow(h.windFromDeg, Modifier.size(12.dp), tint = Color(windColor(h.windKmh?.toFloat() ?: 0f)))
                            Text(" ${units.windNumber(h.windKmh)}", style = MaterialTheme.typography.labelSmall, color = OnSkyDim)
                        }
                    }
                }
            }
        }
    }
}

/** Seven days, each with its low-to-high bar on the week's scale. */
@Composable
private fun DailyCard(f: Forecast, units: Units) {
    val lows = f.days.mapNotNull { it.minC }
    val highs = f.days.mapNotNull { it.maxC }
    if (lows.isEmpty() || highs.isEmpty()) return
    val weekMin = lows.min()
    val weekMax = highs.max()
    GlassCard {
        SectionLabel(Icons.Outlined.CalendarMonth, "7-day forecast")
        f.days.forEachIndexed { i, d ->
            if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.12f))
            Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                Text(dayLabel(d.date, i), style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium, modifier = Modifier.width(84.dp))
                Column(Modifier.width(40.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(weatherEmoji(d.code), fontSize = 20.sp)
                    d.precipProb?.takeIf { it >= 20 }?.let {
                        Text("${it.roundToInt()}%", style = MaterialTheme.typography.labelSmall, color = RainBlue, fontWeight = FontWeight.SemiBold)
                    }
                }
                Text(units.temp(d.minC), color = OnSkyDim, textAlign = TextAlign.End, modifier = Modifier.width(40.dp))
                if (d.minC != null && d.maxC != null) {
                    RangeBar(
                        d.minC, d.maxC, weekMin, weekMax,
                        now = if (i == 0) f.now.tempC else null,
                        modifier = Modifier.weight(1f).padding(horizontal = 10.dp),
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }
                Text(units.temp(d.maxC), fontWeight = FontWeight.SemiBold, modifier = Modifier.width(36.dp))
            }
        }
    }
}

@Composable
private fun WindCard(f: Forecast, units: Units, onMap: () -> Unit) {
    val now = f.now
    val (force, name) = beaufort(now.windKmh ?: 0.0)
    val peak = f.hours.filter { it.gustKmh != null }.maxByOrNull { it.gustKmh!! }
    GlassCard {
        SectionLabel(Icons.Outlined.Air, "Wind")
        Row(verticalAlignment = Alignment.CenterVertically) {
            CompassDial(now.windFromDeg, units.windNumber(now.windKmh), units.wind.label)
            Spacer(Modifier.width(16.dp))
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Metric("From the", compass(now.windFromDeg))
                Metric("Gusts", units.wind(now.gustKmh))
                Metric("Beaufort", "Force $force", sub = name)
            }
        }
        peak?.let { h ->
            val (pf, pn) = beaufort(h.gustKmh!!)
            val strong = pf >= 8
            Row(
                Modifier
                    .padding(top = 14.dp)
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(14.dp))
                    .background(if (strong) Color(0xFFEF4444).copy(alpha = 0.3f) else Color.White.copy(alpha = 0.08f))
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(if (strong) Icons.Outlined.Warning else Icons.Outlined.Air, null, Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text(
                    "Strongest gust next 48 h: ${units.wind(h.gustKmh)} at ${hourLabel(h.time)}" +
                        (if (h.time.take(10) != f.time.take(10)) " ${dayLabel(h.time.take(10), 2)}" else "") + " · $pn",
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        Row(
            Modifier
                .padding(top = 12.dp)
                .fillMaxWidth()
                .clip(RoundedCornerShape(50))
                .border(1.dp, Color.White.copy(alpha = 0.35f), RoundedCornerShape(50))
                .clickable(onClick = onMap)
                .padding(vertical = 10.dp),
            horizontalArrangement = Arrangement.Center,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Outlined.Air, null, Modifier.size(18.dp))
            Spacer(Modifier.width(8.dp))
            Text("See the wind flow on the map", style = MaterialTheme.typography.labelLarge, fontWeight = FontWeight.SemiBold)
        }
    }
}

@Composable
private fun DetailsGrid(f: Forecast, units: Units) {
    val today = f.days.firstOrNull()
    val uv = today?.uvMax
    val tiles = listOf(
        Triple(Icons.Outlined.Thermostat, "Feels like", units.temp(f.feelsLikeC)) to null,
        Triple(Icons.Outlined.WaterDrop, "Humidity", pct(f.now.humidity)) to null,
        Triple(Icons.Outlined.Speed, "Pressure", units.pressure(f.now.pressureHpa)) to null,
        Triple(Icons.Outlined.Cloud, "Cloud cover", pct(f.cloudPct)) to null,
        Triple(Icons.Outlined.Umbrella, "Rain today", units.precip(today?.precipMm)) to "Now: ${units.precip(f.now.precipMm)}",
        Triple(Icons.Outlined.WbSunny, "UV index", uv?.let { "%.1f".format(it) } ?: DASH) to uv?.let(::uvLevel),
        Triple(Icons.Outlined.WbTwilight, "Sunrise", hourLabel(today?.sunrise)) to null,
        Triple(Icons.Outlined.NightsStay, "Sunset", hourLabel(today?.sunset)) to null,
    )
    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        tiles.chunked(2).forEach { row ->
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                row.forEach { (t, sub) ->
                    GlassCard(Modifier.weight(1f)) { Metric(t.second, t.third, icon = t.first, sub = sub) }
                }
            }
        }
    }
}

private fun uvLevel(uv: Double) = when {
    uv < 3 -> "Low"
    uv < 6 -> "Moderate"
    uv < 8 -> "High"
    uv < 11 -> "Very high"
    else -> "Extreme"
}

/** Every source's "right now" in one list, with a strip showing where each sits in the spread. */
@Composable
private fun SourcesCompare(readings: List<Reading>, units: Units) {
    val order = listOf("open-meteo") + OpenMeteo.MODELS.map { it.id } + listOf("metno", "nws")
    val sorted = readings.sortedBy { order.indexOf(it.sourceId).let { i -> if (i < 0) 99 else i } }
    val temps = sorted.mapNotNull { it.tempC }
    val lo = temps.minOrNull() ?: 0.0
    val hi = temps.maxOrNull() ?: 0.0
    GlassCard {
        SectionLabel(Icons.Outlined.Hub, "What each source says now")
        Text(
            "${sorted.size} forecasts from weather services around the world" +
                if (temps.size > 1) " · spread ${units.temp(lo)} to ${units.temp(hi)}" else "",
            style = MaterialTheme.typography.bodySmall,
            color = OnSkyDim,
            modifier = Modifier.padding(bottom = 6.dp),
        )
        sorted.forEachIndexed { i, r ->
            if (i > 0) HorizontalDivider(color = Color.White.copy(alpha = 0.10f))
            Row(Modifier.fillMaxWidth().padding(vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text(r.sourceName, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text(r.agency, style = MaterialTheme.typography.labelSmall, color = OnSkyDim, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
                Text(weatherEmoji(r.code), fontSize = 16.sp, modifier = Modifier.width(26.dp))
                SpreadDot(r.tempC, lo, hi, Modifier.width(56.dp))
                Text(units.temp(r.tempC), style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, textAlign = TextAlign.End, modifier = Modifier.width(42.dp))
                Column(Modifier.width(66.dp), horizontalAlignment = Alignment.End) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        WindArrow(r.windFromDeg, Modifier.size(12.dp))
                        Text(" ${units.windNumber(r.windKmh)}", style = MaterialTheme.typography.labelMedium)
                    }
                    r.gustKmh?.let { Text("gust ${units.windNumber(it)}", style = MaterialTheme.typography.labelSmall, color = OnSkyDim) }
                }
            }
        }
    }
}

/** A tiny track from the lowest to the highest forecast, with a dot for this one. */
@Composable
private fun SpreadDot(t: Double?, lo: Double, hi: Double, modifier: Modifier) {
    Canvas(modifier.height(12.dp).padding(horizontal = 6.dp)) {
        val mid = size.height / 2
        drawLine(Color.White.copy(alpha = 0.2f), Offset(0f, mid), Offset(size.width, mid), strokeWidth = 2.dp.toPx())
        if (t == null) return@Canvas
        val x = if (hi - lo < 0.05) size.width / 2 else ((t - lo) / (hi - lo)).toFloat() * size.width
        drawCircle(tempColor(t.toFloat()), radius = 4.dp.toPx(), center = Offset(x, mid))
    }
}

private val AQI_COLORS = listOf(
    Color(0xFF22C55E), Color(0xFFEAB308), Color(0xFFF97316), Color(0xFFEF4444), Color(0xFFA855F7), Color(0xFF9F1239),
)

@Composable
private fun AirCard(a: AirQuality) {
    val aqi = a.usAqi
    val (label, color) = when {
        aqi == null -> DASH to OnSkyDim
        aqi <= 50 -> "Good" to AQI_COLORS[0]
        aqi <= 100 -> "Moderate" to AQI_COLORS[1]
        aqi <= 150 -> "Unhealthy for sensitive groups" to AQI_COLORS[2]
        aqi <= 200 -> "Unhealthy" to AQI_COLORS[3]
        aqi <= 300 -> "Very unhealthy" to AQI_COLORS[4]
        else -> "Hazardous" to AQI_COLORS[5]
    }
    fun ug(v: Double?) = v?.let { "${it.roundToInt()}" } ?: DASH
    GlassCard {
        SectionLabel(Icons.Outlined.Masks, "Air quality")
        Row(verticalAlignment = Alignment.Bottom) {
            Text(aqi?.roundToInt()?.toString() ?: DASH, fontSize = 40.sp, fontWeight = FontWeight.Light, lineHeight = 44.sp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.padding(bottom = 6.dp)) {
                Text(label, color = color, fontWeight = FontWeight.SemiBold, style = MaterialTheme.typography.titleMedium)
                Text(
                    "US AQI" + (a.euAqi?.let { " · European AQI ${it.roundToInt()}" } ?: ""),
                    style = MaterialTheme.typography.labelSmall,
                    color = OnSkyDim,
                )
            }
        }
        if (aqi != null) GaugeBar((aqi / 300).toFloat(), AQI_COLORS, Modifier.padding(vertical = 12.dp))
        val cells = listOf(
            "PM2.5" to ug(a.pm25), "PM10" to ug(a.pm10), "O₃" to ug(a.ozone),
            "NO₂" to ug(a.no2), "SO₂" to ug(a.so2), "Dust" to ug(a.dust),
        )
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            cells.forEach { (k, v) ->
                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                    Text(v, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                    Text(k, style = MaterialTheme.typography.labelSmall, color = OnSkyDim)
                }
            }
        }
        Text("Pollutants in µg/m³", style = MaterialTheme.typography.labelSmall, color = OnSkyDim, modifier = Modifier.padding(top = 8.dp))
    }
}

@Composable
private fun MarineCard(m: Marine, units: Units) {
    fun metres(v: Double?) = v?.let { if (units.fahrenheit) "%.1f ft".format(it * 3.28084) else "%.1f m".format(it) } ?: DASH
    GlassCard {
        SectionLabel(Icons.Outlined.Waves, "Sea")
        Row(Modifier.fillMaxWidth()) {
            Metric("Waves", metres(m.waveM), Modifier.weight(1f))
            Metric("Swell", metres(m.swellM), Modifier.weight(1f))
            Metric("Period", m.wavePeriodS?.let { "${it.roundToInt()} s" } ?: DASH, Modifier.weight(1f))
            Metric("From", compass(m.waveFromDeg), Modifier.weight(1f))
        }
    }
}
