package com.souru.koyomi.ui.event

import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.createSavedStateHandle
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.data.CalendarRepository
import com.souru.koyomi.data.model.CalendarInfo
import com.souru.koyomi.data.model.EventDraft
import com.souru.koyomi.util.RepeatFreq
import com.souru.koyomi.util.RepeatRule
import java.time.DayOfWeek
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** How a save applies to a recurring event. */
enum class SaveScope { ALL, THIS_ONLY }

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    /** The event being edited already repeats (drives the save-scope dialog). */
    val isRecurring: Boolean = false,
    val title: String = "",
    val allDay: Boolean = false,
    val start: LocalDateTime = LocalDateTime.now(),
    val end: LocalDateTime = LocalDateTime.now().plusHours(1),
    val calendars: List<CalendarInfo> = emptyList(),
    val calendarId: Long? = null,
    val location: String = "",
    val description: String = "",
    val reminderMinutes: Int? = null,
    val repeat: RepeatRule = RepeatRule(),
    val saving: Boolean = false,
    val saved: Boolean = false,
)

class EventEditViewModel(
    private val repository: CalendarRepository,
    savedStateHandle: SavedStateHandle,
) : ViewModel() {

    private val eventId: Long = savedStateHandle["eventId"] ?: -1L
    private val beginMs: Long = savedStateHandle["beginMs"] ?: -1L
    private val endMs: Long = savedStateHandle["endMs"] ?: -1L
    private val dateEpochDay: Long = savedStateHandle["dateEpochDay"] ?: -1L

    private val _uiState = MutableStateFlow(EditorUiState())
    val uiState: StateFlow<EditorUiState> = _uiState.asStateFlow()

    /** BEGIN of the instance being edited; identifies the exception occurrence. */
    private var originalInstanceBegin: Long = -1L

    init {
        viewModelScope.launch { load() }
    }

    private suspend fun load() {
        val zone = ZoneId.systemDefault()
        val writableCalendars = repository.loadCalendars().filter { it.isWritable }

        if (eventId >= 0) {
            val details = repository.loadEventDetails(eventId)
            if (details != null) {
                val start: LocalDateTime
                val end: LocalDateTime
                if (details.allDay) {
                    val startDay =
                        Instant.ofEpochMilli(details.dtStart).atZone(ZoneOffset.UTC).toLocalDate()
                    val endExclusive = details.dtEnd
                        ?.let { Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate() }
                        ?: startDay.plusDays(1)
                    start = startDay.atStartOfDay()
                    end = maxOf(startDay, endExclusive.minusDays(1)).atStartOfDay()
                } else {
                    // Prefer the tapped instance's times (correct for recurring events).
                    val b = if (beginMs >= 0) beginMs else details.dtStart
                    val e = if (endMs >= 0) endMs else (details.dtEnd ?: (b + 3_600_000L))
                    start = Instant.ofEpochMilli(b).atZone(zone).toLocalDateTime()
                    end = Instant.ofEpochMilli(e).atZone(zone).toLocalDateTime()
                }
                originalInstanceBegin = if (beginMs >= 0) beginMs else details.dtStart

                _uiState.value = EditorUiState(
                    loading = false,
                    isNew = false,
                    isRecurring = !details.rrule.isNullOrBlank(),
                    title = details.title,
                    allDay = details.allDay,
                    start = start,
                    end = end,
                    calendars = writableCalendars,
                    calendarId = details.calendarId,
                    location = details.location.orEmpty(),
                    description = details.description.orEmpty(),
                    reminderMinutes = details.reminderMinutes,
                    repeat = RepeatRule.parse(details.rrule),
                )
                return
            }
        }

        // New event.
        val date = if (dateEpochDay >= 0) LocalDate.ofEpochDay(dateEpochDay) else LocalDate.now()
        val startTime = if (date == LocalDate.now()) {
            LocalTime.now().plusHours(1).withMinute(0)
        } else {
            LocalTime.of(9, 0)
        }
        val start = LocalDateTime.of(date, startTime)
        _uiState.value = EditorUiState(
            loading = false,
            isNew = true,
            start = start,
            end = start.plusHours(1),
            calendars = writableCalendars,
            calendarId = writableCalendars.firstOrNull()?.id,
        )
    }

    fun setTitle(value: String) = _uiState.update { it.copy(title = value) }
    fun setLocation(value: String) = _uiState.update { it.copy(location = value) }
    fun setDescription(value: String) = _uiState.update { it.copy(description = value) }
    fun setAllDay(value: Boolean) = _uiState.update { it.copy(allDay = value) }
    fun setCalendar(id: Long) = _uiState.update { it.copy(calendarId = id) }
    fun setReminder(minutes: Int?) = _uiState.update { it.copy(reminderMinutes = minutes) }

    fun setRepeatFreq(freq: RepeatFreq) = _uiState.update { state ->
        val byDays = if (freq == RepeatFreq.WEEKLY && state.repeat.byDays.isEmpty()) {
            setOf(state.start.dayOfWeek)
        } else {
            state.repeat.byDays
        }
        state.copy(
            repeat = state.repeat.copy(freq = freq, byDays = byDays, raw = null),
        )
    }

    fun toggleRepeatDay(day: DayOfWeek) = _uiState.update { state ->
        val current = state.repeat.byDays
        val next = if (day in current) current - day else current + day
        // Keep at least one day selected for a weekly rule.
        state.copy(
            repeat = state.repeat.copy(
                byDays = if (next.isEmpty()) setOf(state.start.dayOfWeek) else next,
            ),
        )
    }

    fun setRepeatUntil(date: LocalDate?) = _uiState.update { state ->
        state.copy(repeat = state.repeat.copy(until = date))
    }

    /** Moving the start keeps the event duration; the end follows. */
    fun setStart(value: LocalDateTime) = _uiState.update { state ->
        val duration = Duration.between(state.start, state.end)
        state.copy(start = value, end = value.plus(duration))
    }

    fun setEnd(value: LocalDateTime) = _uiState.update { state ->
        if (value.isBefore(state.start)) {
            state.copy(end = state.start.plusHours(1))
        } else {
            state.copy(end = value)
        }
    }

    fun save(scope: SaveScope = SaveScope.ALL) {
        val state = _uiState.value
        val calendarId = state.calendarId ?: return
        if (state.saving) return
        _uiState.update { it.copy(saving = true) }

        val zone = ZoneId.systemDefault()
        val thisOnly = scope == SaveScope.THIS_ONLY && state.isRecurring && eventId >= 0
        val draft = EventDraft(
            id = if (eventId >= 0) eventId else null,
            calendarId = calendarId,
            title = state.title.trim(),
            allDay = state.allDay,
            startMillis = if (state.allDay) {
                state.start.toLocalDate().atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                state.start.atZone(zone).toInstant().toEpochMilli()
            },
            endMillis = if (state.allDay) {
                maxOf(state.start.toLocalDate(), state.end.toLocalDate())
                    .atStartOfDay(zone).toInstant().toEpochMilli()
            } else {
                state.end.atZone(zone).toInstant().toEpochMilli()
            },
            location = state.location.trim(),
            description = state.description.trim(),
            rrule = if (thisOnly) null else state.repeat.toRRule(),
            reminderMinutes = state.reminderMinutes,
        )
        viewModelScope.launch {
            val ok = when {
                draft.id == null -> repository.createEvent(draft) != null
                thisOnly -> repository.updateEventInstance(eventId, originalInstanceBegin, draft)
                else -> repository.updateEvent(draft)
            }
            _uiState.update { it.copy(saving = false, saved = ok) }
        }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as KoyomiApplication
                EventEditViewModel(
                    repository = app.container.calendarRepository,
                    savedStateHandle = createSavedStateHandle(),
                )
            }
        }
    }
}
