package com.souru.koyomi.widget

import android.content.Context
import android.os.Build
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
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
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.size
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import androidx.glance.unit.ColorProvider
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.model.EventInstance
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.flow.first

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/** 4x2 widget: today's and tomorrow's events with times. */
class TodayWidget : GlanceAppWidget() {

    private data class DaySection(val date: LocalDate, val events: List<EventInstance>)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as KoyomiApplication
        val today = LocalDate.now()
        val hidden = app.container.settingsRepository.hiddenCalendarIds.first()
        val eventsByDay = app.container.calendarRepository.loadEventsByDay(
            today,
            today.plusDays(2),
            hidden,
        )
        val sections = listOf(
            DaySection(today, eventsByDay[today].orEmpty()),
            DaySection(today.plusDays(1), eventsByDay[today.plusDays(1)].orEmpty()),
        )

        provideContent {
            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceTheme.colors
                } else {
                    KoyomiWidgetColors
                },
            ) {
                TodayWidgetContent(context, sections)
            }
        }
    }

    @Composable
    private fun TodayWidgetContent(context: Context, sections: List<DaySection>) {
        val dateFormatter = DateTimeFormatter.ofPattern(
            context.getString(R.string.sheet_date_pattern),
            Locale.getDefault(),
        )
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(12.dp),
        ) {
            LazyColumn(modifier = GlanceModifier.fillMaxSize()) {
                for (section in sections) {
                    item {
                        Text(
                            text = section.date.format(dateFormatter),
                            style = TextStyle(
                                color = GlanceTheme.colors.primary,
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Medium,
                            ),
                            modifier = GlanceModifier
                                .padding(top = 2.dp, bottom = 2.dp)
                                .clickable(
                                    actionStartActivity(openDayIntent(context, section.date)),
                                ),
                        )
                    }
                    if (section.events.isEmpty()) {
                        item {
                            Text(
                                text = context.getString(R.string.no_events),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 12.sp,
                                ),
                                modifier = GlanceModifier.padding(start = 8.dp, bottom = 4.dp),
                            )
                        }
                    } else {
                        items(section.events) { event ->
                            EventRow(context, section.date, event)
                        }
                    }
                }
            }
        }
    }

    @Composable
    private fun EventRow(context: Context, date: LocalDate, event: EventInstance) {
        val zone = ZoneId.systemDefault()
        val timeText = if (event.allDay) {
            context.getString(R.string.all_day)
        } else {
            DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
                .format(Instant.ofEpochMilli(event.begin).atZone(zone))
        }
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable(actionStartActivity(openDayIntent(context, date))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(width = 3.dp, height = 22.dp)
                    .background(
                        ColorProvider(
                            com.souru.koyomi.util.providerColor(event.color)
                                ?: Color(0xFF3D4A3D),
                        ),
                    )
                    .cornerRadius(2.dp),
            ) {}
            Text(
                text = timeText,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                modifier = GlanceModifier.padding(start = 8.dp).width(42.dp),
            )
            Text(
                text = event.title.ifBlank { context.getString(R.string.untitled) },
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = 4.dp),
            )
        }
    }
}
