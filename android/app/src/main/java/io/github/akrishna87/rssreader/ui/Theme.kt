package io.github.akrishna87.rssreader.ui

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Orange = Color(0xFFD4521F)
private val OrangeDark = Color(0xFFFF8A5B)

private val LightColors = lightColorScheme(
    primary = Orange,
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCF),
    onPrimaryContainer = Color(0xFF3A0B00),
    secondaryContainer = Color(0xFFFBE6DA),
    background = Color(0xFFFFFBF8),
    surface = Color(0xFFFFFBF8),
)

private val DarkColors = darkColorScheme(
    primary = OrangeDark,
    onPrimary = Color(0xFF3A0B00),
    primaryContainer = Color(0xFF7A2E10),
    onPrimaryContainer = Color(0xFFFFDBCF),
    secondaryContainer = Color(0xFF3A2419),
)

/** Material You colours from the wallpaper on Android 12+, the app's orange elsewhere. */
@Composable
fun ReaderTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val context = LocalContext.current
    val colors = when {
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.S -> if (dark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
        dark -> DarkColors
        else -> LightColors
    }
    MaterialTheme(colorScheme = colors, content = content)
}
