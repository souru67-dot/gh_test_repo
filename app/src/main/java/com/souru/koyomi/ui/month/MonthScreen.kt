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
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetScaffold
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
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlinx.coroutines.launch

private val SheetPeekHeight = 280.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MonthScreen(
    onCreateEvent: (LocalDate) -> Unit,
    onEditEvent: (eventId: Long, beginMs: Long, endMs: Long) -> Unit,
) {
    val viewModel: MonthViewModel = viewModel(factory = MonthViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val weekStart by viewModel.weekStart.collectAsStateWithLifecycle()
    val selectedDate by viewModel.selectedDate.collectAsStateWithLifecycle()
    val scope = rememberCoroutineScope()
    val context = LocalContext.current

    var detailInstance by remember { mutableStateOf<EventInstance?>(null) }
    var detail by remember { mutableStateOf<EventDetails?>(null) }
    var pendingDelete by remember { mutableStateOf<EventInstance?>(null) }

    LifecycleResumeEffect(Unit) {
        viewModel.refreshPermission()
        onPauseOrDispose { }
    }

    val pagerState = rememberPagerState(
        initialPage = MonthPages.pageOf(YearMonth.now()),
        pageCount = { MonthPages.COUNT },
    )
    LaunchedEffect(pagerState) {
        snapshotFlow { pagerState.currentPage }.collect { page ->
            viewModel.setVisibleMonth(MonthPages.monthAt(page))
        }
    }

    val monthPattern = stringResource(R.string.month_title_pattern)
    val monthFormatter = remember(monthPattern) {
        DateTimeFormatter.ofPattern(monthPattern, Locale.getDefault())
    }
    val currentMonth = MonthPages.monthAt(pagerState.currentPage)

    fun closeDetail() {
        detailInstance = null
        detail = null
    }

    BottomSheetScaffold(
        scaffoldState = rememberBottomSheetScaffoldState(),
        sheetPeekHeight = SheetPeekHeight,
        topBar = {
            MonthTopBar(
                title = currentMonth.format(monthFormatter),
                onTodayClick = {
                    viewModel.select(LocalDate.now())
                    scope.launch {
                        pagerState.animateScrollToPage(MonthPages.pageOf(YearMonth.now()))
                    }
                },
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
                onDeleteRequest = { event -> pendingDelete = event },
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
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 4.dp),
                )
            }
        }
    }

    pendingDelete?.let { event ->
        AlertDialog(
            onDismissRequest = { pendingDelete = null },
            title = { Text(stringResource(R.string.delete_confirm_title)) },
            text = {
                Text(
                    stringResource(
                        R.string.delete_confirm_message,
                        event.title.ifBlank { stringResource(R.string.untitled) },
                    ),
                )
            },
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

@Composable
private fun MonthTopBar(title: String, onTodayClick: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .statusBarsPadding()
            .height(56.dp)
            .padding(start = 20.dp, end = 8.dp),
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
