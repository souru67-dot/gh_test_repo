package com.souru.koyomi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

/** App preferences. Event data itself lives in CalendarProvider, never here. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val WEEK_START = stringPreferencesKey("week_start")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val VERTICAL_MONTH_SCROLL = booleanPreferencesKey("vertical_month_scroll")
    }

    val weekStart: Flow<DayOfWeek> = context.dataStore.data.map { prefs ->
        when (prefs[Keys.WEEK_START]) {
            DayOfWeek.MONDAY.name -> DayOfWeek.MONDAY
            else -> DayOfWeek.SUNDAY
        }
    }

    val onboardingDone: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.ONBOARDING_DONE] ?: false
    }

    val verticalMonthScroll: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.VERTICAL_MONTH_SCROLL] ?: false
    }

    suspend fun setWeekStart(day: DayOfWeek) {
        context.dataStore.edit { it[Keys.WEEK_START] = day.name }
    }

    suspend fun setOnboardingDone() {
        context.dataStore.edit { it[Keys.ONBOARDING_DONE] = true }
    }

    suspend fun setVerticalMonthScroll(enabled: Boolean) {
        context.dataStore.edit { it[Keys.VERTICAL_MONTH_SCROLL] = enabled }
    }
}
