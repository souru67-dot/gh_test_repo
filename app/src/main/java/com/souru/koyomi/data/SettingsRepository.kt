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

/** 季節の彩り — theme packs. SUMI (墨と和紙) is the brand default; CUSTOM derives a palette from a user-picked color. */
enum class ThemePack { SUMI, SAKURA, WAKABA, AI, MOMIJI, CUSTOM }

/** App preferences. Event data itself lives in CalendarProvider, never here. */
class SettingsRepository(private val context: Context) {

    private object Keys {
        val WEEK_START = stringPreferencesKey("week_start")
        val ONBOARDING_DONE = booleanPreferencesKey("onboarding_done")
        val VERTICAL_MONTH_SCROLL = booleanPreferencesKey("vertical_month_scroll")
        val MULTI_DAY_BARS = booleanPreferencesKey("multi_day_bars")
        val DYNAMIC_COLOR = booleanPreferencesKey("dynamic_color")
        val THEME_MODE = stringPreferencesKey("theme_mode")
        val HIDDEN_CALENDAR_IDS = stringSetPreferencesKey("hidden_calendar_ids")
        val SYNC_INTERVAL_MINUTES = stringPreferencesKey("sync_interval_minutes")
        val WIDGET_OPACITY_PERCENT = stringPreferencesKey("widget_opacity_percent")
        val LAST_USED_CALENDAR_ID = stringPreferencesKey("last_used_calendar_id")
        val TASK_CALENDAR_ID = stringPreferencesKey("task_calendar_id")
        val THEME_PACK = stringPreferencesKey("theme_pack")
        val SHOW_WEEK_NUMBERS = booleanPreferencesKey("show_week_numbers")
        val SHOW_ROKUYO = booleanPreferencesKey("show_rokuyo")
        val SHOW_SOLAR_TERMS = booleanPreferencesKey("show_solar_terms")
        val SHOW_LUNAR_DATE = booleanPreferencesKey("show_lunar_date")
        val SHOW_MOON_AGE = booleanPreferencesKey("show_moon_age")
        val USE_JAPANESE_ERA = booleanPreferencesKey("use_japanese_era")
        val CUSTOM_THEME_COLOR = stringPreferencesKey("custom_theme_color")
        val SHOW_LUCKY_DAYS = booleanPreferencesKey("show_lucky_days")
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

    /** Month view: draw multi-day events as one continuous bar across days. */
    val multiDayBars: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.MULTI_DAY_BARS] ?: true
    }

    suspend fun setMultiDayBars(enabled: Boolean) {
        context.dataStore.edit { it[Keys.MULTI_DAY_BARS] = enabled }
    }

    /** Material You wallpaper colors; OFF keeps the こよみ brand palette. */
    val dynamicColor: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.DYNAMIC_COLOR] ?: false
    }

    suspend fun setDynamicColor(enabled: Boolean) {
        context.dataStore.edit { it[Keys.DYNAMIC_COLOR] = enabled }
    }

    val themeMode: Flow<ThemeMode> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_MODE]?.let { name ->
            ThemeMode.entries.find { it.name == name }
        } ?: ThemeMode.SYSTEM
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        context.dataStore.edit { it[Keys.THEME_MODE] = mode.name }
    }

    val themePack: Flow<ThemePack> = context.dataStore.data.map { prefs ->
        prefs[Keys.THEME_PACK]?.let { name ->
            ThemePack.entries.find { it.name == name }
        } ?: ThemePack.SUMI
    }

    /** Picking a pack also switches Dynamic Color off — the pack IS the look. */
    suspend fun setThemePack(pack: ThemePack) {
        context.dataStore.edit {
            it[Keys.THEME_PACK] = pack.name
            it[Keys.DYNAMIC_COLOR] = false
        }
    }

    /** Seed color (ARGB) for [ThemePack.CUSTOM]; the palette derives from its hue. */
    val customThemeColor: Flow<Int> = context.dataStore.data.map { prefs ->
        prefs[Keys.CUSTOM_THEME_COLOR]?.toIntOrNull() ?: DEFAULT_CUSTOM_THEME_COLOR
    }

    suspend fun setCustomThemeColor(argb: Int) {
        context.dataStore.edit { it[Keys.CUSTOM_THEME_COLOR] = argb.toString() }
    }

    /** Month view: ISO week numbers in a narrow left rail. */
    val showWeekNumbers: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_WEEK_NUMBERS] ?: false
    }

    suspend fun setShowWeekNumbers(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_WEEK_NUMBERS] = enabled }
    }

    /** Month view: 六曜 (大安・仏滅...) under each day number. */
    val showRokuyo: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_ROKUYO] ?: false
    }

    suspend fun setShowRokuyo(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_ROKUYO] = enabled }
    }

    /** 開運日 (一粒万倍日・天赦日・寅の日・巳の日) in month view + day sheet. */
    val showLuckyDays: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_LUCKY_DAYS] ?: false
    }

    suspend fun setShowLuckyDays(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_LUCKY_DAYS] = enabled }
    }

    /** Month view: 二十四節気 (立春・夏至...) on their day. */
    val showSolarTerms: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_SOLAR_TERMS] ?: false
    }

    suspend fun setShowSolarTerms(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_SOLAR_TERMS] = enabled }
    }

    /** Day sheet: 旧暦 (lunar) date, e.g. 神無月十五日. */
    val showLunarDate: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_LUNAR_DATE] ?: false
    }

    suspend fun setShowLunarDate(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_LUNAR_DATE] = enabled }
    }

    /** Day sheet: 月齢 (moon age / phase). */
    val showMoonAge: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.SHOW_MOON_AGE] ?: false
    }

    suspend fun setShowMoonAge(enabled: Boolean) {
        context.dataStore.edit { it[Keys.SHOW_MOON_AGE] = enabled }
    }

    /** Header year shown as a Japanese era (令和8年) instead of Gregorian. */
    val useJapaneseEra: Flow<Boolean> = context.dataStore.data.map { prefs ->
        prefs[Keys.USE_JAPANESE_ERA] ?: false
    }

    suspend fun setUseJapaneseEra(enabled: Boolean) {
        context.dataStore.edit { it[Keys.USE_JAPANESE_ERA] = enabled }
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

    /** Calendar new tasks are created in; null = follow last-used calendar. */
    val taskCalendarId: Flow<Long?> = context.dataStore.data.map { prefs ->
        prefs[Keys.TASK_CALENDAR_ID]?.toLongOrNull()
    }

    suspend fun setTaskCalendarId(calendarId: Long) {
        context.dataStore.edit { it[Keys.TASK_CALENDAR_ID] = calendarId.toString() }
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

    companion object {
        /** 藍鼠 — the out-of-the-box seed for the custom theme. */
        const val DEFAULT_CUSTOM_THEME_COLOR = 0xFF56698D.toInt()
    }
}
