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
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Description
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Share
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.CheckboxDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.mytorrents.TorrentsViewModel
import io.github.akrishna87.mytorrents.engine.Status
import io.github.akrishna87.mytorrents.engine.Torrent
import io.github.akrishna87.mytorrents.engine.TorrentFile
import io.github.akrishna87.mytorrents.formatBytes
import io.github.akrishna87.mytorrents.formatDuration
import io.github.akrishna87.mytorrents.formatPercent
import io.github.akrishna87.mytorrents.formatSpeed
import java.text.DateFormat
import java.util.Date

@Composable
fun DetailsScreen(vm: TorrentsViewModel, id: String) {
    val torrents by vm.torrents.collectAsState()
    val t = torrents.firstOrNull { it.id == id }
    val files by remember(id) { vm.files(id) }.collectAsState(initial = emptyList())
    var confirmDelete by remember { mutableStateOf(false) }

    // Gone (deleted, or a stale notification): back to the list.
    LaunchedEffect(t == null) {
        if (t == null) {
            kotlinx.coroutines.delay(3000)
            if (vm.torrents.value.none { it.id == id }) vm.back()
        }
    }

    LazyColumn(Modifier.fillMaxSize().navigationBarsPadding(), contentPadding = PaddingValues(bottom = 48.dp)) {
        item {
            Row(Modifier.statusBarsPadding().padding(4.dp), verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = { vm.back() }) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
                Spacer(Modifier.weight(1f))
                if (t != null) {
                    IconButton(onClick = { vm.shareMagnet(t) }) { Icon(Icons.Rounded.Share, "Share magnet link") }
                    IconButton(onClick = { confirmDelete = true }) { Icon(Icons.Rounded.Delete, "Delete") }
                }
            }
        }
        if (t == null) {
            item { Text("Loading…", Modifier.padding(24.dp), color = Palette.SubText) }
            return@LazyColumn
        }
        item { Summary(vm, t) }
        item { Stats(t) }
        item {
            Text(
                if (files.isEmpty()) "FILES" else "FILES · ${files.size}",
                style = MaterialTheme.typography.labelSmall,
                color = Palette.SubText,
                modifier = Modifier.padding(start = 20.dp, top = 24.dp, bottom = 4.dp),
            )
            if (files.isEmpty()) {
                Text(
                    if (t.hasMetadata) "Loading…" else "The list of files appears once the torrent's info has arrived from other people.",
                    Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                    color = Palette.SubText,
                    style = MaterialTheme.typography.bodySmall,
                )
            }
        }
        items(files, key = { it.index }) { f -> FileRow(vm, t, f) }
    }

    if (confirmDelete && t != null) DeleteDialog(t, onDismiss = { confirmDelete = false }) { deleteFiles ->
        confirmDelete = false
        vm.remove(t, deleteFiles)
    }
}

@Composable
private fun Summary(vm: TorrentsViewModel, t: Torrent) {
    Column(Modifier.padding(horizontal = 20.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            StatusIcon(t.status, size = 52)
            Spacer(Modifier.width(14.dp))
            Text(t.name, style = MaterialTheme.typography.titleLarge, maxLines = 3, overflow = TextOverflow.Ellipsis)
        }
        Spacer(Modifier.height(20.dp))
        Row(verticalAlignment = Alignment.Bottom) {
            Text(formatPercent(t.progress), style = MaterialTheme.typography.headlineMedium, color = t.status.color())
            Spacer(Modifier.width(10.dp))
            Text(
                statusLine(t).substringBefore(" · ").takeIf { t.status != Status.Downloading } ?: "Downloading",
                color = Palette.SubText,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }
        Spacer(Modifier.height(8.dp))
        LinearProgressIndicator(
            progress = { t.progress },
            modifier = Modifier.fillMaxWidth().height(6.dp).clip(CircleShape),
            color = t.status.color(),
            trackColor = Palette.Highlight,
            drawStopIndicator = {},
            gapSize = 0.dp,
        )
        if (t.error != null) {
            Spacer(Modifier.height(10.dp))
            Text(t.error, color = Palette.Red, style = MaterialTheme.typography.bodySmall)
        }
        Spacer(Modifier.height(16.dp))
        val paused = t.status in setOf(Status.Paused, Status.Finished, Status.Error)
        val label = when (t.status) {
            Status.Finished -> "Share again"
            Status.Seeding -> "Stop sharing"
            Status.Error -> "Retry"
            else -> if (paused) "Resume" else "Pause"
        }
        if (paused) {
            Button(
                onClick = { vm.togglePause(t) },
                shape = CircleShape,
                colors = ButtonDefaults.buttonColors(containerColor = Palette.Mint, contentColor = Palette.Background),
                modifier = Modifier.fillMaxWidth().height(48.dp),
            ) {
                Icon(Icons.Rounded.PlayArrow, null)
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Bold)
            }
        } else {
            OutlinedButton(onClick = { vm.togglePause(t) }, shape = CircleShape, modifier = Modifier.fillMaxWidth().height(48.dp)) {
                Icon(Icons.Rounded.Pause, null)
                Spacer(Modifier.width(8.dp))
                Text(label, fontWeight = FontWeight.Bold)
            }
        }
    }
}

@Composable
private fun Stats(t: Torrent) {
    val rows = buildList {
        add("Downloaded" to "${formatBytes(t.doneBytes)} of ${formatBytes(t.wantedBytes)}")
        add("Speed" to "↓ ${formatSpeed(t.downloadRate)}   ↑ ${formatSpeed(t.uploadRate)}")
        if (t.etaSeconds >= 0) add("Time left" to formatDuration(t.etaSeconds))
        add("Peers" to "${t.peers} connected (${t.seeds} with the whole thing)")
        add("Shared" to formatBytes(t.uploadedBytes))
        add("Saved in" to t.savePath)
        if (t.addedAt > 0) add("Added" to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(t.addedAt * 1000)))
    }
    Column(
        Modifier
            .padding(horizontal = 16.dp, vertical = 20.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(Palette.Elevated)
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        rows.forEach { (label, value) ->
            Row {
                Text(label, color = Palette.SubText, modifier = Modifier.width(110.dp), style = MaterialTheme.typography.bodyMedium)
                Text(value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun FileRow(vm: TorrentsViewModel, t: Torrent, f: TorrentFile) {
    val progress = if (f.size > 0) f.done.toFloat() / f.size else 1f
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { vm.openFile(t.id, f) }
            .padding(start = 8.dp, end = 20.dp, top = 6.dp, bottom = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(
            checked = f.wanted,
            onCheckedChange = { vm.setWanted(t.id, f, it) },
            colors = CheckboxDefaults.colors(checkedColor = Palette.Mint, checkmarkColor = Palette.Background),
        )
        Box(
            Modifier.size(36.dp).clip(RoundedCornerShape(10.dp)).background(Palette.Elevated2),
            contentAlignment = Alignment.Center,
        ) {
            Icon(Icons.Rounded.Description, null, tint = Palette.SubText, modifier = Modifier.size(20.dp))
        }
        Spacer(Modifier.width(12.dp))
        Column(Modifier.weight(1f)) {
            Text(
                f.path.substringAfterLast('/'),
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                color = if (f.wanted) Palette.Text else Palette.Faint,
            )
            Text(
                when {
                    !f.wanted -> "Skipped · ${formatBytes(f.size)}"
                    progress >= 1f -> "${formatBytes(f.size)} · Tap to open"
                    else -> "${formatPercent(progress)} of ${formatBytes(f.size)}"
                },
                style = MaterialTheme.typography.bodySmall,
                color = Palette.SubText,
            )
        }
    }
}

@Composable
private fun DeleteDialog(t: Torrent, onDismiss: () -> Unit, onDelete: (deleteFiles: Boolean) -> Unit) {
    var deleteFiles by remember { mutableStateOf(t.progress < 1f) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Remove this torrent?") },
        text = {
            Column {
                Text(t.name, color = Palette.SubText, maxLines = 3, overflow = TextOverflow.Ellipsis)
                Spacer(Modifier.height(12.dp))
                Row(
                    Modifier.fillMaxWidth().clip(RoundedCornerShape(10.dp)).clickable { deleteFiles = !deleteFiles },
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Checkbox(checked = deleteFiles, onCheckedChange = { deleteFiles = it })
                    Text("Also delete the downloaded files")
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onDelete(deleteFiles) }) { Text("Remove", color = Palette.Red, fontWeight = FontWeight.Bold) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
        containerColor = Palette.Elevated2,
    )
}
