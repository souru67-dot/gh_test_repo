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
import com.souru.koyomi.data.ThemePack
import com.souru.koyomi.data.holiday.JapaneseHolidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.ui.theme.koyomiColorScheme
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


// ---------- Per-widget look (theme + opacity) ----------

data class WidgetLook(
    val theme: ThemeMode,
    val opacityPercent: Int,
    val pack: ThemePack = ThemePack.SUMI,
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
    val pack = settings.themePack.first()
    return WidgetLook(theme = theme, opacityPercent = opacity, pack = pack)
}

/** Color scheme for the chosen widget theme (SYSTEM = dynamic on Android 12+). */
@Composable
fun widgetColors(look: WidgetLook): androidx.glance.color.ColorProviders = when (look.theme) {
    ThemeMode.SYSTEM ->
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            GlanceTheme.colors
        } else {
            ColorProviders(
                light = koyomiColorScheme(look.pack, darkTheme = false),
                dark = koyomiColorScheme(look.pack, darkTheme = true),
            )
        }
    ThemeMode.LIGHT -> {
        val scheme = koyomiColorScheme(look.pack, darkTheme = false)
        ColorProviders(light = scheme, dark = scheme)
    }
    ThemeMode.DARK -> {
        val scheme = koyomiColorScheme(look.pack, darkTheme = true)
        ColorProviders(light = scheme, dark = scheme)
    }
}

/** Widget background honoring the opacity setting (0 = fully transparent). */
@Composable
fun widgetBackground(look: WidgetLook): androidx.glance.unit.ColorProvider {
    if (look.opacityPercent >= 100 && look.theme == ThemeMode.SYSTEM) {
        return GlanceTheme.colors.surface
    }
    val alpha = look.opacityPercent / 100f
    val lightSurface = koyomiColorScheme(look.pack, darkTheme = false).surface
    val darkSurface = koyomiColorScheme(look.pack, darkTheme = true).surface
    return when (look.theme) {
        ThemeMode.SYSTEM -> ColorProvider(
            lightSurface.copy(alpha = alpha),
            darkSurface.copy(alpha = alpha),
        )
        ThemeMode.LIGHT -> androidx.glance.unit.ColorProvider(
            lightSurface.copy(alpha = alpha),
        )
        ThemeMode.DARK -> androidx.glance.unit.ColorProvider(
            darkSurface.copy(alpha = alpha),
        )
    }
}

/** 1dp hairline — the only divider used inside widgets. */
@Composable
fun WidgetHairlineDivider(modifier: GlanceModifier = GlanceModifier) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(GlanceTheme.colors.surfaceVariant),
    ) {}
}

/** Section heading in the brand accent (朱) — 今日/明日/dates in list widgets. */
@Composable
fun WidgetSectionLabel(text: String, modifier: GlanceModifier = GlanceModifier) {
    Text(
        text = text,
        style = TextStyle(
            color = GlanceTheme.colors.tertiary,
            fontSize = 11.sp,
            fontWeight = FontWeight.Medium,
        ),
        modifier = modifier,
    )
}

/** Calendar color tuned for readability on light/dark widget backgrounds. */
fun eventColorProvider(
    colorInt: Int,
    alpha: Float = 1f,
): androidx.glance.unit.ColorProvider {
    val base = com.souru.koyomi.util.providerColor(colorInt)
        ?: return androidx.glance.unit.ColorProvider(Color(0xFF8A8A8A).copy(alpha = alpha))
    return ColorProvider(
        com.souru.koyomi.util.mutedColor(base, darkTheme = false).copy(alpha = alpha),
        com.souru.koyomi.util.mutedColor(base, darkTheme = true).copy(alpha = alpha),
    )
}

// ---------- Month grid ----------

data class MonthGridData(
    val month: YearMonth,
    val weekStart: DayOfWeek,
    val days: List<LocalDate>,
    /** Up to 3 (title, color) entries per day, in start order. */
    val dayEvents: Map<LocalDate, List<Pair<String, Int>>>,
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
        dayEvents = events.mapValues { (_, list) ->
            list.take(3).map { it.title to it.color }
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
            // 朱 dot after the month — the launcher icon's brand mark, echoed.
            Box(
                modifier = GlanceModifier
                    .padding(start = 5.dp, top = if (compact) 6.dp else 10.dp),
            ) {
                Box(
                    modifier = GlanceModifier
                        .size(5.dp)
                        .background(GlanceTheme.colors.tertiary)
                        .cornerRadius(3.dp),
                ) {}
            }
            Text(
                text = data.month.year.toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 11.sp,
                ),
                modifier = GlanceModifier.padding(start = 7.dp, top = 4.dp),
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
        WidgetHairlineDivider(
            modifier = GlanceModifier.padding(top = 2.dp, bottom = 2.dp),
        )
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
                        events = data.dayEvents[date].orEmpty(),
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
    events: List<Pair<String, Int>>,
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
        // Event titles, tinted with their calendar color — a miniature of
        // the app's month view instead of anonymous dots.
        for ((title, colorInt) in events.take(if (compact) 1 else 2)) {
            Text(
                text = title.ifBlank { "·" },
                style = TextStyle(
                    color = eventColorProvider(colorInt),
                    fontSize = 8.sp,
                ),
                maxLines = 1,
                modifier = GlanceModifier.padding(horizontal = 1.dp),
            )
        }
        val hidden = events.size - events.take(if (compact) 1 else 2).size
        if (hidden > 0) {
            Text(
                text = "+$hidden",
                style = TextStyle(
                    color = GlanceTheme.colors.onSurfaceVariant,
                    fontSize = 7.sp,
                ),
                maxLines = 1,
            )
        }
    }
}

/**
 * Dots-only compact month for small composite widgets: weekday letters,
 * day numbers and a single calendar-color dot under days with events.
 */
@Composable
fun GlanceMiniMonth(
    context: Context,
    data: MonthGridData,
    modifier: GlanceModifier = GlanceModifier,
) {
    val today = LocalDate.now()
    val locale = Locale.getDefault()
    Column(modifier = modifier) {
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
                        fontSize = 8.sp,
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
                    MiniDayCell(
                        context = context,
                        date = date,
                        inMonth = YearMonth.from(date) == data.month,
                        isToday = date == today,
                        dotColor = data.dayEvents[date]?.firstOrNull()?.second,
                    )
                }
            }
        }
    }
}

@Composable
private fun androidx.glance.layout.RowScope.MiniDayCell(
    context: Context,
    date: LocalDate,
    inMonth: Boolean,
    isToday: Boolean,
    dotColor: Int?,
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
        Box(
            modifier = if (isToday) {
                GlanceModifier
                    .size(14.dp)
                    .background(GlanceTheme.colors.primary)
                    .cornerRadius(7.dp)
            } else {
                GlanceModifier.size(14.dp)
            },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = TextStyle(
                    color = dayColor,
                    fontSize = 8.sp,
                    fontWeight = if (isToday) FontWeight.Bold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                ),
            )
        }
        if (dotColor != null && inMonth) {
            Box(
                modifier = GlanceModifier
                    .size(3.dp)
                    .background(eventColorProvider(dotColor))
                    .cornerRadius(2.dp),
            ) {}
        } else {
            Spacer(modifier = GlanceModifier.size(3.dp))
        }
    }
}

// ---------- Event rows ----------

data class WidgetDaySection(val date: LocalDate, val events: List<EventInstance>)

/**
 * The next [maxDays] days that actually HAVE events, searched within
 * [lookaheadDays] from today. Today is included even when empty so the
 * clock widget can lead with "予定はありません".
 */
suspend fun loadUpcomingDaySections(
    context: Context,
    maxDays: Int = 3,
    lookaheadDays: Long = 14,
): List<WidgetDaySection> {
    val app = context.applicationContext as KoyomiApplication
    val today = LocalDate.now()
    val hidden = app.container.settingsRepository.hiddenCalendarIds.first()
    val eventsByDay = app.container.calendarRepository.loadEventsByDay(
        today,
        today.plusDays(lookaheadDays),
        hidden,
    )
    val sections = mutableListOf<WidgetDaySection>()
    var date = today
    while (date.isBefore(today.plusDays(lookaheadDays)) && sections.size < maxDays) {
        val events = eventsByDay[date].orEmpty()
        if (events.isNotEmpty() || date == today) {
            sections += WidgetDaySection(date, events)
        }
        date = date.plusDays(1)
    }
    return sections
}

/** 今日 / 明日 / "7月14日(火)" — for section headers in list widgets. */
fun relativeDayLabel(context: Context, date: LocalDate): String {
    val today = LocalDate.now()
    return when (date) {
        today -> context.getString(R.string.back_to_today)
        today.plusDays(1) -> context.getString(R.string.tomorrow_label)
        else -> date.format(
            DateTimeFormatter.ofPattern(
                context.getString(R.string.sheet_date_pattern),
                Locale.getDefault(),
            ),
        )
    }
}

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
                .size(width = 3.dp, height = 26.dp)
                .background(eventColorProvider(event.color, alpha = if (dimmed) 0.45f else 1f))
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
                fontWeight = FontWeight.Medium,
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
