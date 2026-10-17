package io.github.akrishna87.myvideos.ui

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.AspectRatio
import androidx.compose.material.icons.rounded.ClosedCaption
import androidx.compose.material.icons.rounded.PictureInPictureAlt
import androidx.compose.material.icons.rounded.ScreenRotation
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import io.github.akrishna87.myvideos.Format

/** What the player screen shows on top of the video. The player activity updates it. */
class PlayerUiState {
    var title by mutableStateOf("")
    var controlsVisible by mutableStateOf(true)
    var inPip by mutableStateOf(false)
    var hint by mutableStateOf<GestureHint?>(null)
    /** Set for a few seconds after picking up where the person left off. */
    var resumedAt by mutableStateOf<Long?>(null)
}

/** The bubble in the middle while seeking or changing brightness/volume. [level] draws a bar from 0 to 1. */
data class GestureHint(val icon: ImageVector, val text: String, val level: Float? = null)

class PlayerActions(
    val onBack: () -> Unit,
    val onSubtitles: () -> Unit,
    val onResize: () -> Unit,
    val onRotate: () -> Unit,
    /** Null when the phone doesn't do picture-in-picture. */
    val onPip: (() -> Unit)?,
    val onStartOver: () -> Unit,
)

@Composable
fun PlayerOverlay(ui: PlayerUiState, actions: PlayerActions) {
    if (ui.inPip) return
    Box(Modifier.fillMaxSize()) {
        AnimatedVisibility(
            visible = ui.controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            TopBar(ui.title, actions)
        }

        ui.hint?.let { hint ->
            Column(
                Modifier
                    .align(Alignment.Center)
                    .background(Color.Black.copy(alpha = 0.6f), RoundedCornerShape(16.dp))
                    .padding(horizontal = 20.dp, vertical = 14.dp)
                    .widthIn(min = 120.dp),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Icon(hint.icon, null, Modifier.size(32.dp))
                Spacer(Modifier.height(6.dp))
                Text(hint.text, fontWeight = FontWeight.Bold)
                hint.level?.let { level ->
                    Spacer(Modifier.height(10.dp))
                    LinearProgressIndicator(
                        progress = { level },
                        modifier = Modifier.width(120.dp).height(4.dp),
                        color = Palette.Coral,
                        trackColor = Color.White.copy(alpha = 0.2f),
                    )
                }
            }
        }

        ui.resumedAt?.let { at ->
            Row(
                Modifier
                    .align(Alignment.BottomStart)
                    .windowInsetsPadding(WindowInsets.displayCutout)
                    .padding(start = 16.dp, bottom = 120.dp)
                    .background(Color.Black.copy(alpha = 0.7f), CircleShape)
                    .padding(start = 16.dp, end = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Resumed at ${Format.duration(at)}", style = MaterialTheme.typography.bodyMedium)
                TextButton(onClick = actions.onStartOver) { Text("Start over", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

@Composable
private fun TopBar(title: String, actions: PlayerActions) {
    Row(
        Modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(Color.Black.copy(alpha = 0.7f), Color.Transparent)))
            .windowInsetsPadding(WindowInsets.displayCutout)
            .padding(horizontal = 4.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = actions.onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
        Text(
            title,
            Modifier.weight(1f).padding(horizontal = 4.dp),
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        IconButton(onClick = actions.onSubtitles) { Icon(Icons.Rounded.ClosedCaption, "Load subtitles") }
        IconButton(onClick = actions.onResize) { Icon(Icons.Rounded.AspectRatio, "Resize") }
        IconButton(onClick = actions.onRotate) { Icon(Icons.Rounded.ScreenRotation, "Rotate") }
        actions.onPip?.let { pip ->
            IconButton(onClick = pip) { Icon(Icons.Rounded.PictureInPictureAlt, "Picture in picture") }
        }
    }
}
