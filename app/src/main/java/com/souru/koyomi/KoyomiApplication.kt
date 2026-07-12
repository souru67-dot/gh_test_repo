package com.souru.koyomi

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import com.souru.koyomi.data.CalendarRepository
import com.souru.koyomi.data.CalendarSyncWorker
import com.souru.koyomi.data.SettingsRepository
import com.souru.koyomi.data.task.TaskRepository
import com.souru.koyomi.notifications.ReminderReceiver
import com.souru.koyomi.widget.WidgetUpdateWorker
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/** Plain manual DI — the app is small enough not to need a framework. */
class AppContainer(context: Context) {
    val calendarRepository = CalendarRepository(context)
    val settingsRepository = SettingsRepository(context)
    val taskRepository = TaskRepository(calendarRepository, settingsRepository)
}

class KoyomiApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        // Keep widgets fresh across date rollover and calendar changes.
        WidgetUpdateWorker.schedule(this)
        // (Re)schedule periodic calendar sync, following the setting live.
        appScope.launch {
            container.settingsRepository.syncIntervalMinutes.collect { minutes ->
                CalendarSyncWorker.schedule(this@KoyomiApplication, minutes)
            }
        }
        // Redraw widgets when their opacity setting changes.
        appScope.launch {
            container.settingsRepository.widgetOpacityPercent.collect {
                WidgetUpdateWorker.updateAllWidgets(this@KoyomiApplication)
            }
        }
    }

    private fun createNotificationChannel() {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(
                ReminderReceiver.CHANNEL_ID,
                getString(R.string.notification_channel_reminders),
                NotificationManager.IMPORTANCE_HIGH,
            ),
        )
    }
}
