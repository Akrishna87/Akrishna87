package io.github.akrishna87.rssreader.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Shapes
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.akrishna87.rssreader.R

/**
 * Colours modelled on Apple's iOS system palette (light and dark), so the app
 * reads like a first-party Apple app.
 */
@Immutable
data class AppleColors(
    val background: Color,          // systemBackground
    val groupedBackground: Color,   // systemGroupedBackground
    val cell: Color,                // secondarySystemGroupedBackground
    val label: Color,
    val secondaryLabel: Color,
    val tertiaryLabel: Color,
    val separator: Color,
    val fill: Color,                // tertiarySystemFill (search fields, segmented control)
    val selectedSegment: Color,
    val bar: Color,                 // translucent navigation bar
    val tint: Color,                // app accent (systemOrange)
    val blue: Color,
    val red: Color,
    val green: Color,
    val yellow: Color,
    val purple: Color,
    val gray: Color,
    val dark: Boolean,
)

val LightApple = AppleColors(
    background = Color(0xFFFFFFFF),
    groupedBackground = Color(0xFFF2F2F7),
    cell = Color(0xFFFFFFFF),
    label = Color(0xFF000000),
    secondaryLabel = Color(0x993C3C43),
    tertiaryLabel = Color(0x4D3C3C43),
    separator = Color(0x4A3C3C43),
    fill = Color(0x1F767680),
    selectedSegment = Color(0xFFFFFFFF),
    bar = Color(0xF2F9F9F9),
    tint = Color(0xFFFF9500),
    blue = Color(0xFF007AFF),
    red = Color(0xFFFF3B30),
    green = Color(0xFF34C759),
    yellow = Color(0xFFFFCC00),
    purple = Color(0xFFAF52DE),
    gray = Color(0xFF8E8E93),
    dark = false,
)

val DarkApple = AppleColors(
    background = Color(0xFF000000),
    groupedBackground = Color(0xFF000000),
    cell = Color(0xFF1C1C1E),
    label = Color(0xFFFFFFFF),
    secondaryLabel = Color(0x99EBEBF5),
    tertiaryLabel = Color(0x4DEBEBF5),
    separator = Color(0x99545458),
    fill = Color(0x3D767680),
    selectedSegment = Color(0xFF636366),
    bar = Color(0xF0161618),
    tint = Color(0xFFFF9F0A),
    blue = Color(0xFF0A84FF),
    red = Color(0xFFFF453A),
    green = Color(0xFF30D158),
    yellow = Color(0xFFFFD60A),
    purple = Color(0xFFBF5AF2),
    gray = Color(0xFF8E8E93),
    dark = true,
)

val LocalApple = staticCompositionLocalOf { LightApple }

/** Inter: an open-source typeface designed along the same lines as Apple's San Francisco. */
val Inter = FontFamily(
    Font(R.font.inter_regular, FontWeight.Normal),
    Font(R.font.inter_medium, FontWeight.Medium),
    Font(R.font.inter_semibold, FontWeight.SemiBold),
    Font(R.font.inter_bold, FontWeight.Bold),
)

private fun style(size: Int, line: Int, weight: FontWeight, tracking: Double) =
    TextStyle(fontFamily = Inter, fontSize = size.sp, lineHeight = line.sp, fontWeight = weight, letterSpacing = tracking.sp)

/** Apple's text styles (sizes/leading from the iOS HIG), tracked slightly tighter to suit Inter. */
object AppleType {
    val largeTitle = style(34, 41, FontWeight.Bold, -0.8)
    val title1 = style(28, 34, FontWeight.Bold, -0.6)
    val title2 = style(22, 28, FontWeight.Bold, -0.45)
    val title3 = style(20, 25, FontWeight.SemiBold, -0.4)
    val headline = style(17, 22, FontWeight.SemiBold, -0.35)
    val body = style(17, 22, FontWeight.Normal, -0.35)
    val callout = style(16, 21, FontWeight.Normal, -0.3)
    val subheadline = style(15, 20, FontWeight.Normal, -0.2)
    val footnote = style(13, 18, FontWeight.Normal, -0.05)
    val caption1 = style(12, 16, FontWeight.Normal, 0.0)
    val caption2 = style(11, 13, FontWeight.Normal, 0.05)
}

@Composable
fun ReaderTheme(content: @Composable () -> Unit) {
    val apple = if (isSystemInDarkTheme()) DarkApple else LightApple
    val scheme = if (apple.dark) {
        darkColorScheme(
            primary = apple.tint, onPrimary = Color.Black, background = apple.background, onBackground = apple.label,
            surface = apple.background, onSurface = apple.label, surfaceVariant = apple.cell, onSurfaceVariant = apple.secondaryLabel,
            surfaceContainerHigh = Color(0xFF2C2C2E), surfaceContainerLow = apple.cell, surfaceContainer = apple.cell,
            outline = apple.separator, outlineVariant = apple.separator, error = apple.red, secondaryContainer = apple.fill,
        )
    } else {
        lightColorScheme(
            primary = apple.tint, onPrimary = Color.White, background = apple.background, onBackground = apple.label,
            surface = apple.background, onSurface = apple.label, surfaceVariant = apple.groupedBackground, onSurfaceVariant = apple.secondaryLabel,
            surfaceContainerHigh = Color(0xFFF2F2F7), surfaceContainerLow = Color.White, surfaceContainer = Color.White,
            outline = apple.separator, outlineVariant = apple.separator, error = apple.red, secondaryContainer = apple.fill,
        )
    }
    val typography = Typography(
        displayLarge = AppleType.largeTitle,
        headlineLarge = AppleType.title1,
        headlineMedium = AppleType.title2,
        headlineSmall = AppleType.title3,
        titleLarge = AppleType.title3,
        titleMedium = AppleType.headline,
        titleSmall = AppleType.subheadline.copy(fontWeight = FontWeight.SemiBold),
        bodyLarge = AppleType.body,
        bodyMedium = AppleType.subheadline,
        bodySmall = AppleType.footnote,
        labelLarge = AppleType.body,
        labelMedium = AppleType.footnote,
        labelSmall = AppleType.caption1,
    )
    val shapes = Shapes(
        extraSmall = RoundedCornerShape(6.dp),
        small = RoundedCornerShape(10.dp),
        medium = RoundedCornerShape(12.dp),
        large = RoundedCornerShape(14.dp),
        extraLarge = RoundedCornerShape(14.dp),
    )
    CompositionLocalProvider(LocalApple provides apple) {
        MaterialTheme(colorScheme = scheme, typography = typography, shapes = shapes, content = content)
    }
}
