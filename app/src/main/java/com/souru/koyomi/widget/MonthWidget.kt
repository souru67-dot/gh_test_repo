package com.souru.koyomi.widget

import android.content.Context
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/**
 * Month calendar widget: big month name, one-letter weekday header and up to
 * three calendar-color dots per day. Responsive from 4x3 to 5x5.
 */
class MonthWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_COMPACT, SIZE_FULL),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadMonthGridData(context)
        val look = resolveWidgetLook(context, id)

        provideContent {
            GlanceTheme(colors = widgetColors(look.theme)) {
                val size = LocalSize.current
                val compact = size.height < 230.dp
                GlanceMonthCalendar(
                    context = context,
                    data = data,
                    compact = compact,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(widgetBackground(look))
                        .cornerRadius(28.dp)
                        .padding(if (compact) 10.dp else 14.dp),
                )
            }
        }
    }

    private companion object {
        val SIZE_COMPACT = DpSize(180.dp, 180.dp)
        val SIZE_FULL = DpSize(250.dp, 250.dp)
    }
}
