package io.github.akrishna87.signal.ui

import android.text.format.DateUtils
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.EmojiEvents
import androidx.compose.material.icons.rounded.Straighten
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.akrishna87.signal.Measuring
import io.github.akrishna87.signal.SignalViewModel
import io.github.akrishna87.signal.Spot
import io.github.akrishna87.signal.SpotMath
import io.github.akrishna87.signal.SpotResult
import kotlinx.coroutines.delay

private val PLACES = listOf("Bedroom", "Living room", "Kitchen", "Balcony", "Terrace", "Office", "By the window")

@Composable
fun SpotScreen(vm: SignalViewModel) {
    // Keeps the monitor running while this tab is open, even though it doesn't show the snapshot.
    val snapshot by vm.snapshot.collectAsStateWithLifecycle()
    val spots by vm.spots.collectAsStateWithLifecycle()
    val measuring by vm.measuring.collectAsStateWithLifecycle()
    val justSaved by vm.justSaved.collectAsStateWithLifecycle()
    var asking by rememberSaveable { mutableStateOf(false) }
    var deleting by rememberSaveable { mutableStateOf<Long?>(null) }

    // Results are grouped per SIM ("SIM 1 · Jio"), since each operator has its own towers.
    val sims = spots.flatMap { s -> s.results.map { it.sim } }.distinct().sorted()
    var simChoice by rememberSaveable { mutableStateOf<String?>(null) }
    val sim = simChoice?.takeIf { it in sims } ?: sims.firstOrNull()

    val view = LocalView.current
    val busy = measuring != null
    DisposableEffect(busy) {
        view.keepScreenOn = busy
        onDispose { view.keepScreenOn = false }
    }

    LazyColumn(
        Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        item {
            SectionCard {
                Text("Find the best spot", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.SemiBold)
                Text(
                    "Stand in a spot, tap Measure and hold the phone as you normally would for 20 seconds. " +
                        "Do this in a few places and Alaimaani ranks them, so you know where calls and data work best.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                val m = measuring
                if (m == null) {
                    Button(
                        onClick = { asking = true },
                        enabled = snapshot?.sims?.isNotEmpty() == true,
                        modifier = Modifier.fillMaxWidth(),
                    ) {
                        Icon(Icons.Rounded.Straighten, null)
                        Text("  Measure a spot")
                    }
                } else {
                    MeasuringPanel(m, onCancel = vm::cancelMeasuring)
                }
            }
        }
        if (sims.size > 1) {
            item {
                Row(Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    sims.forEach { s -> FilterChip(selected = s == sim, onClick = { simChoice = s }, label = { Text(s) }) }
                }
            }
        }
        if (sim != null) {
            val ranked = SpotMath.ranked(spots, sim)
            items(ranked, key = { it.first.id }) { (spot, result) ->
                SpotRow(
                    rank = ranked.indexOfFirst { it.first.id == spot.id } + 1,
                    spot = spot,
                    result = result,
                    best = ranked.size > 1 && ranked.first().first.id == spot.id,
                    fresh = spot.id == justSaved,
                    onDelete = { deleting = spot.id },
                )
            }
        } else if (measuring == null) {
            item {
                Text(
                    "No spots yet. Try your bedroom, the living room and by a window to start.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }

    if (asking) NameDialog(onStart = { vm.startMeasuring(it); asking = false }, onDismiss = { asking = false })
    deleting?.let { id ->
        val name = spots.firstOrNull { it.id == id }?.name.orEmpty()
        AlertDialog(
            onDismissRequest = { deleting = null },
            title = { Text("Delete $name?") },
            confirmButton = { TextButton(onClick = { vm.deleteSpot(id); deleting = null }) { Text("Delete") } },
            dismissButton = { TextButton(onClick = { deleting = null }) { Text("Cancel") } },
        )
    }
}

@Composable
private fun MeasuringPanel(m: Measuring, onCancel: () -> Unit) {
    var now by remember { mutableLongStateOf(System.currentTimeMillis()) }
    LaunchedEffect(m.startedAt) {
        while (true) {
            now = System.currentTimeMillis()
            delay(200)
        }
    }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("Measuring ${m.name}… ${m.secondsLeft(now)} s", style = MaterialTheme.typography.titleMedium)
        LinearProgressIndicator(progress = { m.progress(now) }, modifier = Modifier.fillMaxWidth())
        for (s in m.latest) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text(s.sim, style = MaterialTheme.typography.bodyMedium)
                Text("${s.dbm} dBm", style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
            }
        }
        Text("Keep still and hold the phone normally.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        OutlinedButton(onClick = onCancel, modifier = Modifier.fillMaxWidth()) { Text("Cancel") }
    }
}

@Composable
private fun SpotRow(rank: Int, spot: Spot, result: SpotResult, best: Boolean, fresh: Boolean, onDelete: () -> Unit) {
    val container = if (best) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer
    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = container)) {
        Row(
            Modifier.fillMaxWidth().padding(start = 16.dp, top = 12.dp, bottom = 12.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (best) Icon(Icons.Rounded.EmojiEvents, "Best spot", tint = MaterialTheme.colorScheme.primary)
            else Text("$rank", Modifier.width(24.dp), style = MaterialTheme.typography.titleLarge, color = MaterialTheme.colorScheme.onSurfaceVariant)
            Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    spot.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (fresh) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurface,
                )
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    RatingDot(result.rating)
                    Text(
                        "${result.rating.label} · ${result.network}",
                        style = MaterialTheme.typography.bodySmall,
                        color = ratingColor(result.rating),
                    )
                }
                Text(
                    listOfNotNull(
                        "${result.minDbm} to ${result.maxDbm} dBm",
                        result.avgSinr?.let { "${result.tech.qualityName ?: "quality"} $it dB" },
                        if (System.currentTimeMillis() - spot.atMillis < DateUtils.MINUTE_IN_MILLIS) "just now"
                        else DateUtils.getRelativeTimeSpanString(spot.atMillis).toString(),
                    ).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(horizontalAlignment = Alignment.End) {
                Text("${result.avgDbm}", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.SemiBold)
                Text("dBm", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Rounded.Delete, "Delete ${spot.name}") }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
private fun NameDialog(onStart: (String) -> Unit, onDismiss: () -> Unit) {
    var name by rememberSaveable { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Where are you?") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it.take(40) },
                    label = { Text("Spot name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                FlowRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    PLACES.forEach { p -> SuggestionChip(onClick = { name = p }, label = { Text(p) }) }
                }
            }
        },
        confirmButton = { TextButton(onClick = { onStart(name) }) { Text("Start") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
