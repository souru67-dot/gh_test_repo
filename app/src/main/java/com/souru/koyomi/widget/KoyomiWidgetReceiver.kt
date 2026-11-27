package com.souru.koyomi.widget

import android.content.Context
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import com.souru.koyomi.KoyomiApplication
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch

/**
 * Base for every Glance widget receiver: arms the shared refresh triggers
 * when the first instance is placed and forgets per-widget settings when an
 * instance is removed.
 */
abstract class KoyomiWidgetReceiver : GlanceAppWidgetReceiver() {

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }

    override fun onDeleted(context: Context, appWidgetIds: IntArray) {
        super.onDeleted(context, appWidgetIds)
        forgetWidgets(context, appWidgetIds)
    }
}

/** Drops the per-widget theme / opacity / LED color of removed widgets. */
internal fun forgetWidgets(context: Context, appWidgetIds: IntArray) {
    val settings = (context.applicationContext as KoyomiApplication).container.settingsRepository
    CoroutineScope(Dispatchers.IO).launch {
        appWidgetIds.forEach { runCatching { settings.clearWidget(it) } }
    }
}
