package com.souru.koyomi.ui.month

import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.souru.koyomi.data.holiday.JapaneseHolidays
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.ui.theme.LocalCalendarColors
import com.souru.koyomi.util.monthGridDays
import com.souru.koyomi.util.orderedWeekDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.format.TextStyle
import java.util.Locale

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

/** A fixed 6-week grid for [month]. Cell heights stay stable while paging. */
@Composable
fun MonthGrid(
    month: YearMonth,
    weekStart: DayOfWeek,
    eventsByDay: Map<LocalDate, List<EventInstance>>,
    selectedDate: LocalDate,
    onSelect: (LocalDate) -> Unit,
    onLongPress: (LocalDate) -> Unit,
    modifier: Modifier = Modifier,
) {
    val days = monthGridDays(month, weekStart)
    val today = LocalDate.now()
    Column(modifier = modifier.fillMaxSize()) {
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
                        events = eventsByDay[date].orEmpty(),
                        onSelect = onSelect,
                        onLongPress = onLongPress,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxSize(),
                    )
                }
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
    events: List<EventInstance>,
    onSelect: (LocalDate) -> Unit,
    onLongPress: (LocalDate) -> Unit,
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
                EventChip(event = event, dimmed = !inCurrentMonth)
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

@Composable
private fun EventChip(event: EventInstance, dimmed: Boolean) {
    val background = eventColor(event).copy(alpha = if (dimmed) 0.35f else 1f)
    val textColor = if (background.luminance() > 0.5f) {
        Color.Black.copy(alpha = 0.8f)
    } else {
        Color.White
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 1.dp)
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
fun eventColor(event: EventInstance): Color =
    if (event.color != 0) Color(event.color) else MaterialTheme.colorScheme.primary
