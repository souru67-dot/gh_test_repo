package com.souru.koyomi.widget

import android.content.Context
import android.provider.CalendarContract
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.glance.appwidget.updateAll
import java.time.Duration
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * Refreshes every widget, then re-arms its own triggers:
 * - a content-URI trigger on CalendarProvider (event changes / account sync)
 * - a one-shot (plus a non-wakeup alarm) at the next local midnight
 * - a one-shot when today's next timed item ends
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        // ひとめ shows today's weather: top the forecast up first (a no-op
        // while the 3-hour cache is fresh, silent when offline).
        runCatching {
            (applicationContext as com.souru.koyomi.KoyomiApplication)
                .container.weatherRepository.refreshIfStale()
        }
        updateAllWidgets(applicationContext)
        schedule(applicationContext)
        scheduleNextExpiry(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_CALENDAR_CHANGES = "koyomi_widget_calendar_changes"
        private const val WORK_MIDNIGHT = "koyomi_widget_midnight"
        private const val WORK_NEXT_EXPIRY = "koyomi_widget_next_expiry"

        suspend fun updateAllWidgets(context: Context) {
            MonthWidget().updateAll(context)
            TodayWidget().updateAll(context)
            ClockTodayWidget().updateAll(context)
            MonthTodayWidget().updateAll(context)
            AgendaMonthWidget().updateAll(context)
            TodoAgendaWidget().updateAll(context)
            HimekuriWidget().updateAll(context)
            MiniMonthWidget().updateAll(context)
            HitomeWidget().updateAll(context)
        }

        fun schedule(context: Context) {
            val workManager = WorkManager.getInstance(context)

            val onCalendarChange = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setConstraints(
                    Constraints.Builder()
                        .addContentUriTrigger(CalendarContract.CONTENT_URI, true)
                        .build(),
                )
                .build()
            workManager.enqueueUniqueWork(
                WORK_CALENDAR_CHANGES,
                ExistingWorkPolicy.REPLACE,
                onCalendarChange,
            )

            val zone = ZoneId.systemDefault()
            val now = java.time.ZonedDateTime.now(zone)
            val nextMidnight = LocalDate.now(zone).plusDays(1).atStartOfDay(zone).plusMinutes(1)
            val atMidnight = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                .setInitialDelay(Duration.between(now, nextMidnight))
                .build()
            workManager.enqueueUniqueWork(
                WORK_MIDNIGHT,
                ExistingWorkPolicy.REPLACE,
                atMidnight,
            )
            scheduleMidnightAlarm(context, nextMidnight.toInstant().toEpochMilli())
        }

        /**
         * Belt and braces for the date rollover. The WorkManager one-shot can
         * be deferred for hours by Doze, and DATE_CHANGED is an implicit
         * broadcast that manifest receivers may not get on Android 8+. A
         * non-wakeup RTC alarm (no exact-alarm permission needed) is delivered
         * at the latest when the screen next turns on — exactly when the user
         * would look at the widget — so yesterday's "today" ring never lingers.
         */
        private fun scheduleMidnightAlarm(context: Context, atMillis: Long) {
            val alarmManager =
                context.getSystemService(Context.ALARM_SERVICE) as? android.app.AlarmManager
                    ?: return
            val intent = android.content.Intent(context, TimeChangeReceiver::class.java)
                .setAction(TimeChangeReceiver.ACTION_MIDNIGHT)
            val pendingIntent = android.app.PendingIntent.getBroadcast(
                context,
                0,
                intent,
                android.app.PendingIntent.FLAG_UPDATE_CURRENT or
                    android.app.PendingIntent.FLAG_IMMUTABLE,
            )
            runCatching {
                alarmManager.setWindow(
                    android.app.AlarmManager.RTC,
                    atMillis,
                    MIDNIGHT_WINDOW_MILLIS,
                    pendingIntent,
                )
            }
        }

        private const val MIDNIGHT_WINDOW_MILLIS = 5 * 60 * 1000L

        /**
         * One-shot re-render at the moment today's next timed event ends (or
         * a timed to-do comes due), so finished items drop off the widgets
         * right on time instead of waiting for the next calendar change.
         */
        private suspend fun scheduleNextExpiry(context: Context) {
            val app = context.applicationContext as com.souru.koyomi.KoyomiApplication
            val now = System.currentTimeMillis()
            val today = LocalDate.now()
            val zone = ZoneId.systemDefault()

            val eventExpiries = runCatching {
                val hidden = app.container.settingsRepository.hiddenCalendarIds.first()
                app.container.calendarRepository
                    .loadEventsByDay(today, today.plusDays(1), hidden)[today]
                    .orEmpty()
                    .filter { !it.allDay }
                    .map { it.end }
            }.getOrDefault(emptyList())
            val taskExpiries = runCatching {
                app.container.taskRepository
                    .loadTasksByDay(today, today.plusDays(1))[today]
                    .orEmpty()
                    .filter { !it.done }
                    .mapNotNull { task ->
                        task.timeMinutes?.let { minutes ->
                            today.atStartOfDay(zone)
                                .plusMinutes(minutes.toLong())
                                .toInstant()
                                .toEpochMilli()
                        }
                    }
            }.getOrDefault(emptyList())

            val next = (eventExpiries + taskExpiries).filter { it > now }.minOrNull() ?: return
            val request = OneTimeWorkRequestBuilder<WidgetUpdateWorker>()
                // A small grace so the item is definitely past when we redraw.
                .setInitialDelay(Duration.ofMillis(next - now).plusSeconds(30))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(
                WORK_NEXT_EXPIRY,
                ExistingWorkPolicy.REPLACE,
                request,
            )
        }
    }
}
