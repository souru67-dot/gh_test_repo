package com.souru.koyomi.data

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.souru.koyomi.KoyomiApplication
import java.util.concurrent.TimeUnit

/**
 * Periodically nudges the sync framework so changes made on Google Calendar
 * (web or other devices) reach the local CalendarProvider without waiting
 * for the system's own sync cadence. The ContentObserver pipeline then
 * refreshes the UI and widgets automatically.
 */
class CalendarSyncWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val app = applicationContext as KoyomiApplication
        app.container.calendarRepository.requestSync()
        return Result.success()
    }

    companion object {
        private const val WORK_NAME = "koyomi_calendar_sync"

        /** [minutes] <= 0 disables the periodic request (system auto-sync only). */
        fun schedule(context: Context, minutes: Int) {
            val workManager = WorkManager.getInstance(context)
            if (minutes <= 0) {
                workManager.cancelUniqueWork(WORK_NAME)
                return
            }
            // WorkManager enforces a 15-minute floor for periodic work.
            val interval = minutes.coerceAtLeast(15).toLong()
            val request = PeriodicWorkRequestBuilder<CalendarSyncWorker>(
                interval,
                TimeUnit.MINUTES,
            ).build()
            workManager.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
