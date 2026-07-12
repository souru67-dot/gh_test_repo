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
 * Calms a raw calendar color for large surfaces (chips, bars, dots): Google's
 * palette at full saturation reads loud, so pull saturation and brightness
 * back a touch. Pickers keep the true color; only rendering is muted.
 */
fun mutedColor(color: Color): Color {
    val hsv = FloatArray(3)
    android.graphics.Color.colorToHSV(color.toArgb(), hsv)
    hsv[1] *= 0.70f
    hsv[2] *= 0.96f
    return Color(android.graphics.Color.HSVToColor(hsv))
}
