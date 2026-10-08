package com.tgsorter.app.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.Immutable
import androidx.compose.runtime.ReadOnlyComposable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/** Семантические цвета статусов: + зелёный, − красный, пропуск — янтарный. */
@Immutable
data class StatusColors(
    val positive: Color,
    val onPositive: Color,
    val negative: Color,
    val onNegative: Color,
    val skipped: Color,
    val onSkipped: Color,
    val pending: Color,
    val onPending: Color,
)

private val LightStatusColors = StatusColors(
    positive = Color(0xFF1B873F),
    onPositive = Color.White,
    negative = Color(0xFFD03A35),
    onNegative = Color.White,
    skipped = Color(0xFFF0C24B),
    onSkipped = Color(0xFF3A2C00),
    pending = Color(0xFFE1E6EC),
    onPending = Color(0xFF2B3138),
)

private val DarkStatusColors = StatusColors(
    positive = Color(0xFF1E8A43),
    onPositive = Color.White,
    negative = Color(0xFFC9372F),
    onNegative = Color.White,
    skipped = Color(0xFFD8A83A),
    onSkipped = Color(0xFF231A00),
    pending = Color(0xFF2B323B),
    onPending = Color(0xFFDDE3EA),
)

private val LightColors = lightColorScheme(
    primary = Color(0xFF1E6FB8),
    onPrimary = Color.White,
    primaryContainer = Color(0xFFD6E7F8),
    onPrimaryContainer = Color(0xFF0B2E4F),
    secondary = Color(0xFF52606D),
    onSecondary = Color.White,
    secondaryContainer = Color(0xFFDDE4EC),
    onSecondaryContainer = Color(0xFF1A242E),
    tertiary = Color(0xFF6B5E10),
    onTertiary = Color.White,
    tertiaryContainer = Color(0xFFF5E28A),
    onTertiaryContainer = Color(0xFF211B00),
    background = Color(0xFFF6F8FB),
    onBackground = Color(0xFF161B21),
    surface = Color(0xFFF6F8FB),
    onSurface = Color(0xFF161B21),
    surfaceVariant = Color(0xFFE1E6EC),
    onSurfaceVariant = Color(0xFF434B55),
    outline = Color(0xFF8A939E),
    outlineVariant = Color(0xFFC6CDD5),
    surfaceBright = Color(0xFFF6F8FB),
    surfaceDim = Color(0xFFD6DCE3),
    surfaceContainerLowest = Color(0xFFFFFFFF),
    surfaceContainerLow = Color(0xFFF0F3F7),
    surfaceContainer = Color(0xFFEAEEF3),
    surfaceContainerHigh = Color(0xFFE4E9EF),
    surfaceContainerHighest = Color(0xFFDEE4EB),
)

private val DarkColors = darkColorScheme(
    primary = Color(0xFF8EC5FF),
    onPrimary = Color(0xFF00335C),
    primaryContainer = Color(0xFF184D7D),
    onPrimaryContainer = Color(0xFFD6E7F8),
    secondary = Color(0xFFB9C6D3),
    onSecondary = Color(0xFF243140),
    secondaryContainer = Color(0xFF323E4B),
    onSecondaryContainer = Color(0xFFDDE4EC),
    tertiary = Color(0xFFD9C66F),
    onTertiary = Color(0xFF383000),
    tertiaryContainer = Color(0xFF514700),
    onTertiaryContainer = Color(0xFFF5E28A),
    background = Color(0xFF0F1419),
    onBackground = Color(0xFFE1E6EC),
    surface = Color(0xFF0F1419),
    onSurface = Color(0xFFE1E6EC),
    surfaceVariant = Color(0xFF2B323B),
    onSurfaceVariant = Color(0xFFBFC7D1),
    outline = Color(0xFF89919B),
    outlineVariant = Color(0xFF3F4750),
    surfaceBright = Color(0xFF343B44),
    surfaceDim = Color(0xFF0F1419),
    surfaceContainerLowest = Color(0xFF0A0E12),
    surfaceContainerLow = Color(0xFF161C22),
    surfaceContainer = Color(0xFF1A2027),
    surfaceContainerHigh = Color(0xFF222931),
    surfaceContainerHighest = Color(0xFF2B323B),
)

private val LocalStatusColors = staticCompositionLocalOf { LightStatusColors }

object AppTheme {
    val statusColors: StatusColors
        @Composable
        @ReadOnlyComposable
        get() = LocalStatusColors.current
}

/** Светлая/тёмная тема по системной настройке Android. */
@Composable
fun TgSorterTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalStatusColors provides if (darkTheme) DarkStatusColors else LightStatusColors,
    ) {
        MaterialTheme(
            colorScheme = if (darkTheme) DarkColors else LightColors,
            typography = Typography(),
            content = content,
        )
    }
}
