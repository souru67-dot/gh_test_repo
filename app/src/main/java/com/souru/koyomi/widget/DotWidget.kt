package com.souru.koyomi.widget

import android.content.Context
import android.widget.RemoteViews
import androidx.compose.runtime.Composable
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.glance.GlanceId
import androidx.glance.GlanceModifier
import androidx.glance.GlanceTheme
import androidx.glance.action.clickable
import androidx.glance.appwidget.AndroidRemoteViews
import androidx.glance.appwidget.GlanceAppWidget
import androidx.glance.appwidget.GlanceAppWidgetReceiver
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.appwidget.lazy.LazyColumn
import androidx.glance.appwidget.lazy.items
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.Spacer
import androidx.glance.layout.fillMaxHeight
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.fillMaxWidth
import androidx.glance.layout.height
import androidx.glance.layout.padding
import androidx.glance.layout.width
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.ThemeMode
import com.souru.koyomi.data.holiday.Holidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.data.weather.DailyWeather
import com.souru.koyomi.data.weather.WeatherKind
import com.souru.koyomi.data.task.Task
import com.souru.koyomi.util.orderedWeekDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

class DotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DotWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/**
 * LED colors for the 「ドット」 widget. Each has a variant for light
 * backgrounds, where the bright LED tones would wash out.
 */
enum class DotColor(val dark: Int, val light: Int, val discDark: Int, val discLight: Int) {
    ORANGE(0xFFF2711C.toInt(), 0xFFF2711C.toInt(), R.drawable.dot_today_bg_orange, R.drawable.dot_today_bg_orange),
    GREEN(0xFF39E27A.toInt(), 0xFF1E9E52.toInt(), R.drawable.dot_today_bg_green, R.drawable.dot_today_bg_green_light),
    WHITE(0xFFF2F2F2.toInt(), 0xFF111111.toInt(), R.drawable.dot_today_bg_white, R.drawable.dot_today_bg_white_light),
    RED(0xFFFF4D3D.toInt(), 0xFFE03A2E.toInt(), R.drawable.dot_today_bg_red, R.drawable.dot_today_bg_red_light),
    BLUE(0xFF4DA3FF.toInt(), 0xFF2F6FD6.toInt(), R.drawable.dot_today_bg_blue, R.drawable.dot_today_bg_blue_light),
    ;

    companion object {
        fun fromName(name: String?): DotColor = entries.find { it.name == name } ?: ORANGE
    }
}

/**
 * ドット (4x2): an LED dot-matrix board — a big round-dot clock with its
 * unlit dots glowing faintly, the date with the day's weather, a live
 * countdown to the next event, today's events and to-dos, and a dot-matrix
 * month. Orange on black by default; the LED color is per widget.
 *
 * The clock is a TextClock and the countdown a Chronometer (both tick in
 * the launcher without widget redraws); the rest refreshes with the other
 * widgets (calendar changes, event start/end, midnight). Event/to-do titles
 * stay in the system font because the Doto face only covers Latin.
 */
class DotWidget : GlanceAppWidget() {

    private class Snapshot(
        val month: MonthGridData,
        val events: List<EventInstance>,
        val next: EventInstance?,
        val tasks: List<Task>,
        val weather: DailyWeather?,
        val color: DotColor,
        val look: WidgetLook,
        val premium: Boolean,
    )

    private suspend fun load(context: Context, id: GlanceId): Snapshot {
        val app = context.applicationContext as KoyomiApplication
        val today = LocalDate.now()
        val now = LocalTime.now()
        val nowMs = System.currentTimeMillis()
        val tasks = runCatching {
            app.container.taskRepository
                .loadTasksByDay(today, today.plusDays(1))[today]
                .orEmpty()
                // Open to-dos; timed ones drop off once their time has passed.
                .filter { !it.done && (it.time?.isAfter(now) ?: true) }
        }.getOrDefault(emptyList())
        val sections = loadDaySections(context, days = 2)
        // Next timed event that hasn't started, within 12 hours (rolls over
        // to tomorrow morning once today's are done).
        val next = sections.flatMap { it.events }
            .filter { !it.allDay && it.begin > nowMs && it.begin - nowMs <= 12 * 3_600_000L }
            .minByOrNull { it.begin }
        val weather = runCatching {
            app.container.weatherRepository.forecastByDay.first()[today]
        }.getOrNull()
        val appWidgetId = id.toString().filter { it.isDigit() }.toIntOrNull() ?: -1
        val color = DotColor.fromName(
            app.container.settingsRepository.widgetDotColor(appWidgetId).first(),
        )
        return Snapshot(
            month = loadMonthGridData(context),
            // The next event already has its own countdown line.
            events = sections.first().events.filter { it != next },
            next = next,
            tasks = tasks,
            weather = weather,
            color = color,
            look = resolveWidgetLook(context, id, defaultTheme = ThemeMode.DARK),
            premium = isPremiumUnlocked(context),
        )
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideFreshContent(load = { load(context, id) }) { s ->
            GlanceTheme(colors = widgetColors(s.look)) {
                if (!s.premium) {
                    WidgetPremiumLock(context, s.look)
                } else {
                    Content(context, s)
                }
            }
        }
    }

    /** Whether this widget currently draws on a dark background. */
    private fun isDark(context: Context, look: WidgetLook): Boolean = look.forcedDark
        ?: ((context.resources.configuration.uiMode and
            android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
            android.content.res.Configuration.UI_MODE_NIGHT_YES)

    @Composable
    private fun Content(context: Context, s: Snapshot) {
        val today = LocalDate.now()
        val dark = isDark(context, s.look)
        val lit = if (dark) s.color.dark else s.color.light
        Row(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(s.look))
                .cornerRadius(24.dp)
                .clickable(actionStartActivity(openDayIntent(context, today)))
                .padding(horizontal = 14.dp, vertical = 12.dp),
        ) {
            Column(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
            ) {
                AndroidRemoteViews(clockViews(context, lit))
                Spacer(modifier = GlanceModifier.height(4.dp))
                AndroidRemoteViews(dateViews(context, today, s.weather, lit))
                Spacer(modifier = GlanceModifier.height(6.dp))
                s.next?.let { next ->
                    AndroidRemoteViews(nextViews(context, next, lit, dark))
                    Spacer(modifier = GlanceModifier.height(2.dp))
                }
                Agenda(context, s, lit)
            }
            Spacer(modifier = GlanceModifier.width(10.dp))
            AndroidRemoteViews(
                remoteViews = calendarViews(context, s.month, today, s.color, lit, dark),
                modifier = GlanceModifier
                    .width(124.dp)
                    .fillMaxHeight(),
            )
        }
    }

    /** Today's remaining events, then today's open to-dos — one line each. */
    @Composable
    private fun Agenda(context: Context, s: Snapshot, lit: Int) {
        val markColor = androidx.glance.unit.ColorProvider(androidx.compose.ui.graphics.Color(lit))
        LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
            if (s.events.isEmpty() && s.tasks.isEmpty() && s.next == null) {
                item {
                    Text(
                        text = context.getString(R.string.no_events),
                        style = TextStyle(
                            color = GlanceTheme.colors.onSurfaceVariant,
                            fontSize = 10.sp,
                        ),
                    )
                }
            }
            items(s.events) { event ->
                AgendaLine(
                    mark = formatWidgetTime(context, event),
                    title = event.title.ifBlank { context.getString(R.string.untitled) },
                    markColor = markColor,
                )
            }
            items(s.tasks) { task ->
                AgendaLine(
                    mark = task.time?.format(com.souru.koyomi.util.deviceTimeFormatter(context))
                        ?.let { "□ $it" } ?: "□",
                    title = task.title,
                    markColor = markColor,
                )
            }
        }
    }

    @Composable
    private fun AgendaLine(
        mark: String,
        title: String,
        markColor: androidx.glance.unit.ColorProvider,
    ) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = mark,
                style = TextStyle(color = markColor, fontSize = 9.sp, fontWeight = FontWeight.Medium),
                maxLines = 1,
                modifier = GlanceModifier.width(44.dp),
            )
            Text(
                text = title,
                style = TextStyle(color = GlanceTheme.colors.onSurface, fontSize = 10.sp),
                maxLines = 1,
            )
        }
    }

    private fun ghostOf(lit: Int): Int = (lit and 0x00FFFFFF) or (0x2E shl 24)

    private fun clockViews(context: Context, lit: Int): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_dot_clock).apply {
            setTextColor(R.id.dot_clock_time, lit)
            setTextColor(R.id.dot_clock_ghost, ghostOf(lit))
        }

    /** "11/23 MON ☀ 18° 70%" — Latin on purpose: the dot face has no CJK. */
    private fun dateViews(
        context: Context,
        date: LocalDate,
        weather: DailyWeather?,
        lit: Int,
    ): RemoteViews = RemoteViews(context.packageName, R.layout.widget_dot_date).apply {
        val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
            .uppercase(Locale.ENGLISH)
        setTextViewText(
            R.id.dot_date,
            "%02d/%02d %s".format(Locale.ENGLISH, date.monthValue, date.dayOfMonth, weekday),
        )
        setTextColor(R.id.dot_date, lit)
        if (weather != null) {
            setImageViewResource(R.id.dot_wx, weatherIcon(weather.kind))
            setInt(R.id.dot_wx, "setColorFilter", lit)
            setViewVisibility(R.id.dot_wx, android.view.View.VISIBLE)
            // High only: the 4x2 left column has no room for both temps
            // next to the date.
            setTextViewText(R.id.dot_temp, "${weather.maxC.toInt()}°")
            setTextColor(R.id.dot_temp, lit)
            setViewVisibility(R.id.dot_temp, android.view.View.VISIBLE)
            // Rain chance only when it matters (umbrella-worthy days).
            val rain = weather.rainChance
            if (rain != null && rain >= 50) {
                setTextViewText(R.id.dot_rain, "$rain%")
                setTextColor(R.id.dot_rain, lit)
                setViewVisibility(R.id.dot_rain, android.view.View.VISIBLE)
            }
        }
    }

    private fun weatherIcon(kind: WeatherKind): Int = when (kind) {
        WeatherKind.SUN -> R.drawable.dot_wx_sun
        WeatherKind.PARTLY -> R.drawable.dot_wx_partly
        WeatherKind.CLOUD -> R.drawable.dot_wx_cloud
        WeatherKind.FOG -> R.drawable.dot_wx_fog
        WeatherKind.RAIN -> R.drawable.dot_wx_rain
        WeatherKind.SNOW -> R.drawable.dot_wx_snow
        WeatherKind.THUNDER -> R.drawable.dot_wx_thunder
    }

    /** "NEXT 41:59 歯医者" with a launcher-side countdown. */
    private fun nextViews(context: Context, event: EventInstance, lit: Int, dark: Boolean): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_dot_next).apply {
            val base = android.os.SystemClock.elapsedRealtime() +
                (event.begin - System.currentTimeMillis())
            setChronometer(R.id.dot_next_timer, base, null, true)
            setChronometerCountDown(R.id.dot_next_timer, true)
            setTextColor(R.id.dot_next_timer, lit)
            setTextColor(R.id.dot_next_label, lit)
            setTextViewText(
                R.id.dot_next_title,
                event.title.ifBlank { context.getString(R.string.untitled) },
            )
            setTextColor(R.id.dot_next_title, if (dark) 0xFFF2F2F2.toInt() else 0xFF111111.toInt())
        }

    private fun calendarViews(
        context: Context,
        data: MonthGridData,
        today: LocalDate,
        color: DotColor,
        lit: Int,
        dark: Boolean,
    ): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dot_calendar)
        val month: YearMonth = data.month
        views.setTextViewText(
            R.id.dot_cal_header,
            month.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
                .uppercase(Locale.ENGLISH) + " " + month.year,
        )
        views.setTextColor(R.id.dot_cal_header, lit)
        orderedWeekDays(data.weekStart).forEachIndexed { i, day ->
            views.setTextViewText(
                WEEKDAY_IDS[i],
                day.getDisplayName(JavaTextStyle.NARROW, Locale.ENGLISH),
            )
            views.setTextColor(WEEKDAY_IDS[i], lit)
        }
        // Sundays/holidays in red — unless the LED itself is red, then in
        // plain ink so they still stand apart.
        val holiday = if (color == DotColor.RED) {
            if (dark) 0xFFF2F2F2.toInt() else 0xFF111111.toInt()
        } else {
            context.getColor(R.color.dot_holiday)
        }
        val onDisc = if (color == DotColor.WHITE && dark) 0xFF111111.toInt() else 0xFFFFFFFF.toInt()
        val disc = if (dark) color.discDark else color.discLight
        for (week in 0 until 6) {
            val inMonth = (0 until 7).any { YearMonth.from(data.days[week * 7 + it]) == month }
            views.setViewVisibility(
                ROW_IDS[week],
                if (inMonth) android.view.View.VISIBLE else android.view.View.GONE,
            )
            for (i in 0 until 7) {
                val index = week * 7 + i
                val date = data.days[index]
                val cell = CELL_IDS[index]
                if (YearMonth.from(date) != month) {
                    views.setTextViewText(cell, "")
                    continue
                }
                views.setTextViewText(cell, date.dayOfMonth.toString())
                if (date == today) {
                    views.setInt(cell, "setBackgroundResource", disc)
                    views.setTextColor(cell, onDisc)
                } else {
                    views.setTextColor(
                        cell,
                        if (Holidays.isRedDay(date) || date.dayOfWeek == DayOfWeek.SUNDAY) holiday else lit,
                    )
                }
            }
        }
        return views
    }

    private companion object {
        val WEEKDAY_IDS = intArrayOf(R.id.dot_wd_0, R.id.dot_wd_1, R.id.dot_wd_2, R.id.dot_wd_3, R.id.dot_wd_4, R.id.dot_wd_5, R.id.dot_wd_6)
        val ROW_IDS = intArrayOf(R.id.dot_row_0, R.id.dot_row_1, R.id.dot_row_2, R.id.dot_row_3, R.id.dot_row_4, R.id.dot_row_5)
        val CELL_IDS = intArrayOf(
        R.id.dot_d_0,
        R.id.dot_d_1,
        R.id.dot_d_2,
        R.id.dot_d_3,
        R.id.dot_d_4,
        R.id.dot_d_5,
        R.id.dot_d_6,
        R.id.dot_d_7,
        R.id.dot_d_8,
        R.id.dot_d_9,
        R.id.dot_d_10,
        R.id.dot_d_11,
        R.id.dot_d_12,
        R.id.dot_d_13,
        R.id.dot_d_14,
        R.id.dot_d_15,
        R.id.dot_d_16,
        R.id.dot_d_17,
        R.id.dot_d_18,
        R.id.dot_d_19,
        R.id.dot_d_20,
        R.id.dot_d_21,
        R.id.dot_d_22,
        R.id.dot_d_23,
        R.id.dot_d_24,
        R.id.dot_d_25,
        R.id.dot_d_26,
        R.id.dot_d_27,
        R.id.dot_d_28,
        R.id.dot_d_29,
        R.id.dot_d_30,
        R.id.dot_d_31,
        R.id.dot_d_32,
        R.id.dot_d_33,
        R.id.dot_d_34,
        R.id.dot_d_35,
        R.id.dot_d_36,
        R.id.dot_d_37,
        R.id.dot_d_38,
        R.id.dot_d_39,
        R.id.dot_d_40,
        R.id.dot_d_41,
        )
    }
}
