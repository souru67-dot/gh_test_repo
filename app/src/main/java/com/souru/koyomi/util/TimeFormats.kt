package com.souru.koyomi.util

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * A time-of-day formatter that honors the device's 12/24-hour setting and
 * the current locale's arrangement (e.g. "14:30" vs "2:30 PM" vs "午後2:30").
 * Use everywhere a time is shown to the user, instead of a fixed "HH:mm".
 */
fun deviceTimeFormatter(
    context: Context,
    locale: Locale = Locale.getDefault(),
): DateTimeFormatter {
    val skeleton = if (DateFormat.is24HourFormat(context)) "Hm" else "hm"
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return DateTimeFormatter.ofPattern(pattern, locale)
}

/** Composable helper: rebuilds if the 24-hour setting or locale changes. */
@Composable
fun rememberDeviceTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val is24 = DateFormat.is24HourFormat(context)
    return remember(context, is24, locale) { deviceTimeFormatter(context, locale) }
}
