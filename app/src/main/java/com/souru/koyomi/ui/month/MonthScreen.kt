package com.souru.koyomi.ui.month

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.PagerState
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.CalendarViewMonth
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
import com.souru.koyomi.util.MonthPages
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlinx.coroutines.launch

private val SheetPeekHeight = 280.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    onCreateEvent: (LocalDate) -> Unit,
    onEditEvent: (eventId: Long, beginMs: Long, endMs: Long) -> Unit,
    onOpenTimeline: (mode: String, date: LocalDate) -> Unit,
    onOpenSettings: () -> Unit,
    deepLinkEpochDay: Long?,
    onDeepLinkConsumed: () -> Unit,
) {
    val viewModel: MonthViewModel = viewModel(factory = MonthViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val visibleMonth by viewModel.visibleMonth.collectAsStateWithLifecycle()
    val verticalScroll by viewModel.verticalScroll.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var detailInstance by remember { mutableStateOf<EventInstance?>(null) }
    var detail by remember { mutableStateOf<EventDetails?>(null) }
    var pendingDelete by remember { mutableStateOf<PendingDelete?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
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

    val monthPattern = stringResource(R.string.month_title_pattern)
    val monthFormatter = remember(monthPattern) {
        DateTimeFormatter.ofPattern(monthPattern, Locale.getDefault())
    }

    fun closeDetail() {
        detailInstance = null
        detail = null
    }

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(),
        sheetPeekHeight = SheetPeekHeight,
        topBar = {
            MonthTopBar(
                title = visibleMonth.format(monthFormatter),
                onTodayClick = {
                    viewModel.select(LocalDate.now())
                    scope.launch { scrollToMonth(YearMonth.now()) }
                },
                onOpenTimeline = { mode -> onOpenTimeline(mode, selectedDate) },
                onOpenSettings = onOpenSettings,
            )
        },
        sheetContent = {
            DaySheetContent(
                date = selectedDate,
                events = state.eventsByDay[selectedDate].orEmpty(),
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
                onDuplicate = { event ->
                    viewModel.duplicateEvent(event.eventId)
                    closeDetail()
                },
                onDeleteRequest = { event ->
                    // `detail` belongs to the event shown in the sheet, so its
                    // RRULE tells us whether this is a recurring series.
                    pendingDelete = PendingDelete(
                        event = event,
                        isRecurring = !detail?.rrule.isNullOrBlank(),
                    )
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
                    onOpenSettings = {
                        val intent = Intent(
                            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                            Uri.fromParts("package", context.packageName, null),
                        )
                        context.startActivity(intent)
                    },
                )
            }
            WeekdayHeader(
                weekStart = weekStart,
                modifier = Modifier.padding(horizontal = 4.dp, vertical = 4.dp),
            )
            if (verticalScroll) {
                VerticalMonthList(
                    listState = listState,
                    weekStart = weekStart,
                    state = state,
                    selectedDate = selectedDate,
                    viewModel = viewModel,
                    onCreateEvent = onCreateEvent,
                    onCloseDetail = ::closeDetail,
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
                        selectedDate = selectedDate,
                        onSelect = { date ->
                            viewModel.select(date)
                            closeDetail()
                        },
                        onLongPress = { date ->
                            viewModel.select(date)
                            onCreateEvent(date)
                        },
                        onMoveEvent = { event, days ->
                            viewModel.moveEvent(event.eventId, days)
                        },
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(horizontal = 4.dp),
                    )
                }
            }
        }
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

/** Seamless vertically scrolling months (settings option). */
@Composable
private fun VerticalMonthList(
    listState: LazyListState,
    weekStart: DayOfWeek,
    state: MonthUiState,
    selectedDate: LocalDate,
    viewModel: MonthViewModel,
    onCreateEvent: (LocalDate) -> Unit,
    onCloseDetail: () -> Unit,
) {
    LazyColumn(state = listState, modifier = Modifier.fillMaxSize()) {
        items(count = MonthPages.COUNT, key = { it }) { page ->
            val month = MonthPages.monthAt(page)
            MonthGrid(
                month = month,
                weekStart = weekStart,
                eventsByDay = state.eventsByDay,
                selectedDate = selectedDate,
                onSelect = { date ->
                    viewModel.select(date)
                    onCloseDetail()
                },
                onLongPress = { date ->
                    viewModel.select(date)
                    onCreateEvent(date)
                },
                onMoveEvent = { event, days ->
                    viewModel.moveEvent(event.eventId, days)
                },
                modifier = Modifier
                    .fillMaxWidth()
                    .height(288.dp)
                    .padding(horizontal = 4.dp),
            )
        }
    }
}

@Composable
private fun MonthTopBar(
    title: String,
    onTodayClick: () -> Unit,
    onOpenTimeline: (mode: String) -> Unit,
    onOpenSettings: () -> Unit,
) {
    var viewMenuOpen by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 20.dp, end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(text = title, style = MaterialTheme.typography.titleLarge)
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onTodayClick) {
            Icon(
                imageVector = Icons.Outlined.Today,
                contentDescription = stringResource(R.string.back_to_today),
            )
        }
        IconButton(onClick = { viewMenuOpen = true }) {
            Icon(
                imageVector = Icons.Outlined.CalendarViewMonth,
                contentDescription = stringResource(R.string.switch_view),
            )
            DropdownMenu(expanded = viewMenuOpen, onDismissRequest = { viewMenuOpen = false }) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.view_week)) },
                    onClick = {
                        viewMenuOpen = false
                        onOpenTimeline("week")
                    },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.view_day)) },
                    onClick = {
                        viewMenuOpen = false
                        onOpenTimeline("day")
                    },
                )
            }
        }
        IconButton(onClick = onOpenSettings) {
            Icon(
                imageVector = Icons.Outlined.Settings,
                contentDescription = stringResource(R.string.settings),
            )
        }
    }
}

@Composable
private fun PermissionBanner(onOpenSettings: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(12.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 4.dp),
    ) {
        Column(modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)) {
            Text(
                text = stringResource(R.string.permission_banner),
                style = MaterialTheme.typography.bodySmall,
            )
            TextButton(onClick = onOpenSettings) {
                Text(stringResource(R.string.open_settings))
            }
        }
    }
}
