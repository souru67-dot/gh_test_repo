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
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
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

/**
 * A live clock next to the upcoming agenda: the next few days that actually
 * have events (up to 3), scrollable, each under a 今日/明日/date header.
 */
class ClockTodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sections = loadUpcomingDaySections(context, maxDays = 3, lookaheadDays = 14)
        val look = resolveWidgetLook(context, id)

        provideContent {
            GlanceTheme(colors = widgetColors(look)) {
                Content(context, sections, look)
            }
        }
    }

    @Composable
    private fun Content(
        context: Context,
        sections: List<WidgetDaySection>,
        look: WidgetLook,
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(look))
                .cornerRadius(28.dp)
                // Whole-widget tap opens today; the clock and event rows
                // (children) keep their own taps.
                .clickable(actionStartActivity(openDayIntent(context, java.time.LocalDate.now())))
                .padding(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            WidgetClock(
                context = context,
                modifier = GlanceModifier.width(128.dp),
            )
            Spacer(modifier = GlanceModifier.width(12.dp))
            // Vertical hairline separating the clock from the agenda.
            Box(
                modifier = GlanceModifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(GlanceTheme.colors.surfaceVariant),
            ) {}
            Spacer(modifier = GlanceModifier.width(12.dp))
            LazyColumn(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
            ) {
                if (sections.all { it.events.isEmpty() }) {
                    item { WidgetCenteredMessage(context.getString(R.string.no_events)) }
                } else {
                    for (section in sections) {
                        if (section.events.isEmpty()) continue
                        item {
                            WidgetSectionLabel(
                                text = relativeDayLabel(context, section.date),
                                modifier = GlanceModifier.padding(top = 4.dp, bottom = 2.dp),
                            )
                        }
                        items(section.events) { event ->
                            WidgetEventRow(context, section.date, event)
                        }
                    }
                }
            }
        }
    }
}
