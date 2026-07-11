package com.souru.koyomi.ui.month

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.souru.koyomi.data.holiday.JapaneseHolidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.ui.theme.LocalCalendarColors
import com.souru.koyomi.util.monthGridDays
import com.souru.koyomi.util.orderedWeekDays
import com.souru.koyomi.util.providerColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_EVENT_CHIPS = 3

@Composable
fun WeekdayHeader(weekStart: DayOfWeek, modifier: Modifier = Modifier) {
    val calendarColors = LocalCalendarColors.current
    Row(modifier = modifier.fillMaxWidth()) {
        for (day in orderedWeekDays(weekStart)) {
            Text(
                text = day.getDisplayName(TextStyle.NARROW, Locale.getDefault()),
                modifier = Modifier.weight(1f),
                textAlign = TextAlign.Center,
                style = MaterialTheme.typography.labelSmall,
                color = when (day) {
                    DayOfWeek.SUNDAY -> calendarColors.sunday
                    DayOfWeek.SATURDAY -> calendarColors.saturday
                    else -> MaterialTheme.colorScheme.onSurfaceVariant
                },
            )
        }
    }
}

/** Drag & drop state shared between the grid and its chips. */
private class EventDragState {
    var event by mutableStateOf<EventInstance?>(null)
    var sourceDate by mutableStateOf<LocalDate?>(null)
    var position by mutableStateOf(Offset.Zero)

    fun clear() {
        event = null
        sourceDate = null
    }
}

/**
 * A fixed 6-week grid for [month]. Cell heights stay stable while paging.
 * Long-press a chip to drag the event onto another day; long-press an empty
 * area of a cell to quick-create an event.
 */
@Composable
fun MonthGrid(
    month: YearMonth,
    weekStart: DayOfWeek,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onLongPress: (LocalDate) -> Unit,
    onMoveEvent: (event: EventInstance, days: Long) -> Unit,
    modifier: Modifier = Modifier,
    taskCounts: Map<LocalDate, Int> = emptyMap(),
) {
    val days = monthGridDays(month, weekStart)
    val today = LocalDate.now()
    val dragState = remember { EventDragState() }
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }

    fun dateAt(position: Offset): LocalDate? {
        if (gridSize.width == 0 || gridSize.height == 0) return null
        val col = (position.x / (gridSize.width / 7f)).toInt().coerceIn(0, 6)
        val row = (position.y / (gridSize.height / 6f)).toInt().coerceIn(0, 5)
        return days[row * 7 + col]
    }

    val dropTarget = if (dragState.event != null) dateAt(dragState.position) else null

    Box(
        modifier = modifier.onGloballyPositioned {
            gridCoords = it
            gridSize = it.size
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (week in 0 until 6) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    for (i in 0 until 7) {
                        val date = days[week * 7 + i]
                        DayCell(
                            date = date,
                            inCurrentMonth = YearMonth.from(date) == month,
                            isToday = date == today,
                            isSelected = date == selectedDate,
                            isDropTarget = date == dropTarget,
                            events = eventsByDay[date].orEmpty(),
                            taskCount = taskCounts[date] ?: 0,
                            onSelect = onSelect,
                            onLongPress = onLongPress,
                            dragState = dragState,
                            gridCoords = { gridCoords },
                            onDrop = { event, source, target ->
                                onMoveEvent(event, ChronoUnit.DAYS.between(source, target))
                            },
                            dropTargetOf = ::dateAt,
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxSize(),
                        )
                    }
                }
            }
        }

        // Floating chip that follows the finger while dragging.
        dragState.event?.let { event ->
            Box(
                modifier = Modifier.offset {
                    IntOffset(
                        (dragState.position.x - 40.dp.toPx()).roundToInt(),
                        (dragState.position.y - 24.dp.toPx()).roundToInt(),
                    )
                },
            ) {
                DragGhostChip(event)
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun DayCell(
    date: LocalDate,
    inCurrentMonth: Boolean,
    isToday: Boolean,
    isSelected: Boolean,
    isDropTarget: Boolean,
    events: List<EventInstance>,
    taskCount: Int,
    onSelect: (LocalDate) -> Unit,
    onLongPress: (LocalDate) -> Unit,
    dragState: EventDragState,
    gridCoords: () -> LayoutCoordinates?,
    onDrop: (event: EventInstance, source: LocalDate, target: LocalDate) -> Unit,
    dropTargetOf: (Offset) -> LocalDate?,
    modifier: Modifier = Modifier,
) {
    val calendarColors = LocalCalendarColors.current
    val baseColor = when {
        JapaneseHolidays.isRedDay(date) -> calendarColors.sunday
        date.dayOfWeek == DayOfWeek.SATURDAY -> calendarColors.saturday
        else -> MaterialTheme.colorScheme.onSurface
    }
    val dayColor = if (inCurrentMonth) baseColor else baseColor.copy(alpha = 0.3f)

    Column(
        modifier = modifier
            .then(
                when {
                    isDropTarget -> Modifier.background(
                        MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.6f),
                        RoundedCornerShape(6.dp),
                    )
                    // Light grey wash so the selected day reads at a glance.
                    isSelected -> Modifier.background(
                        MaterialTheme.colorScheme.onSurface.copy(alpha = 0.08f),
                        RoundedCornerShape(6.dp),
                    )
                    else -> Modifier
                },
            )
            .combinedClickable(
                onClick = { onSelect(date) },
                onLongClick = { onLongPress(date) },
            )
            .padding(horizontal = 1.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Box(
            modifier = Modifier
                .size(24.dp)
                .let {
                    when {
                        isToday -> it.background(MaterialTheme.colorScheme.primary, CircleShape)
                        isSelected -> it.border(
                            width = 1.5.dp,
                            color = MaterialTheme.colorScheme.primary,
                            shape = CircleShape,
                        )
                        else -> it
                    }
                },
            contentAlignment = Alignment.Center,
        ) {
            Text(
                text = date.dayOfMonth.toString(),
                style = MaterialTheme.typography.labelMedium,
                fontWeight = if (isToday) FontWeight.Bold else FontWeight.Medium,
                color = if (isToday) MaterialTheme.colorScheme.onPrimary else dayColor,
            )
        }

        val shown = events.take(MAX_EVENT_CHIPS)
        val overflow = events.size - shown.size
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            for (event in shown) {
                DraggableEventChip(
                    event = event,
                    cellDate = date,
                    dimmed = !inCurrentMonth,
                    dragState = dragState,
                    gridCoords = gridCoords,
                    onDrop = onDrop,
                    dropTargetOf = dropTargetOf,
                )
            }
            if (overflow > 0) {
                Text(
                    text = "+$overflow",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp),
                    fontSize = 9.sp,
                )
            }
            if (taskCount > 0) {
                Text(
                    text = "☑$taskCount",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(start = 3.dp),
                    fontSize = 9.sp,
                )
            }
        }
    }
}

@Composable
private fun DraggableEventChip(
    event: EventInstance,
    cellDate: LocalDate,
    dimmed: Boolean,
    dragState: EventDragState,
    gridCoords: () -> LayoutCoordinates?,
    onDrop: (event: EventInstance, source: LocalDate, target: LocalDate) -> Unit,
    dropTargetOf: (Offset) -> LocalDate?,
) {
    var chipCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val beingDragged = dragState.event?.let {
        it.eventId == event.eventId && dragState.sourceDate == cellDate
    } ?: false

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp)
            .onGloballyPositioned { chipCoords = it }
            .pointerInput(event.eventId, event.begin) {
                detectDragGesturesAfterLongPress(
                    onDragStart = { startOffset ->
                        val grid = gridCoords()
                        val chip = chipCoords
                        if (grid != null && chip != null && grid.isAttached && chip.isAttached) {
                            dragState.event = event
                            dragState.sourceDate = cellDate
                            dragState.position = grid.localPositionOf(chip, startOffset)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragState.event != null) {
                            dragState.position += dragAmount
                        }
                    },
                    onDragEnd = {
                        val dragged = dragState.event
                        val source = dragState.sourceDate
                        val target = dropTargetOf(dragState.position)
                        if (dragged != null && source != null && target != null) {
                            onDrop(dragged, source, target)
                        }
                        dragState.clear()
                    },
                    onDragCancel = { dragState.clear() },
                )
            },
    ) {
        EventChipBody(event = event, dimmed = dimmed, ghosted = beingDragged)
    }
}

@Composable
private fun EventChipBody(event: EventInstance, dimmed: Boolean, ghosted: Boolean = false) {
    val solid = eventColor(event)
    val backgroundAlpha = when {
        ghosted -> 0.25f
        dimmed -> 0.45f
        else -> 1f
    }
    val background = solid.copy(alpha = backgroundAlpha)
    // Contrast must be judged against the SOLID color — the alpha'd value has
    // a misleading luminance and produced unreadable chips on adjacent months.
    val baseText = if (solid.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.8f)
    } else {
        Color.White
    }
    val textColor = if (dimmed || ghosted) baseText.copy(alpha = 0.7f) else baseText
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(background, RoundedCornerShape(3.dp)),
    ) {
        Text(
            text = event.title.ifBlank { " " },
            modifier = Modifier.padding(horizontal = 3.dp, vertical = 0.5.dp),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            color = textColor,
        )
    }
}

@Composable
private fun DragGhostChip(event: EventInstance) {
    val background = eventColor(event)
    val textColor = if (background.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.8f)
    } else {
        Color.White
    }
    Box(
        modifier = Modifier.background(background, RoundedCornerShape(6.dp)),
    ) {
        Text(
            text = event.title.ifBlank { " " },
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelMedium,
            maxLines = 1,
            color = textColor,
        )
    }
}

@Composable
fun eventColor(event: EventInstance): Color =
    providerColor(event.color) ?: MaterialTheme.colorScheme.primary
