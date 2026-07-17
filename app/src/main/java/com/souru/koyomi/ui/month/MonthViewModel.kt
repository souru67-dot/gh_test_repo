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
import kotlinx.coroutines.flow.first
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
    private val settingsRepository: SettingsRepository,
    private val templateRepository: com.souru.koyomi.data.template.EventTemplateRepository,
    private val weatherRepository: com.souru.koyomi.data.weather.WeatherRepository,
    private val anniversaryRepository: com.souru.koyomi.data.anniversary.AnniversaryRepository,
    private val diaryRepository: com.souru.koyomi.data.diary.DiaryRepository,
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

    val showSolarTerms: StateFlow<Boolean> = settingsRepository.showSolarTerms
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val showLuckyDays: StateFlow<Boolean> = settingsRepository.showLuckyDays
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    /** Forecast keyed by date; empty until a place is chosen in settings. */
    val weatherByDay: StateFlow<Map<LocalDate, com.souru.koyomi.data.weather.DailyWeather>> =
        weatherRepository.forecastByDay
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyMap())

    /** Refreshes the forecast if the cache is stale; cheap no-op otherwise. */
    fun refreshWeather() {
        viewModelScope.launch { weatherRepository.refreshIfStale() }
    }

    /** All saved anniversaries; the sheet shows the ones falling on a day. */
    val anniversaries: StateFlow<List<com.souru.koyomi.data.anniversary.Anniversary>> =
        anniversaryRepository.changes
            .mapLatest { anniversaryRepository.loadAll() }
            .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    data class DiaryUi(
        /** Today's note (null = none yet). */
        val text: String? = null,
        /** 過去の今日: (years ago, note) for the same date in earlier years. */
        val past: List<Pair<Int, String>> = emptyList(),
    )

    /** The selected day's diary note plus the same date 1..3 years back. */
    val diary: StateFlow<DiaryUi> =
        combine(_selectedDate, diaryRepository.changes) { date, _ -> date }
            .mapLatest { date ->
                DiaryUi(
                    text = diaryRepository.entryFor(date),
                    past = (1..3).mapNotNull { yearsAgo ->
                        val pastDate = runCatching { date.minusYears(yearsAgo.toLong()) }
                            .getOrNull() ?: return@mapNotNull null
                        diaryRepository.entryFor(pastDate)?.let { yearsAgo to it }
                    },
                )
            }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), DiaryUi())

    fun saveDiary(date: LocalDate, text: String) {
        viewModelScope.launch { diaryRepository.save(date, text) }
    }

    val showLunarDate: StateFlow<Boolean> = settingsRepository.showLunarDate
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val showMoonAge: StateFlow<Boolean> = settingsRepository.showMoonAge
        .stateIn(viewModelScope, SharingStarted.Eagerly, false)

    val useJapaneseEra: StateFlow<Boolean> = settingsRepository.useJapaneseEra
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

    // ---- 複数日への一括複製 ----
    private val _batchCopied = kotlinx.coroutines.channels.Channel<Int>(
        kotlinx.coroutines.channels.Channel.BUFFERED,
    )
    val batchCopied = _batchCopied.receiveAsFlow()
    private var batchUndoIds: List<Long> = emptyList()

    /** Copies [event] onto each of [targets] (new events); result sent to UI. */
    fun batchDuplicate(event: EventInstance, targets: List<LocalDate>) {
        if (targets.isEmpty()) return
        viewModelScope.launch {
            val ids = mutableListOf<Long>()
            for (target in targets) {
                val days = ChronoUnit.DAYS.between(event.startDate, target)
                calendarRepository.duplicateEventToReturningId(event.eventId, days)
                    ?.let { ids += it }
            }
            batchUndoIds = ids
            _batchCopied.send(ids.size)
        }
    }

    /** Deletes the events created by the last batch copy. */
    fun undoBatchCopy() {
        val ids = batchUndoIds
        batchUndoIds = emptyList()
        viewModelScope.launch { ids.forEach { calendarRepository.deleteEvent(it) } }
    }

    // ---- #7 予定テンプレート ----
    val templates: StateFlow<List<com.souru.koyomi.data.template.EventTemplate>> =
        templateRepository.changes
            .mapLatest { templateRepository.loadTemplates() }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    private val _templateSaved =
        kotlinx.coroutines.channels.Channel<Boolean>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val templateSaved = _templateSaved.receiveAsFlow()

    /** Saves the given event as a reusable template. */
    fun saveTemplateFromEvent(eventId: Long) {
        viewModelScope.launch {
            val details = calendarRepository.loadEventDetails(eventId)
            if (details == null) {
                _templateSaved.send(false)
                return@launch
            }
            val zone = java.time.ZoneId.systemDefault()
            val startMinutes = if (details.allDay) {
                DEFAULT_TEMPLATE_START
            } else {
                val t = java.time.Instant.ofEpochMilli(details.dtStart).atZone(zone).toLocalTime()
                t.hour * 60 + t.minute
            }
            val durationMinutes = if (details.allDay) {
                24 * 60
            } else {
                val end = details.dtEnd ?: (details.dtStart + 60 * 60_000L)
                ((end - details.dtStart) / 60_000L).toInt().coerceAtLeast(15)
            }
            templateRepository.save(
                com.souru.koyomi.data.template.EventTemplate(
                    id = 0L,
                    title = details.title,
                    allDay = details.allDay,
                    startMinutes = startMinutes,
                    durationMinutes = durationMinutes,
                    calendarId = details.calendarId,
                    color = details.eventColor.takeIf { it != 0 },
                    location = details.location,
                    description = details.description,
                    reminderMinutes = details.reminderMinutes,
                ),
            )
            _templateSaved.send(true)
        }
    }

    private val _templateApplied =
        kotlinx.coroutines.channels.Channel<Boolean>(kotlinx.coroutines.channels.Channel.BUFFERED)
    val templateApplied = _templateApplied.receiveAsFlow()

    /** Creates an event from [template] on [date]. */
    fun applyTemplate(template: com.souru.koyomi.data.template.EventTemplate, date: LocalDate) {
        viewModelScope.launch {
            val calendarId = resolveWritableCalendar(template.calendarId)
            if (calendarId == null) {
                _templateApplied.send(false)
                return@launch
            }
            val zone = java.time.ZoneId.systemDefault()
            val draft = if (template.allDay) {
                val startMs = date.atStartOfDay(zone).toInstant().toEpochMilli()
                com.souru.koyomi.data.model.EventDraft(
                    calendarId = calendarId,
                    title = template.title,
                    allDay = true,
                    startMillis = startMs,
                    endMillis = startMs,
                    location = template.location.orEmpty(),
                    description = template.description.orEmpty(),
                    reminderMinutes = template.reminderMinutes,
                    eventColor = template.color?.let {
                        com.souru.koyomi.data.model.EventColor(null, it)
                    },
                )
            } else {
                val start = date.atTime(template.startMinutes / 60, template.startMinutes % 60)
                val startMs = start.atZone(zone).toInstant().toEpochMilli()
                val endMs = start.plusMinutes(template.durationMinutes.toLong())
                    .atZone(zone).toInstant().toEpochMilli()
                com.souru.koyomi.data.model.EventDraft(
                    calendarId = calendarId,
                    title = template.title,
                    allDay = false,
                    startMillis = startMs,
                    endMillis = endMs,
                    location = template.location.orEmpty(),
                    description = template.description.orEmpty(),
                    reminderMinutes = template.reminderMinutes,
                    eventColor = template.color?.let {
                        com.souru.koyomi.data.model.EventColor(null, it)
                    },
                )
            }
            val created = calendarRepository.createEvent(draft)
            if (created != null) {
                settingsRepository.setLastUsedCalendarId(calendarId)
                _templateApplied.send(true)
            } else {
                _templateApplied.send(false)
            }
        }
    }

    fun deleteTemplate(id: Long) {
        viewModelScope.launch { templateRepository.delete(id) }
    }

    /** A writable calendar: the preferred one if usable, else last-used, else first. */
    private suspend fun resolveWritableCalendar(preferred: Long?): Long? {
        val writable = calendarRepository.loadCalendars().filter { it.isWritable }
        if (writable.isEmpty()) return null
        preferred?.let { p -> if (writable.any { it.id == p }) return p }
        val last = settingsRepository.lastUsedCalendarId.first()
        last?.let { l -> if (writable.any { it.id == l }) return l }
        return writable.first().id
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
        private const val DEFAULT_TEMPLATE_START = 9 * 60

        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as KoyomiApplication
                MonthViewModel(
                    calendarRepository = app.container.calendarRepository,
                    taskRepository = app.container.taskRepository,
                    settingsRepository = app.container.settingsRepository,
                    templateRepository = app.container.templateRepository,
                    weatherRepository = app.container.weatherRepository,
                    anniversaryRepository = app.container.anniversaryRepository,
                    diaryRepository = app.container.diaryRepository,
                )
            }
        }
    }
}
