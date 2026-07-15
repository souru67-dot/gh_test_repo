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
import com.souru.koyomi.data.task.Task
import com.souru.koyomi.data.task.TaskRepository
import com.souru.koyomi.util.monthGridDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class MonthUiState(
    val hasPermission: Boolean = true,
    val eventsByDay: Map<LocalDate, List<EventInstance>> = emptyMap(),
    val tasksByDay: Map<LocalDate, List<Task>> = emptyMap(),
    val calendars: List<CalendarInfo> = emptyList(),
    /** False until the first provider load finishes; gates the empty banners
     *  so "no calendars" never flashes during startup. */
    val loaded: Boolean = false,
)

@OptIn(ExperimentalCoroutinesApi::class, FlowPreview::class)
class MonthViewModel(
    private val calendarRepository: CalendarRepository,
    private val taskRepository: TaskRepository,
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

    val multiDayBars: StateFlow<Boolean> = settingsRepository.multiDayBars
        .stateIn(viewModelScope, SharingStarted.Eagerly, true)

    val showWeekNumbers: StateFlow<Boolean> = settingsRepository.showWeekNumbers
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val showRokuyo: StateFlow<Boolean> = settingsRepository.showRokuyo
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    // Re-collect the ContentObserver flow whenever the permission state may have
    // changed, so the observer gets registered right after the grant.
    private val dataChanges = merge(
        permissionTick.flatMapLatest { calendarRepository.changes },
        taskRepository.changes,
    )

    val uiState: StateFlow<MonthUiState> =
        combine(
            // Debounced: while the pager is flinging through months there is
            // no point querying the provider for every page passed — wait for
            // the scroll to settle, then load once. Keeps the fling smooth.
            _visibleMonth.debounce(180),
            weekStart,
            settingsRepository.hiddenCalendarIds,
            dataChanges,
        ) { month, weekStart, hidden, _ ->
            Triple(month, weekStart, hidden)
        }.mapLatest { (month, weekStart, hidden) ->
            // Load the visible month plus two on each side: the horizontal pager
            // only needs ±1, but the vertical scroll mode shows several months
            // at once and ±1 left the neighbours' outer weeks looking empty.
            val gridStart = monthGridDays(month.minusMonths(2), weekStart).first()
            val gridEnd = monthGridDays(month.plusMonths(2), weekStart).last().plusDays(1)
            MonthUiState(
                hasPermission = calendarRepository.hasReadPermission(),
                eventsByDay = calendarRepository.loadEventsByDay(gridStart, gridEnd, hidden),
                tasksByDay = taskRepository.loadTasksByDay(gridStart, gridEnd),
                calendars = calendarRepository.loadCalendars(),
                loaded = true,
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

    /** Removes a single occurrence of a recurring event. */
    fun deleteEventInstance(eventId: Long, instanceBeginMs: Long) {
        viewModelScope.launch {
            calendarRepository.deleteEventInstance(eventId, instanceBeginMs)
        }
    }

    fun duplicateEvent(eventId: Long) {
        viewModelScope.launch { calendarRepository.duplicateEvent(eventId) }
    }

    /** Month-view drag & drop: shift an event by whole days. */
    fun moveEvent(eventId: Long, days: Long) {
        if (days == 0L) return
        viewModelScope.launch { calendarRepository.moveEventByDays(eventId, days) }
    }

    /** Month-view drag & drop: copy an event onto another day. */
    fun duplicateEventTo(eventId: Long, days: Long) {
        viewModelScope.launch { calendarRepository.duplicateEventTo(eventId, days) }
    }

    // ---- Drag & drop with snackbar + undo ----

    /** One-shot UI feedback after a drag operation; consumed by the screen. */
    sealed interface DragEvent {
        data class Moved(val target: LocalDate) : DragEvent
        data class Copied(val target: LocalDate) : DragEvent
        data object Failed : DragEvent
        data object NotEditable : DragEvent
    }

    private val _dragEvents = kotlinx.coroutines.channels.Channel<DragEvent>(
        kotlinx.coroutines.channels.Channel.BUFFERED,
    )
    val dragEvents = _dragEvents.receiveAsFlow()

    /** The reversal for the most recent successful drag, or null. */
    private var pendingUndo: (suspend () -> Boolean)? = null

    /** Move the whole event/series to [target] (keeps time of day). */
    fun dragMoveAll(event: EventInstance, target: LocalDate) {
        val days = ChronoUnit.DAYS.between(event.startDate, target)
        if (days == 0L) return
        viewModelScope.launch {
            if (calendarRepository.moveEventByDays(event.eventId, days)) {
                pendingUndo = { calendarRepository.moveEventByDays(event.eventId, -days) }
                _dragEvents.send(DragEvent.Moved(target))
            } else {
                _dragEvents.send(DragEvent.Failed)
            }
        }
    }

    /** Move only the dragged occurrence of a recurring event to [target]. */
    fun dragMoveInstance(event: EventInstance, target: LocalDate) {
        val days = ChronoUnit.DAYS.between(event.startDate, target)
        if (days == 0L) return
        viewModelScope.launch {
            if (calendarRepository.moveEventInstanceByDays(event.eventId, event.begin, days)) {
                pendingUndo = {
                    calendarRepository.restoreEventInstance(event.eventId, event.begin)
                }
                _dragEvents.send(DragEvent.Moved(target))
            } else {
                _dragEvents.send(DragEvent.Failed)
            }
        }
    }

    /** Copy the event onto [target] as a new, non-recurring event. */
    fun dragCopy(event: EventInstance, target: LocalDate) {
        val days = ChronoUnit.DAYS.between(event.startDate, target)
        viewModelScope.launch {
            val newId = calendarRepository.duplicateEventReturningId(event.eventId, days)
            if (newId != null) {
                pendingUndo = { calendarRepository.deleteEvent(newId) }
                _dragEvents.send(DragEvent.Copied(target))
            } else {
                _dragEvents.send(DragEvent.Failed)
            }
        }
    }

    fun reportNotEditable() {
        viewModelScope.launch { _dragEvents.send(DragEvent.NotEditable) }
    }

    /** Reverses the last successful move/copy. */
    fun undoLastDrag() {
        val undo = pendingUndo ?: return
        pendingUndo = null
        viewModelScope.launch { undo() }
    }

    /** Nudge the sync framework so remote Google Calendar changes come in. */
    fun syncNow() {
        if (calendarRepository.hasReadPermission()) calendarRepository.requestSync()
    }

    fun addTask(title: String, dueDate: LocalDate) {
        viewModelScope.launch { taskRepository.addTask(title, dueDate) }
    }

    fun setTaskDone(taskId: Long, done: Boolean) {
        viewModelScope.launch { taskRepository.setDone(taskId, done) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { taskRepository.deleteTask(taskId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as KoyomiApplication
                MonthViewModel(
                    calendarRepository = app.container.calendarRepository,
                    taskRepository = app.container.taskRepository,
                    settingsRepository = app.container.settingsRepository,
                )
            }
        }
    }
}
