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

/**
 * Refreshes both widgets, then re-arms its own triggers:
 * - a content-URI trigger on CalendarProvider (event changes / account sync)
 * - a one-shot at the next local midnight (date rollover)
 */
class WidgetUpdateWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        updateAllWidgets(applicationContext)
        schedule(applicationContext)
        return Result.success()
    }

    companion object {
        private const val WORK_CALENDAR_CHANGES = "koyomi_widget_calendar_changes"
        private const val WORK_MIDNIGHT = "koyomi_widget_midnight"

        suspend fun updateAllWidgets(context: Context) {
            MonthWidget().updateAll(context)
            TodayWidget().updateAll(context)
            ClockTodayWidget().updateAll(context)
            MonthTodayWidget().updateAll(context)
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
        }
    }
}
