package com.souru.koyomi.ui.timeline

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ChevronLeft
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material.icons.outlined.Today
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.koyomi.R
import com.souru.koyomi.data.holiday.JapaneseHolidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.ui.theme.LocalCalendarColors
import java.time.DayOfWeek
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val HourHeight = 56.dp

/** Minimal week/day timeline (Phase 3 — the month view stays the hero). */
@Composable
fun TimelineScreen(
    mode: String,
    onBack: () -> Unit,
    onEditEvent: (eventId: Long, beginMs: Long, endMs: Long) -> Unit,
) {
    val viewModel: TimelineViewModel = viewModel(factory = TimelineViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val anchor by viewModel.anchor.collectAsStateWithLifecycle()
    val isWeek = mode == "week"

    val days: List<LocalDate> = if (isWeek) {
        val lead = ((anchor.dayOfWeek.value - state.weekStart.value) % 7 + 7) % 7
        val start = anchor.minusDays(lead.toLong())
        List(7) { start.plusDays(it.toLong()) }
    } else {
        listOf(anchor)
    }

    Surface(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.statusBarsPadding()) {
            TimelineTopBar(
                title = timelineTitle(days, isWeek),
                onBack = onBack,
                onPrev = { viewModel.shift(if (isWeek) -7 else -1) },
                onNext = { viewModel.shift(if (isWeek) 7 else 1) },
                onToday = { viewModel.setAnchor(LocalDate.now()) },
            )
            if (isWeek) {
                WeekDayHeaderRow(days)
            }
            AllDayRow(days, state.eventsByDay, onEditEvent)
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)
            TimelineBody(days, state.eventsByDay, onEditEvent)
        }
    }
}

@Composable
private fun TimelineTopBar(
    title: String,
    onBack: () -> Unit,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onToday: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(56.dp)
            .padding(end = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onBack) {
            Icon(
                Icons.AutoMirrored.Filled.ArrowBack,
                contentDescription = stringResource(R.string.close),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        Spacer(modifier = Modifier.weight(1f))
        IconButton(onClick = onPrev) {
            Icon(Icons.Filled.ChevronLeft, contentDescription = null)
        }
        IconButton(onClick = onNext) {
            Icon(Icons.Filled.ChevronRight, contentDescription = null)
        }
        IconButton(onClick = onToday) {
            Icon(
                Icons.Outlined.Today,
                contentDescription = stringResource(R.string.back_to_today),
            )
        }
    }
}

@Composable
private fun timelineTitle(days: List<LocalDate>, isWeek: Boolean): String {
    val pattern = stringResource(R.string.sheet_date_pattern)
    val formatter = remember(pattern) { DateTimeFormatter.ofPattern(pattern, Locale.getDefault()) }
    return if (isWeek) {
        "${days.first().format(formatter)} – ${days.last().format(formatter)}"
    } else {
        days.first().format(formatter)
    }
}

@Composable
private fun WeekDayHeaderRow(days: List<LocalDate>) {
    val calendarColors = LocalCalendarColors.current
    val today = LocalDate.now()
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(44.dp)) // gutter for hour labels
        for (day in days) {
            val color = when {
                JapaneseHolidays.isRedDay(day) -> calendarColors.sunday
                day.dayOfWeek == DayOfWeek.SATURDAY -> calendarColors.saturday
                else -> MaterialTheme.colorScheme.onSurface
            }
            Column(
                modifier = Modifier.weight(1f),
                horizontalAlignment = Alignment.CenterHorizontally,
            ) {
                Text(
                    text = day.dayOfWeek.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                    style = MaterialTheme.typography.labelSmall,
                    color = color,
                )
                Text(
                    text = day.dayOfMonth.toString(),
                    style = MaterialTheme.typography.labelLarge,
                    color = if (day == today) MaterialTheme.colorScheme.primary else color,
                    textAlign = TextAlign.Center,
                )
            }
        }
    }
}

@Composable
private fun AllDayRow(
    days: List<LocalDate>,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
    onEditEvent: (Long, Long, Long) -> Unit,
) {
    val hasAllDay = days.any { day -> eventsByDay[day].orEmpty().any { it.allDay } }
    if (!hasAllDay) return
    Row(modifier = Modifier.fillMaxWidth()) {
        Spacer(modifier = Modifier.width(44.dp))
        for (day in days) {
            Column(modifier = Modifier.weight(1f)) {
                for (event in eventsByDay[day].orEmpty().filter { it.allDay }.take(3)) {
                    TimelineChip(
                        event = event,
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 1.dp, vertical = 1.dp)
                            .clickable { onEditEvent(event.eventId, event.begin, event.end) },
                    )
                }
            }
        }
    }
}

@Composable
private fun TimelineBody(
    days: List<LocalDate>,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
    onEditEvent: (Long, Long, Long) -> Unit,
) {
    val zone = ZoneId.systemDefault()
    Row(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState()),
    ) {
        // Hour gutter
        Column(modifier = Modifier.width(44.dp)) {
            for (hour in 0 until 24) {
                Box(modifier = Modifier.height(HourHeight)) {
                    Text(
                        text = "%02d:00".format(hour),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(start = 6.dp),
                        fontSize = 9.sp,
                    )
                }
            }
        }
        for (day in days) {
            DayTimelineColumn(
                day = day,
                events = eventsByDay[day].orEmpty().filter { !it.allDay },
                zone = zone,
                onEditEvent = onEditEvent,
                modifier = Modifier
                    .weight(1f)
                    .height(HourHeight * 24),
            )
        }
    }
}

@Composable
private fun DayTimelineColumn(
    day: LocalDate,
    events: List<EventInstance>,
    zone: ZoneId,
    onEditEvent: (Long, Long, Long) -> Unit,
    modifier: Modifier = Modifier,
) {
    BoxWithConstraints(modifier = modifier) {
        val columnWidth = maxWidth

        // Hour lines
        Column(modifier = Modifier.fillMaxSize()) {
            for (hour in 0 until 24) {
                Box(modifier = Modifier.height(HourHeight)) {
                    HorizontalDivider(
                        color = MaterialTheme.colorScheme.outlineVariant.copy(alpha = 0.6f),
                    )
                }
            }
        }

        // Current-time indicator on today's column.
        if (day == LocalDate.now()) {
            val now = java.time.LocalTime.now()
            Box(
                modifier = Modifier
                    .offset(y = HourHeight * ((now.hour * 60 + now.minute) / 60f))
                    .fillMaxWidth()
                    .height(2.dp)
                    .background(LocalCalendarColors.current.sunday),
            )
        }

        for (positioned in assignLanes(events, day, zone)) {
            val event = positioned.event
            val heightMinutes =
                (positioned.endMinute - positioned.startMinute).coerceAtLeast(24)
            val laneWidth = columnWidth / positioned.laneCount
            Box(
                modifier = Modifier
                    .offset(
                        x = laneWidth * positioned.lane,
                        y = HourHeight * (positioned.startMinute / 60f),
                    )
                    .width(laneWidth)
                    .height(HourHeight * (heightMinutes / 60f))
                    .padding(horizontal = 1.dp, vertical = 1.dp),
            ) {
                TimelineChip(
                    event = event,
                    modifier = Modifier
                        .fillMaxSize()
                        .clickable { onEditEvent(event.eventId, event.begin, event.end) },
                )
            }
        }
    }
}

private data class PositionedEvent(
    val event: EventInstance,
    val startMinute: Int,
    val endMinute: Int,
    val lane: Int,
    val laneCount: Int,
)

/** Greedy lane assignment for overlapping events (minimal week/day view). */
private fun assignLanes(
    events: List<EventInstance>,
    day: LocalDate,
    zone: ZoneId,
): List<PositionedEvent> {
    fun minuteOf(millis: Long, default: Int): Int {
        val time = Instant.ofEpochMilli(millis).atZone(zone)
        if (time.toLocalDate() != day) return default
        return time.hour * 60 + time.minute
    }

    val sorted = events.sortedBy { it.begin }
    val laneEnds = mutableListOf<Int>()
    val provisional = sorted.map { event ->
        val start = minuteOf(event.begin, 0)
        val end = minuteOf(maxOf(event.begin, event.end - 1), 24 * 60).coerceAtLeast(start + 1)
        var lane = laneEnds.indexOfFirst { it <= start }
        if (lane == -1) {
            laneEnds.add(end)
            lane = laneEnds.size - 1
        } else {
            laneEnds[lane] = end
        }
        Triple(event, start to end, lane)
    }
    val laneCount = laneEnds.size.coerceAtLeast(1)
    return provisional.map { (event, range, lane) ->
        PositionedEvent(event, range.first, range.second, lane, laneCount)
    }
}

@Composable
private fun TimelineChip(event: EventInstance, modifier: Modifier = Modifier) {
    val background = com.souru.koyomi.ui.month.eventColor(event)
    val textColor = if (background.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.8f)
    } else {
        Color.White
    }
    Box(
        modifier = modifier.background(background.copy(alpha = 0.92f), RoundedCornerShape(4.dp)),
    ) {
        Text(
            text = event.title.ifBlank { stringResource(R.string.untitled) },
            modifier = Modifier.padding(horizontal = 4.dp, vertical = 2.dp),
            fontSize = 10.sp,
            lineHeight = 12.sp,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            color = textColor,
        )
    }
}
