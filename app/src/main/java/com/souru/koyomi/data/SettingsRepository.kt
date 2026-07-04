package com.souru.koyomi.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import java.time.DayOfWeek
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.dataStore: DataStore<Preferences> by preferencesDataStore(name = "settings")

enum class ThemeMode { SYSTEM, LIGHT, DARK }

/** App preferences. Event data itself lives in CalendarProvider, never here. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val WEEK_START = stringPreferencesKey("week_start")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val VERTICAL_MONTH_SCROLL = booleanPreferencesKey("vertical_month_scroll")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HIDDEN_CALENDAR_IDS = stringSetPreferencesKey("hidden_calendar_ids")
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

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { name ->
            ThemeMode.entries.find { it.name == name }
        } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    /** Calendars the user switched off in settings; their events are not shown. */
    val hiddenCalendarIds: Flow<Set<Long>> = context.dataStore.data.map { prefs ->
        prefs[Keys.HIDDEN_CALENDAR_IDS].orEmpty().mapNotNull { it.toLongOrNull() }.toSet()
    }

    suspend fun setCalendarHidden(calendarId: Long, hidden: Boolean) {
        context.dataStore.edit { prefs ->
            val current = prefs[Keys.HIDDEN_CALENDAR_IDS].orEmpty()
            prefs[Keys.HIDDEN_CALENDAR_IDS] = if (hidden) {
                current + calendarId.toString()
            } else {
                current - calendarId.toString()
            }
        }
    }
}
