package io.github.akrishna87.screentime.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

/** Near-black with a violet accent; follows the phone's dark/light setting. */
private val Dark = darkColorScheme(
    primary = Color(0xFFA78BFA),
    onPrimary = Color(0xFF1E0B4B),
    primaryContainer = Color(0xFF3B2A6E),
    onPrimaryContainer = Color(0xFFEDE9FE),
    secondary = Color(0xFFF472B6),
    onSecondary = Color(0xFF3B0A24),
    secondaryContainer = Color(0xFF5A1D3D),
    onSecondaryContainer = Color(0xFFFCE7F3),
    background = Color(0xFF0D0B14),
    onBackground = Color(0xFFECE9F2),
    surface = Color(0xFF0D0B14),
    onSurface = Color(0xFFECE9F2),
    surfaceVariant = Color(0xFF1F1B29),
    onSurfaceVariant = Color(0xFFA49DB4),
    surfaceContainerLowest = Color(0xFF08070D),
    surfaceContainerLow = Color(0xFF13111B),
    surfaceContainer = Color(0xFF181521),
    surfaceContainerHigh = Color(0xFF201C2A),
    surfaceContainerHighest = Color(0xFF282334),
    outline = Color(0xFF453E55),
    outlineVariant = Color(0xFF2B2638),
    error = Color(0xFFFF8A80),
)

private val Light = lightColorScheme(
    primary = Color(0xFF6D28D9),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFEDE9FE),
    onPrimaryContainer = Color(0xFF1E0B4B),
    secondary = Color(0xFFDB2777),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFCE7F3),
    onSecondaryContainer = Color(0xFF3B0A24),
    background = Color(0xFFF9F8FC),
    onBackground = Color(0xFF16131D),
    surface = Color(0xFFF9F8FC),
    onSurface = Color(0xFF16131D),
    surfaceVariant = Color(0xFFE8E4F0),
    onSurfaceVariant = Color(0xFF5A5468),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF3F1F8),
    surfaceContainer = Color(0xFFEDEAF4),
    surfaceContainerHigh = Color(0xFFE7E3EF),
    surfaceContainerHighest = Color(0xFFE0DCE9),
    outline = Color(0xFF8F88A0),
    outlineVariant = Color(0xFFD3CEDD),
    error = Color(0xFFB3261E),
)

@Composable
fun ScreenTimeTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
