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
import androidx.glance.text.TextStyle
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.R
import com.souru.koyomi.data.task.Task
import java.time.LocalDate

class TodoAgendaWidgetReceiver : GlanceAppWidgetReceiver() {
    override val glanceAppWidget: GlanceAppWidget = TodoAgendaWidget()

    override fun onEnabled(context: Context) {
        super.onEnabled(context)
        WidgetUpdateWorker.schedule(context)
    }
}

/**
 * 4x2: one scrollable list combining the to-do list and the coming days'
 * events (今日 first, then the next days that have events), with a
 * dots-only mini month on the right.
 */
class TodoAgendaWidget : GlanceAppWidget() {

    override suspend fun provideGlance(context: Context, id: GlanceId) {
        val tasks = loadOpenTasks(context)
        val sections = loadUpcomingDaySections(context, maxDays = 7, lookaheadDays = 14)
        val data = loadMonthGridData(context)
        val look = resolveWidgetLook(context, id)

        val premium = isPremiumUnlocked(context)
        provideContent {
            GlanceTheme(colors = widgetColors(look)) {
                if (!premium) {
                    WidgetPremiumLock(context, look)
                } else {
                    Content(context, tasks, sections, data, look)
                }
            }
        }
    }

    /**
     * Open to-dos due within two weeks; overdue ones come first. Today's
     * timed to-dos vanish once their time has passed (date-only ones stay).
     */
    private suspend fun loadOpenTasks(context: Context, limit: Int = 10): List<Task> {
        val app = context.applicationContext as KoyomiApplication
        val today = LocalDate.now()
        val horizon = today.plusDays(14)
        val nowMinutes = java.time.LocalTime.now().let { it.hour * 60 + it.minute }
        return app.container.taskRepository.loadAllTasks()
            .filter { !it.done && !it.dueDate.isAfter(horizon) }
            .filterNot { task ->
                task.dueDate == today &&
                    task.timeMinutes != null &&
                    task.timeMinutes < nowMinutes
            }
            .take(limit)
    }

    @Composable
    private fun Content(
        context: Context,
        tasks: List<Task>,
        sections: List<WidgetDaySection>,
        data: MonthGridData,
        look: WidgetLook,
    ) {
        val today = LocalDate.now()
        Row(
            // NOTE: no whole-widget clickable here — a click target wrapping
            // the LazyColumn swallows the list's touch events on several
            // launchers, which made the list unscrollable (events appeared
            // cut off). Rows and the mini month carry their own taps.
            modifier = GlanceModifier
                .fillMaxSize()
                .background(widgetBackground(look))
                .cornerRadius(28.dp)
                .padding(14.dp),
        ) {
            LazyColumn(
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
            ) {
                if (tasks.isNotEmpty()) {
                    item {
                        WidgetSectionLabel(
                            context.getString(R.string.tasks_list),
                            modifier = GlanceModifier.clickable(
                                actionStartActivity(openDayIntent(context, today)),
                            ),
                        )
                    }
                    for (task in tasks) {
                        item { TaskRow(context, task) }
                    }
                    item { Spacer(modifier = GlanceModifier.height(6.dp)) }
                }
                for (section in sections) {
                    if (section.events.isEmpty() && section.date != today) continue
                    item {
                        WidgetSectionLabel(
                            relativeDayLabel(context, section.date),
                            modifier = GlanceModifier
                                .padding(top = 2.dp)
                                .clickable(
                                    actionStartActivity(openDayIntent(context, section.date)),
                                ),
                        )
                    }
                    if (section.events.isEmpty()) {
                        item {
                            Text(
                                text = context.getString(R.string.no_events),
                                style = TextStyle(
                                    color = GlanceTheme.colors.onSurfaceVariant,
                                    fontSize = 11.sp,
                                ),
                                modifier = GlanceModifier
                                    .padding(vertical = 2.dp)
                                    .clickable(
                                        actionStartActivity(
                                            openDayIntent(context, section.date),
                                        ),
                                    ),
                            )
                        }
                    } else {
                        for (event in section.events) {
                            item {
                                WidgetEventRow(
                                    context = context,
                                    date = section.date,
                                    event = event,
                                    dimmed = section.date != today,
                                )
                            }
                        }
                    }
                }
            }
            Spacer(modifier = GlanceModifier.width(12.dp))
            Box(
                modifier = GlanceModifier
                    .width(1.dp)
                    .fillMaxHeight()
                    .background(GlanceTheme.colors.surfaceVariant),
            ) {}
            Spacer(modifier = GlanceModifier.width(12.dp))
            GlanceMiniMonth(
                context = context,
                data = data,
                modifier = GlanceModifier
                    .defaultWeight()
                    .fillMaxHeight(),
            )
        }
    }

    /** A to-do: small ring + title (+ due date when not today). */
    @Composable
    private fun TaskRow(context: Context, task: Task) {
        val today = LocalDate.now()
        val overdue = task.dueDate.isBefore(today)
        Row(
            modifier = GlanceModifier
                .fillMaxWidth()
                .padding(vertical = 2.dp)
                .clickable(actionStartActivity(openDayIntent(context, task.dueDate))),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Box(
                modifier = GlanceModifier
                    .size(8.dp)
                    .background(
                        task.color?.let { eventColorProvider(it) }
                            ?: androidx.glance.unit.ColorProvider(
                                androidx.compose.ui.graphics.Color(0xFFA8503C),
                            ),
                    )
                    .cornerRadius(4.dp),
            ) {}
            Text(
                text = task.title,
                style = TextStyle(
                    color = GlanceTheme.colors.onSurface,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.Medium,
                ),
                maxLines = 1,
                modifier = GlanceModifier.padding(start = 8.dp),
            )
            if (task.dueDate != today) {
                Text(
                    text = relativeDayLabel(context, task.dueDate),
                    style = TextStyle(
                        color = if (overdue) {
                            GlanceTheme.colors.tertiary
                        } else {
                            GlanceTheme.colors.onSurfaceVariant
                        },
                        fontSize = 10.sp,
                    ),
                    maxLines = 1,
                    modifier = GlanceModifier.padding(start = 6.dp),
                )
            }
        }
    }
}
