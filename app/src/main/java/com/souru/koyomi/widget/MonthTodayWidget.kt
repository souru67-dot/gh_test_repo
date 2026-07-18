package com.souru.koyomi.widget

import android.content.Context
import androidx.compose.runtime.Composable
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
import androidx.glance.layout.Column
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import com.souru.koyomi.R
import java.time.LocalDate

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
        val look = resolveWidgetLook(context, id)

        val premium = isPremiumUnlocked(context)
        provideContent {
            GlanceTheme(colors = widgetColors(look)) {
                if (!premium) {
                    WidgetPremiumLock(context, look)
                } else {
                    Content(context, data, sections.first(), look)
                }
            }
        }
    }

    @Composable
    private fun Content(
        context: Context,
        data: MonthGridData,
        today: WidgetDaySection,
        look: WidgetLook,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(look))
                .cornerRadius(28.dp)
                // Whole-widget tap opens the app; day cells and event rows
                // (children) keep their own, more specific taps.
                .clickable(actionStartActivity(openDayIntent(context, LocalDate.now())))
                .padding(14.dp),
        ) {
            GlanceMonthCalendar(
                context = context,
                data = data,
                compact = true,
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            WidgetHairlineDivider()
            Spacer(modifier = GlanceModifier.height(4.dp))
            WidgetSectionLabel(
                text = context.getString(R.string.widget_today_label),
                modifier = GlanceModifier.padding(start = 4.dp, bottom = 2.dp),
            )
            if (today.events.isEmpty()) {
                WidgetCenteredMessage(context.getString(R.string.no_events))
            } else {
                for (event in today.events.take(MAX_EVENTS)) {
                    WidgetEventRow(context, today.date, event)
                }
            }
        }
    }
}
