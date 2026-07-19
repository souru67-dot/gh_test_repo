package com.souru.colorhunt.work

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.souru.colorhunt.data.notification.ThemeNotifier
import com.souru.colorhunt.domain.roulette.DailyColorRoulette
import com.souru.colorhunt.ui.common.labelRes

/**
 * Posts the daily "today's color" notification. Scheduled by
 * [com.souru.colorhunt.data.notification.ThemeReminderScheduler].
 */
class DailyThemeWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val bucket = DailyColorRoulette.todayColor()
        val name = applicationContext.getString(bucket.labelRes())
        ThemeNotifier.notifyTodayColor(applicationContext, name)
        return Result.success()
    }
}
