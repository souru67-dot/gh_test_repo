package com.souru.koyomi.ui.month

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDropDown
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.CalendarViewMonth
import androidx.compose.material.icons.outlined.Search
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberBottomSheetScaffoldState
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.LifecycleResumeEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.koyomi.R
import com.souru.koyomi.data.model.EventDetails
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.ui.common.KoyomiDatePickerDialog
import com.souru.koyomi.util.MonthPages
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

private val SheetPeekHeight = 280.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    onCreateEvent: (LocalDate) -> Unit,
    onEditEvent: (eventId: Long, beginMs: Long, endMs: Long) -> Unit,
    onEditTask: (taskId: Long) -> Unit,
    onOpenTimeline: (mode: String, date: LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    deepLinkEpochDay: Long?,
    onDeepLinkConsumed: () -> Unit,
) {
    val viewModel: MonthViewModel = viewModel(factory = MonthViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val verticalScroll by viewModel.verticalScroll.collectAsStateWithLifecycle()
    val multiDayBars by viewModel.multiDayBars.collectAsStateWithLifecycle()
    val showWeekNumbers by viewModel.showWeekNumbers.collectAsStateWithLifecycle()
    val showRokuyo by viewModel.showRokuyo.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var detailInstance by remember { mutableStateOf<EventInstance?>(null) }
    var detail by remember { mutableStateOf<EventDetails?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }
    var pendingDuplicate by remember { mutableStateOf<EventInstance?>(null) }
    var pendingRecurringMove by remember { mutableStateOf<PendingMove?>(null) }
    var showMonthJump by remember { mutableStateOf(false) }
    val snackbarHostState = remember { androidx.compose.material3.SnackbarHostState() }

    // Whether an event's calendar accepts edits (read-only calendars can't be
    // dragged). Recomputed only when the calendar list changes.
    val writableCalendarIds = remember(state.calendars) {
        state.calendars.filter { it.isWritable }.map { it.id }.toSet()
    }

    // Drop router: copy is always a new single event; a move on a recurring
    // series first asks for scope (this one / all).
    fun handleDrop(event: EventInstance, target: LocalDate, copy: Boolean) {
        if (copy) {
            viewModel.dragCopy(event, target)
            return
        }
        if (target == event.startDate) return
        scope.launch {
            val details = viewModel.loadDetails(event.eventId)
            if (!details?.rrule.isNullOrBlank()) {
                pendingRecurringMove = PendingMove(event, target)
            } else {
                viewModel.dragMoveAll(event, target)
            }
        }
    }

    // Drag feedback: snackbar with an undo action for move/copy.
    val undoLabel = stringResource(R.string.undo)
    val dragDatePattern = stringResource(R.string.drag_date_pattern)
    LaunchedEffect(Unit) {
        viewModel.dragEvents.collect { ev ->
            val formatter = java.time.format.DateTimeFormatter
                .ofPattern(dragDatePattern, Locale.getDefault())
            val (message, undoable) = when (ev) {
                is MonthViewModel.DragEvent.Moved ->
                    context.getString(R.string.drag_moved, ev.target.format(formatter)) to true
                is MonthViewModel.DragEvent.Copied ->
                    context.getString(R.string.drag_copied, ev.target.format(formatter)) to true
                MonthViewModel.DragEvent.Failed ->
                    context.getString(R.string.drag_failed) to false
                MonthViewModel.DragEvent.NotEditable ->
                    context.getString(R.string.not_editable) to false
            }
            val result = snackbarHostState.showSnackbar(
                message = message,
                actionLabel = if (undoable) undoLabel else null,
                duration = androidx.compose.material3.SnackbarDuration.Short,
            )
            if (result == androidx.compose.material3.SnackbarResult.ActionPerformed) {
                viewModel.undoLastDrag()
            }
        }
    }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        // Pull in changes made on Google Calendar since the app was last open.
        viewModel.syncNow()
        onPauseOrDispose { }
    }

    val pagerState = rememberPagerState(
        initialPage = MonthPages.pageOf(YearMonth.now()),
        pageCount = { MonthPages.COUNT },
    )
    val listState = rememberLazyListState(
        initialFirstVisibleItemIndex = MonthPages.pageOf(YearMonth.now()),
    )
    LaunchedEffect(pagerState, verticalScroll) {
        if (!verticalScroll) {
            snapshotFlow { pagerState.currentPage }.collect { page ->
                viewModel.setVisibleMonth(MonthPages.monthAt(page))
            }
        }
    }
    LaunchedEffect(listState, verticalScroll) {
        if (verticalScroll) {
            snapshotFlow { listState.firstVisibleItemIndex }.collect { index ->
                viewModel.setVisibleMonth(MonthPages.monthAt(index))
            }
        }
    }

    suspend fun scrollToMonth(month: YearMonth) {
        val page = MonthPages.pageOf(month)
        if (verticalScroll) {
            if (abs(listState.firstVisibleItemIndex - page) <= 3) {
                listState.animateScrollToItem(page)
            } else {
                listState.scrollToItem(page)
            }
        } else {
            if (abs(pagerState.currentPage - page) <= 3) {
                pagerState.animateScrollToPage(page)
            } else {
                pagerState.scrollToPage(page)
            }
        }
    }

    // Widget taps arrive as an epoch-day extra: select the day and show its month.
    LaunchedEffect(deepLinkEpochDay) {
        val epochDay = deepLinkEpochDay ?: return@LaunchedEffect
        val date = LocalDate.ofEpochDay(epochDay)
        viewModel.select(date)
        scrollToMonth(YearMonth.from(date))
        onDeepLinkConsumed()
    }

    fun closeDetail() {
        detailInstance = null
        detail = null
    }

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(),
        snackbarHost = { androidx.compose.material3.SnackbarHost(snackbarHostState) },
        sheetPeekHeight = SheetPeekHeight,
        sheetDragHandle = {
            // Slim brand handle instead of the stock Material pill.
            Box(
                modifier = Modifier
                    .padding(vertical = 10.dp)
                    .size(width = 36.dp, height = 4.dp)
                    .background(
                        MaterialTheme.colorScheme.outlineVariant,
                        CircleShape,
                    ),
            )
        },
        topBar = {
            MonthTopBar(
                month = visibleMonth,
                onTodayClick = {
                    viewModel.select(LocalDate.now())
                    scope.launch { scrollToMonth(YearMonth.now()) }
                },
                onOpenTimeline = { mode -> onOpenTimeline(mode, selectedDate) },
                onOpenSettings = onOpenSettings,
                onOpenSearch = onOpenSearch,
                onMonthClick = { showMonthJump = true },
            )
        },
        sheetContent = {
            DaySheetContent(
                date = selectedDate,
                rokuyo = if (showRokuyo) {
                    com.souru.koyomi.data.rokuyo.Kyureki.rokuyoFor(selectedDate)
                } else {
                    null
                },
                events = state.eventsByDay[selectedDate].orEmpty(),
                tasks = state.tasksByDay[selectedDate].orEmpty(),
                calendars = state.calendars,
                detailInstance = detailInstance,
                detail = detail,
                onEventClick = { event ->
                    detailInstance = event
                    detail = null
                    scope.launch { detail = viewModel.loadDetails(event.eventId) }
                },
                onCloseDetail = ::closeDetail,
                onAdd = { onCreateEvent(selectedDate) },
                onEdit = { event ->
                    closeDetail()
                    onEditEvent(event.eventId, event.begin, event.end)
                },
                onDuplicate = { event -> pendingDuplicate = event },
                onDeleteRequest = { event ->
                    // `detail` belongs to the event shown in the sheet, so its
                    // RRULE tells us whether this is a recurring series.
                    pendingDelete = PendingDelete(
                        event = event,
                        isRecurring = !detail?.rrule.isNullOrBlank(),
                    )
                },
                onAddTask = { title -> viewModel.addTask(title, selectedDate) },
                onToggleTask = { task -> viewModel.setTaskDone(task.id, !task.done) },
                onDeleteTask = { task -> viewModel.deleteTask(task.id) },
                onEditTask = { task ->
                    closeDetail()
                    onEditTask(task.id)
                },
                modifier = Modifier
                    .fillMaxHeight(0.88f)
                    .navigationBarsPadding(),
            )
        },
    ) { innerPadding ->
        Column(
            // innerPadding already reserves the sheet peek height at the bottom.
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (!state.hasPermission) {
                PermissionBanner(
                    text = stringResource(R.string.permission_banner),
                    actionLabel = stringResource(R.string.open_settings),
                    onAction = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                )
            } else if (state.loaded && state.calendars.isEmpty()) {
                // Permission granted but the device has no calendar account.
                // Gated on `loaded` so it never flashes before the first load.
                PermissionBanner(
                    text = stringResource(R.string.no_calendars_found),
                    actionLabel = stringResource(R.string.open_settings),
                    onAction = {
                        context.startActivity(Intent(Settings.ACTION_SYNC_SETTINGS))
                    },
                )
            }
            WeekdayHeader(
                weekStart = weekStart,
                showWeekNumbers = showWeekNumbers,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            if (verticalScroll) {
                VerticalMonthList(
                    listState = listState,
                    weekStart = weekStart,
                    state = state,
                    tasksByDay = state.tasksByDay,
                    selectedDate = selectedDate,
                    multiDayBars = multiDayBars,
                    showWeekNumbers = showWeekNumbers,
                    showRokuyo = showRokuyo,
                    viewModel = viewModel,
                    onCreateEvent = onCreateEvent,
                    onCloseDetail = ::closeDetail,
                    canDragEvent = { it.calendarId in writableCalendarIds },
                    onNotEditable = { viewModel.reportNotEditable() },
                    onDropEvent = ::handleDrop,
                )
            } else {
                HorizontalPager(
                    state = pagerState,
                    beyondViewportPageCount = 1,
                    key = { it },
                    modifier = Modifier.fillMaxSize(),
                ) { page ->
                    MonthGrid(
                        month = MonthPages.monthAt(page),
                        weekStart = weekStart,
                        eventsByDay = state.eventsByDay,
                        tasksByDay = state.tasksByDay,
                        selectedDate = selectedDate,
                        onSelect = { date ->
                            viewModel.select(date)
                            closeDetail()
                        },
                        onLongPress = { date ->
                            viewModel.select(date)
                            onCreateEvent(date)
                        },
                        onDropEvent = ::handleDrop,
                        canDragEvent = { it.calendarId in writableCalendarIds },
                        onNotEditable = { viewModel.reportNotEditable() },
                        multiDayBars = multiDayBars,
                        showWeekNumbers = showWeekNumbers,
                        showRokuyo = showRokuyo,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                    )
                }
            }
        }
    }

    // 年月ジャンプ: tapping the "7月 2026" header opens a month picker.
    if (showMonthJump) {
        MonthJumpDialog(
            initial = visibleMonth,
            onDismiss = { showMonthJump = false },
            onSelect = { target ->
                showMonthJump = false
                scope.launch { scrollToMonth(target) }
            },
        )
    }

    // 複製: let the user pick which day the copy lands on.
    pendingDuplicate?.let { source ->
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = source.startDate
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        KoyomiDatePickerDialog(
            state = pickerState,
            onDismiss = { pendingDuplicate = null },
            confirmLabel = stringResource(R.string.duplicate),
            onConfirm = {
                pickerState.selectedDateMillis?.let { millis ->
                    val target = Instant.ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    viewModel.duplicateEventTo(
                        source.eventId,
                        ChronoUnit.DAYS.between(source.startDate, target),
                    )
                }
                pendingDuplicate = null
                closeDetail()
            },
        )
    }

    // Moving a recurring event: choose the scope (Google Calendar convention).
    pendingRecurringMove?.let { move ->
        AlertDialog(
            onDismissRequest = { pendingRecurringMove = null },
            title = { Text(stringResource(R.string.move_recurring_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.move_recurring_message,
                        move.event.title.ifBlank { stringResource(R.string.untitled) },
                    ),
                )
            },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            viewModel.dragMoveInstance(move.event, move.target)
                            pendingRecurringMove = null
                        },
                    ) { Text(stringResource(R.string.move_this_occurrence)) }
                    TextButton(
                        onClick = {
                            viewModel.dragMoveAll(move.event, move.target)
                            pendingRecurringMove = null
                        },
                    ) { Text(stringResource(R.string.move_all_occurrences)) }
                    TextButton(onClick = { pendingRecurringMove = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }

    pendingDelete?.let { pending ->
        val event = pending.event
        val title = event.title.ifBlank { stringResource(R.string.untitled) }
        if (pending.isRecurring) {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.delete_recurring_title)) },
                text = { Text(stringResource(R.string.delete_recurring_message, title)) },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(
                            onClick = {
                                viewModel.deleteEventInstance(event.eventId, event.begin)
                                pendingDelete = null
                                closeDetail()
                            },
                        ) { Text(stringResource(R.string.delete_this_occurrence)) }
                        TextButton(
                            onClick = {
                                viewModel.deleteEvent(event.eventId)
                                pendingDelete = null
                                closeDetail()
                            },
                        ) {
                            Text(
                                stringResource(R.string.delete_all_occurrences),
                                color = MaterialTheme.colorScheme.error,
                            )
                        }
                        TextButton(onClick = { pendingDelete = null }) {
                            Text(stringResource(R.string.cancel))
                        }
                    }
                },
            )
        } else {
            AlertDialog(
                onDismissRequest = { pendingDelete = null },
                title = { Text(stringResource(R.string.delete_confirm_title)) },
                text = { Text(stringResource(R.string.delete_confirm_message, title)) },
                confirmButton = {
                    TextButton(
                        onClick = {
                            viewModel.deleteEvent(event.eventId)
                            pendingDelete = null
                            closeDetail()
                        },
                    ) {
                        Text(
                            stringResource(R.string.delete),
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDelete = null }) {
                        Text(stringResource(R.string.cancel))
                    }
                },
            )
        }
    }
}

private data class PendingDelete(val event: EventInstance, val isRecurring: Boolean)

private data class PendingMove(val event: EventInstance, val target: LocalDate)

/** Seamless vertically scrolling months (settings option). */
@Composable
private fun VerticalMonthList(
    listState: LazyListState,
    weekStart: DayOfWeek,
    state: MonthUiState,
    tasksByDay: Map<LocalDate, List<com.souru.koyomi.data.task.Task>>,
    selectedDate: LocalDate,
    multiDayBars: Boolean,
    showWeekNumbers: Boolean,
    showRokuyo: Boolean,
    viewModel: MonthViewModel,
    onCreateEvent: (LocalDate) -> Unit,
    onCloseDetail: () -> Unit,
    canDragEvent: (EventInstance) -> Boolean,
    onNotEditable: () -> Unit,
    onDropEvent: (event: EventInstance, target: LocalDate, copy: Boolean) -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(count = MonthPages.COUNT, key = { it }) { page ->
            val month = MonthPages.monthAt(page)
            MonthGrid(
                month = month,
                weekStart = weekStart,
                eventsByDay = state.eventsByDay,
                tasksByDay = tasksByDay,
                selectedDate = selectedDate,
                onSelect = { date ->
                    viewModel.select(date)
                    onCloseDetail()
                },
                onLongPress = { date ->
                    viewModel.select(date)
                    onCreateEvent(date)
                },
                onDropEvent = onDropEvent,
                canDragEvent = canDragEvent,
                onNotEditable = onNotEditable,
                multiDayBars = multiDayBars,
                showWeekNumbers = showWeekNumbers,
                showRokuyo = showRokuyo,
                // Same cell density as the pager: a fixed compact height made
                // the chips overflow their cells in continuous scroll mode.
                modifier = Modifier
                    .fillMaxWidth()
                    .fillParentMaxHeight()
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun MonthTopBar(
    month: YearMonth,
    onTodayClick: () -> Unit,
    onOpenTimeline: (mode: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onMonthClick: () -> Unit,
) {
    var viewMenuOpen by remember { mutableStateOf(false) }
    // BottomSheetScaffold does not wrap its topBar slot in a themed Surface,
    // so without one the content color falls back to plain black.
    Surface(
        color = MaterialTheme.colorScheme.surface,
        contentColor = MaterialTheme.colorScheme.onSurface,
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .statusBarsPadding()
                .height(64.dp)
                .padding(start = 20.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            MonthTopBarContent(
                month = month,
                onTodayClick = onTodayClick,
                onOpenTimeline = onOpenTimeline,
                onOpenSettings = onOpenSettings,
                onOpenSearch = onOpenSearch,
                onMonthClick = onMonthClick,
                viewMenuOpen = viewMenuOpen,
                onViewMenuChange = { viewMenuOpen = it },
            )
        }
    }
}

@Composable
private fun androidx.compose.foundation.layout.RowScope.MonthTopBarContent(
    month: YearMonth,
    onTodayClick: () -> Unit,
    onOpenTimeline: (mode: String) -> Unit,
    onOpenSettings: () -> Unit,
    onOpenSearch: () -> Unit,
    onMonthClick: () -> Unit,
    viewMenuOpen: Boolean,
    onViewMenuChange: (Boolean) -> Unit,
) {
    // Typography-led header: the month is the hero, the year whispers.
    val locale = Locale.getDefault()
    val monthLabel = if (locale.language == "ja") {
        "${month.monthValue}月"
    } else {
        month.format(DateTimeFormatter.ofPattern("MMMM", locale))
    }
    Row(
        verticalAlignment = Alignment.Bottom,
        // Tapping the month opens the year/month jump picker.
        modifier = Modifier.clickable(onClick = onMonthClick),
    ) {
        Text(
            text = monthLabel,
            style = MaterialTheme.typography.headlineMedium,
            fontWeight = androidx.compose.ui.text.font.FontWeight.Bold,
            color = MaterialTheme.colorScheme.onSurface,
        )
        Text(
            text = month.year.toString(),
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(start = 8.dp, bottom = 5.dp),
        )
        Icon(
            imageVector = Icons.Filled.ArrowDropDown,
            contentDescription = stringResource(R.string.jump_to_month),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(bottom = 2.dp),
        )
    }
    Spacer(modifier = Modifier.weight(1f))
    IconButton(onClick = onOpenSearch) {
        Icon(
            imageVector = Icons.Outlined.Search,
            contentDescription = stringResource(R.string.search),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(onClick = onTodayClick) {
        Icon(
            imageVector = Icons.Outlined.Today,
            contentDescription = stringResource(R.string.back_to_today),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
    IconButton(onClick = { onViewMenuChange(true) }) {
        Icon(
            imageVector = Icons.Outlined.CalendarViewMonth,
            contentDescription = stringResource(R.string.switch_view),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        DropdownMenu(expanded = viewMenuOpen, onDismissRequest = { onViewMenuChange(false) }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.view_week)) },
                onClick = {
                    onViewMenuChange(false)
                    onOpenTimeline("week")
                },
            )
            DropdownMenuItem(
                text = { Text(stringResource(R.string.view_day)) },
                onClick = {
                    onViewMenuChange(false)
                    onOpenTimeline("day")
                },
            )
        }
    }
    IconButton(onClick = onOpenSettings) {
        Icon(
            imageVector = Icons.Outlined.Settings,
            contentDescription = stringResource(R.string.settings),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

/** Year stepper + 12-month grid; jumps the pager to the chosen month. */
@Composable
private fun MonthJumpDialog(
    initial: YearMonth,
    onDismiss: () -> Unit,
    onSelect: (YearMonth) -> Unit,
) {
    var year by remember { androidx.compose.runtime.mutableIntStateOf(initial.year) }
    val locale = Locale.getDefault()
    AlertDialog(
        onDismissRequest = onDismiss,
        confirmButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
        text = {
            Column {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    IconButton(
                        onClick = { if (year > 1970) year-- },
                    ) {
                        Icon(
                            Icons.Filled.ChevronLeft,
                            contentDescription = stringResource(R.string.previous_period),
                        )
                    }
                    Text(
                        text = if (locale.language == "ja") "${year}年" else year.toString(),
                        style = MaterialTheme.typography.titleMedium,
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        modifier = Modifier.weight(1f),
                    )
                    IconButton(
                        onClick = { if (year < 2169) year++ },
                    ) {
                        Icon(
                            Icons.Filled.ChevronRight,
                            contentDescription = stringResource(R.string.next_period),
                        )
                    }
                }
                for (rowIndex in 0 until 4) {
                    Row(modifier = Modifier.fillMaxWidth()) {
                        for (columnIndex in 0 until 3) {
                            val target = YearMonth.of(year, rowIndex * 3 + columnIndex + 1)
                            val selected = target == initial
                            TextButton(
                                onClick = { onSelect(target) },
                                modifier = Modifier.weight(1f),
                            ) {
                                Text(
                                    text = target.month.getDisplayName(
                                        java.time.format.TextStyle.SHORT,
                                        locale,
                                    ),
                                    color = if (selected) {
                                        MaterialTheme.colorScheme.tertiary
                                    } else {
                                        MaterialTheme.colorScheme.onSurface
                                    },
                                    fontWeight = if (selected) {
                                        androidx.compose.ui.text.font.FontWeight.Bold
                                    } else {
                                        null
                                    },
                                )
                            }
                        }
                    }
                }
            }
        },
    )
}

@Composable
private fun PermissionBanner(
    text: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = text,
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onAction) {
                Text(actionLabel)
            }
        }
    }
}
