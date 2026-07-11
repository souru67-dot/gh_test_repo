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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.layout.width
import com.souru.koyomi.R

class ClockTodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = ClockTodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/** 4x2 widget: a live clock next to today's events. */
class ClockTodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sections = loadDaySections(context, days = 1)
        val look = resolveWidgetLook(context, id)

        provideContent {
            GlanceTheme(colors = widgetColors(look.theme)) {
                Content(context, sections.first(), look)
            }
        }
    }

    @Composable
    private fun Content(context: Context, today: WidgetDaySection, look: WidgetLook) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(look))
                .cornerRadius(28.dp)
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetClock(
                context = context,
                modifier = GlanceModifier.width(120.dp),
            )
            Spacer(modifier = GlanceModifier.width(12.dp))
            LazyColumn(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
            ) {
                if (today.events.isEmpty()) {
                    item { WidgetCenteredMessage(context.getString(R.string.no_events)) }
                } else {
                    items(today.events) { event ->
                        WidgetEventRow(context, today.date, event)
                    }
                }
            }
        }
    }
}
