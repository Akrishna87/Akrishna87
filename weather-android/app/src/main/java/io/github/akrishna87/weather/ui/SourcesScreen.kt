package io.github.akrishna87.weather.ui

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CheckCircle
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.RemoveCircleOutline
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import io.github.akrishna87.weather.WeatherViewModel
import io.github.akrishna87.weather.data.SourceState
import io.github.akrishna87.weather.data.SourceStatus
import io.github.akrishna87.weather.data.WindUnit

/** Units, and how every data source fared on its last fetch. */
@Composable
fun SourcesScreen(vm: WeatherViewModel) {
    val units by vm.units.collectAsState()
    val sources by vm.sources.collectAsState()
    val ok = sources.count { it.state == SourceState.Ok }

    LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Column {
                Text("Units", style = MaterialTheme.typography.titleMedium)
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.padding(top = 8.dp)) {
                    FilterChip(selected = !units.fahrenheit, onClick = { vm.setUnits(units.copy(fahrenheit = false)) }, label = { Text("°C, mm, hPa") })
                    FilterChip(selected = units.fahrenheit, onClick = { vm.setUnits(units.copy(fahrenheit = true)) }, label = { Text("°F, in, inHg") })
                }
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    WindUnit.entries.forEach { w ->
                        FilterChip(selected = units.wind == w, onClick = { vm.setUnits(units.copy(wind = w)) }, label = { Text(w.label) })
                    }
                }
            }
        }
        item {
            Column {
                Text("Data sources", style = MaterialTheme.typography.titleMedium, modifier = Modifier.padding(top = 8.dp))
                Text(
                    if (sources.isEmpty()) "Nothing fetched yet." else "$ok of ${sources.size} answered on the last refresh. No accounts or API keys: every source is free and open.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        items(sources, key = { it.id }) { SourceRow(it) }
        item {
            Card {
                Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Credits", style = MaterialTheme.typography.titleSmall)
                    listOf(
                        "Weather and air-quality data by Open-Meteo.com (CC BY 4.0), from ECMWF, NOAA, DWD, Environment Canada, JMA, Météo-France, UK Met Office, CMA, BoM and Copernicus CAMS.",
                        "Forecast from MET Norway (api.met.no, CC BY 4.0).",
                        "US forecasts and alerts from the National Weather Service (public domain).",
                        "Tropical cyclones from GDACS, the Global Disaster Alert and Coordination System (UN / European Commission JRC), and the NOAA National Hurricane Center.",
                        "Map outlines from Natural Earth (public domain).",
                    ).forEach { Text("• $it", style = MaterialTheme.typography.bodySmall) }
                }
            }
        }
    }
}

@Composable
private fun SourceRow(s: SourceStatus) {
    Card {
        Row(Modifier.fillMaxWidth().padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            when (s.state) {
                SourceState.Loading -> CircularProgressIndicator(Modifier.size(22.dp), strokeWidth = 2.dp)
                SourceState.Ok -> Icon(Icons.Outlined.CheckCircle, "OK", tint = Color(0xFF22C55E))
                SourceState.Failed -> Icon(Icons.Outlined.ErrorOutline, "Failed", tint = MaterialTheme.colorScheme.error)
                SourceState.Skipped -> Icon(Icons.Outlined.RemoveCircleOutline, "Not used here", tint = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(s.name, fontWeight = FontWeight.Medium)
                Text(s.role, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                val detail = when (s.state) {
                    SourceState.Ok -> "OK in ${s.millis} ms · ${ago(s.at)}"
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
}
