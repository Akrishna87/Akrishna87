package io.github.akrishna87.pakkam.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Light = lightColorScheme(
    primary = Color(0xFFB3261E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDAD5),
    onPrimaryContainer = Color(0xFF410001),
    secondary = Color(0xFF775651),
    secondaryContainer = Color(0xFFFFDAD5),
    onSecondaryContainer = Color(0xFF2C1512),
    tertiary = Color(0xFF6F5D0E),
    tertiaryContainer = Color(0xFFFBE287),
    background = Color(0xFFFFFBFF),
    surface = Color(0xFFFFFBFF),
    surfaceVariant = Color(0xFFF5DDDA),
    surfaceContainer = Color(0xFFF8ECEA),
    surfaceContainerHigh = Color(0xFFF2E6E4),
    surfaceContainerLow = Color(0xFFFDF2F0),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB4A9),
    onPrimary = Color(0xFF690002),
    primaryContainer = Color(0xFF930006),
    onPrimaryContainer = Color(0xFFFFDAD5),
    secondary = Color(0xFFE7BDB6),
    secondaryContainer = Color(0xFF5D3F3B),
    onSecondaryContainer = Color(0xFFFFDAD5),
    tertiary = Color(0xFFDEC66E),
    tertiaryContainer = Color(0xFF544600),
    background = Color(0xFF201A19),
    surface = Color(0xFF201A19),
    surfaceVariant = Color(0xFF534341),
    surfaceContainer = Color(0xFF2B2221),
    surfaceContainerHigh = Color(0xFF362C2B),
    surfaceContainerLow = Color(0xFF241D1C),
)

@Composable
fun PakkamTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = if (isSystemInDarkTheme()) Dark else Light, content = content)
}
