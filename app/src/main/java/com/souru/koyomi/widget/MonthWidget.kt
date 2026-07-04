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
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.provideContent
import androidx.glance.background
import androidx.glance.color.ColorProvider
import androidx.glance.action.clickable
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.layout.Alignment
import androidx.glance.layout.Box
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.holiday.JapaneseHolidays
import com.souru.koyomi.util.monthGridDays
import com.souru.koyomi.util.orderedWeekDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

private val SundayColor = ColorProvider(Color(0xFFC4574E), Color(0xFFE2867E))
private val SaturdayColor = ColorProvider(Color(0xFF4A6FA5), Color(0xFF8FAEDC))

class MonthWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = MonthWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/** 4x4 widget: this month's grid with a dot on days that have events. */
class MonthWidget : GlanceAppWidget() {

    private data class MonthData(
        val month: YearMonth,
        val weekStart: DayOfWeek,
        val days: List<LocalDate>,
        val eventDays: Set<LocalDate>,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
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
        val data = MonthData(
            month = month,
            weekStart = weekStart,
            days = days,
            eventDays = events.filterValues { it.isNotEmpty() }.keys,
        )

        provideContent {
            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceTheme.colors
                } else {
                    KoyomiWidgetColors
                },
            ) {
                MonthWidgetContent(context, data)
            }
        }
    }

    @Composable
    private fun MonthWidgetContent(context: Context, data: MonthData) {
        val today = LocalDate.now()
        val titleFormatter = DateTimeFormatter.ofPattern(
            context.getString(R.string.month_title_pattern),
            Locale.getDefault(),
        )
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(GlanceTheme.colors.surface)
                .cornerRadius(16.dp)
                .padding(10.dp)
                .clickable(actionStartActivity(openDayIntent(context, today))),
        ) {
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
                                DayOfWeek.SUNDAY -> SundayColor
                                DayOfWeek.SATURDAY -> SaturdayColor
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
                        DayCell(
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
    private fun androidx.glance.layout.RowScope.DayCell(
        context: Context,
        date: LocalDate,
        inMonth: Boolean,
        isToday: Boolean,
        hasEvents: Boolean,
    ) {
        val dayColor = when {
            !inMonth -> GlanceTheme.colors.onSurfaceVariant
            isToday -> GlanceTheme.colors.onPrimary
            JapaneseHolidays.isRedDay(date) -> SundayColor
            date.dayOfWeek == DayOfWeek.SATURDAY -> SaturdayColor
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
}
