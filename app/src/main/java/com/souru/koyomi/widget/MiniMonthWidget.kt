package com.souru.koyomi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Box
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding

class MiniMonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MiniMonthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/** 2x2: the dots-only mini month on its own — the smallest こよみ. */
class MiniMonthWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadMonthGridData(context)
        val look = resolveWidgetLook(context, id)
        val premium = isPremiumUnlocked(context)

        provideContent {
            GlanceTheme(colors = widgetColors(look)) {
                if (!premium) {
                    WidgetPremiumLock(context, look)
                } else {
                    Box(
                        modifier = GlanceModifier
                            .fillMaxSize()
                            .background(widgetBackground(look))
                            .cornerRadius(28.dp)
                            .padding(12.dp),
                    ) {
                        GlanceMiniMonth(
                            context = context,
                            data = data,
                            modifier = GlanceModifier.fillMaxSize(),
                        )
                    }
                }
            }
        }
    }
}
