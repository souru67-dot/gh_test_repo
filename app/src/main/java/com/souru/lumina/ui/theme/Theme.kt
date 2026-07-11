package com.souru.lumina.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// 写真が主役の黒基調ミニマルパレット。アクセントは温白色。
private val WarmWhite = Color(0xFFE8E2D4)
private val NearBlack = Color(0xFF0A0A0A)

private val LuminaColors = darkColorScheme(
    primary = WarmWhite,
    onPrimary = Color(0xFF1C1B17),
    primaryContainer = Color(0xFF2A2823),
    onPrimaryContainer = WarmWhite,
    secondary = Color(0xFFBDB8AC),
    onSecondary = Color(0xFF23221E),
    secondaryContainer = Color(0xFF262521),
    onSecondaryContainer = Color(0xFFD8D2C4),
    tertiary = Color(0xFFA8C0B8),
    onTertiary = Color(0xFF1B2420),
    background = Color.Black,
    onBackground = Color(0xFFEDEAE2),
    surface = NearBlack,
    onSurface = Color(0xFFEDEAE2),
    surfaceVariant = Color(0xFF1D1D1B),
    onSurfaceVariant = Color(0xFFB3AFA5),
    surfaceContainer = Color(0xFF141412),
    surfaceContainerHigh = Color(0xFF1D1D1B),
    surfaceContainerHighest = Color(0xFF262622),
    outline = Color(0xFF4A4944),
    outlineVariant = Color(0xFF2E2E2A),
    error = Color(0xFFFFB4AB),
    onError = Color(0xFF690005),
)

@Composable
fun LuminaTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = LuminaColors,
        typography = Typography(),
        content = content,
    )
}
