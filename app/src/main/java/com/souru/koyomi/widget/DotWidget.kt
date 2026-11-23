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
import com.souru.koyomi.data.task.Task
import com.souru.koyomi.util.orderedWeekDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.LocalTime
import java.time.YearMonth
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

class DotWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = DotWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/**
 * ドット (4x2): an LED dot-matrix board — a big round-dot clock with its
 * unlit dots glowing faintly, the date, today's events and to-dos on the
 * left, and a dot-matrix month on the right. Orange on black by default.
 *
 * The clock is a TextClock (ticks without widget redraws); the date and the
 * month are RemoteViews TextViews in the bundled Doto face, refreshed at
 * midnight with the rest of the widgets. Event/to-do titles stay in the
 * system font because Doto only covers Latin.
 */
class DotWidget : GlanceAppWidget() {

    private class Snapshot(
        val month: MonthGridData,
        val events: List<EventInstance>,
        val tasks: List<Task>,
        val look: WidgetLook,
        val premium: Boolean,
    )

    private suspend fun load(context: Context, id: GlanceId): Snapshot {
        val app = context.applicationContext as KoyomiApplication
        val today = LocalDate.now()
        val now = LocalTime.now()
        val tasks = runCatching {
            app.container.taskRepository
                .loadTasksByDay(today, today.plusDays(1))[today]
                .orEmpty()
                // Open to-dos; timed ones drop off once their time has passed.
                .filter { !it.done && (it.time?.isAfter(now) ?: true) }
        }.getOrDefault(emptyList())
        return Snapshot(
            month = loadMonthGridData(context),
            events = loadDaySections(context, days = 1).first().events,
            tasks = tasks,
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

    @Composable
    private fun Content(context: Context, s: Snapshot) {
        val today = LocalDate.now()
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
                AndroidRemoteViews(RemoteViews(context.packageName, R.layout.widget_dot_clock))
                Spacer(modifier = GlanceModifier.height(4.dp))
                AndroidRemoteViews(dateViews(context, today))
                Spacer(modifier = GlanceModifier.height(6.dp))
                Agenda(context, s)
            }
            Spacer(modifier = GlanceModifier.width(10.dp))
            AndroidRemoteViews(
                remoteViews = calendarViews(context, s.month, today),
                modifier = GlanceModifier
                    .width(124.dp)
                    .fillMaxHeight(),
            )
        }
    }

    /** Today's events, then today's open to-dos — one compact line each. */
    @Composable
    private fun Agenda(context: Context, s: Snapshot) {
        LazyColumn(modifier = GlanceModifier.fillMaxWidth()) {
            if (s.events.isEmpty() && s.tasks.isEmpty()) {
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
                )
            }
            items(s.tasks) { task ->
                AgendaLine(
                    mark = task.time?.format(com.souru.koyomi.util.deviceTimeFormatter(context))
                        ?.let { "□ $it" } ?: "□",
                    title = task.title,
                )
            }
        }
    }

    @Composable
    private fun AgendaLine(mark: String, title: String) {
        Row(
            modifier = GlanceModifier.fillMaxWidth().padding(vertical = 1.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = mark,
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 9.sp,
                    fontWeight = FontWeight.Medium,
                ),
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

    /** "11/23 MON" — Latin on purpose: the dot face has no CJK glyphs. */
    private fun dateViews(context: Context, date: LocalDate): RemoteViews =
        RemoteViews(context.packageName, R.layout.widget_dot_date).apply {
            val weekday = date.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
                .uppercase(Locale.ENGLISH)
            setTextViewText(
                R.id.dot_date,
                "%02d/%02d %s".format(Locale.ENGLISH, date.monthValue, date.dayOfMonth, weekday),
            )
        }

    private fun calendarViews(context: Context, data: MonthGridData, today: LocalDate): RemoteViews {
        val views = RemoteViews(context.packageName, R.layout.widget_dot_calendar)
        val month: YearMonth = data.month
        views.setTextViewText(
            R.id.dot_cal_header,
            month.month.getDisplayName(JavaTextStyle.SHORT, Locale.ENGLISH)
                .uppercase(Locale.ENGLISH) + " " + month.year,
        )
        orderedWeekDays(data.weekStart).forEachIndexed { i, day ->
            views.setTextViewText(
                WEEKDAY_IDS[i],
                day.getDisplayName(JavaTextStyle.NARROW, Locale.ENGLISH),
            )
        }
        val lit = context.getColor(R.color.dot_lit)
        val holiday = context.getColor(R.color.dot_holiday)
        val onLit = context.getColor(R.color.dot_on_lit)
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
                    views.setInt(cell, "setBackgroundResource", R.drawable.dot_today_bg)
                    views.setTextColor(cell, onLit)
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
