package com.souru.colorhunt.data.notification

import android.content.Context
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.souru.colorhunt.work.DailyThemeWorker
import java.time.Duration
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.util.concurrent.TimeUnit

/**
 * Schedules / cancels the daily theme-colour reminder via WorkManager (Phase 4).
 * A periodic 24h job fires around the user-chosen time each day.
 */
object ThemeReminderScheduler {

    const val DEFAULT_HOUR = 8
    const val DEFAULT_MINUTE = 0
    private const val WORK_NAME = "daily_theme_reminder"

    fun enable(context: Context, hour: Int, minute: Int) {
        val request = PeriodicWorkRequestBuilder<DailyThemeWorker>(24, TimeUnit.HOURS)
            .setInitialDelay(initialDelayMinutes(hour, minute), TimeUnit.MINUTES)
            .build()
        WorkManager.getInstance(context).enqueueUniquePeriodicWork(
            WORK_NAME,
            ExistingPeriodicWorkPolicy.UPDATE,
            request,
        )
    }

    fun disable(context: Context) {
        WorkManager.getInstance(context).cancelUniqueWork(WORK_NAME)
    }

    private fun initialDelayMinutes(hour: Int, minute: Int): Long {
        val now = LocalDateTime.now()
        var target = LocalDateTime.of(LocalDate.now(), LocalTime.of(hour, minute))
        if (!target.isAfter(now)) target = target.plusDays(1)
        return Duration.between(now, target).toMinutes().coerceAtLeast(1)
    }
}
