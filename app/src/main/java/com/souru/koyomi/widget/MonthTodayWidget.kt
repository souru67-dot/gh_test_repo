package com.souru.koyomi.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.souru.koyomi.R

private const val MAX_EVENTS = 3

class MonthTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthTodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/** 4x4 widget: this month's calendar with today's events underneath. */
class MonthTodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadMonthGridData(context)
        val sections = loadDaySections(context, days = 1)
        val opacity = widgetOpacity(context)

        provideContent {
            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceTheme.colors
                } else {
                    KoyomiWidgetColors
                },
            ) {
                Content(context, data, sections.first(), opacity)
            }
        }
    }

    @Composable
    private fun Content(
        context: Context,
        data: MonthGridData,
        today: WidgetDaySection,
        opacity: Int,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(opacity))
                .cornerRadius(16.dp)
                .padding(10.dp),
        ) {
            GlanceMonthCalendar(
                context = context,
                data = data,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            Text(
                text = context.getString(R.string.widget_today_label),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = GlanceModifier.padding(start = 4.dp, bottom = 2.dp),
            )
            if (today.events.isEmpty()) {
                WidgetNoEventsText(context)
            } else {
                for (event in today.events.take(MAX_EVENTS)) {
                    WidgetEventRow(context, today.date, event)
                }
            }
        }
    }
}
