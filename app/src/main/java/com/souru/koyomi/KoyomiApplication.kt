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
import kotlinx.coroutines.flow.distinctUntilChanged
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
    val diaryRepository = com.souru.koyomi.data.diary.DiaryRepository(context)
    val backupManager = com.souru.koyomi.data.backup.BackupManager(
        context = context,
        taskRepository = taskRepository,
        templateRepository = templateRepository,
        anniversaryRepository = anniversaryRepository,
        diaryRepository = diaryRepository,
    )
    val billingRepository = com.souru.koyomi.data.billing.BillingRepository(context)
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
        // Re-sync the premium entitlement with Play on every cold start.
        container.billingRepository.refresh()
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
            container.settingsRepository.syncIntervalMinutes.distinctUntilChanged().collect { minutes ->
                CalendarSyncWorker.schedule(this@KoyomiApplication, minutes)
            }
        }
        // Redraw widgets when a fresh forecast lands (ひとめ shows it).
        appScope.launch {
            container.weatherRepository.forecastByDay.distinctUntilChanged().drop(1).collect {
                WidgetUpdateWorker.updateAllWidgets(this@KoyomiApplication)
            }
        }
        // Settings the widgets render with (DataStore re-emits on ANY key, hence
        // distinctUntilChanged). Values are mirrored into their static holders
        // right away; widgets are redrawn only when a value
        // actually changes — not on every cold start (the 「ドット」 clock starts
        // the process every minute). A non-default value seen at start-up still
        // triggers one redraw, in case a widget was drawn before it loaded.
        appScope.launch {
            var first = true
            container.settingsRepository.clockStyle.distinctUntilChanged().collect { style ->
                com.souru.koyomi.util.TimeStyle.style = style
                if (!first || style != com.souru.koyomi.util.ClockStyle.SYSTEM) {
                    WidgetUpdateWorker.updateAllWidgets(this@KoyomiApplication)
                }
                first = false
            }
        }
        appScope.launch {
            var first = true
            container.settingsRepository.holidayCountry.distinctUntilChanged().collect { country ->
                com.souru.koyomi.data.holiday.Holidays.country = country
                    ?: com.souru.koyomi.data.holiday.HolidayCountry.fromLocale(
                        java.util.Locale.getDefault(),
                    )
                if (!first || country != null) {
                    WidgetUpdateWorker.updateAllWidgets(this@KoyomiApplication)
                }
                first = false
            }
        }
        // App-wide widget opacity.
        appScope.launch {
            container.settingsRepository.widgetOpacityPercent.distinctUntilChanged().drop(1).collect {
                WidgetUpdateWorker.updateAllWidgets(this@KoyomiApplication)
            }
        }
        registerDotClockNudges()
    }

    /**
     * While the process is alive, the system's own minute tick and screen
     * on/off/unlock keep the 「ドット」 clock current (and hand it to the
     * self-ticking TextClock while the screen is off). Cheap: it only redraws
     * when the shown clock is actually behind or the wrong kind.
     */
    private fun registerDotClockNudges() {
        val filter = android.content.IntentFilter().apply {
            addAction(android.content.Intent.ACTION_TIME_TICK)
            addAction(android.content.Intent.ACTION_SCREEN_ON)
            addAction(android.content.Intent.ACTION_SCREEN_OFF)
            addAction(android.content.Intent.ACTION_USER_PRESENT)
        }
        val receiver = object : android.content.BroadcastReceiver() {
            override fun onReceive(context: Context, intent: android.content.Intent) {
                appScope.launch {
                    runCatching {
                        com.souru.koyomi.widget.DotWidgetUpdater.tickIfStale(this@KoyomiApplication)
                    }
                }
            }
        }
        runCatching {
            androidx.core.content.ContextCompat.registerReceiver(
                this,
                receiver,
                filter,
                androidx.core.content.ContextCompat.RECEIVER_NOT_EXPORTED,
            )
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
