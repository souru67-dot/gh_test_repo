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
        val SYNC_INTERVAL_MINUTES = stringPreferencesKey("sync_interval_minutes")
        val WIDGET_OPACITY_PERCENT = stringPreferencesKey("widget_opacity_percent")
        val LAST_USED_CALENDAR_ID = stringPreferencesKey("last_used_calendar_id")
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

    /** Minutes between background sync requests; 0 = rely on system auto-sync only. */
    val syncIntervalMinutes: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.SYNC_INTERVAL_MINUTES]?.toIntOrNull() ?: 30
    }

    suspend fun setSyncIntervalMinutes(minutes: Int) {
        context.dataStore.edit { it[Keys.SYNC_INTERVAL_MINUTES] = minutes.toString() }
    }

    /** The calendar last saved to; the editor preselects it for new events. */
    val lastUsedCalendarId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.LAST_USED_CALENDAR_ID]?.toLongOrNull()
    }

    suspend fun setLastUsedCalendarId(calendarId: Long) {
        context.dataStore.edit { it[Keys.LAST_USED_CALENDAR_ID] = calendarId.toString() }
    }

    /** Widget background opacity, 0..100 (%), app-wide default. */
    val widgetOpacityPercent: Flow<Int> = context.dataStore.data.map { prefs ->
        (prefs[Keys.WIDGET_OPACITY_PERCENT]?.toIntOrNull() ?: 100).coerceIn(0, 100)
    }

    suspend fun setWidgetOpacityPercent(percent: Int) {
        context.dataStore.edit {
            it[Keys.WIDGET_OPACITY_PERCENT] = percent.coerceIn(0, 100).toString()
        }
    }

    // ---- Per-widget overrides (keyed by appWidgetId) ----

    private fun widgetThemeKey(appWidgetId: Int) =
        stringPreferencesKey("widget_theme_$appWidgetId")

    private fun widgetOpacityKey(appWidgetId: Int) =
        stringPreferencesKey("widget_opacity_$appWidgetId")

    /** Per-widget theme; SYSTEM follows the device (with Dynamic Color). */
    fun widgetTheme(appWidgetId: Int): Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[widgetThemeKey(appWidgetId)]?.let { name ->
            ThemeMode.entries.find { it.name == name }
        } ?: ThemeMode.SYSTEM
    }

    suspend fun setWidgetTheme(appWidgetId: Int, mode: ThemeMode) {
        context.dataStore.edit { it[widgetThemeKey(appWidgetId)] = mode.name }
    }

    /** Per-widget opacity; null = use the app-wide default. */
    fun widgetOpacity(appWidgetId: Int): Flow<Int?> = context.dataStore.data.map { prefs ->
        prefs[widgetOpacityKey(appWidgetId)]?.toIntOrNull()?.coerceIn(0, 100)
    }

    suspend fun setWidgetOpacity(appWidgetId: Int, percent: Int) {
        context.dataStore.edit {
            it[widgetOpacityKey(appWidgetId)] = percent.coerceIn(0, 100).toString()
        }
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
