package io.github.akrishna87.signal.ui

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.SimCardAlert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.signal.Bands
import io.github.akrishna87.signal.CellReading
import io.github.akrishna87.signal.HISTORY_MILLIS
import io.github.akrishna87.signal.Permissions
import io.github.akrishna87.signal.Rating
import io.github.akrishna87.signal.SignalViewModel
import io.github.akrishna87.signal.SimReading
import io.github.akrishna87.signal.Tech
import kotlin.math.roundToInt

@Composable
fun NowScreen(vm: SignalViewModel, permissions: Permissions) {
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val history by vm.history.collectAsStateWithLifecycle()
    var selected by rememberSaveable { mutableIntStateOf(0) }

    val snap = snapshot
    if (snap == null) {
        Centered { CircularProgressIndicator(); Text("Reading the signal…") }
        return
    }
    if (snap.noSim || snap.sims.isEmpty()) {
        Centered {
            Icon(Icons.Rounded.SimCardAlert, null, tint = MaterialTheme.colorScheme.onSurfaceVariant)
            Text("No SIM card", style = MaterialTheme.typography.titleLarge)
            Text(
                "Put in a SIM card to measure its signal.",
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        return
    }
    val sim = snap.sims[selected.coerceIn(0, snap.sims.lastIndex)]

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (snap.sims.size > 1) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    snap.sims.forEachIndexed { i, s ->
                        FilterChip(selected = s.subId == sim.subId, onClick = { selected = i }, label = { Text(s.label) })
                    }
                }
            }
        }
        item { PermissionHints(permissions) }
        item { StrengthCard(sim) }
        sim.main?.let { main -> if (main.rsrq != null || main.sinr != null) item { QualityCard(main) } }
        sim.nr?.let { nr -> item { NrCard(nr) } }
        sim.main?.let { main ->
            item {
                SectionCard("Last 10 minutes") {
                    HistoryGraph(history[sim.subId].orEmpty(), main.tech, snap.atMillis, HISTORY_MILLIS)
                    HistorySummary(history[sim.subId].orEmpty().filter { it.tech == main.tech }.mapNotNull { it.dbm })
                }
            }
        }
        sim.main?.let { main -> if (permissions.location) item { TowerCard(main) } }
        if (sim.neighbours.isNotEmpty()) item { NeighboursCard(sim.neighbours) }
    }
}

@Composable
private fun Centered(content: @Composable () -> Unit) {
    Box(Modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(12.dp)) {
            content()
        }
    }
}

@Composable
private fun PermissionHints(p: Permissions) {
    val context = LocalContext.current
    val hint = when {
        !p.location -> "Tower details are hidden until you allow location." to
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        !p.locationOn -> "Turn on the phone's Location switch to see tower details." to
            Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)
        !p.phone -> "Only the default SIM is shown until you allow phone access." to
            Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS, Uri.fromParts("package", context.packageName, null))
        else -> null
    } ?: return
    SectionCard {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Icon(Icons.Rounded.Info, null, tint = MaterialTheme.colorScheme.primary)
            Text(hint.first, Modifier.weight(1f), style = MaterialTheme.typography.bodyMedium)
            TextButton(onClick = { runCatching { context.startActivity(hint.second) } }) { Text("Fix") }
        }
    }
}

@Composable
private fun StrengthCard(sim: SimReading) {
    val main = sim.main
    SectionCard {
        Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(sim.operator.ifBlank { "SIM ${sim.slot + 1}" }, style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text("SIM ${sim.slot + 1}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            AssistChip(onClick = {}, label = { Text(sim.networkLabel) })
        }
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Gauge(main?.tech, main?.dbm, main?.rating)
        }
        if (main?.dbm != null) {
            Text(
                "Signal strength (${main.tech.label} ${main.tech.strengthName})" +
                    (sim.systemLevel?.let { " · $it of 4 bars" } ?: ""),
                Modifier.fillMaxWidth(),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(strengthAdvice(main), style = MaterialTheme.typography.bodyMedium)
        } else {
            Text(
                if (sim.service.label.isNotBlank()) sim.service.label else "This phone isn't reporting a signal reading right now.",
                style = MaterialTheme.typography.bodyMedium,
            )
        }
    }
}

private fun strengthAdvice(c: CellReading): String = when (c.rating?.score) {
    4 -> "Strong signal. Calls and data should work well here."
    3 -> "Good signal. Everything should work fine."
    2 -> "Usable. Data may slow down at busy times."
    1 -> "Weak. Expect slow data and the odd dropped call."
    0 -> "Very weak. Calls may fail; try near a window or higher up."
    else -> ""
}

@Composable
private fun QualityCard(c: CellReading) {
    SectionCard("Signal quality") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            c.sinr?.let { Metric(c.tech.qualityName ?: "Quality", "$it dB", c.qualityRating, Modifier.weight(1f)) }
            c.rsrq?.let { Metric("RSRQ", "$it dB", null, Modifier.weight(1f)) }
        }
        Text(
            "Strength is how loud the tower is. Quality is how clearly it can be heard over noise " +
                "and other towers. Full bars with poor quality still means slow data.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun Metric(label: String, value: String, rating: Rating?, modifier: Modifier = Modifier) {
    Column(modifier) {
        Text(label, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(value, style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
        if (rating != null) {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                RatingDot(rating)
                Text(rating.label, style = MaterialTheme.typography.bodySmall, color = ratingColor(rating))
            }
        }
    }
}

@Composable
private fun NrCard(nr: CellReading) {
    SectionCard("5G layer") {
        Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            Metric("SS-RSRP", nr.dbm?.let { "$it dBm" } ?: "–", nr.rating, Modifier.weight(1f))
            Metric("SS-SINR", nr.sinr?.let { "$it dB" } ?: "–", nr.qualityRating, Modifier.weight(1f))
        }
        Bands.describe(nr.bands, nr.frequencyMhz)?.let { InfoRow("Band", it) }
        Text(
            "Your phone is on 5G that rides on a 4G connection (called NSA). The big number above is " +
                "the 4G anchor; this is the 5G signal used for extra speed.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun HistorySummary(values: List<Int>) {
    if (values.isEmpty()) return
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
        Text("Lowest ${values.min()}", style = MaterialTheme.typography.bodySmall)
        Text("Average ${values.average().roundToInt()}", style = MaterialTheme.typography.bodySmall)
        Text("Highest ${values.max()} dBm", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
private fun TowerCard(c: CellReading) {
    SectionCard("Tower") {
        InfoRow("Band", Bands.describe(c.bands, c.frequencyMhz))
        InfoRow(
            when (c.tech) { Tech.LTE -> "EARFCN"; Tech.NR -> "NR-ARFCN"; Tech.WCDMA, Tech.TDSCDMA -> "UARFCN"; else -> "Channel" },
            c.channel?.toString(),
        )
        InfoRow(
            when (c.tech) { Tech.WCDMA -> "Scrambling code"; Tech.GSM -> "BSIC"; else -> "PCI" },
            c.pci?.toString(),
        )
        InfoRow(if (c.tech == Tech.NR) "gNB (site)" else "eNB (site)", c.siteId?.toString())
        InfoRow("Cell ID", c.cellId?.toString())
        InfoRow(if (c.tech == Tech.LTE || c.tech == Tech.NR) "TAC" else "LAC", c.areaCode?.toString())
        InfoRow("Network code", c.plmn?.let { "${it.take(3)} ${it.drop(3)}" })
        if (c.channel == null && c.cellId == null) {
            Text(
                "Your phone isn't sharing tower details right now.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun NeighboursCard(cells: List<CellReading>) {
    SectionCard("Other towers nearby") {
        for (c in cells) {
            Row(Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                RatingDot(c.rating)
                Text(
                    listOfNotNull(
                        c.tech.label,
                        c.bands.joinToString("+").ifEmpty { null },
                        c.pci?.let { "PCI $it" },
                    ).joinToString(" · "),
                    Modifier.weight(1f),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Text("${c.dbm} dBm", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
        Text(
            "Your phone switches to one of these when it becomes stronger than the current tower.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
