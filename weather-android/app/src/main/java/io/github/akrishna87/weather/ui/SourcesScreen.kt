package io.github.akrishna87.weather.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.weather.WeatherViewModel
import io.github.akrishna87.weather.data.SourceState
import io.github.akrishna87.weather.data.SourceStatus
import io.github.akrishna87.weather.data.WindUnit

private val OK_GREEN = Color(0xFF22C55E)

/** Units, and how every data source fared on its last fetch. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SourcesScreen(vm: WeatherViewModel) {
    val units by vm.units.collectAsState()
    val sources by vm.sources.collectAsState()
    val ok = sources.count { it.state == SourceState.Ok }
    val top = WindowInsets.statusBars.asPaddingValues().calculateTopPadding()

    LazyColumn(
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = top + 12.dp, bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp),
    ) {
        item {
            Column {
                Text("Sources & settings", style = MaterialTheme.typography.headlineMedium, fontWeight = FontWeight.Bold)
                Text(
                    "No accounts or API keys: every source is free and open.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        item {
            Section("Units") {
                Text("Temperature, rain and pressure", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(bottom = 8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    listOf(false to "°C · mm · hPa", true to "°F · in · inHg").forEachIndexed { i, (f, label) ->
                        SegmentedButton(
                            selected = units.fahrenheit == f,
                            onClick = { vm.setUnits(units.copy(fahrenheit = f)) },
                            shape = SegmentedButtonDefaults.itemShape(i, 2),
                        ) { Text(label) }
                    }
                }
                Text("Wind speed", style = MaterialTheme.typography.labelLarge, modifier = Modifier.padding(top = 16.dp, bottom = 8.dp))
                SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                    WindUnit.entries.forEachIndexed { i, w ->
                        SegmentedButton(
                            selected = units.wind == w,
                            onClick = { vm.setUnits(units.copy(wind = w)) },
                            shape = SegmentedButtonDefaults.itemShape(i, WindUnit.entries.size),
                        ) { Text(w.label) }
                    }
                }
            }
        }
        item {
            Section("Data sources") {
                if (sources.isEmpty()) {
                    Text("Nothing fetched yet.", color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Row(verticalAlignment = Alignment.Bottom) {
                        Text("$ok", style = MaterialTheme.typography.displaySmall, fontWeight = FontWeight.SemiBold, color = OK_GREEN)
                        Text(
                            " of ${sources.size} answered on the last refresh",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(bottom = 8.dp),
                        )
                    }
                    LinearProgressIndicator(
                        progress = { ok.toFloat() / sources.size },
                        modifier = Modifier.fillMaxWidth().padding(top = 4.dp, bottom = 8.dp),
                        color = OK_GREEN,
                        drawStopIndicator = {},
                    )
                    sources.forEachIndexed { i, s ->
                        if (i > 0) HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
                        SourceRow(s)
                    }
                }
            }
        }
        item {
            Section("Credits") {
                listOf(
                    "Weather and air-quality data by Open-Meteo.com (CC BY 4.0), from ECMWF, NOAA, DWD, Environment Canada, JMA, Météo-France, UK Met Office, CMA, BoM and Copernicus CAMS.",
                    "Forecast from MET Norway (api.met.no, CC BY 4.0).",
                    "US forecasts and alerts from the National Weather Service (public domain).",
                    "Tropical cyclones from GDACS, the Global Disaster Alert and Coordination System (UN / European Commission JRC), and the NOAA National Hurricane Center.",
                    "Map outlines from Natural Earth (public domain).",
                ).forEach {
                    Text("• $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.padding(vertical = 3.dp))
                }
            }
        }
    }
}

@Composable
private fun Section(title: String, content: @Composable () -> Unit) {
    Surface(shape = RoundedCornerShape(24.dp), color = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(18.dp)) {
            Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold, modifier = Modifier.padding(bottom = 12.dp))
            content()
        }
    }
}

@Composable
private fun SourceRow(s: SourceStatus) {
    Row(Modifier.fillMaxWidth().padding(vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(50)).background(
                when (s.state) {
                    SourceState.Ok -> OK_GREEN.copy(alpha = 0.15f)
                    SourceState.Failed -> MaterialTheme.colorScheme.error.copy(alpha = 0.15f)
                    else -> MaterialTheme.colorScheme.onSurface.copy(alpha = 0.06f)
                },
            ),
            contentAlignment = Alignment.Center,
        ) {
            when (s.state) {
                SourceState.Loading -> CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp)
                SourceState.Ok -> Icon(Icons.Outlined.CheckCircle, "OK", Modifier.size(20.dp), tint = OK_GREEN)
                SourceState.Failed -> Icon(Icons.Outlined.ErrorOutline, "Failed", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.error)
                SourceState.Skipped -> Icon(Icons.Outlined.RemoveCircleOutline, "Not used here", Modifier.size(20.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(s.name, style = MaterialTheme.typography.bodyLarge, fontWeight = FontWeight.Medium)
            Text(s.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            val detail = when (s.state) {
                SourceState.Ok -> "OK in ${s.millis} ms · ${ago(s.at)}" + if (s.detail.isNotBlank()) " · ${s.detail}" else ""
                SourceState.Loading -> "Loading…"
                else -> s.detail
            }
            Text(
                detail,
                style = MaterialTheme.typography.labelSmall,
                color = if (s.state == SourceState.Failed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
