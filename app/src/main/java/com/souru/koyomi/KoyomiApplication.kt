package com.souru.koyomi

import android.app.Application
import android.content.Context
import com.souru.koyomi.data.CalendarRepository
import com.souru.koyomi.data.SettingsRepository

/** Plain manual DI — the app is small enough not to need a framework. */
class AppContainer(context: Context) {
    val calendarRepository = CalendarRepository(context)
    val settingsRepository = SettingsRepository(context)
}

class KoyomiApplication : Application() {
    lateinit var container: AppContainer
        private set

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)
    }
}
