package com.souru.koyomi.widget

import android.content.Context
import android.os.Build
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
import androidx.glance.layout.Column
import androidx.glance.layout.fillMaxSize
import androidx.glance.layout.padding
import androidx.glance.text.FontWeight
import androidx.glance.text.Text
import androidx.glance.text.TextStyle
import java.time.format.DateTimeFormatter
import java.util.Locale
import com.souru.koyomi.R

class TodayWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodayWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/** 4x2 widget: today's and tomorrow's events with times. */
class TodayWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val sections = loadDaySections(context, days = 2)
        val opacity = widgetOpacity(context)

        provideContent {
            GlanceTheme(
                colors = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                    GlanceTheme.colors
                } else {
                    KoyomiWidgetColors
                },
            ) {
                Content(context, sections, opacity)
            }
        }
    }

    @Composable
    private fun Content(
        context: Context,
        sections: List<WidgetDaySection>,
        opacity: Int,
    ) {
        val dateFormatter = DateTimeFormatter.ofPattern(
            context.getString(R.string.sheet_date_pattern),
            Locale.getDefault(),
        )
        Column(
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(opacity))
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
                        item { WidgetNoEventsText(context) }
                    } else {
                        items(section.events) { event ->
                            WidgetEventRow(context, section.date, event)
                        }
                    }
                }
            }
        }
    }
}
