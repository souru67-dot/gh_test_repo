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
)

class SettingsViewModel(
    private val settingsRepository: SettingsRepository,
    calendarRepository: CalendarRepository,
) : ViewModel() {

    private val calendars = MutableStateFlow<List<CalendarInfo>>(emptyList())

    init {
        viewModelScope.launch { calendars.value = calendarRepository.loadCalendars() }
    }

    val uiState: StateFlow<SettingsUiState> = combine(
        combine(
            settingsRepository.weekStart,
            settingsRepository.verticalMonthScroll,
            settingsRepository.themeMode,
        ) { weekStart, vertical, theme -> Triple(weekStart, vertical, theme) },
        settingsRepository.hiddenCalendarIds,
        settingsRepository.syncIntervalMinutes,
        calendars,
    ) { (weekStart, vertical, theme), hidden, syncMinutes, calendars ->
        SettingsUiState(
            weekStart = weekStart,
            verticalScroll = vertical,
            themeMode = theme,
            calendars = calendars,
            hiddenCalendarIds = hidden,
            syncIntervalMinutes = syncMinutes,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SettingsUiState())

    fun setWeekStart(day: DayOfWeek) {
        viewModelScope.launch { settingsRepository.setWeekStart(day) }
    }

    fun setVerticalScroll(enabled: Boolean) {
        viewModelScope.launch { settingsRepository.setVerticalMonthScroll(enabled) }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { settingsRepository.setThemeMode(mode) }
    }

    fun setCalendarHidden(calendarId: Long, hidden: Boolean) {
        viewModelScope.launch { settingsRepository.setCalendarHidden(calendarId, hidden) }
    }

    fun setSyncInterval(minutes: Int) {
        viewModelScope.launch { settingsRepository.setSyncIntervalMinutes(minutes) }
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
