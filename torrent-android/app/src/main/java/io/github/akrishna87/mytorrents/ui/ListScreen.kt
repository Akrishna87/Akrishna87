package io.github.akrishna87.mytorrents.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Upload
import androidx.compose.material.icons.rounded.WifiOff
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.mytorrents.Filter
import io.github.akrishna87.mytorrents.Screen
import io.github.akrishna87.mytorrents.TorrentsViewModel
import io.github.akrishna87.mytorrents.engine.Status
import io.github.akrishna87.mytorrents.engine.Torrent
import io.github.akrishna87.mytorrents.formatBytes
import io.github.akrishna87.mytorrents.formatDuration
import io.github.akrishna87.mytorrents.formatPercent
import io.github.akrishna87.mytorrents.formatSpeed

@Composable
fun ListScreen(vm: TorrentsViewModel) {
    val torrents by vm.torrents.collectAsState()
    val waitingForWifi by vm.waitingForWifi.collectAsState()
    val shown = torrents.filter {
        when (vm.filter) {
            Filter.All -> true
            Filter.Active -> it.status != Status.Finished
            Filter.Done -> it.progress >= 1f
        }
    }

    Box(Modifier.fillMaxSize()) {
        LazyColumn(
            Modifier.fillMaxSize(),
            contentPadding = PaddingValues(bottom = 120.dp),
        ) {
            item { Header(vm, torrents) }
            if (waitingForWifi) item { WifiBanner() }
            if (torrents.isNotEmpty()) item { Filters(vm) }
            items(shown, key = { it.id }) { t -> TorrentRow(vm, t) }
            if (torrents.isEmpty()) item { EmptyState() }
            else if (shown.isEmpty()) item {
                Text(
                    "Nothing here",
                    Modifier.fillMaxWidth().padding(40.dp),
                    textAlign = TextAlign.Center,
                    color = Palette.SubText,
                )
            }
        }
        ExtendedFloatingActionButton(
            onClick = { vm.showAdd = true },
            icon = { Icon(Icons.Rounded.Add, null) },
            text = { Text("Add", fontWeight = FontWeight.Bold) },
            containerColor = Palette.Mint,
            contentColor = Palette.Background,
            shape = CircleShape,
            modifier = Modifier.align(Alignment.BottomEnd).navigationBarsPadding().padding(20.dp)
                .semantics { contentDescription = "Add a torrent" },
        )
    }
}

@Composable
private fun Header(vm: TorrentsViewModel, torrents: List<Torrent>) {
    var menu by remember { mutableStateOf(false) }
    Column(Modifier.statusBarsPadding().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 8.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Torrents", style = MaterialTheme.typography.headlineLarge, modifier = Modifier.weight(1f))
            IconButton(onClick = { vm.open(Screen.Settings) }) { Icon(Icons.Rounded.Settings, "Settings") }
            Box {
                IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More") }
                DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                    DropdownMenuItem(text = { Text("Pause all") }, onClick = { menu = false; vm.pauseAll() })
                    DropdownMenuItem(text = { Text("Resume all") }, onClick = { menu = false; vm.resumeAll() })
                }
            }
        }
        if (torrents.isNotEmpty()) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Speed(Icons.Rounded.Download, torrents.sumOf { it.downloadRate }, Palette.Blue)
                Spacer(Modifier.width(16.dp))
                Speed(Icons.Rounded.Upload, torrents.sumOf { it.uploadRate }, Palette.Mint)
            }
        }
    }
}

@Composable
private fun Speed(icon: ImageVector, rate: Int, tint: androidx.compose.ui.graphics.Color) {
    Icon(icon, null, tint = tint, modifier = Modifier.size(16.dp))
    Spacer(Modifier.width(4.dp))
    Text(formatSpeed(rate), style = MaterialTheme.typography.bodyMedium, color = Palette.SubText)
}

@Composable
private fun WifiBanner() {
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(Palette.Amber.copy(alpha = 0.15f))
            .padding(14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(Icons.Rounded.WifiOff, null, tint = Palette.Amber)
        Spacer(Modifier.width(12.dp))
        Text("Waiting for Wi-Fi. You can turn this off in Settings.", color = Palette.Amber)
    }
}

@Composable
private fun Filters(vm: TorrentsViewModel) {
    Row(Modifier.padding(horizontal = 16.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        Filter.entries.forEach { f ->
            FilterChip(
                selected = vm.filter == f,
                onClick = { vm.filter = f },
                label = { Text(f.label) },
                shape = CircleShape,
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = Palette.Mint,
                    selectedLabelColor = Palette.Background,
                ),
            )
        }
    }
}

@Composable
private fun TorrentRow(vm: TorrentsViewModel, t: Torrent) {
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { vm.open(Screen.Details(t.id)) }
            .padding(start = 16.dp, end = 4.dp, top = 12.dp, bottom = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        StatusIcon(t.status)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(t.name, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(8.dp))
            LinearProgressIndicator(
                progress = { t.progress },
                modifier = Modifier.fillMaxWidth().height(4.dp).clip(CircleShape),
                color = t.status.color(),
                trackColor = Palette.Highlight,
                drawStopIndicator = {},
                gapSize = 0.dp,
            )
            Spacer(Modifier.height(6.dp))
            Text(statusLine(t), style = MaterialTheme.typography.bodySmall, color = Palette.SubText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        PauseButton(t) { vm.togglePause(t) }
    }
}

@Composable
fun StatusIcon(status: Status, size: Int = 44) {
    val icon = when (status) {
        Status.Finished, Status.Seeding -> Icons.Rounded.CheckCircle
        Status.Error -> Icons.Rounded.ErrorOutline
        Status.Paused -> Icons.Rounded.Pause
        else -> Icons.Rounded.Download
    }
    Box(
        Modifier.size(size.dp).clip(RoundedCornerShape(12.dp)).background(status.color().copy(alpha = 0.16f)),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, null, tint = status.color(), modifier = Modifier.size((size / 2).dp))
    }
}

@Composable
fun PauseButton(t: Torrent, onClick: () -> Unit) {
    val paused = t.status in setOf(Status.Paused, Status.Finished, Status.Error)
    val label = when (t.status) {
        Status.Finished -> "Share again"
        Status.Seeding -> "Stop sharing"
        Status.Error -> "Retry"
        else -> if (paused) "Resume" else "Pause"
    }
    IconButton(onClick = onClick) {
        Icon(if (paused) Icons.Rounded.PlayArrow else Icons.Rounded.Pause, label, tint = Palette.SubText)
    }
}

/** "Downloading · 2.1 MB/s · 45% · 3 min left", "Finished · 1.4 GB", ... */
fun statusLine(t: Torrent): String = when (t.status) {
    Status.GettingInfo -> "Getting info from peers…" + if (t.peers > 0) " · ${t.peers} peers" else ""
    Status.Checking -> "Checking files · ${formatPercent(t.progress)}"
    Status.Queued -> "Queued · ${formatPercent(t.progress)}"
    Status.Paused -> "Paused · ${formatPercent(t.progress)} of ${formatBytes(t.wantedBytes)}"
    Status.Error -> "Error: ${t.error}"
    Status.Finished -> "Finished · ${formatBytes(t.wantedBytes)}"
    Status.Seeding -> "Sharing · ↑ ${formatSpeed(t.uploadRate)} · ${t.peers} peers"
    Status.Downloading -> buildString {
        append(formatPercent(t.progress))
        append(" of ")
        append(formatBytes(t.wantedBytes))
        if (t.downloadRate > 0) {
            append(" · ")
            append(formatSpeed(t.downloadRate))
            if (t.etaSeconds >= 0) append(" · ${formatDuration(t.etaSeconds)} left")
        } else {
            append(if (t.peers == 0) " · Looking for peers…" else " · ${t.peers} peers")
        }
    }
}

@Composable
private fun EmptyState() {
    Column(
        Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 48.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            Modifier.size(96.dp).clip(RoundedCornerShape(28.dp)).background(BrandGradient),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.CloudDownload, null, tint = Palette.Text, modifier = Modifier.size(48.dp))
        }
        Spacer(Modifier.height(24.dp))
        Text("No torrents yet", style = MaterialTheme.typography.headlineSmall)
        Spacer(Modifier.height(10.dp))
        Text(
            "Tap Add to paste a magnet link or pick a .torrent file. You can also tap a magnet link " +
                "in your browser, or open a .torrent file, and it comes straight here.",
            textAlign = TextAlign.Center,
            color = Palette.SubText,
        )
        Spacer(Modifier.height(10.dp))
        Text(
            "Downloads are saved in Download/Torrents.",
            textAlign = TextAlign.Center,
            style = MaterialTheme.typography.bodySmall,
            color = Palette.Faint,
        )
    }
}
