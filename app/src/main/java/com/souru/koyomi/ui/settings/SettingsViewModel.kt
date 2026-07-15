package com.souru.koyomi.ui.settings

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.data.CalendarRepository
import com.souru.koyomi.data.SettingsRepository
import com.souru.koyomi.data.ThemeMode
import com.souru.koyomi.data.ThemePack
import com.souru.koyomi.data.model.CalendarInfo
import com.souru.koyomi.util.Ics
import java.time.DayOfWeek
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class SettingsUiState(
    val weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    val verticalScroll: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val calendars: List<CalendarInfo> = emptyList(),
    val hiddenCalendarIds: Set<Long> = emptySet(),
    val syncIntervalMinutes: Int = 30,
    val widgetOpacityPercent: Int = 100,
    val multiDayBars: Boolean = true,
    val dynamicColor: Boolean = false,
    val themePack: ThemePack = ThemePack.SUMI,
    val showWeekNumbers: Boolean = false,
    val showRokuyo: Boolean = false,
    val showSolarTerms: Boolean = false,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val calendarRepository: CalendarRepository,
    private val appContext: Context,
) : ViewModel() {

    private val calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())

    init {
        // Fresh from the provider on every screen open — never cached, so
        // calendars newly created on the Google side show up after a sync.
        viewModelScope.launch {
            calendarRepository.requestSync()
            calendars.value = calendarRepository.loadCalendars()
        }
    }

    private data class ExtraPrefs(
        val themePack: ThemePack,
        val showWeekNumbers: Boolean,
        val showRokuyo: Boolean,
        val syncIntervalMinutes: Int,
        val widgetOpacityPercent: Int,
    )

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsRepository.weekStart,
            settingsRepository.verticalMonthScroll,
            settingsRepository.themeMode,
            settingsRepository.multiDayBars,
            settingsRepository.dynamicColor,
        ) { weekStart, vertical, theme, bars, dynamic ->
            SettingsUiState(
                weekStart = weekStart,
                verticalScroll = vertical,
                themeMode = theme,
                multiDayBars = bars,
                dynamicColor = dynamic,
            )
        },
        combine(
            settingsRepository.themePack,
            settingsRepository.showWeekNumbers,
            settingsRepository.showRokuyo,
            settingsRepository.syncIntervalMinutes,
            settingsRepository.widgetOpacityPercent,
        ) { pack, weekNumbers, rokuyo, syncMinutes, opacity ->
            ExtraPrefs(pack, weekNumbers, rokuyo, syncMinutes, opacity)
        },
        settingsRepository.hiddenCalendarIds,
        calendars,
        settingsRepository.showSolarTerms,
    ) { base, extras, hidden, calendars, solarTerms ->
        base.copy(
            calendars = calendars,
            hiddenCalendarIds = hidden,
            syncIntervalMinutes = extras.syncIntervalMinutes,
            widgetOpacityPercent = extras.widgetOpacityPercent,
            themePack = extras.themePack,
            showWeekNumbers = extras.showWeekNumbers,
            showRokuyo = extras.showRokuyo,
            showSolarTerms = solarTerms,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setWeekStart(day: DayOfWeek) {
        viewModelScope.launch { settingsRepository.setWeekStart(day) }
    }

    fun setVerticalScroll(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVerticalMonthScroll(enabled) }
    }

    fun setMultiDayBars(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setMultiDayBars(enabled) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setDynamicColor(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    /**
     * Checkbox = "show this calendar in the app". Enabling a calendar the
     * provider marks invisible also flips provider-level VISIBLE/SYNC_EVENTS
     * so its events start syncing.
     */
    fun setCalendarShown(calendar: CalendarInfo, shown: Boolean) {
        viewModelScope.launch {
            settingsRepository.setCalendarHidden(calendar.id, hidden = !shown)
            if (shown && !calendar.isVisible) {
                calendarRepository.setCalendarVisible(calendar.id, true)
                calendars.value = calendarRepository.loadCalendars()
            }
        }
    }

    fun setSyncInterval(minutes: Int) {
        viewModelScope.launch { settingsRepository.setSyncIntervalMinutes(minutes) }
    }

    fun setWidgetOpacity(percent: Int) {
        viewModelScope.launch { settingsRepository.setWidgetOpacityPercent(percent) }
    }

    fun setThemePack(pack: ThemePack) {
        viewModelScope.launch { settingsRepository.setThemePack(pack) }
    }

    fun setShowWeekNumbers(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowWeekNumbers(enabled) }
    }

    fun setShowRokuyo(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowRokuyo(enabled) }
    }

    fun setShowSolarTerms(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setShowSolarTerms(enabled) }
    }

    /** Writes all shown calendars to [uri] as .ics; -1 on failure. */
    fun exportIcs(uri: Uri, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val count = runCatching {
                withContext(Dispatchers.IO) {
                    val events = calendarRepository.exportIcsEvents(
                        settingsRepository.hiddenCalendarIds.first(),
                    )
                    val text = Ics.write(events)
                    appContext.contentResolver.openOutputStream(uri)?.use { stream ->
                        stream.write(text.toByteArray(Charsets.UTF_8))
                    } ?: error("no stream")
                    events.size
                }
            }.getOrDefault(-1)
            onDone(count)
        }
    }

    /** Imports events from the .ics at [uri] into [calendarId]; -1 on failure. */
    fun importIcs(uri: Uri, calendarId: Long, onDone: (Int) -> Unit) {
        viewModelScope.launch {
            val count = runCatching {
                withContext(Dispatchers.IO) {
                    val text = appContext.contentResolver.openInputStream(uri)?.use { stream ->
                        stream.readBytes().toString(Charsets.UTF_8)
                    } ?: error("no stream")
                    val events = Ics.parse(text)
                    if (events.isEmpty()) 0 else {
                        calendarRepository.importIcsEvents(events, calendarId)
                    }
                }
            }.getOrDefault(-1)
            onDone(count)
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as KoyomiApplication
                SettingsViewModel(
                    settingsRepository = app.container.settingsRepository,
                    calendarRepository = app.container.calendarRepository,
                    appContext = app.applicationContext,
                )
            }
        }
    }
}
