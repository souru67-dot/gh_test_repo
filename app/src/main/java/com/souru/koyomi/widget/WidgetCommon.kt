package com.souru.koyomi.widget

import android.content.Context
import android.os.Build
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
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
import androidx.glance.material3.ColorProviders
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextAlign
import androidx.glance.text.TextStyle
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.ThemeMode
import com.souru.koyomi.data.holiday.JapaneseHolidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.ui.theme.KoyomiDarkColors
import com.souru.koyomi.ui.theme.KoyomiLightColors
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

// Muted (desaturated) weekend tints — quiet enough for a home screen.
val WidgetSundayColor = ColorProvider(Color(0xFFBA7B74), Color(0xFFCB968F))
val WidgetSaturdayColor = ColorProvider(Color(0xFF7A8FA8), Color(0xFF93A8BF))

private val LightSurface = Color(0xFFFBF9F6)
private val DarkSurface = Color(0xFF15140F)

// ---------- Per-widget look (theme + opacity) ----------

data class WidgetLook(
    val theme: ThemeMode,
    val opacityPercent: Int,
)

/**
 * Resolves the per-widget theme/opacity chosen in the widget config screen,
 * falling back to app-wide defaults. The appWidgetId is recovered from the
 * GlanceId's stable string form ("AppWidgetId(appWidgetId=42)").
 */
suspend fun resolveWidgetLook(context: Context, glanceId: GlanceId): WidgetLook {
    val app = context.applicationContext as KoyomiApplication
    val settings = app.container.settingsRepository
    val appWidgetId = glanceId.toString().filter { it.isDigit() }.toIntOrNull() ?: -1
    val theme = settings.widgetTheme(appWidgetId).first()
    val opacity = settings.widgetOpacity(appWidgetId).first()
        ?: settings.widgetOpacityPercent.first()
    return WidgetLook(theme = theme, opacityPercent = opacity)
}

/** Color scheme for the chosen widget theme (SYSTEM = dynamic on Android 12+). */
@Composable
fun widgetColors(theme: ThemeMode): androidx.glance.color.ColorProviders = when (theme) {
    ThemeMode.SYSTEM ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceTheme.colors
        } else {
            KoyomiWidgetColors
        }
    ThemeMode.LIGHT -> ColorProviders(light = KoyomiLightColors, dark = KoyomiLightColors)
    ThemeMode.DARK -> ColorProviders(light = KoyomiDarkColors, dark = KoyomiDarkColors)
}

/** Widget background honoring the opacity setting (0 = fully transparent). */
@Composable
fun widgetBackground(look: WidgetLook): androidx.glance.unit.ColorProvider {
    if (look.opacityPercent >= 100 && look.theme == ThemeMode.SYSTEM) {
        return GlanceTheme.colors.surface
    }
    val alpha = look.opacityPercent / 100f
    return when (look.theme) {
        ThemeMode.SYSTEM -> ColorProvider(
            LightSurface.copy(alpha = alpha),
            DarkSurface.copy(alpha = alpha),
        )
        ThemeMode.LIGHT -> androidx.glance.unit.ColorProvider(
            LightSurface.copy(alpha = alpha),
        )
        ThemeMode.DARK -> androidx.glance.unit.ColorProvider(
            DarkSurface.copy(alpha = alpha),
        )
    }
}

// ---------- Month grid ----------

data class MonthGridData(
    val month: YearMonth,
    val weekStart: DayOfWeek,
    val days: List<LocalDate>,
    /** Up to 3 distinct calendar colors per day, in start order. */
    val dayDots: Map<LocalDate, List<Int>>,
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
        dayDots = events.mapValues { (_, list) ->
            list.map { it.color }.distinct().take(3)
        }.filterValues { it.isNotEmpty() },
    )
}

/**
 * Minimal month calendar: a big month name with a small year, one-letter
 * weekday header and a 6-week grid where days carry up to three
 * calendar-color dots. Tapping a day opens it in the app.
 */
@Composable
fun GlanceMonthCalendar(
    context: Context,
    data: MonthGridData,
    modifier: GlanceModifier = GlanceModifier,
    compact: Boolean = false,
) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    val monthLabel = if (locale.language == "ja") {
        "${data.month.monthValue}月"
    } else {
        data.month.format(DateTimeFormatter.ofPattern("MMMM", locale))
    }
    Column(modifier = modifier) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(start = 6.dp, end = 2.dp, bottom = if (compact) 2.dp else 6.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = monthLabel,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = if (compact) 18.sp else 24.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Text(
                text = data.month.year.toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                modifier = GlanceModifier.padding(start = 6.dp, top = 4.dp),
            )
            Spacer(modifier = GlanceModifier.defaultWeight())
            Text(
                text = context.getString(R.string.back_to_today),
                style = TextStyle(
                    color = GlanceTheme.colors.onPrimaryContainer,
                    fontSize = 11.sp,
                    fontWeight = FontWeight.Medium,
                ),
                modifier = GlanceModifier
                    .background(GlanceTheme.colors.primaryContainer)
                    .cornerRadius(12.dp)
                    .padding(horizontal = 10.dp, vertical = 4.dp)
                    .clickable(actionStartActivity(openDayIntent(context, today))),
            )
        }
        Row(modifier = GlanceModifier.fillMaxWidth()) {
            for (day in orderedWeekDays(data.weekStart)) {
                Text(
                    text = day.getDisplayName(JavaTextStyle.NARROW, locale),
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
                        dots = data.dayDots[date].orEmpty(),
                        compact = compact,
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
    dots: List<Int>,
    compact: Boolean,
) {
    val dayColor = when {
        !inMonth -> GlanceTheme.colors.outline
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
        // The only emphasis in the grid: a single filled circle on today.
        Box(
            modifier = if (isToday) {
                GlanceModifier
                    .size(if (compact) 18.dp else 22.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(11.dp)
            } else {
                GlanceModifier.size(if (compact) 18.dp else 22.dp)
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = TextStyle(
                    color = dayColor,
                    fontSize = if (compact) 10.sp else 12.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        Row {
            for (colorInt in dots) {
                Box(
                    modifier = GlanceModifier
                        .padding(horizontal = 1.dp)
                        .size(4.dp)
                        .background(
                            androidx.glance.unit.ColorProvider(
                                com.souru.koyomi.util.providerColor(colorInt)
                                    ?: Color(0xFF8A8A8A),
                            ),
                        )
                        .cornerRadius(2.dp),
                ) {}
            }
            if (dots.isEmpty()) {
                Spacer(modifier = GlanceModifier.size(4.dp))
            }
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

fun formatWidgetTime(context: Context, event: EventInstance): String =
    if (event.allDay) {
        context.getString(R.string.all_day)
    } else {
        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault())
            .format(Instant.ofEpochMilli(event.begin).atZone(ZoneId.systemDefault()))
    }

@Composable
fun WidgetEventRow(
    context: Context,
    date: LocalDate,
    event: EventInstance,
    dimmed: Boolean = false,
    timePrefix: String? = null,
) {
    val titleColor = if (dimmed) GlanceTheme.colors.onSurfaceVariant else GlanceTheme.colors.onSurface
    Row(
        modifier = GlanceModifier
            .fillMaxWidth()
            .padding(vertical = 2.dp)
            .clickable(actionStartActivity(openDayIntent(context, date))),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = GlanceModifier
                .size(width = 3.dp, height = 24.dp)
                .background(
                    androidx.glance.unit.ColorProvider(
                        (
                            com.souru.koyomi.util.providerColor(event.color)
                                ?: Color(0xFF8A8A8A)
                            ).copy(alpha = if (dimmed) 0.45f else 1f),
                    ),
                )
                .cornerRadius(2.dp),
        ) {}
        Text(
            text = listOfNotNull(timePrefix, formatWidgetTime(context, event))
                .joinToString(" "),
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 11.sp,
            ),
            modifier = GlanceModifier.padding(start = 8.dp).width(58.dp),
            maxLines = 1,
        )
        Text(
            text = event.title.ifBlank { context.getString(R.string.untitled) },
            style = TextStyle(
                color = titleColor,
                fontSize = 13.sp,
            ),
            maxLines = 1,
            modifier = GlanceModifier.padding(start = 4.dp),
        )
    }
}

@Composable
fun WidgetCenteredMessage(text: String, modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier.fillMaxWidth().padding(vertical = 8.dp),
        contentAlignment = Alignment.Center,
    ) {
        Text(
            text = text,
            style = TextStyle(
                color = GlanceTheme.colors.onSurfaceVariant,
                fontSize = 12.sp,
                textAlign = TextAlign.Center,
            ),
        )
    }
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
