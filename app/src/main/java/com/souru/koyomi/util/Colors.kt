package com.souru.koyomi.util

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.toArgb

/**
 * Colors coming out of CalendarProvider are plain ints that sync adapters
 * sometimes store without an alpha channel (0x00RRGGBB). Rendered as-is they
 * become fully transparent/black, so force full opacity. 0 means "no color".
 */
fun providerColor(colorInt: Int): Color? =
    if (colorInt == 0) null else Color(colorInt or 0xFF000000.toInt())

/**
 * Calms a raw calendar color for large surfaces (chips, bars, dots) while
 * KEEPING text legible. In light themes the color is deepened (white text
 * stays readable); in dark themes it is brightened slightly so it doesn't
 * sink into the background. Pickers keep the true color.
 */
fun mutedColor(color: Color, darkTheme: Boolean = false): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    if (darkTheme) {
        hsv[1] = (hsv[1] * 0.80f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 1.12f).coerceIn(0.55f, 0.92f)
    } else {
        hsv[1] = (hsv[1] * 0.92f).coerceIn(0f, 1f)
        hsv[2] = (hsv[2] * 0.80f).coerceIn(0.32f, 0.82f)
    }
    return Color(android.graphics.Color.HSVToColor(hsv))
}
