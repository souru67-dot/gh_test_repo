package com.souru.koyomi.ui.month

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.ui.draw.alpha
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
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
import androidx.compose.ui.platform.LocalDensity
import com.souru.koyomi.data.holiday.JapaneseHolidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.data.task.Task
import com.souru.koyomi.data.rokuyo.Kyureki
import com.souru.koyomi.ui.theme.LocalCalendarColors
import com.souru.koyomi.util.monthGridDays
import com.souru.koyomi.util.orderedWeekDays
import com.souru.koyomi.util.providerColor
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.time.temporal.ChronoUnit
import java.time.temporal.WeekFields
import java.util.Locale
import kotlin.math.roundToInt

private const val MAX_EVENT_CHIPS = 3

/** Width of the ISO week-number rail on the left of the grid. */
private val WeekNumberRailWidth = 18.dp

@Composable
fun WeekdayHeader(
    weekStart: DayOfWeek,
    modifier: Modifier = Modifier,
    showWeekNumbers: Boolean = false,
) {
    val calendarColors = LocalCalendarColors.current
    Row(modifier = modifier.fillMaxWidth()) {
        if (showWeekNumbers) {
            Spacer(modifier = Modifier.width(WeekNumberRailWidth))
        }
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

/**
 * Drag & drop state. [position] is in WINDOW coordinates so a drag that
 * started on one month page stays meaningful after the pager flips to a
 * neighboring month; hoist one instance above the pager to share it.
 */
class EventDragState {
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
/** A multi-day event's stripe within one week row. */
private data class BarSegment(
    val event: EventInstance,
    val startCol: Int,
    val endCol: Int,
    val lane: Int,
    val startsHere: Boolean,
    val endsHere: Boolean,
)

private const val MAX_BAR_LANES = 2

/** Precomputed layout of one week row: lanes, bars and per-cell chips. */
private data class WeekModel(
    val days: List<LocalDate>,
    val segments: List<BarSegment>,
    val barLanes: Int,
    val cellEvents: Map<LocalDate, List<EventInstance>>,
)

/** Greedy lane assignment for the week's multi-day events. */
private fun weekBarSegments(
    weekDays: List<LocalDate>,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
): List<BarSegment> {
    val weekStart = weekDays.first()
    val weekEnd = weekDays.last()
    val spanning = weekDays
        .flatMap { eventsByDay[it].orEmpty() }
        .filter { it.startDate != it.endDate }
        .distinctBy { "${it.eventId}-${it.begin}" }
        .sortedWith(compareBy({ it.startDate }, { it.endDate.toEpochDay() * -1 }))

    val laneEnds = mutableListOf<Int>() // last occupied column per lane
    val segments = mutableListOf<BarSegment>()
    for (event in spanning) {
        val startCol = ChronoUnit.DAYS
            .between(weekStart, maxOf(event.startDate, weekStart)).toInt()
        val endCol = ChronoUnit.DAYS
            .between(weekStart, minOf(event.endDate, weekEnd)).toInt()
        var lane = laneEnds.indexOfFirst { it < startCol }
        if (lane == -1) {
            laneEnds.add(endCol)
            lane = laneEnds.size - 1
        } else {
            laneEnds[lane] = endCol
        }
        segments += BarSegment(
            event = event,
            startCol = startCol,
            endCol = endCol,
            lane = lane,
            startsHere = event.startDate >= weekStart,
            endsHere = event.endDate <= weekEnd,
        )
    }
    return segments
}

@Composable
fun MonthGrid(
    month: YearMonth,
    weekStart: DayOfWeek,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onCreateNew: (LocalDate) -> Unit,
    onMoveEvent: (event: EventInstance, days: Long) -> Unit,
    modifier: Modifier = Modifier,
    tasksByDay: Map<LocalDate, List<Task>> = emptyMap(),
    multiDayBars: Boolean = true,
    showWeekNumbers: Boolean = false,
    showRokuyo: Boolean = false,
    showSolarTerms: Boolean = false,
    showLuckyDays: Boolean = false,
    weatherByDay: Map<LocalDate, com.souru.koyomi.data.weather.DailyWeather> = emptyMap(),
    dragState: EventDragState = remember { EventDragState() },
    /** Window-space drag position updates (for edge auto-paging). */
    onDragMoved: (Offset) -> Unit = {},
    /** Resolves a drop across all visible months; null = this grid only. */
    resolveDropDate: ((Offset) -> LocalDate?)? = null,
    /** Registers/unregisters this grid's own resolver with the screen. */
    onRegisterDropResolver: (((Offset) -> LocalDate?)?) -> Unit = {},
) {
    // The grid and lane layout are pure functions of their inputs; caching
    // them keeps drag/selection recompositions from redoing date math.
    val days = remember(month, weekStart) { monthGridDays(month, weekStart) }
    val weekModels = remember(days, eventsByDay, multiDayBars) {
        List(6) { week ->
            val weekDays = days.subList(week * 7, week * 7 + 7)
            val segments = if (multiDayBars) {
                weekBarSegments(weekDays, eventsByDay)
            } else {
                emptyList()
            }
            val shown = segments.filter { it.lane < MAX_BAR_LANES }
            val barKeys = shown.map { "${it.event.eventId}-${it.event.begin}" }.toSet()
            WeekModel(
                days = weekDays,
                segments = shown,
                barLanes = (shown.maxOfOrNull { it.lane } ?: -1) + 1,
                cellEvents = weekDays.associateWith { date ->
                    eventsByDay[date].orEmpty().filter {
                        "${it.eventId}-${it.begin}" !in barKeys
                    }
                },
            )
        }
    }
    val rokuyoByDay = remember(days, showRokuyo) {
        if (showRokuyo) days.associateWith { Kyureki.rokuyoFor(it) } else emptyMap()
    }
    val solarTermByDay = remember(days, showSolarTerms) {
        if (showSolarTerms) days.associateWith { Kyureki.solarTermFor(it) } else emptyMap()
    }
    val luckyByDay = remember(days, showLuckyDays) {
        if (showLuckyDays) days.associateWith { Kyureki.luckyMarkFor(it) } else emptyMap()
    }
    // A calendar sub-line under each day (六曜・二十四節気・開運日); reserved with
    // a fixed height so multi-day bars stay aligned even on days without a term.
    val subLine = showRokuyo || showSolarTerms || showLuckyDays
    val today = LocalDate.now()
    var gridCoords by remember { mutableStateOf<LayoutCoordinates?>(null) }
    var gridSize by remember { mutableStateOf(IntSize.Zero) }
    val railPx = if (showWeekNumbers) {
        with(LocalDensity.current) { WeekNumberRailWidth.toPx() }
    } else {
        0f
    }

    fun dateAtWindow(windowPos: Offset): LocalDate? {
        val coords = gridCoords?.takeIf { it.isAttached } ?: return null
        if (gridSize.width == 0 || gridSize.height == 0) return null
        val local = coords.windowToLocal(windowPos)
        if (local.x < 0f || local.y < 0f ||
            local.x > gridSize.width || local.y > gridSize.height
        ) {
            return null
        }
        val cellsWidth = gridSize.width - railPx
        if (cellsWidth <= 0f) return null
        val col = ((local.x - railPx) / (cellsWidth / 7f)).toInt().coerceIn(0, 6)
        val row = (local.y / (gridSize.height / 6f)).toInt().coerceIn(0, 5)
        return days[row * 7 + col]
    }

    // Let the screen route drops from OTHER months into this grid.
    DisposableEffect(month, weekStart, showWeekNumbers) {
        onRegisterDropResolver(::dateAtWindow)
        onDispose { onRegisterDropResolver(null) }
    }

    val dropTarget = if (dragState.event != null) dateAtWindow(dragState.position) else null

    Box(
        modifier = modifier.onGloballyPositioned {
            gridCoords = it
            gridSize = it.size
        },
    ) {
        Column(modifier = Modifier.fillMaxSize()) {
            for (week in 0 until 6) {
                val model = weekModels[week]
                val weekDays = model.days
                val shownSegments = model.segments
                val barLanes = model.barLanes

                BoxWithConstraints(
                    modifier = Modifier
                        .fillMaxWidth()
                        .weight(1f),
                ) {
                    val rail = if (showWeekNumbers) WeekNumberRailWidth else 0.dp
                    val cellWidth = (maxWidth - rail) / 7
                    Row(modifier = Modifier.fillMaxSize()) {
                        if (showWeekNumbers) {
                            WeekNumberCell(weekDays)
                        }
                        for (i in 0 until 7) {
                            val date = weekDays[i]
                            DayCell(
                                date = date,
                                inCurrentMonth = YearMonth.from(date) == month,
                                isToday = date == today,
                                isSelected = date == selectedDate,
                                isDropTarget = date == dropTarget,
                                events = model.cellEvents[date].orEmpty(),
                                barLanes = barLanes,
                                tasks = tasksByDay[date].orEmpty(),
                                rokuyo = rokuyoByDay[date],
                                solarTerm = solarTermByDay[date],
                                luckyMark = luckyByDay[date],
                                weather = weatherByDay[date]?.emoji,
                                reserveSubLine = subLine,
                                onSelect = onSelect,
                                onCreateNew = onCreateNew,
                                dragState = dragState,
                                onDragMoved = onDragMoved,
                                onDrop = { event, source, target ->
                                    onMoveEvent(event, ChronoUnit.DAYS.between(source, target))
                                },
                                dropTargetOf = resolveDropDate ?: ::dateAtWindow,
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxSize(),
                            )
                        }
                    }
                    // Continuous stripes for multi-day events, over the cells.
                    // They start below the day number (and the calendar sub-line
                    // if 六曜/二十四節気 is shown).
                    val barTop = if (subLine) 38.dp else 27.dp
                    for (segment in shownSegments) {
                        MultiDayBar(
                            segment = segment,
                            date = weekDays[segment.startCol],
                            cellWidth = cellWidth,
                            onSelect = onSelect,
                            modifier = Modifier.offset(
                                x = rail + cellWidth * segment.startCol,
                                y = barTop + (segment.lane * 14).dp,
                            ),
                        )
                    }
                }
            }
        }

        // Floating chip that follows the finger; drawn only by the grid the
        // finger is currently over, so page flips hand the ghost over cleanly.
        dragState.event?.let { event ->
            val coords = gridCoords?.takeIf { it.isAttached }
            val local = coords?.windowToLocal(dragState.position)
            if (local != null &&
                local.x in 0f..gridSize.width.toFloat() &&
                local.y in 0f..gridSize.height.toFloat()
            ) {
                Box(
                    modifier = Modifier.offset {
                        IntOffset(
                            (local.x - 40.dp.toPx()).roundToInt(),
                            (local.y - 24.dp.toPx()).roundToInt(),
                        )
                    },
                ) {
                    DragGhostChip(event)
                }
            }
        }
    }
}

/** Narrow rail cell showing the ISO week number of this row. */
@Composable
private fun WeekNumberCell(weekDays: List<LocalDate>) {
    // ISO weeks are Monday-based; the row always contains exactly one Monday.
    val monday = weekDays.first { it.dayOfWeek == DayOfWeek.MONDAY }
    val weekNumber = monday.get(WeekFields.ISO.weekOfWeekBasedYear())
    Box(
        modifier = Modifier
            .width(WeekNumberRailWidth)
            .padding(top = 6.dp),
        contentAlignment = Alignment.TopCenter,
    ) {
        Text(
            text = weekNumber.toString(),
            style = MaterialTheme.typography.labelSmall,
            fontSize = 8.sp,
            color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.7f),
        )
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
    barLanes: Int,
    tasks: List<Task>,
    rokuyo: String?,
    solarTerm: String?,
    luckyMark: String?,
    weather: String?,
    reserveSubLine: Boolean,
    onSelect: (LocalDate) -> Unit,
    onCreateNew: (LocalDate) -> Unit,
    dragState: EventDragState,
    onDragMoved: (Offset) -> Unit,
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
                        RoundedCornerShape(8.dp),
                    )
                    // Selection = rounded square wash; today = filled circle.
                    // Two distinct shapes so the states never get confused.
                    isSelected -> Modifier.background(
                        MaterialTheme.colorScheme.surfaceVariant,
                        RoundedCornerShape(8.dp),
                    )
                    else -> Modifier
                },
            )
            // Double-tap creates an event; long-press is reserved for
            // dragging event chips, so the two gestures no longer collide.
            .combinedClickable(
                onClick = { onSelect(date) },
                onDoubleClick = { onCreateNew(date) },
            )
            .padding(horizontal = 1.dp, vertical = 2.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier
                    .size(24.dp)
                    .let {
                        if (isToday) {
                            it.background(MaterialTheme.colorScheme.primary, CircleShape)
                        } else {
                            it
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
            // Forecast glyph for the next two weeks, when a place is set.
            if (weather != null) {
                Text(
                    text = weather,
                    fontSize = 7.sp,
                    lineHeight = 8.sp,
                    modifier = Modifier.alpha(if (inCurrentMonth) 1f else 0.4f),
                )
            }
        }

        // Sub-line: 二十四節気 takes precedence (rare, notable, 朱), otherwise 六曜.
        // Fixed height keeps every cell — and the bars above them — aligned.
        if (reserveSubLine) {
            Box(
                modifier = Modifier.height(10.dp),
                contentAlignment = Alignment.Center,
            ) {
                val termLabel = solarTerm ?: luckyMark
                val label = termLabel ?: rokuyo
                if (label != null) {
                    Text(
                        text = label,
                        fontSize = 7.sp,
                        lineHeight = 9.sp,
                        maxLines = 1,
                        color = if (termLabel != null) {
                            MaterialTheme.colorScheme.tertiary.copy(
                                alpha = if (inCurrentMonth) 1f else 0.4f,
                            )
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant.copy(
                                alpha = if (inCurrentMonth) 0.85f else 0.4f,
                            )
                        },
                    )
                }
            }
        }

        // Space reserved for the week's multi-day bars drawn above the cells,
        // plus a small gap so day chips don't touch the bottom bar line.
        if (barLanes > 0) {
            Spacer(modifier = Modifier.height((barLanes * 14 + 4).dp))
        }

        // Events fill the cell's chip slots first, then tasks; any remainder
        // collapses into a single "+N" counter so the cell height stays stable.
        val maxChips = (MAX_EVENT_CHIPS - barLanes).coerceAtLeast(1)
        val shownEvents = events.take(maxChips)
        val shownTasks = tasks.take((maxChips - shownEvents.size).coerceAtLeast(0))
        val overflow = (events.size + tasks.size) - shownEvents.size - shownTasks.size
        Column(
            modifier = Modifier.fillMaxWidth(),
            verticalArrangement = Arrangement.spacedBy(1.dp),
        ) {
            for (event in shownEvents) {
                DraggableEventChip(
                    event = event,
                    cellDate = date,
                    dimmed = !inCurrentMonth,
                    dragState = dragState,
                    onDragMoved = onDragMoved,
                    onDrop = onDrop,
                    dropTargetOf = dropTargetOf,
                )
            }
            for (task in shownTasks) {
                TaskChip(task = task, dimmed = !inCurrentMonth)
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
        }
    }
}

/**
 * Task chip: a check glyph (done/undone) followed by the title, tinted with
 * the task's color. Mirrors the event chip so the month reads consistently,
 * while the ☑/☐ marker keeps tasks distinguishable from events.
 */
@Composable
private fun TaskChip(task: Task, dimmed: Boolean) {
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val solid = task.color
        ?.let { com.souru.koyomi.util.providerColor(it) }
        ?.let { com.souru.koyomi.util.mutedColor(it, darkTheme) }
        ?: MaterialTheme.colorScheme.primary
    val alpha = if (dimmed) 0.55f else 1f
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp)
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(solid.copy(alpha = 0.15f * alpha), RoundedCornerShape(3.dp)),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = if (task.done) "☑" else "☐",
            fontSize = 8.sp,
            lineHeight = 11.sp,
            color = solid.copy(alpha = alpha),
            modifier = Modifier.padding(start = 2.dp),
        )
        Text(
            text = task.title.ifBlank { " " },
            modifier = Modifier.padding(start = 2.dp, end = 3.dp, top = 0.5.dp, bottom = 0.5.dp),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textDecoration = if (task.done) {
                androidx.compose.ui.text.style.TextDecoration.LineThrough
            } else {
                null
            },
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (dimmed || task.done) 0.55f else 0.92f,
            ),
        )
    }
}

/**
 * FirstSeed-style multi-day marker: the title sits ON TOP of a thin colored
 * line that runs continuously across the covered days.
 */
@Composable
private fun MultiDayBar(
    segment: BarSegment,
    date: LocalDate,
    cellWidth: androidx.compose.ui.unit.Dp,
    onSelect: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val lineColor = eventColor(segment.event)
    val shape = RoundedCornerShape(
        topStart = if (segment.startsHere) 2.dp else 0.dp,
        bottomStart = if (segment.startsHere) 2.dp else 0.dp,
        topEnd = if (segment.endsHere) 2.dp else 0.dp,
        bottomEnd = if (segment.endsHere) 2.dp else 0.dp,
    )
    Column(
        modifier = modifier
            .width(cellWidth * (segment.endCol - segment.startCol + 1))
            .height(14.dp)
            .padding(horizontal = 1.dp)
            .clickable { onSelect(date) },
    ) {
        Text(
            text = segment.event.title.ifBlank { " " },
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
                .padding(horizontal = 3.dp),
            fontSize = 9.sp,
            lineHeight = 10.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
            color = lineColor,
        )
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(2.5.dp)
                .background(lineColor, shape),
        )
    }
}

@Composable
private fun DraggableEventChip(
    event: EventInstance,
    cellDate: LocalDate,
    dimmed: Boolean,
    dragState: EventDragState,
    onDragMoved: (Offset) -> Unit,
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
                        val chip = chipCoords
                        if (chip != null && chip.isAttached) {
                            dragState.event = event
                            dragState.sourceDate = cellDate
                            dragState.position = chip.localToWindow(startOffset)
                            onDragMoved(dragState.position)
                        }
                    },
                    onDrag = { change, dragAmount ->
                        change.consume()
                        if (dragState.event != null) {
                            dragState.position += dragAmount
                            onDragMoved(dragState.position)
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

/**
 * こよみの chip language: a left color notch on a lightly tinted body, with
 * theme-colored text — legible in both themes and visually our own.
 */
@Composable
private fun EventChipBody(event: EventInstance, dimmed: Boolean, ghosted: Boolean = false) {
    val solid = eventColor(event)
    val strength = when {
        ghosted -> 0.30f
        dimmed -> 0.55f
        else -> 1f
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .height(androidx.compose.foundation.layout.IntrinsicSize.Min)
            .background(
                solid.copy(alpha = 0.15f * strength),
                RoundedCornerShape(3.dp),
            ),
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .fillMaxHeight()
                .background(
                    solid.copy(alpha = strength),
                    RoundedCornerShape(topStart = 3.dp, bottomStart = 3.dp),
                ),
        )
        Text(
            text = event.title.ifBlank { " " },
            modifier = Modifier.padding(start = 4.dp, end = 3.dp, top = 0.5.dp, bottom = 0.5.dp),
            fontSize = 9.sp,
            lineHeight = 11.sp,
            maxLines = 1,
            overflow = TextOverflow.Clip,
            color = MaterialTheme.colorScheme.onSurface.copy(
                alpha = if (dimmed || ghosted) 0.55f else 0.92f,
            ),
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
fun eventColor(event: EventInstance): Color {
    // Follow the ACTIVE theme (the user can force light/dark in settings).
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    return providerColor(event.color)?.let { com.souru.koyomi.util.mutedColor(it, darkTheme) }
        ?: MaterialTheme.colorScheme.primary
}
