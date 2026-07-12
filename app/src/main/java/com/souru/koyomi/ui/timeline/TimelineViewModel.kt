package com.souru.koyomi.ui.timeline

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.data.CalendarRepository
import com.souru.koyomi.data.SettingsRepository
import com.souru.koyomi.data.model.EventInstance
import java.time.DayOfWeek
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class TimelineUiState(
    val eventsByDay: Map<LocalDate, List<EventInstance>> = emptyMap(),
    val weekStart: DayOfWeek = DayOfWeek.SUNDAY,
)

/** Backs both the week and the day timeline; loads events around [anchor]. */
@OptIn(ExperimentalCoroutinesApi::class)
class TimelineViewModel(
    private val calendarRepository: CalendarRepository,
    settingsRepository: SettingsRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val _anchor = MutableStateFlow(
        (savedStateHandle["epochDay"] ?: -1L).let { epochDay ->
            if (epochDay >= 0) LocalDate.ofEpochDay(epochDay) else LocalDate.now()
        },
    )
    val anchor: StateFlow<LocalDate> = _anchor.asStateFlow()

    val uiState: StateFlow<TimelineUiState> =
        combine(
            _anchor,
            settingsRepository.weekStart,
            settingsRepository.hiddenCalendarIds,
            calendarRepository.changes,
        ) { anchor, weekStart, hidden, _ ->
            Triple(anchor, weekStart, hidden)
        }.mapLatest { (anchor, weekStart, hidden) ->
            TimelineUiState(
                eventsByDay = calendarRepository.loadEventsByDay(
                    anchor.minusDays(8),
                    anchor.plusDays(9),
                    hidden,
                ),
                weekStart = weekStart,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TimelineUiState())

    fun setAnchor(date: LocalDate) {
        _anchor.value = date
    }

    fun shift(days: Long) {
        _anchor.value = _anchor.value.plusDays(days)
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as KoyomiApplication
                TimelineViewModel(
                    calendarRepository = app.container.calendarRepository,
                    settingsRepository = app.container.settingsRepository,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
