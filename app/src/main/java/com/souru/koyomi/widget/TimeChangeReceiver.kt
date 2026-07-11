package com.souru.koyomi.widget

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Redraws the widgets when the clock context shifts under them: a timezone
 * change or a manual time set. (Midnight rollover is handled by the
 * WorkManager one-shot in WidgetUpdateWorker.)
 */
class TimeChangeReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action !in setOf(Intent.ACTION_TIMEZONE_CHANGED, Intent.ACTION_TIME_CHANGED)) {
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
