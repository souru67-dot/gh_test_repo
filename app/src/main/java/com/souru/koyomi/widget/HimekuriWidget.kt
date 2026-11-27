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
import androidx.glance.appwidget.action.actionStartActivity
import androidx.glance.appwidget.cornerRadius
import androidx.glance.background
import androidx.glance.layout.Alignment
import androidx.glance.layout.Column
import androidx.glance.layout.Row
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import com.souru.koyomi.data.holiday.Holidays
import com.souru.koyomi.data.rokuyo.Kyureki
import java.time.LocalDate
import java.time.format.TextStyle as JavaTextStyle
import java.util.Locale

class HimekuriWidgetReceiver : KoyomiWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = HimekuriWidget()
}

/**
 * 2x2 日めくり: the app icon come to life — today's number large, with the
 * month/weekday and one almanac line (祝日 > 六曜).
 */
class HimekuriWidget : GlanceAppWidget() {

    private data class HimekuriData(
        val holiday: String?,
        val rokuyo: String?,
        val lucky: String?,
    )

    private class Snapshot(
        val today: LocalDate,
        val data: HimekuriData,
        val look: WidgetLook,
        val premium: Boolean,
    )

    private suspend fun load(context: Context, id: GlanceId): Snapshot {
        val today = LocalDate.now()
        // 六曜・開運日 are Japanese-almanac concepts; only surface them in
        // Japanese so non-JP users don't see untranslatable text (大安 etc.).
        val japanese = Locale.getDefault().language == "ja"
        val data = HimekuriData(
            holiday = Holidays.nameFor(today),
            rokuyo = if (japanese) Kyureki.rokuyoFor(today) else null,
            lucky = if (japanese) Kyureki.luckyDaysFor(today).firstOrNull() else null,
        )
        return Snapshot(today, data, resolveWidgetLook(context, id), isPremiumUnlocked(context))
    }

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        provideFreshContent(load = { load(context, id) }) { s ->
            GlanceTheme(colors = widgetColors(s.look)) {
                if (!s.premium) {
                    WidgetPremiumLock(context, s.look)
                } else {
                    Content(context, s.today, s.data, s.look)
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
        }
    }
}
