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
import com.souru.koyomi.data.holiday.Holidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.data.weather.DailyWeather
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale
import kotlinx.coroutines.flow.first

class HitomeWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HitomeWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/**
 * ひとめ (4x3): the whole day at a glance — a big date with today's
 * weather on top, then the month on the left and today's agenda on the
 * right. Typography-led and monochrome so it sits well on any wallpaper,
 * including fully see-through.
 */
class HitomeWidget : GlanceAppWidget() {

    private data class WeatherNow(val weather: DailyWeather, val place: String?)

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val app = context.applicationContext as KoyomiApplication
        val month = loadMonthGridData(context)
        val today = loadDaySections(context, days = 1).first()
        val look = resolveWidgetLook(context, id)
        val premium = isPremiumUnlocked(context)
        val weather = runCatching {
            val repo = app.container.weatherRepository
            repo.forecastByDay.first()[today.date]?.let { forecast ->
                WeatherNow(forecast, repo.place.first()?.name)
            }
        }.getOrNull()

        provideContent {
            GlanceTheme(colors = widgetColors(look)) {
                if (!premium) {
                    WidgetPremiumLock(context, look)
                } else {
                    Content(context, today, month, weather, look)
                }
            }
        }
    }

    @Composable
    private fun Content(
        context: Context,
        today: WidgetDaySection,
        month: MonthGridData,
        weather: WeatherNow?,
        look: WidgetLook,
    ) {
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(look))
                .cornerRadius(28.dp)
                .clickable(actionStartActivity(openDayIntent(context, today.date)))
                .padding(horizontal = 16.dp, vertical = 14.dp),
        ) {
            Header(context, today.date, weather)
            Spacer(modifier = GlanceModifier.height(10.dp))
            WidgetHairlineDivider()
            Spacer(modifier = GlanceModifier.height(10.dp))
            Row(
                modifier = GlanceModifier
                    .fillMaxWidth()
                    .defaultWeight(),
            ) {
                GlanceMiniMonth(
                    context = context,
                    data = month,
                    showHeader = false,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                )
                Spacer(modifier = GlanceModifier.width(12.dp))
                Box(
                    modifier = GlanceModifier
                        .width(1.dp)
                        .fillMaxHeight()
                        .background(GlanceTheme.colors.surfaceVariant),
                ) {}
                Spacer(modifier = GlanceModifier.width(12.dp))
                Agenda(
                    context = context,
                    today = today,
                    modifier = GlanceModifier
                        .defaultWeight()
                        .fillMaxHeight(),
                )
            }
        }
    }

    /** "23" + weekday / month on the left, weather on the right. */
    @Composable
    private fun Header(context: Context, date: LocalDate, weather: WeatherNow?) {
        val locale = Locale.getDefault()
        val monthLabel = if (locale.language == "ja") {
            "${date.year}年${date.monthValue}月"
        } else {
            date.format(DateTimeFormatter.ofPattern("MMMM yyyy", locale))
        }
        val holiday = Holidays.nameFor(date)
        Row(
            modifier = GlanceModifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = TextStyle(
                    color = if (Holidays.isRedDay(date)) {
                        WidgetSundayColor
                    } else {
                        GlanceTheme.colors.onSurface
                    },
                    fontSize = 40.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            Column(modifier = GlanceModifier.padding(start = 10.dp)) {
                Text(
                    text = date.dayOfWeek.getDisplayName(JavaTextStyle.FULL, locale),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = holiday ?: monthLabel,
                    style = TextStyle(
                        color = if (holiday != null) {
                            WidgetSundayColor
                        } else {
                            GlanceTheme.colors.onSurfaceVariant
                        },
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                )
            }
            Spacer(modifier = GlanceModifier.defaultWeight())
            if (weather != null) {
                WeatherBlock(weather)
            }
        }
    }

    @Composable
    private fun WeatherBlock(now: WeatherNow) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = now.weather.emoji,
                style = TextStyle(fontSize = 24.sp),
            )
            Column(
                modifier = GlanceModifier.padding(start = 6.dp),
                horizontalAlignment = Alignment.End,
            ) {
                Text(
                    text = "${now.weather.maxC.toInt()}°",
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 18.sp,
                        fontWeight = FontWeight.Medium,
                        textAlign = TextAlign.End,
                    ),
                )
                Text(
                    text = listOfNotNull("${now.weather.minC.toInt()}°", now.place)
                        .joinToString("  "),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 9.sp,
                        textAlign = TextAlign.End,
                    ),
                    maxLines = 1,
                )
            }
        }
    }

    @Composable
    private fun Agenda(context: Context, today: WidgetDaySection, modifier: GlanceModifier) {
        Column(modifier = modifier) {
            WidgetSectionLabel(
                text = context.getString(R.string.back_to_today),
                modifier = GlanceModifier.padding(bottom = 4.dp),
            )
            LazyColumn(modifier = GlanceModifier.defaultWeight()) {
                if (today.events.isEmpty()) {
                    item {
                        Text(
                            text = context.getString(R.string.no_events),
                            style = TextStyle(
                                color = GlanceTheme.colors.onSurfaceVariant,
                                fontSize = 11.sp,
                            ),
                            modifier = GlanceModifier.padding(top = 4.dp),
                        )
                    }
                } else {
                    items(today.events) { event ->
                        AgendaRow(context, today.date, event)
                    }
                }
            }
        }
    }

    /** Time on top in small caps-ish gray, title below; a calendar-color notch. */
    @Composable
    private fun AgendaRow(context: Context, date: LocalDate, event: EventInstance) {
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 3.dp)
                .clickable(actionStartActivity(openDayIntent(context, date))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(width = 3.dp, height = 26.dp)
                    .background(eventColorProvider(event.color))
                    .cornerRadius(2.dp),
            ) {}
            Column(modifier = GlanceModifier.padding(start = 8.dp)) {
                Text(
                    text = formatWidgetTime(context, event),
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 9.sp,
                    ),
                    maxLines = 1,
                )
                Text(
                    text = event.title.ifBlank { context.getString(R.string.untitled) },
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurface,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
