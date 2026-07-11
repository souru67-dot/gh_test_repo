package com.souru.koyomi.widget

import android.content.Context
import android.os.Build
import androidx.compose.ui.unit.dp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import java.time.LocalDate

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/** 4x4 widget: this month's grid with a dot on days that have events. */
class MonthWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadMonthGridData(context)
        val opacity = widgetOpacity(context)

        provideContent {
            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceTheme.colors
                } else {
                    KoyomiWidgetColors
                },
            ) {
                GlanceMonthCalendar(
                    context = context,
                    data = data,
                    modifier = GlanceModifier
                        .fillMaxSize()
                        .background(widgetBackground(opacity))
                        .cornerRadius(16.dp)
                        .padding(10.dp)
                        .clickable(
                            actionStartActivity(openDayIntent(context, LocalDate.now())),
                        ),
                )
            }
        }
    }
}
