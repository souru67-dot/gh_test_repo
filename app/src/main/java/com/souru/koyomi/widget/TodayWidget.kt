package com.souru.koyomi.widget

import android.content.Context
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.LocalSize
import androidx.glance.action.clickable
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.SizeMode
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.souru.koyomi.R
import com.souru.koyomi.data.model.EventInstance
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/**
 * Today's agenda: a big date, remaining events with calendar-color bars, and
 * tomorrow's first event dimmed at the end. At 2x2 it degrades to a minimal
 * "date + next event" card.
 */
class TodayWidget : GlanceAppWidget() {

    override val sizeMode: SizeMode = SizeMode.Responsive(
        setOf(SIZE_MINI, SIZE_REGULAR),
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sections = loadDaySections(context, days = 2)
        val look = resolveWidgetLook(context, id)

        provideContent {
            GlanceTheme(colors = widgetColors(look.theme)) {
                val size = LocalSize.current
                if (size.width < 180.dp) {
                    MiniContent(context, sections[0], look)
                } else {
                    FullContent(context, sections[0], sections[1], look)
                }
            }
        }
    }

    /** Events still ahead of "now" (all-day events count all day). */
    private fun remaining(events: List<EventInstance>): List<EventInstance> {
        val now = System.currentTimeMillis()
        return events.filter { it.allDay || it.end > now }
    }

    @Composable
    private fun FullContent(
        context: Context,
        today: WidgetDaySection,
        tomorrow: WidgetDaySection,
        look: WidgetLook,
    ) {
        val remainingEvents = remaining(today.events)
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(look))
                .cornerRadius(28.dp)
                .padding(16.dp),
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = today.date.dayOfMonth.toString(),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 34.sp,
                        fontWeight = FontWeight.Bold,
                    ),
                    modifier = GlanceModifier.clickable(
                        actionStartActivity(openDayIntent(context, today.date)),
                    ),
                )
                Column(modifier = GlanceModifier.padding(start = 12.dp)) {
                    Text(
                        text = monthAndWeekday(today.date),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 11.sp,
                        ),
                    )
                    Text(
                        text = if (today.events.isEmpty()) {
                            context.getString(R.string.no_events)
                        } else {
                            context.getString(R.string.events_count, today.events.size)
                        },
                        style = TextStyle(
                            // 朱 when the day holds something — a quiet pulse.
                            color = if (today.events.isEmpty()) {
                                GlanceTheme.colors.onSurfaceVariant
                            } else {
                                GlanceTheme.colors.tertiary
                            },
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Medium,
                        ),
                    )
                }
            }
            Spacer(modifier = GlanceModifier.height(8.dp))
            WidgetHairlineDivider()
            Spacer(modifier = GlanceModifier.height(4.dp))
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                when {
                    today.events.isEmpty() -> item {
                        WidgetCenteredMessage(context.getString(R.string.no_events))
                    }
                    remainingEvents.isEmpty() -> item {
                        WidgetCenteredMessage(context.getString(R.string.no_more_today))
                    }
                    else -> items(remainingEvents) { event ->
                        WidgetEventRow(context, today.date, event)
                    }
                }
                tomorrow.events.firstOrNull()?.let { first ->
                    item {
                        WidgetEventRow(
                            context = context,
                            date = tomorrow.date,
                            event = first,
                            dimmed = true,
                            timePrefix = context.getString(R.string.tomorrow_label),
                        )
                    }
                }
            }
        }
    }

    @Composable
    private fun MiniContent(
        context: Context,
        today: WidgetDaySection,
        look: WidgetLook,
    ) {
        val next = remaining(today.events).firstOrNull()
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(look))
                .cornerRadius(28.dp)
                .padding(12.dp)
                .clickable(actionStartActivity(openDayIntent(context, today.date))),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = today.date.dayOfMonth.toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 36.sp,
                    fontWeight = FontWeight.Bold,
                    textAlign = TextAlign.Center,
                ),
            )
            Text(
                text = monthAndWeekday(today.date),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                    textAlign = TextAlign.Center,
                ),
            )
            Spacer(modifier = GlanceModifier.height(6.dp))
            if (next == null) {
                Text(
                    text = context.getString(R.string.no_events),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                        textAlign = TextAlign.Center,
                    ),
                )
            } else {
                Text(
                    text = formatWidgetTime(context, next),
                    style = TextStyle(
                        color = GlanceTheme.colors.primary,
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.Center,
                    ),
                )
                Text(
                    text = next.title.ifBlank { context.getString(R.string.untitled) },
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        textAlign = TextAlign.Center,
                    ),
                    maxLines = 1,
                )
            }
        }
    }

    private fun monthAndWeekday(date: LocalDate): String {
        val locale = Locale.getDefault()
        val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale)
        return if (locale.language == "ja") {
            "${date.monthValue}月 · $weekday"
        } else {
            "${date.format(DateTimeFormatter.ofPattern("MMMM", locale))} · $weekday"
        }
    }

    private companion object {
        val SIZE_MINI = DpSize(110.dp, 110.dp)
        val SIZE_REGULAR = DpSize(250.dp, 120.dp)
    }
}
