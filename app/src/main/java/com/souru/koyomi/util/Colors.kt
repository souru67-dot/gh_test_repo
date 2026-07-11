package com.souru.koyomi.util

import androidx.compose.ui.graphics.Color

/**
 * Colors coming out of CalendarProvider are plain ints that sync adapters
 * sometimes store without an alpha channel (0x00RRGGBB). Rendered as-is they
 * become fully transparent/black, so force full opacity. 0 means "no color".
 */
fun providerColor(colorInt: Int): Color? =
    if (colorInt == 0) null else Color(colorInt or 0xFF000000.toInt())
