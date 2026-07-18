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
import androidx.glance.text.TextStyle
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.holiday.Holidays
import com.souru.koyomi.data.rokuyo.Kyureki
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

class HimekuriWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HimekuriWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/**
 * 2x2 日めくり: the app icon come to life — today's number large, with the
 * month/weekday, one almanac line (祝日 > 六曜) and the nearest countdown.
 */
class HimekuriWidget : GlanceAppWidget() {

    private data class HimekuriData(
        val holiday: String?,
        val rokuyo: String?,
        val lucky: String?,
        /** "◯◯まであと3日" for the nearest anniversary within 99 days. */
        val countdown: String?,
    )

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val today = LocalDate.now()
        val app = context.applicationContext as KoyomiApplication
        val nearest = runCatching {
            app.container.anniversaryRepository.loadAll()
                .map { it to it.daysUntil(today) }
                .filter { (_, days) -> days in 0..99 }
                .minByOrNull { (_, days) -> days }
        }.getOrNull()
        val data = HimekuriData(
            holiday = Holidays.nameFor(today),
            rokuyo = Kyureki.rokuyoFor(today),
            lucky = Kyureki.luckyDaysFor(today).firstOrNull(),
            countdown = nearest?.let { (anniversary, days) ->
                if (days == 0L) {
                    context.getString(R.string.himekuri_countdown_today, anniversary.title)
                } else {
                    context.getString(R.string.himekuri_countdown, anniversary.title, days)
                }
            },
        )
        val look = resolveWidgetLook(context, id)
        val premium = isPremiumUnlocked(context)

        provideContent {
            GlanceTheme(colors = widgetColors(look)) {
                if (!premium) {
                    WidgetPremiumLock(context, look)
                } else {
                    Content(context, today, data, look)
                }
            }
        }
    }

    @Composable
    private fun Content(
        context: Context,
        today: LocalDate,
        data: HimekuriData,
        look: WidgetLook,
    ) {
        val locale = Locale.getDefault()
        val monthLabel = if (locale.language == "ja") {
            "${today.monthValue}月"
        } else {
            today.month.getDisplayName(JavaTextStyle.SHORT, locale)
        }
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(look))
                .cornerRadius(28.dp)
                .clickable(actionStartActivity(openDayIntent(context, today)))
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = monthLabel,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                )
                Text(
                    text = today.dayOfWeek.getDisplayName(JavaTextStyle.SHORT, locale),
                    style = TextStyle(
                        color = GlanceTheme.colors.tertiary,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                    ),
                    modifier = GlanceModifier.padding(start = 6.dp),
                )
            }
            Text(
                text = today.dayOfMonth.toString(),
                style = TextStyle(
                    color = GlanceTheme.colors.primary,
                    fontSize = 56.sp,
                    fontWeight = FontWeight.Bold,
                ),
            )
            // One almanac line: 祝日 wins, then 開運日, then 六曜.
            val line = data.holiday ?: data.lucky ?: data.rokuyo
            if (line != null) {
                Text(
                    text = line,
                    style = TextStyle(
                        color = if (data.holiday != null || data.lucky != null) {
                            GlanceTheme.colors.tertiary
                        } else {
                            GlanceTheme.colors.onSurfaceVariant
                        },
                        fontSize = 12.sp,
                    ),
                    maxLines = 1,
                )
            }
            if (data.countdown != null) {
                Spacer(modifier = GlanceModifier.height(2.dp))
                Text(
                    text = data.countdown,
                    style = TextStyle(
                        color = GlanceTheme.colors.onSurfaceVariant,
                        fontSize = 11.sp,
                    ),
                    maxLines = 1,
                )
            }
        }
    }
}
