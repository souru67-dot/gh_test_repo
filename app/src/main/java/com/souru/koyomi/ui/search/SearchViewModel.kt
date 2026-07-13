package com.souru.koyomi.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.data.CalendarRepository
import com.souru.koyomi.data.SettingsRepository
import com.souru.koyomi.data.model.EventInstance
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    /** Chronological results; the agenda (upcoming events) when query is blank. */
    val results: List<EventInstance> = emptyList(),
    val isAgenda: Boolean = true,
    val searched: Boolean = false,
)

/**
 * Search across all shown calendars. A blank query shows the agenda —
 * everything coming up in the next [AGENDA_DAYS] days — so the screen is
 * useful the moment it opens.
 */
@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class SearchViewModel(
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _query = MutableStateFlow("")
    val query: StateFlow<String> = _query.asStateFlow()

    val uiState: StateFlow<SearchUiState> =
        combine(
            _query.debounce { if (it.isBlank()) 0L else 250L },
            settingsRepository.hiddenCalendarIds,
            calendarRepository.changes,
        ) { query, hidden, _ ->
            query to hidden
        }.mapLatest { (query, hidden) ->
            val today = LocalDate.now()
            if (query.isBlank()) {
                val byDay = calendarRepository.loadEventsByDay(
                    today,
                    today.plusDays(AGENDA_DAYS),
                    hidden,
                )
                SearchUiState(
                    results = byDay.toSortedMap().values.asSequence().flatten()
                        .distinctBy { "${it.eventId}-${it.begin}" }
                        .toList(),
                    isAgenda = true,
                    searched = false,
                )
            } else {
                SearchUiState(
                    results = calendarRepository.searchEvents(
                        query = query,
                        rangeStart = today.minusYears(1),
                        rangeEndExclusive = today.plusYears(2),
                        hiddenCalendarIds = hidden,
                    ),
                    isAgenda = false,
                    searched = true,
                )
            }
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) {
        _query.value = value
    }

    companion object {
        private const val AGENDA_DAYS = 60L

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as KoyomiApplication
                SearchViewModel(
                    calendarRepository = app.container.calendarRepository,
                    settingsRepository = app.container.settingsRepository,
                )
            }
        }
    }
}
