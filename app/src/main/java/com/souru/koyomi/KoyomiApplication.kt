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
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.drop
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.launch

/** Plain manual DI — the app is small enough not to need a framework. */
class AppContainer(context: Context) {
    val calendarRepository = CalendarRepository(context)
    val settingsRepository = SettingsRepository(context)
    val taskRepository = TaskRepository(context)
    val templateRepository =
        com.souru.koyomi.data.template.EventTemplateRepository(context)
    val weatherRepository = com.souru.koyomi.data.weather.WeatherRepository(context)
    val anniversaryRepository =
        com.souru.koyomi.data.anniversary.AnniversaryRepository(context)
}

class KoyomiApplication : Application() {
    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    @OptIn(FlowPreview::class)
    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
        createNotificationChannel()
        // Keep widgets fresh across date rollover and calendar changes.
        WidgetUpdateWorker.schedule(this)
        // Redraw widgets promptly when events or tasks change, instead of
        // waiting for the periodic/content-trigger worker. `drop(1)` skips the
        // replayed/initial emission so we don't redraw on every cold start;
        // debounce coalesces the burst a single edit can produce.
        appScope.launch {
            merge(
                container.calendarRepository.changes.drop(1),
                container.taskRepository.changes.drop(1),
            ).debounce(400).collect {
                WidgetUpdateWorker.updateAllWidgets(this@KoyomiApplication)
            }
        }
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
