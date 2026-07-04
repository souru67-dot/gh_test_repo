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

enum class Repeat(val rrule: String?) {
    NONE(null),
    DAILY("FREQ=DAILY"),
    WEEKLY("FREQ=WEEKLY"),
    MONTHLY("FREQ=MONTHLY"),
    YEARLY("FREQ=YEARLY"),
    /** An RRULE we don't model; preserved untouched on save. */
    CUSTOM(null),
}

data class EditorUiState(
    val loading: Boolean = true,
    val isNew: Boolean = true,
    val title: String = "",
    val allDay: Boolean = false,
    val start: LocalDateTime = LocalDateTime.now(),
    val end: LocalDateTime = LocalDateTime.now().plusHours(1),
    val calendars: List<CalendarInfo> = emptyList(),
    val calendarId: Long? = null,
    val location: String = "",
    val description: String = "",
    val reminderMinutes: Int? = null,
    val repeat: Repeat = Repeat.NONE,
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

    /** RRULE carried through unmodified when repeat == CUSTOM. */
    private var customRrule: String? = null

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
                val repeat = when (details.rrule?.substringBefore(";")) {
                    null, "" -> Repeat.NONE
                    "FREQ=DAILY" -> if (details.rrule == "FREQ=DAILY") Repeat.DAILY else Repeat.CUSTOM
                    "FREQ=WEEKLY" -> if (details.rrule == "FREQ=WEEKLY") Repeat.WEEKLY else Repeat.CUSTOM
                    "FREQ=MONTHLY" -> if (details.rrule == "FREQ=MONTHLY") Repeat.MONTHLY else Repeat.CUSTOM
                    "FREQ=YEARLY" -> if (details.rrule == "FREQ=YEARLY") Repeat.YEARLY else Repeat.CUSTOM
                    else -> Repeat.CUSTOM
                }
                if (repeat == Repeat.CUSTOM) customRrule = details.rrule

                _uiState.value = EditorUiState(
                    loading = false,
                    isNew = false,
                    title = details.title,
                    allDay = details.allDay,
                    start = start,
                    end = end,
                    calendars = writableCalendars,
                    calendarId = details.calendarId,
                    location = details.location.orEmpty(),
                    description = details.description.orEmpty(),
                    reminderMinutes = details.reminderMinutes,
                    repeat = repeat,
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

    fun setRepeat(repeat: Repeat) {
        if (repeat != Repeat.CUSTOM) customRrule = null
        _uiState.update { it.copy(repeat = repeat) }
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

    fun save() {
        val state = _uiState.value
        val calendarId = state.calendarId ?: return
        if (state.saving) return
        _uiState.update { it.copy(saving = true) }

        val zone = ZoneId.systemDefault()
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
            rrule = if (state.repeat == Repeat.CUSTOM) customRrule else state.repeat.rrule,
            reminderMinutes = state.reminderMinutes,
        )
        viewModelScope.launch {
            val ok = if (draft.id == null) {
                repository.createEvent(draft) != null
            } else {
                repository.updateEvent(draft)
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
