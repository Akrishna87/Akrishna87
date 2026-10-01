package io.github.akrishna87.myvideos.ui

import android.content.Intent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Folder
import androidx.compose.material.icons.rounded.Movie
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.myvideos.Format
import io.github.akrishna87.myvideos.Progress
import io.github.akrishna87.myvideos.Thumbnails
import io.github.akrishna87.myvideos.Video
import io.github.akrishna87.myvideos.VideoFolder
import java.text.DateFormat
import java.util.Date

/** A video's thumbnail with its length in the corner and a bar for how much has been watched. */
@Composable
fun VideoThumbnail(video: Video, progress: Progress?, modifier: Modifier = Modifier, isNew: Boolean = false) {
    val context = LocalContext.current
    val bitmap by produceState(Thumbnails.cached(video), video.id) {
        if (value == null) value = Thumbnails.load(context, video)
    }
    Box(modifier.aspectRatio(16f / 9f).clip(RoundedCornerShape(10.dp)).background(Palette.Elevated2)) {
        val bmp = bitmap
        if (bmp != null) {
            Image(bmp.asImageBitmap(), null, Modifier.fillMaxSize(), contentScale = ContentScale.Crop)
        } else {
            Icon(Icons.Rounded.Movie, null, Modifier.align(Alignment.Center).size(32.dp), tint = Palette.Faint)
        }
        if (isNew && progress == null) {
            Tag("NEW", Modifier.align(Alignment.TopStart).padding(5.dp), Palette.Coral, Color(0xFF1A0600))
        }
        if (progress?.finished == true) {
            Icon(
                Icons.Rounded.CheckCircle,
                "Watched",
                Modifier.align(Alignment.TopEnd).padding(5.dp).size(18.dp).background(Color.Black.copy(alpha = 0.5f), RoundedCornerShape(50)),
                tint = Color.White,
            )
        }
        if (video.durationMs > 0) {
            Tag(Format.duration(video.durationMs), Modifier.align(Alignment.BottomEnd).padding(5.dp))
        }
        val fraction = progress?.takeIf { !it.finished }?.fraction ?: 0f
        if (fraction > 0f) {
            Box(Modifier.align(Alignment.BottomStart).fillMaxWidth().height(3.dp).background(Color.White.copy(alpha = 0.25f))) {
                Box(Modifier.fillMaxHeight().fillMaxWidth(fraction).background(Palette.Coral))
            }
        }
    }
}

@Composable
private fun Tag(text: String, modifier: Modifier, background: Color = Color.Black.copy(alpha = 0.7f), color: Color = Color.White) {
    Text(
        text,
        modifier.background(background, RoundedCornerShape(4.dp)).padding(horizontal = 5.dp, vertical = 1.dp),
        color = color,
        fontSize = 11.sp,
        fontWeight = FontWeight.Bold,
    )
}

/** One video in a list: thumbnail, name, details, and a ⋮ menu. */
@Composable
fun VideoRow(
    video: Video,
    progress: Progress?,
    showFolder: Boolean,
    onClick: () -> Unit,
    onPlayFromStart: () -> Unit,
    onMarkWatched: () -> Unit,
    onMarkUnwatched: () -> Unit,
    onDetails: () -> Unit,
) {
    val context = LocalContext.current
    var menu by remember { mutableStateOf(false) }
    val isNew = System.currentTimeMillis() / 1000 - video.dateAdded < 3 * 24 * 3600
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(start = 16.dp, top = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        VideoThumbnail(video, progress, Modifier.width(136.dp), isNew = isNew)
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(video.title, style = MaterialTheme.typography.titleSmall, maxLines = 2, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val details = listOfNotNull(
                video.folder.takeIf { showFolder }?.substringAfterLast('/')?.ifEmpty { "Internal storage" },
                video.quality,
                Format.size(video.sizeBytes).takeIf { video.sizeBytes > 0 },
            ).joinToString(" · ")
            Text(details, style = MaterialTheme.typography.bodySmall, color = Palette.SubText, maxLines = 1, overflow = TextOverflow.Ellipsis)
            if (progress != null && progress.resumeAt > 0) {
                Text(
                    "${Format.minutes(progress.durationMs - progress.positionMs)} left",
                    style = MaterialTheme.typography.bodySmall,
                    color = Palette.Coral,
                )
            }
        }
        Box {
            IconButton(onClick = { menu = true }) { Icon(Icons.Rounded.MoreVert, "More options", tint = Palette.SubText) }
            DropdownMenu(expanded = menu, onDismissRequest = { menu = false }) {
                if (progress != null && progress.resumeAt > 0) {
                    DropdownMenuItem(text = { Text("Play from start") }, onClick = { menu = false; onPlayFromStart() })
                }
                if (progress?.finished == true) {
                    DropdownMenuItem(text = { Text("Mark as unwatched") }, onClick = { menu = false; onMarkUnwatched() })
                } else {
                    DropdownMenuItem(text = { Text("Mark as watched") }, onClick = { menu = false; onMarkWatched() })
                }
                DropdownMenuItem(
                    text = { Text("Share") },
                    onClick = {
                        menu = false
                        val send = Intent(Intent.ACTION_SEND)
                            .setType("video/*")
                            .putExtra(Intent.EXTRA_STREAM, video.uri)
                            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                        context.startActivity(Intent.createChooser(send, video.title))
                    },
                )
                DropdownMenuItem(text = { Text("Details") }, onClick = { menu = false; onDetails() })
            }
        }
    }
}

/** A "Continue watching" card. */
@Composable
fun ContinueCard(video: Video, progress: Progress?, width: Dp, onClick: () -> Unit) {
    Column(Modifier.width(width).clickable(onClick = onClick)) {
        VideoThumbnail(video, progress, Modifier.fillMaxWidth())
        Spacer(Modifier.height(6.dp))
        Text(video.title, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.SemiBold, maxLines = 1, overflow = TextOverflow.Ellipsis)
        if (progress != null) {
            Text(
                "${Format.minutes(progress.durationMs - progress.positionMs)} left",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.SubText,
            )
        }
    }
}

@Composable
fun FolderRow(folder: VideoFolder, onClick: () -> Unit) {
    Row(
        Modifier.fillMaxWidth().clickable(onClick = onClick).padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(Modifier.width(136.dp)) {
            VideoThumbnail(folder.videos.first(), null, Modifier.fillMaxWidth())
            Box(Modifier.matchParentSize().background(Color.Black.copy(alpha = 0.35f), RoundedCornerShape(10.dp)))
            Icon(Icons.Rounded.Folder, null, Modifier.align(Alignment.Center).size(36.dp), tint = Color.White)
        }
        Spacer(Modifier.width(14.dp))
        Column(Modifier.weight(1f)) {
            Text(folder.name, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
            Spacer(Modifier.height(4.dp))
            val count = folder.videos.size
            Text(
                "$count ${if (count == 1) "video" else "videos"} · ${Format.size(folder.totalBytes)}",
                style = MaterialTheme.typography.bodySmall,
                color = Palette.SubText,
            )
            if (folder.parent.isNotEmpty()) {
                Text(folder.parent, style = MaterialTheme.typography.bodySmall, color = Palette.Faint, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
        }
    }
}

@Composable
fun SectionHeader(text: String, modifier: Modifier = Modifier) {
    Text(
        text,
        modifier.padding(start = 16.dp, end = 16.dp, top = 20.dp, bottom = 8.dp),
        style = MaterialTheme.typography.titleLarge,
    )
}

@Composable
fun DetailsDialog(video: Video, onDismiss: () -> Unit) {
    val rows = listOfNotNull(
        "File" to video.fileName,
        "Folder" to video.folder.ifEmpty { "Internal storage" },
        ("Length" to Format.duration(video.durationMs)).takeIf { video.durationMs > 0 },
        ("Resolution" to "${video.width} × ${video.height}").takeIf { video.width > 0 && video.height > 0 },
        "Size" to Format.size(video.sizeBytes),
        "Added" to DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(video.dateAdded * 1000)),
    )
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
        title = { Text(video.title, maxLines = 2, overflow = TextOverflow.Ellipsis) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                rows.forEach { (label, value) ->
                    Column {
                        Text(label, style = MaterialTheme.typography.labelMedium, color = Palette.SubText)
                        Text(value, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        containerColor = Palette.Elevated,
    )
}
