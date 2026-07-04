package com.souru.koyomi.ui.month

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.data.CalendarRepository
import com.souru.koyomi.data.SettingsRepository
import com.souru.koyomi.data.model.CalendarInfo
import com.souru.koyomi.data.model.EventDetails
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.util.monthGridDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MonthUiState(
    val hasPermission: Boolean = true,
    val eventsByDay: Map<LocalDate, List<EventInstance>> = emptyMap(),
    val calendars: List<CalendarInfo> = emptyList(),
)

@OptIn(ExperimentalCoroutinesApi::class)
class MonthViewModel(
    private val calendarRepository: CalendarRepository,
    settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _visibleMonth = MutableStateFlow(YearMonth.now())
    val visibleMonth: StateFlow<YearMonth> = _visibleMonth.asStateFlow()

    private val permissionTick = MutableStateFlow(0)

    private val _selectedDate = MutableStateFlow(LocalDate.now())
    val selectedDate: StateFlow<LocalDate> = _selectedDate.asStateFlow()

    val weekStart: StateFlow<DayOfWeek> = settingsRepository.weekStart
        .stateIn(viewModelScope, SharingStarted.Eagerly, DayOfWeek.SUNDAY)

    val verticalScroll: StateFlow<Boolean> = settingsRepository.verticalMonthScroll
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Re-collect the ContentObserver flow whenever the permission state may have
    // changed, so the observer gets registered right after the grant.
    private val calendarChanges = permissionTick.flatMapLatest { calendarRepository.changes }

    val uiState: StateFlow<MonthUiState> =
        combine(
            _visibleMonth,
            weekStart,
            settingsRepository.hiddenCalendarIds,
            calendarChanges,
        ) { month, weekStart, hidden, _ ->
            Triple(month, weekStart, hidden)
        }.mapLatest { (month, weekStart, hidden) ->
            // Load the visible month plus one on each side so paging feels instant.
            val gridStart = monthGridDays(month.minusMonths(1), weekStart).first()
            val gridEnd = monthGridDays(month.plusMonths(1), weekStart).last().plusDays(1)
            MonthUiState(
                hasPermission = calendarRepository.hasReadPermission(),
                eventsByDay = calendarRepository.loadEventsByDay(gridStart, gridEnd, hidden),
                calendars = calendarRepository.loadCalendars(),
            )
        }.stateIn(
            viewModelScope,
            SharingStarted.WhileSubscribed(5_000),
            MonthUiState(hasPermission = calendarRepository.hasReadPermission()),
        )

    fun setVisibleMonth(month: YearMonth) {
        _visibleMonth.value = month
    }

    fun select(date: LocalDate) {
        _selectedDate.value = date
    }

    /** Called on resume; picks up permission changes made in system settings. */
    fun refreshPermission() {
        permissionTick.value += 1
    }

    suspend fun loadDetails(eventId: Long): EventDetails? =
        calendarRepository.loadEventDetails(eventId)

    fun deleteEvent(eventId: Long) {
        viewModelScope.launch { calendarRepository.deleteEvent(eventId) }
    }

    fun duplicateEvent(eventId: Long) {
        viewModelScope.launch { calendarRepository.duplicateEvent(eventId) }
    }

    /** Month-view drag & drop: shift an event by whole days. */
    fun moveEvent(eventId: Long, days: Long) {
        if (days == 0L) return
        viewModelScope.launch { calendarRepository.moveEventByDays(eventId, days) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as KoyomiApplication
                MonthViewModel(
                    calendarRepository = app.container.calendarRepository,
                    settingsRepository = app.container.settingsRepository,
                )
            }
        }
    }
}
