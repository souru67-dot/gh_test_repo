package com.souru.koyomi.util

import android.content.Context
import android.text.format.DateFormat
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 時刻の表記: follow the device, or force 24-hour / 12-hour everywhere. */
enum class ClockStyle { SYSTEM, H24, H12 }

/**
 * The app-wide clock style, mirrored from settings by the Application (like
 * the holiday country). Snapshot state, so composables reading it recompose
 * when the user changes the setting.
 */
object TimeStyle {
    var style: ClockStyle by mutableStateOf(ClockStyle.SYSTEM)

    /** Whether times should be shown in 24-hour form right now. */
    fun is24Hour(context: Context): Boolean = when (style) {
        ClockStyle.H24 -> true
        ClockStyle.H12 -> false
        ClockStyle.SYSTEM -> DateFormat.is24HourFormat(context)
    }
}

/**
 * A time-of-day formatter that honors the 12/24-hour choice (app setting,
 * else the device's) and the locale's arrangement ("14:30" / "2:30 PM" /
 * "午後2:30"). Use everywhere a time is shown, instead of a fixed "HH:mm".
 */
fun deviceTimeFormatter(
    context: Context,
    locale: Locale = Locale.getDefault(),
): DateTimeFormatter {
    val skeleton = if (TimeStyle.is24Hour(context)) "Hm" else "hm"
    val pattern = DateFormat.getBestDateTimePattern(locale, skeleton)
    return DateTimeFormatter.ofPattern(pattern, locale)
}

/** Composable helper: rebuilds if the 12/24-hour choice or locale changes. */
@Composable
fun rememberDeviceTimeFormatter(): DateTimeFormatter {
    val context = LocalContext.current
    val locale = Locale.getDefault()
    val is24 = TimeStyle.is24Hour(context)
    return remember(context, is24, locale) { deviceTimeFormatter(context, locale) }
}
