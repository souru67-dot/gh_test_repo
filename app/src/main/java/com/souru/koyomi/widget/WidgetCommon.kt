package com.souru.koyomi.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.color.ColorProvider
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
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.holiday.JapaneseHolidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.util.monthGridDays
import com.souru.koyomi.util.orderedWeekDays
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

val WidgetSundayColor = ColorProvider(Color(0xFFC4574E), Color(0xFFE2867E))
val WidgetSaturdayColor = ColorProvider(Color(0xFF4A6FA5), Color(0xFF8FAEDC))

private val LightSurface = Color(0xFFFBF9F6)
private val DarkSurface = Color(0xFF15140F)

/**
 * Widget background honoring the opacity setting. At 100% the dynamic theme
 * surface is used; below that we blend our static palette with alpha (dynamic
 * ColorProviders cannot carry an alpha).
 */
@Composable
fun widgetBackground(opacityPercent: Int): androidx.glance.unit.ColorProvider =
    if (opacityPercent >= 100) {
        GlanceTheme.colors.surface
    } else {
        val alpha = opacityPercent / 100f
        ColorProvider(
            LightSurface.copy(alpha = alpha),
            DarkSurface.copy(alpha = alpha),
        )
    }

suspend fun widgetOpacity(context: Context): Int {
    val app = context.applicationContext as KoyomiApplication
    return app.container.settingsRepository.widgetOpacityPercent.first()
}

// ---------- Month grid ----------

data class MonthGridData(
    val month: YearMonth,
    val weekStart: DayOfWeek,
    val days: List<LocalDate>,
    val eventDays: Set<LocalDate>,
)

suspend fun loadMonthGridData(context: Context): MonthGridData {
    val app = context.applicationContext as KoyomiApplication
    val month = YearMonth.now()
    val weekStart = app.container.settingsRepository.weekStart.first()
    val hidden = app.container.settingsRepository.hiddenCalendarIds.first()
    val days = monthGridDays(month, weekStart)
    val events = app.container.calendarRepository.loadEventsByDay(
        days.first(),
        days.last().plusDays(1),
        hidden,
    )
    return MonthGridData(
        month = month,
        weekStart = weekStart,
        days = days,
        eventDays = events.filterValues { it.isNotEmpty() }.keys,
    )
}

/** Compact month calendar: title, weekday header and a 6-week dot grid. */
@Composable
fun GlanceMonthCalendar(
    context: Context,
    data: MonthGridData,
    modifier: GlanceModifier = GlanceModifier,
) {
    val today = LocalDate.now()
    val titleFormatter = DateTimeFormatter.ofPattern(
        context.getString(R.string.month_title_pattern),
        Locale.getDefault(),
    )
    Column(modifier = modifier) {
        Text(
            text = data.month.format(titleFormatter),
            style = TextStyle(
                color = GlanceTheme.colors.onSurface,
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
            ),
            modifier = GlanceModifier.padding(start = 4.dp, bottom = 4.dp),
        )
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            for (day in orderedWeekDays(data.weekStart)) {
                Text(
                    text = day.getDisplayName(JavaTextStyle.NARROW, Locale.getDefault()),
                    style = TextStyle(
                        color = when (day) {
                            DayOfWeek.SUNDAY -> WidgetSundayColor
                            DayOfWeek.SATURDAY -> WidgetSaturdayColor
                            else -> GlanceTheme.colors.onSurfaceVariant
                        },
                        fontSize = 9.sp,
                        textAlign = TextAlign.Center,
                    ),
                    modifier = GlanceModifier.defaultWeight(),
                )
            }
        }
        for (week in 0 until 6) {
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
            ) {
                for (i in 0 until 7) {
                    val date = data.days[week * 7 + i]
                    WidgetDayCell(
                        context = context,
                        date = date,
                        inMonth = YearMonth.from(date) == data.month,
                        isToday = date == today,
                        hasEvents = date in data.eventDays,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.WidgetDayCell(
    context: Context,
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    hasEvents: Boolean,
) {
    val dayColor = when {
        !inMonth -> GlanceTheme.colors.onSurfaceVariant
        isToday -> GlanceTheme.colors.onPrimary
        JapaneseHolidays.isRedDay(date) -> WidgetSundayColor
        date.dayOfWeek == DayOfWeek.SATURDAY -> WidgetSaturdayColor
        else -> GlanceTheme.colors.onSurface
    }
    Column(
        modifier = GlanceModifier
            .defaultWeight()
            .clickable(actionStartActivity(openDayIntent(context, date))),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = if (isToday) {
                GlanceModifier
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(9.dp)
                    .padding(horizontal = 4.dp)
            } else {
                GlanceModifier.padding(horizontal = 4.dp)
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = TextStyle(
                    color = dayColor,
                    fontSize = 11.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        if (hasEvents) {
            Text(
                text = "•",
                style = TextStyle(
                    color = if (inMonth) {
                        GlanceTheme.colors.primary
                    } else {
                        GlanceTheme.colors.onSurfaceVariant
                    },
                    fontSize = 8.sp,
                    textAlign = TextAlign.Center,
                ),
            )
        } else {
            Spacer(modifier = GlanceModifier.padding(2.dp))
        }
    }
}

// ---------- Event rows ----------

data class WidgetDaySection(val date: LocalDate, val events: List<EventInstance>)

suspend fun loadDaySections(context: Context, days: Int): List<WidgetDaySection> {
    val app = context.applicationContext as KoyomiApplication
    val today = LocalDate.now()
    val hidden = app.container.settingsRepository.hiddenCalendarIds.first()
    val eventsByDay = app.container.calendarRepository.loadEventsByDay(
        today,
        today.plusDays(days.toLong()),
        hidden,
    )
    return (0 until days).map { offset ->
        val date = today.plusDays(offset.toLong())
        WidgetDaySection(date, eventsByDay[date].orEmpty())
    }
}

@Composable
fun WidgetEventRow(context: Context, date: LocalDate, event: EventInstance) {
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
                    androidx.glance.unit.ColorProvider(
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

@Composable
fun WidgetNoEventsText(context: Context) {
    Text(
        text = context.getString(R.string.no_events),
        style = TextStyle(
            color = GlanceTheme.colors.onSurfaceVariant,
            fontSize = 12.sp,
        ),
        modifier = GlanceModifier.padding(start = 8.dp, top = 4.dp),
    )
}

// ---------- Clock ----------

/** Auto-updating clock via RemoteViews TextClock (no periodic redraws needed). */
@Composable
fun WidgetClock(context: Context, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier.clickable(
            actionStartActivity(openDayIntent(context, LocalDate.now())),
        ),
    ) {
        AndroidRemoteViews(RemoteViews(context.packageName, R.layout.widget_clock))
    }
}
