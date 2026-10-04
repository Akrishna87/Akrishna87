package io.github.akrishna87.songgrab.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** Near-black with a coral accent and a gold second colour, like the launcher icon. */
object Palette {
    val Background = Color(0xFF0E0B12)
    val Elevated = Color(0xFF19151F)
    val Elevated2 = Color(0xFF241F2B)
    val Highlight = Color(0xFF2F2938)
    val Text = Color(0xFFFFFFFF)
    val SubText = Color(0xFFADA6B8)
    val Faint = Color(0xFF746C80)
    val Coral = Color(0xFFFF5C77)
    val Violet = Color(0xFF7A3FB0)
    val Gold = Color(0xFFFFC94D)
    val Error = Color(0xFFFF8A80)
}

private val DarkColors = darkColorScheme(
    primary = Palette.Coral,
    onPrimary = Color(0xFF2B0008),
    primaryContainer = Color(0xFF5E1424),
    onPrimaryContainer = Color(0xFFFFD9DF),
    secondary = Palette.Gold,
    onSecondary = Color(0xFF261A00),
    secondaryContainer = Color(0xFF4D3A00),
    onSecondaryContainer = Color(0xFFFFE8B0),
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Background,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Elevated2,
    onSurfaceVariant = Palette.SubText,
    surfaceContainerLowest = Color(0xFF07050A),
    surfaceContainerLow = Color(0xFF130F18),
    surfaceContainer = Palette.Elevated,
    surfaceContainerHigh = Palette.Elevated2,
    surfaceContainerHighest = Palette.Highlight,
    outline = Color(0xFF52495E),
    outlineVariant = Color(0xFF2E2836),
    error = Palette.Error,
    inverseSurface = Color(0xFFF3F0F6),
    inverseOnSurface = Color(0xFF19151F),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.6).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    )
}

val BrandGradient = Brush.linearGradient(listOf(Palette.Violet, Palette.Coral))

@Composable
fun SongGrabTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = AppTypography) {
        // Text and icons default to black outside a Surface; on this dark app they should be white.
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
