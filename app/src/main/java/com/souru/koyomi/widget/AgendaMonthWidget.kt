package com.souru.koyomi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.souru.koyomi.R
import com.souru.koyomi.data.model.EventInstance
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

class AgendaMonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = AgendaMonthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/**
 * 4x2: today's agenda on the left, a dots-only mini month on the right —
 * the requested "day at a glance next to the month at a glance".
 */
class AgendaMonthWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val data = loadMonthGridData(context)
        val sections = loadDaySections(context, days = 1)
        val look = resolveWidgetLook(context, id)

        provideContent {
            GlanceTheme(colors = widgetColors(look)) {
                Content(context, sections.first(), data, look)
            }
        }
    }

    @Composable
    private fun Content(
        context: Context,
        today: WidgetDaySection,
        data: MonthGridData,
        look: WidgetLook,
    ) {
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(look))
                .cornerRadius(28.dp)
                // Whole-widget tap opens today; the header, event rows and
                // mini-month day cells (children) keep their own taps.
                .clickable(actionStartActivity(openDayIntent(context, today.date)))
                .padding(14.dp),
        ) {
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
            ) {
                TodayHeader(context, today.date)
                Spacer(modifier = GlanceModifier.height(4.dp))
                LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                    if (today.events.isEmpty()) {
                        item {
                            Text(
                                text = context.getString(R.string.no_events),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                ),
                                modifier = GlanceModifier.padding(top = 6.dp),
                            )
                        }
                    } else {
                        items(today.events) { event ->
                            CompactEventRow(context, today.date, event)
                        }
                    }
                }
            }
            Spacer(modifier = GlanceModifier.width(12.dp))
            Box(
                modifier = GlanceModifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(GlanceTheme.colors.surfaceVariant),
            ) {}
            Spacer(modifier = GlanceModifier.width(12.dp))
            GlanceMiniMonth(
                context = context,
                data = data,
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
            )
        }
    }

    /** "13" big + weekday small, the widget's left-hand identity. */
    @Composable
    private fun TodayHeader(context: Context, date: LocalDate) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = GlanceModifier.clickable(
                actionStartActivity(openDayIntent(context, date)),
            ),
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = date.dayOfWeek.getDisplayName(
                    JavaTextStyle.SHORT,
                    Locale.getDefault(),
                ),
                style = TextStyle(
                    color = GlanceTheme.colors.tertiary,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = GlanceModifier.padding(start = 6.dp),
            )
        }
    }

    /** Narrow event row: color bar + title over time, fits half a 4x2. */
    @Composable
    private fun CompactEventRow(context: Context, date: LocalDate, event: EventInstance) {
        Row(
            modifier = GlanceModifier
                .padding(vertical = 2.dp)
                .clickable(actionStartActivity(openDayIntent(context, date))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(width = 3.dp, height = 24.dp)
                    .background(eventColorProvider(event.color))
                    .cornerRadius(2.dp),
            ) {}
            Column(modifier = GlanceModifier.padding(start = 6.dp)) {
                Text(
                    text = event.title.ifBlank { context.getString(R.string.untitled) },
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = formatWidgetTime(context, event),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 9.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
