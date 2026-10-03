package io.github.akrishna87.daybook.ui

import androidx.compose.material3.LocalContentColor
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/** White text on a warm background with frosted-glass cards, like the lock screen. */
object Palette {
    val Text = Color.White
    val SubText = Color.White.copy(alpha = 0.86f)
    val Faint = Color.White.copy(alpha = 0.62f)
    val Glass = Color.White.copy(alpha = 0.16f)
    val GlassBorder = Color.White.copy(alpha = 0.26f)
    val TodayFill = Color(0xFF3F1D73)
    val TodayPill = Color(0xFFFF3B5C)
    val TomorrowPill = Color(0xFFF2A03D)
    val Overdue = Color(0xFFFFB4B4)
}

private val AppTypography = Typography().let { t ->
    t.copy(
        displayLarge = t.displayLarge.copy(fontWeight = FontWeight.Bold, fontSize = 96.sp, lineHeight = 100.sp, letterSpacing = (-2).sp),
        headlineLarge = t.headlineLarge.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.8).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.ExtraBold, letterSpacing = (-0.6).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.Bold),
        titleSmall = t.titleSmall.copy(fontWeight = FontWeight.SemiBold),
        labelSmall = t.labelSmall.copy(fontWeight = FontWeight.SemiBold),
    )
}

/** A darker shade, for dialogs and sheets that sit over the background. */
fun Color.deep(amount: Float = 0.35f): Color = lerp(this, Color.Black, amount)

@Composable
fun DaybookTheme(background: Color, content: @Composable () -> Unit) {
    val sheet = background.deep(0.38f)
    val colors = darkColorScheme(
        primary = Color.White,
        onPrimary = Palette.TodayFill,
        primaryContainer = Color.White,
        onPrimaryContainer = Palette.TodayFill,
        secondary = Color(0xFFC69CF4),
        onSecondary = Color(0xFF2A1250),
        secondaryContainer = Color.White.copy(alpha = 0.24f),
        onSecondaryContainer = Color.White,
        background = background,
        onBackground = Color.White,
        surface = sheet,
        onSurface = Color.White,
        surfaceVariant = lerp(background, Color.White, 0.14f),
        onSurfaceVariant = Palette.SubText,
        surfaceContainerLowest = background.deep(0.5f),
        surfaceContainerLow = sheet,
        surfaceContainer = sheet,
        surfaceContainerHigh = sheet,
        surfaceContainerHighest = lerp(sheet, Color.White, 0.1f),
        outline = Color.White.copy(alpha = 0.55f),
        outlineVariant = Color.White.copy(alpha = 0.25f),
        inverseSurface = Color(0xFFF7F2EE),
        inverseOnSurface = Color(0xFF2B201B),
        error = Color(0xFFFFB4AB),
        onError = Color(0xFF690005),
    )
    MaterialTheme(colorScheme = colors, typography = AppTypography) {
        // Text and icons default to black outside a Surface; on this background they should be white.
        CompositionLocalProvider(LocalContentColor provides Color.White, content = content)
    }
}
