package com.souru.colorhunt.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

// Brand accents — vibrant, "映え" forward.
val BrandViolet = Color(0xFF8B5CFF)
val BrandCyan = Color(0xFF22D3EE)
val BrandPink = Color(0xFFF472B6)
val BrandAmber = Color(0xFFFBBF24)

// Dark base surfaces.
private val Ink = Color(0xFF0D0D12)
private val InkSurface = Color(0xFF17171F)
private val InkSurfaceHigh = Color(0xFF20202B)
private val InkOutline = Color(0xFF34343F)

private val ColorHuntDarkColors = darkColorScheme(
    primary = BrandViolet,
    onPrimary = Color.White,
    secondary = BrandCyan,
    onSecondary = Color(0xFF042F35),
    tertiary = BrandPink,
    onTertiary = Color(0xFF3A0B23),
    background = Ink,
    onBackground = Color(0xFFF3F3F7),
    surface = InkSurface,
    onSurface = Color(0xFFF3F3F7),
    surfaceVariant = InkSurfaceHigh,
    onSurfaceVariant = Color(0xFFB9B9C6),
    outline = InkOutline,
)

// A light scheme is kept for completeness, but the app commits to the dark look
// by default for an immersive, gallery-like feel.
private val ColorHuntLightColors = lightColorScheme(
    primary = BrandViolet,
    secondary = Color(0xFF0EA5B7),
    tertiary = BrandPink,
)

@Composable
fun ColorHuntTheme(
    darkTheme: Boolean = true, // committed dark look; pass isSystemInDarkTheme() to follow the system
    content: @Composable () -> Unit,
) {
    // Reference isSystemInDarkTheme so callers can opt in later without a warning.
    val systemDark = isSystemInDarkTheme()
    val useDark = darkTheme || systemDark
    MaterialTheme(
        colorScheme = if (useDark) ColorHuntDarkColors else ColorHuntLightColors,
        typography = ColorHuntTypography,
        content = content,
    )
}
