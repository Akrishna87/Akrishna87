package io.github.akrishna87.weather.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Night-sky blue-black with a sky-blue accent; follows the phone's dark/light setting. */
private val Dark = darkColorScheme(
    primary = Color(0xFF38BDF8),
    onPrimary = Color(0xFF00213A),
    primaryContainer = Color(0xFF0C4A6E),
    onPrimaryContainer = Color(0xFFCDEBFF),
    secondary = Color(0xFFFBBF24),
    onSecondary = Color(0xFF2A1A00),
    secondaryContainer = Color(0xFF4A3500),
    onSecondaryContainer = Color(0xFFFFE6A8),
    background = Color(0xFF0A1018),
    onBackground = Color(0xFFE6EDF5),
    surface = Color(0xFF0A1018),
    onSurface = Color(0xFFE6EDF5),
    surfaceVariant = Color(0xFF18212C),
    onSurfaceVariant = Color(0xFF98A6B5),
    surfaceContainerLowest = Color(0xFF060A10),
    surfaceContainerLow = Color(0xFF0F1620),
    surfaceContainer = Color(0xFF131C27),
    surfaceContainerHigh = Color(0xFF1A2430),
    surfaceContainerHighest = Color(0xFF222D3A),
    outline = Color(0xFF384656),
    outlineVariant = Color(0xFF24303D),
    error = Color(0xFFFF8A80),
    errorContainer = Color(0xFF5C1A16),
    onErrorContainer = Color(0xFFFFDAD5),
)

private val Light = lightColorScheme(
    primary = Color(0xFF0369A1),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFCDEBFF),
    onPrimaryContainer = Color(0xFF001E31),
    secondary = Color(0xFFB45309),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFE6A8),
    onSecondaryContainer = Color(0xFF2A1A00),
    background = Color(0xFFF5F9FC),
    onBackground = Color(0xFF111820),
    surface = Color(0xFFF5F9FC),
    onSurface = Color(0xFF111820),
    surfaceVariant = Color(0xFFE0E8EF),
    onSurfaceVariant = Color(0xFF4D5B68),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFEFF4F8),
    surfaceContainer = Color(0xFFE8EFF5),
    surfaceContainerHigh = Color(0xFFE1E9F0),
    surfaceContainerHighest = Color(0xFFDAE3EA),
    outline = Color(0xFF8595A3),
    outlineVariant = Color(0xFFC8D3DC),
    error = Color(0xFFB3261E),
    errorContainer = Color(0xFFFFDAD5),
    onErrorContainer = Color(0xFF410002),
)

@Composable
fun WeatherTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
