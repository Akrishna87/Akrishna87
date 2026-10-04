@file:OptIn(ExperimentalMaterial3Api::class)

package io.github.akrishna87.songgrab.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.ContentPaste
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.ErrorOutline
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Info
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.Player
import io.github.akrishna87.songgrab.DownloadService
import io.github.akrishna87.songgrab.Engine
import io.github.akrishna87.songgrab.Format
import io.github.akrishna87.songgrab.Job
import io.github.akrishna87.songgrab.Jobs
import io.github.akrishna87.songgrab.Links
import io.github.akrishna87.songgrab.PlayerConnection
import io.github.akrishna87.songgrab.Saver
import io.github.akrishna87.songgrab.Song
import io.github.akrishna87.songgrab.SongGrabViewModel
import io.github.akrishna87.songgrab.Stage
import io.github.akrishna87.songgrab.statusText
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.text.DateFormat
import java.util.Date

@Composable
fun SongGrabApp(model: SongGrabViewModel, onSave: (String, Format) -> Unit) {
    val songs by model.songs.collectAsStateWithLifecycle()
    val jobs by model.jobs.collectAsStateWithLifecycle()
    val format by model.format.collectAsStateWithLifecycle()
    val now by model.player.state.collectAsStateWithLifecycle()
    val engine by model.engine.collectAsStateWithLifecycle()
    var showPlayer by rememberSaveable { mutableStateOf(false) }
    var showAbout by rememberSaveable { mutableStateOf(false) }
    val shares by model.shares.collectAsStateWithLifecycle()
    // A newly shared link closes the full player and dialogs, so its download is in view.
    LaunchedEffect(shares) {
        if (shares > 0) {
            showPlayer = false
            showAbout = false
        }
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        bottomBar = {
            AnimatedVisibility(visible = now.uri != null) {
                MiniPlayer(now, onOpen = { showPlayer = true }, onToggle = model.player::toggle, onNext = model.player::next)
            }
        },
    ) { padding ->
        LazyColumn(
            contentPadding = PaddingValues(
                top = padding.calculateTopPadding() + 8.dp,
                bottom = padding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item { Header(onInfo = { showAbout = true }) }
            item { SaveCard(format, onFormat = model::setFormat, onSave = onSave) }
            if (jobs.isNotEmpty()) {
                item {
                    SectionTitle(
                        "Downloads",
                        action = if (jobs.any { !it.stage.active }) "Clear" else null,
                        onAction = Jobs::clearFinished,
                    )
                }
                items(jobs.asReversed(), key = { it.id }) { job -> JobRow(job) }
            }
            item {
                SectionTitle(
                    if (songs.isEmpty()) "Your songs" else "Your songs · ${songs.size}",
                    action = if (songs.size > 1) "Shuffle" else null,
                    onAction = model::shuffleAll,
                )
            }
            if (songs.isEmpty()) {
                item { EmptySongs() }
            }
            items(songs, key = { it.uri }) { song ->
                SongRow(song, playing = now.uri == song.uri, isPlaying = now.isPlaying, model = model)
            }
        }
    }

    if (showPlayer && now.uri != null) {
        PlayerSheet(model.player, now, onDismiss = { showPlayer = false })
    }
    if (showAbout) {
        AboutDialog(engine, onUpdate = model::updateEngine, onDismiss = { showAbout = false })
    }
}

@Composable
private fun Header(onInfo: () -> Unit) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 8.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text("SongGrab", style = MaterialTheme.typography.headlineMedium)
            Text("A YouTube link in, a song on your phone out", style = MaterialTheme.typography.bodyMedium, color = Palette.SubText)
        }
        IconButton(onClick = onInfo) {
            Icon(Icons.Rounded.Info, contentDescription = "About and updates", tint = Palette.SubText)
        }
    }
}

@Composable
private fun SaveCard(format: Format, onFormat: (Format) -> Unit, onSave: (String, Format) -> Unit) {
    var text by rememberSaveable { mutableStateOf("") }
    val clipboard = LocalClipboardManager.current
    val focus = LocalFocusManager.current
    val url = Links.find(text)
    val save = {
        if (url != null) {
            onSave(url, format)
            text = ""
            focus.clearFocus()
        }
    }
    Surface(
        color = MaterialTheme.colorScheme.surfaceContainer,
        shape = RoundedCornerShape(20.dp),
        modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
    ) {
        Column(Modifier.padding(16.dp)) {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                modifier = Modifier.fillMaxWidth(),
                placeholder = { Text("Paste a YouTube link") },
                singleLine = true,
                isError = text.isNotBlank() && url == null,
                supportingText = if (text.isNotBlank() && url == null) {
                    { Text("That doesn't look like a link. It should start with https://") }
                } else {
                    null
                },
                trailingIcon = {
                    if (text.isEmpty()) {
                        IconButton(onClick = { text = clipboard.getText()?.text.orEmpty() }) {
                            Icon(Icons.Rounded.ContentPaste, contentDescription = "Paste")
                        }
                    } else {
                        IconButton(onClick = { text = "" }) {
                            Icon(Icons.Rounded.Close, contentDescription = "Clear")
                        }
                    }
                },
                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Uri, imeAction = ImeAction.Go),
                keyboardActions = KeyboardActions(onGo = { save() }),
                shape = RoundedCornerShape(14.dp),
                colors = OutlinedTextFieldDefaults.colors(unfocusedBorderColor = MaterialTheme.colorScheme.outline),
            )
            Spacer(Modifier.height(12.dp))
            SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
                Format.entries.forEachIndexed { index, option ->
                    SegmentedButton(
                        selected = option == format,
                        onClick = { onFormat(option) },
                        shape = SegmentedButtonDefaults.itemShape(index, Format.entries.size),
                        label = {
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(option.label, fontWeight = FontWeight.Bold)
                                Text(option.hint, style = MaterialTheme.typography.labelSmall, color = Palette.SubText, maxLines = 1)
                            }
                        },
                    )
                }
            }
            Spacer(Modifier.height(12.dp))
            Button(onClick = save, enabled = url != null, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
                Icon(Icons.Rounded.Download, contentDescription = null)
                Spacer(Modifier.width(8.dp))
                Text("Save as ${format.label}", style = MaterialTheme.typography.titleMedium)
            }
            Spacer(Modifier.height(10.dp))
            Text(
                "Tip: in the YouTube app, tap Share → SongGrab to save without copying the link.",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.Faint,
            )
        }
    }
}

@Composable
private fun SectionTitle(title: String, action: String? = null, onAction: () -> Unit = {}) {
    Row(Modifier.fillMaxWidth().padding(start = 20.dp, end = 8.dp, top = 16.dp, bottom = 4.dp), verticalAlignment = Alignment.CenterVertically) {
        Text(title, style = MaterialTheme.typography.titleLarge, modifier = Modifier.weight(1f))
        if (action != null) TextButton(onClick = onAction) { Text(action) }
    }
}

@Composable
private fun JobRow(job: Job) {
    val context = LocalContext.current
    Row(Modifier.fillMaxWidth().padding(horizontal = 20.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) {
        Column(Modifier.weight(1f)) {
            Text(job.title ?: job.url, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (!job.artist.isNullOrBlank()) {
                Text(job.artist, style = MaterialTheme.typography.bodySmall, color = Palette.SubText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(4.dp))
            Text(
                statusText(job),
                style = MaterialTheme.typography.bodySmall,
                color = if (job.stage == Stage.Failed) MaterialTheme.colorScheme.error else Palette.SubText,
                maxLines = if (job.stage == Stage.Failed) 4 else 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (job.stage.active) {
                Spacer(Modifier.height(6.dp))
                if (job.stage == Stage.Downloading && job.progress >= 0) {
                    LinearProgressIndicator(progress = { job.progress / 100f }, modifier = Modifier.fillMaxWidth().clip(CircleShape))
                } else {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth().clip(CircleShape))
                }
            }
        }
        Spacer(Modifier.width(8.dp))
        when (job.stage) {
            Stage.Done -> Icon(Icons.Rounded.CheckCircle, contentDescription = "Saved", tint = Palette.Gold, modifier = Modifier.padding(12.dp))
            Stage.Failed -> Row {
                IconButton(onClick = { DownloadService.retry(context, job) }) { Icon(Icons.Rounded.Refresh, contentDescription = "Try again") }
                IconButton(onClick = { Jobs.remove(job.id) }) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss") }
            }
            Stage.Cancelled -> IconButton(onClick = { Jobs.remove(job.id) }) { Icon(Icons.Rounded.Close, contentDescription = "Dismiss") }
            else -> IconButton(onClick = { Jobs.cancel(job.id) }) { Icon(Icons.Rounded.Close, contentDescription = "Cancel") }
        }
    }
}

@Composable
private fun EmptySongs() {
    Column(Modifier.fillMaxWidth().padding(horizontal = 32.dp, vertical = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
        Artwork(null, 72.dp, corner = 36.dp)
        Spacer(Modifier.height(16.dp))
        Text("No songs yet", style = MaterialTheme.typography.titleMedium)
        Spacer(Modifier.height(4.dp))
        Text(
            "Paste a link above, or share a video from the YouTube app to SongGrab. Songs are saved in Music/${Saver.FOLDER}, so your other music apps can play them too.",
            style = MaterialTheme.typography.bodyMedium,
            color = Palette.SubText,
            textAlign = TextAlign.Center,
        )
    }
}

@Composable
private fun SongRow(song: Song, playing: Boolean, isPlaying: Boolean, model: SongGrabViewModel) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var menu by remember { mutableStateOf(false) }
    var confirmDelete by remember { mutableStateOf(false) }
    Row(
        Modifier
            .fillMaxWidth()
            .clickable { if (playing) model.player.toggle() else model.play(song) }
            .background(if (playing) Palette.Elevated else Color.Transparent)
            .padding(start = 20.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(contentAlignment = Alignment.Center) {
            Artwork(song.art, 52.dp)
            if (playing) {
                Box(Modifier.size(52.dp).clip(RoundedCornerShape(10.dp)).background(Color.Black.copy(alpha = 0.45f)), contentAlignment = Alignment.Center) {
                    Icon(if (isPlaying) Icons.Rounded.GraphicEq else Icons.Rounded.Pause, contentDescription = null, tint = Palette.Coral)
                }
            }
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                style = MaterialTheme.typography.titleSmall,
                color = if (playing) Palette.Coral else Palette.Text,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val details = listOfNotNull(
                song.artist.ifBlank { null },
                song.durationSec.takeIf { it > 0 }?.let { formatDuration(it.toLong()) },
                song.format,
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = Palette.SubText, maxLines = 1, overflow = TextOverflow.Ellipsis)
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, contentDescription = "More", tint = Palette.SubText) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                DropdownMenuItem(text = { Text("Play") }, onClick = { menu = false; model.play(song) })
                DropdownMenuItem(text = { Text("Share song file") }, onClick = {
                    menu = false
                    val send = Intent(Intent.ACTION_SEND)
                        .setType(if (song.format == Format.M4A.name) Format.M4A.mime else Format.MP3.mime)
                        .putExtra(Intent.EXTRA_STREAM, Uri.parse(song.uri))
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                    runCatching { context.startActivity(Intent.createChooser(send, song.title)) }
                })
                if (song.sourceUrl.isNotBlank()) {
                    DropdownMenuItem(text = { Text("Open the original video") }, onClick = {
                        menu = false
                        runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(song.sourceUrl))) }
                    })
                }
                DropdownMenuItem(text = { Text("Remove from this list") }, onClick = {
                    menu = false
                    scope.launch { model.remove(song, deleteFile = false) }
                })
                DropdownMenuItem(text = { Text("Delete from phone", color = MaterialTheme.colorScheme.error) }, onClick = {
                    menu = false
                    confirmDelete = true
                })
            }
        }
    }
    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text("Delete this song?") },
            text = { Text("“${song.title}” will be deleted from Music/${Saver.FOLDER} on your phone.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    scope.launch {
                        if (!model.remove(song, deleteFile = true)) {
                            Toast.makeText(context, "Android didn't allow deleting it. Delete it from the Files app instead.", Toast.LENGTH_LONG).show()
                        }
                    }
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = { TextButton(onClick = { confirmDelete = false }) { Text("Keep") } },
        )
    }
}

@Composable
private fun MiniPlayer(now: PlayerConnection.NowPlaying, onOpen: () -> Unit, onToggle: () -> Unit, onNext: () -> Unit) {
    Surface(color = MaterialTheme.colorScheme.surfaceContainerHigh, tonalElevation = 3.dp) {
        Row(
            Modifier.fillMaxWidth().clickable(onClick = onOpen).navigationBarsPadding().padding(start = 12.dp, end = 4.dp, top = 8.dp, bottom = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(now.art, 44.dp, corner = 8.dp)
            Spacer(Modifier.width(12.dp))
            Column(Modifier.weight(1f)) {
                Text(now.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                if (now.artist.isNotBlank()) {
                    Text(now.artist, style = MaterialTheme.typography.bodySmall, color = Palette.SubText, maxLines = 1, overflow = TextOverflow.Ellipsis)
                }
            }
            IconButton(onClick = onToggle) {
                Icon(if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow, contentDescription = if (now.isPlaying) "Pause" else "Play")
            }
            IconButton(onClick = onNext, enabled = now.hasNext) {
                Icon(Icons.Rounded.SkipNext, contentDescription = "Next song")
            }
        }
    }
}

@Composable
private fun PlayerSheet(player: PlayerConnection, now: PlayerConnection.NowPlaying, onDismiss: () -> Unit) {
    val sheet = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    // The position isn't pushed by the player, so ask for it while the sheet is open.
    LaunchedEffect(Unit) {
        while (true) {
            player.refresh()
            delay(500)
        }
    }
    var dragging by remember { mutableStateOf(false) }
    var dragValue by remember { mutableFloatStateOf(0f) }
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheet, containerColor = MaterialTheme.colorScheme.surfaceContainer) {
        Column(Modifier.fillMaxWidth().padding(horizontal = 24.dp).padding(bottom = 32.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text("NOW PLAYING", style = MaterialTheme.typography.labelSmall, color = Palette.SubText)
            Spacer(Modifier.height(16.dp))
            Box(Modifier.fillMaxWidth(0.82f).aspectRatio(1f)) {
                Artwork(now.art, size = null, corner = 20.dp)
            }
            Spacer(Modifier.height(24.dp))
            Text(now.title, style = MaterialTheme.typography.headlineSmall, maxLines = 2, overflow = TextOverflow.Ellipsis, textAlign = TextAlign.Center)
            if (now.artist.isNotBlank()) {
                Text(now.artist, style = MaterialTheme.typography.titleMedium, color = Palette.SubText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            Spacer(Modifier.height(16.dp))
            val duration = now.duration.coerceAtLeast(1)
            Slider(
                value = if (dragging) dragValue else (now.position.toFloat() / duration).coerceIn(0f, 1f),
                onValueChange = { dragging = true; dragValue = it },
                onValueChangeFinished = {
                    player.seekTo((dragValue * duration).toLong())
                    dragging = false
                },
                colors = SliderDefaults.colors(thumbColor = Palette.Coral, activeTrackColor = Palette.Coral),
            )
            Row(Modifier.fillMaxWidth()) {
                val shown = if (dragging) (dragValue * duration).toLong() else now.position
                Text(formatDuration(shown / 1000), style = MaterialTheme.typography.bodySmall, color = Palette.SubText)
                Spacer(Modifier.weight(1f))
                Text(formatDuration(now.duration / 1000), style = MaterialTheme.typography.bodySmall, color = Palette.SubText)
            }
            Spacer(Modifier.height(8.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                IconButton(onClick = player::toggleShuffle) {
                    Icon(Icons.Rounded.Shuffle, contentDescription = "Shuffle", tint = if (now.shuffle) Palette.Coral else Palette.SubText)
                }
                IconButton(onClick = player::previous) {
                    Icon(Icons.Rounded.SkipPrevious, contentDescription = "Previous", modifier = Modifier.size(36.dp))
                }
                FilledIconButton(
                    onClick = player::toggle,
                    modifier = Modifier.size(72.dp),
                    colors = IconButtonDefaults.filledIconButtonColors(containerColor = Palette.Coral),
                ) {
                    Icon(
                        if (now.isPlaying) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (now.isPlaying) "Pause" else "Play",
                        modifier = Modifier.size(40.dp),
                        tint = Color.White,
                    )
                }
                IconButton(onClick = player::next, enabled = now.hasNext) {
                    Icon(Icons.Rounded.SkipNext, contentDescription = "Next", modifier = Modifier.size(36.dp))
                }
                IconButton(onClick = player::cycleRepeat) {
                    Icon(
                        if (now.repeat == Player.REPEAT_MODE_ONE) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
                        contentDescription = when (now.repeat) {
                            Player.REPEAT_MODE_ONE -> "Repeating this song"
                            Player.REPEAT_MODE_ALL -> "Repeating all"
                            else -> "Repeat off"
                        },
                        tint = if (now.repeat == Player.REPEAT_MODE_OFF) Palette.SubText else Palette.Coral,
                    )
                }
            }
        }
    }
}

@Composable
private fun AboutDialog(engine: SongGrabViewModel.EngineState, onUpdate: () -> Unit, onDismiss: () -> Unit) {
    val context = LocalContext.current
    val updated = remember { Engine.lastUpdate(context) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("SongGrab") },
        text = {
            Column {
                Text(
                    "Saves the audio of a YouTube video as a song in Music/${Saver.FOLDER}, using yt-dlp and ffmpeg.",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Spacer(Modifier.height(12.dp))
                Text("Downloader (yt-dlp): ${engine.version}", style = MaterialTheme.typography.titleSmall)
                Text(
                    if (updated > 0) "Last checked for updates ${DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(updated))}" else "Checks for updates every couple of days.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.SubText,
                )
                Text(
                    "YouTube changes often. If songs stop saving, update the downloader.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.SubText,
                )
                engine.message?.let {
                    Spacer(Modifier.height(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium, color = Palette.Gold)
                }
                Spacer(Modifier.height(12.dp))
                Text(
                    "Save only what you're allowed to keep: your own uploads, Creative Commons or public-domain audio, or songs you have permission for.",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Faint,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = onUpdate, enabled = !engine.updating) {
                if (engine.updating) {
                    CircularProgressIndicator(Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(8.dp))
                    Text("Updating…")
                } else {
                    Text("Update downloader")
                }
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
