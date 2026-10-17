package io.github.akrishna87.selavu.ui

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

/** Colours for money: spending, income and the budget bar's warning shade. */
@Immutable
data class MoneyColors(val expense: Color, val income: Color, val warning: Color)

val LocalMoneyColors = staticCompositionLocalOf { MoneyColors(Color.Red, Color.Green, Color.Yellow) }

private val Light = lightColorScheme(
    primary = Color(0xFFB4541E),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFFFDBCA),
    onPrimaryContainer = Color(0xFF3A1400),
    secondary = Color(0xFF77574A),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFFFDBCF),
    onSecondaryContainer = Color(0xFF2C160C),
    background = Color(0xFFFFF8F5),
    onBackground = Color(0xFF221A16),
    surface = Color(0xFFFFF8F5),
    onSurface = Color(0xFF221A16),
    surfaceVariant = Color(0xFFF5DED4),
    onSurfaceVariant = Color(0xFF53433C),
    surfaceContainerLowest = Color.White,
    surfaceContainerLow = Color(0xFFFFF1EB),
    surfaceContainer = Color(0xFFFCEAE2),
    surfaceContainerHigh = Color(0xFFF6E4DC),
    surfaceContainerHighest = Color(0xFFF0DFD7),
    outline = Color(0xFF85736B),
    outlineVariant = Color(0xFFD8C2B9),
)

private val Dark = darkColorScheme(
    primary = Color(0xFFFFB68F),
    onPrimary = Color(0xFF552100),
    primaryContainer = Color(0xFF8B3E0C),
    onPrimaryContainer = Color(0xFFFFDBCA),
    secondary = Color(0xFFE7BEAD),
    onSecondary = Color(0xFF442A1F),
    secondaryContainer = Color(0xFF5D4034),
    onSecondaryContainer = Color(0xFFFFDBCF),
    background = Color(0xFF19120E),
    onBackground = Color(0xFFF0DFD7),
    surface = Color(0xFF19120E),
    onSurface = Color(0xFFF0DFD7),
    surfaceVariant = Color(0xFF53433C),
    onSurfaceVariant = Color(0xFFD8C2B9),
    surfaceContainerLowest = Color(0xFF130D0A),
    surfaceContainerLow = Color(0xFF221A16),
    surfaceContainer = Color(0xFF261E1A),
    surfaceContainerHigh = Color(0xFF312824),
    surfaceContainerHighest = Color(0xFF3C332E),
    outline = Color(0xFFA08D85),
    outlineVariant = Color(0xFF53433C),
)

private val AppTypography = Typography().let { t ->
    t.copy(
        displaySmall = t.displaySmall.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.5).sp),
        headlineMedium = t.headlineMedium.copy(fontWeight = FontWeight.Bold, letterSpacing = (-0.4).sp),
        headlineSmall = t.headlineSmall.copy(fontWeight = FontWeight.Bold),
        titleLarge = t.titleLarge.copy(fontWeight = FontWeight.Bold),
        titleMedium = t.titleMedium.copy(fontWeight = FontWeight.SemiBold),
        labelLarge = t.labelLarge.copy(fontWeight = FontWeight.SemiBold),
    )
}

@Composable
fun SelavuTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val money = if (dark) MoneyColors(Color(0xFFFF8A80), Color(0xFF7BD88F), Color(0xFFFFC857))
    else MoneyColors(Color(0xFFC62828), Color(0xFF2E7D32), Color(0xFFE09B00))
    androidx.compose.runtime.CompositionLocalProvider(LocalMoneyColors provides money) {
        MaterialTheme(colorScheme = if (dark) Dark else Light, typography = AppTypography, content = content)
    }
}
