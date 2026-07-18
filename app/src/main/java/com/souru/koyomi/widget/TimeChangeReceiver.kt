package com.souru.koyomi.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Redraws the widgets when the calendar context shifts under them: the date
 * rolling over at midnight, a timezone change, or a manual time set. The
 * DATE_CHANGED broadcast matters most — the WorkManager midnight one-shot
 * can be deferred by Doze, which left yesterday's today-circle on screen
 * (e.g. both the 16th and the 17th ringed) until the next update.
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        val actions = setOf(
            Intent.ACTION_DATE_CHANGED,
            Intent.ACTION_TIMEZONE_CHANGED,
            Intent.ACTION_TIME_CHANGED,
        )
        if (intent.action !in actions) {
            return
        }
        val result = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                WidgetUpdateWorker.updateAllWidgets(context.applicationContext)
                WidgetUpdateWorker.schedule(context.applicationContext)
            } finally {
                result.finish()
            }
        }
    }
}
