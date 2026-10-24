package io.github.akrishna87.semippu.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFF1B6B3A),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFA6F4B5),
    onPrimaryContainer = Color(0xFF00210C),
    secondary = Color(0xFF506352),
    secondaryContainer = Color(0xFFD3E8D3),
    onSecondaryContainer = Color(0xFF0E1F12),
    background = Color(0xFFFBFDF7),
    surface = Color(0xFFFBFDF7),
    surfaceVariant = Color(0xFFDDE5DA),
)

private val Dark = darkColorScheme(
    primary = Color(0xFF8BD79B),
    onPrimary = Color(0xFF00391A),
    primaryContainer = Color(0xFF005227),
    onPrimaryContainer = Color(0xFFA6F4B5),
    secondary = Color(0xFFB7CCB8),
    secondaryContainer = Color(0xFF394B3C),
    onSecondaryContainer = Color(0xFFD3E8D3),
    background = Color(0xFF191C19),
    surface = Color(0xFF191C19),
    surfaceVariant = Color(0xFF414941),
)

@Composable
fun SemippuTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
