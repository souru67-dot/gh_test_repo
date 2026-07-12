package com.souru.koyomi.ui.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.data.CalendarRepository
import com.souru.koyomi.data.SettingsRepository
import com.souru.koyomi.data.ThemeMode
import com.souru.koyomi.data.model.CalendarInfo
import java.time.DayOfWeek
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class SettingsUiState(
    val weekStart: DayOfWeek = DayOfWeek.SUNDAY,
    val verticalScroll: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val calendars: List<CalendarInfo> = emptyList(),
    val hiddenCalendarIds: Set<Long> = emptySet(),
    val syncIntervalMinutes: Int = 30,
    val widgetOpacityPercent: Int = 100,
    val multiDayBars: Boolean = true,
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    private val calendarRepository: CalendarRepository,
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

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsRepository.weekStart,
            settingsRepository.verticalMonthScroll,
            settingsRepository.themeMode,
            settingsRepository.multiDayBars,
        ) { weekStart, vertical, theme, bars ->
            SettingsUiState(
                weekStart = weekStart,
                verticalScroll = vertical,
                themeMode = theme,
                multiDayBars = bars,
            )
        },
        settingsRepository.hiddenCalendarIds,
        settingsRepository.syncIntervalMinutes,
        settingsRepository.widgetOpacityPercent,
        calendars,
    ) { base, hidden, syncMinutes, opacity, calendars ->
        base.copy(
            calendars = calendars,
            hiddenCalendarIds = hidden,
            syncIntervalMinutes = syncMinutes,
            widgetOpacityPercent = opacity,
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

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as KoyomiApplication
                SettingsViewModel(
                    settingsRepository = app.container.settingsRepository,
                    calendarRepository = app.container.calendarRepository,
                )
            }
        }
    }
}
