package com.souru.koyomi.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.glance.material3.ColorProviders
import com.souru.koyomi.MainActivity
import com.souru.koyomi.ui.theme.KoyomiDarkColors
import com.souru.koyomi.ui.theme.KoyomiLightColors
import java.time.LocalDate

/** Static light/dark palette for widgets on devices without dynamic color. */
val KoyomiWidgetColors = ColorProviders(
    light = KoyomiLightColors,
    dark = KoyomiDarkColors,
)

/**
 * Intent that opens the app on [date]. A unique data URI keeps the
 * PendingIntents of different day cells distinct.
 */
fun openDayIntent(context: Context, date: LocalDate): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse("koyomi://day/${date.toEpochDay()}")
        putExtra(MainActivity.EXTRA_EPOCH_DAY, date.toEpochDay())
        addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
