package com.souru.koyomi.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import com.souru.koyomi.MainActivity
import java.time.LocalDate

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
