package io.github.akrishna87.songgrab.ui

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import coil.compose.SubcomposeAsyncImage
import java.io.File

/** A song's cover, cropped square, or a music note when there's none. A null [size] fills the parent. */
@Composable
fun Artwork(path: String?, size: Dp?, modifier: Modifier = Modifier, corner: Dp = 10.dp) {
    val shape = RoundedCornerShape(corner)
    val placeholder = @Composable {
        Box(Modifier.fillMaxSize().background(BrandGradient), contentAlignment = Alignment.Center) {
            Icon(Icons.Rounded.MusicNote, contentDescription = null, tint = Color.White.copy(alpha = 0.9f), modifier = Modifier.size(size?.times(0.45f) ?: 96.dp))
        }
    }
    Box((if (size != null) modifier.size(size) else modifier.fillMaxSize()).clip(shape)) {
        if (path == null) {
            placeholder()
        } else {
            SubcomposeAsyncImage(
                model = File(path),
                contentDescription = null,
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize(),
                loading = { placeholder() },
                error = { placeholder() },
            )
        }
    }
}

/** 3:07, or 1:02:45 for long ones. */
fun formatDuration(seconds: Long): String {
    if (seconds <= 0) return "0:00"
    val h = seconds / 3600
    val m = (seconds % 3600) / 60
    val s = seconds % 60
    return if (h > 0) "%d:%02d:%02d".format(h, m, s) else "%d:%02d".format(m, s)
}
