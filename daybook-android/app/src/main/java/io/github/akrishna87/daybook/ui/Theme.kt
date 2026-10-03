package io.github.akrishna87.daybook.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import io.github.akrishna87.daybook.model.ThemeMode

/** Colours that aren't part of Material's scheme: due-date tones and swipe actions. */
@Immutable
data class Extra(
    val overdue: Color,
    val today: Color,
    val tomorrow: Color,
    val thisWeek: Color,
    val later: Color,
    val complete: Color,
    val delete: Color,
    val highlight: Color,
)

private val LightExtra = Extra(
    overdue = Color(0xFFD1453B),
    today = Color(0xFF058527),
    tomorrow = Color(0xFFAD6200),
    thisWeek = Color(0xFF692EC2),
    later = Color(0xFF6B6D7B),
    complete = Color(0xFF058527),
    delete = Color(0xFFD1453B),
    highlight = Color(0xFFE3E3FB),
)

private val DarkExtra = Extra(
    overdue = Color(0xFFFF7066),
    today = Color(0xFF4CC26E),
    tomorrow = Color(0xFFF2A33A),
    thisWeek = Color(0xFFB794F6),
    later = Color(0xFFA6A7B5),
    complete = Color(0xFF2F9E4F),
    delete = Color(0xFFD1453B),
    highlight = Color(0xFF34346E),
)

val LocalExtra = staticCompositionLocalOf { LightExtra }

private val LightColors = lightColorScheme(
    primary = Color(0xFF5B5BD6),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFE6E6FB),
    onPrimaryContainer = Color(0xFF23236B),
    secondary = Color(0xFF5F6170),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFEDEDF2),
    onSecondaryContainer = Color(0xFF1C1C21),
    tertiary = Color(0xFF058527),
    background = Color.White,
    onBackground = Color(0xFF1C1C21),
    surface = Color.White,
    onSurface = Color(0xFF1C1C21),
    surfaceVariant = Color(0xFFF2F2F5),
    onSurfaceVariant = Color(0xFF5F6170),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFF8F8FA),
    surfaceContainer = Color(0xFFF3F3F6),
    surfaceContainerHigh = Color(0xFFEDEDF1),
    surfaceContainerHighest = Color(0xFFE7E7EC),
    outline = Color(0xFFC6C7D1),
    outlineVariant = Color(0xFFE6E6EC),
    error = Color(0xFFD1453B),
    onError = Color.White,
    inverseSurface = Color(0xFF2B2B33),
    inverseOnSurface = Color(0xFFF2F2F5),
    inversePrimary = Color(0xFFA5A6F6),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF9FA0F5),
    onPrimary = Color(0xFF1C1C5E),
    primaryContainer = Color(0xFF3B3B9C),
    onPrimaryContainer = Color(0xFFE3E3FB),
    secondary = Color(0xFFA6A7B5),
    onSecondary = Color(0xFF1C1C21),
    secondaryContainer = Color(0xFF2A2A32),
    onSecondaryContainer = Color(0xFFE8E8EE),
    tertiary = Color(0xFF4CC26E),
    background = Color(0xFF121216),
    onBackground = Color(0xFFE8E8EE),
    surface = Color(0xFF121216),
    onSurface = Color(0xFFE8E8EE),
    surfaceVariant = Color(0xFF22222A),
    onSurfaceVariant = Color(0xFFA6A7B5),
    surfaceContainerLowest = Color(0xFF0D0D10),
    surfaceContainerLow = Color(0xFF18181D),
    surfaceContainer = Color(0xFF1C1C22),
    surfaceContainerHigh = Color(0xFF23232A),
    surfaceContainerHighest = Color(0xFF2B2B33),
    outline = Color(0xFF4A4B57),
    outlineVariant = Color(0xFF2E2F38),
    error = Color(0xFFFF7066),
    onError = Color(0xFF3A0905),
    inverseSurface = Color(0xFFE8E8EE),
    inverseOnSurface = Color(0xFF1C1C21),
    inversePrimary = Color(0xFF5B5BD6),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, fontSize = 26.sp, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.2).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.SemiBold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun isDark(mode: ThemeMode): Boolean = when (mode) {
    ThemeMode.SYSTEM -> isSystemInDarkTheme()
    ThemeMode.LIGHT -> false
    ThemeMode.DARK -> true
}

@Composable
fun DaybookTheme(dark: Boolean, content: @Composable () -> Unit) {
    androidx.compose.runtime.CompositionLocalProvider(LocalExtra provides if (dark) DarkExtra else LightExtra) {
        MaterialTheme(colorScheme = if (dark) DarkColors else LightColors, typography = AppTypography, content = content)
    }
}
