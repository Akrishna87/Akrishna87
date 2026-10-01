package io.github.akrishna87.mytorrents.ui

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
import io.github.akrishna87.mytorrents.engine.Status

/** The app's own palette: near-black, with a blue → mint accent. */
object Palette {
    val Background = Color(0xFF0B0D10)
    val Elevated = Color(0xFF15181D)
    val Elevated2 = Color(0xFF1E2228)
    val Highlight = Color(0xFF2A2F37)
    val Text = Color(0xFFFFFFFF)
    val SubText = Color(0xFFA0A7B4)
    val Faint = Color(0xFF6B7280)
    val Blue = Color(0xFF3D7BFF)
    val Mint = Color(0xFF2EE6A6)
    val Amber = Color(0xFFFFB547)
    val Red = Color(0xFFFF5C6C)
}

private val DarkColors = darkColorScheme(
    primary = Palette.Mint,
    onPrimary = Color(0xFF00261A),
    primaryContainer = Color(0xFF0E4A37),
    onPrimaryContainer = Color(0xFFB9F7DF),
    secondary = Palette.Blue,
    onSecondary = Color.White,
    secondaryContainer = Color(0xFF1B2E57),
    onSecondaryContainer = Color(0xFFDCE6FF),
    error = Palette.Red,
    background = Palette.Background,
    onBackground = Palette.Text,
    surface = Palette.Background,
    onSurface = Palette.Text,
    surfaceVariant = Palette.Elevated2,
    onSurfaceVariant = Palette.SubText,
    surfaceContainerLowest = Color(0xFF060709),
    surfaceContainerLow = Color(0xFF101317),
    surfaceContainer = Palette.Elevated,
    surfaceContainerHigh = Palette.Elevated2,
    surfaceContainerHighest = Palette.Highlight,
    outline = Color(0xFF475060),
    outlineVariant = Color(0xFF262B33),
    inverseSurface = Color(0xFFF0F2F5),
    inverseOnSurface = Color(0xFF15181D),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.Black, letterSpacing = (-0.8).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.4).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.SemiBold, letterSpacing = 1.sp),
    )
}

val BrandGradient = Brush.linearGradient(listOf(Palette.Blue, Palette.Mint))

/** The colour that goes with a torrent's state, for its progress bar and badge. */
fun Status.color(): Color = when (this) {
    Status.Downloading, Status.GettingInfo -> Palette.Blue
    Status.Seeding, Status.Finished -> Palette.Mint
    Status.Paused, Status.Queued, Status.Checking -> Palette.Faint
    Status.Error -> Palette.Red
}

@Composable
fun TorrentsTheme(content: @Composable () -> Unit) {
    MaterialTheme(colorScheme = DarkColors, typography = AppTypography) {
        // Text and icons default to black outside a Surface; on this dark app they should be white.
        CompositionLocalProvider(LocalContentColor provides Palette.Text, content = content)
    }
}
