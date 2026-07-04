package com.souru.koyomi.ui.month

import androidx.compose.animation.AnimatedContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.souru.koyomi.R
import com.souru.koyomi.data.holiday.JapaneseHolidays
import com.souru.koyomi.data.model.CalendarInfo
import com.souru.koyomi.data.model.EventDetails
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.ui.theme.LocalCalendarColors
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Content of the always-visible bottom sheet: the selected day's events,
 * or the tapped event's detail with edit/duplicate/delete actions.
 * All primary actions live here, in the bottom half of the screen.
 */
@Composable
fun DaySheetContent(
    date: LocalDate,
    events: List<EventInstance>,
    calendars: List<CalendarInfo>,
    detailInstance: EventInstance?,
    detail: EventDetails?,
    onEventClick: (EventInstance) -> Unit,
    onCloseDetail: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (EventInstance) -> Unit,
    onDuplicate: (EventInstance) -> Unit,
    onDeleteRequest: (EventInstance) -> Unit,
    modifier: Modifier = Modifier,
) {
    AnimatedContent(
        targetState = detailInstance,
        label = "sheet",
        modifier = modifier,
    ) { shownDetail ->
        if (shownDetail == null) {
            DayEventList(
                date = date,
                events = events,
                onEventClick = onEventClick,
                onAdd = onAdd,
            )
        } else {
            EventDetailPane(
                instance = shownDetail,
                detail = detail,
                calendars = calendars,
                onClose = onCloseDetail,
                onEdit = { onEdit(shownDetail) },
                onDuplicate = { onDuplicate(shownDetail) },
                onDelete = { onDeleteRequest(shownDetail) },
            )
        }
    }
}

@Composable
private fun DayEventList(
    date: LocalDate,
    events: List<EventInstance>,
    onEventClick: (EventInstance) -> Unit,
    onAdd: () -> Unit,
) {
    val calendarColors = LocalCalendarColors.current
    val dateFormatter = rememberPatternFormatter(R.string.sheet_date_pattern)
    val holidayName = JapaneseHolidays.nameFor(date)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 20.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text(
                text = date.format(dateFormatter),
                style = MaterialTheme.typography.titleMedium,
            )
            if (holidayName != null) {
                Text(
                    text = holidayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = calendarColors.sunday,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_event),
                )
            }
        }

        if (events.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 40.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.no_events),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(events, key = { "${it.eventId}-${it.begin}" }) { event ->
                    EventRow(date = date, event = event, onClick = { onEventClick(event) })
                }
            }
        }
    }
}

@Composable
private fun EventRow(date: LocalDate, event: EventInstance, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = rememberTimeFormatter()
    val (topLabel, bottomLabel) = when {
        event.allDay -> stringResource(R.string.all_day) to null
        event.startDate == date && event.endDate == date ->
            timeFormatter.format(Instant.ofEpochMilli(event.begin).atZone(zone)) to
                timeFormatter.format(Instant.ofEpochMilli(event.end).atZone(zone))
        event.startDate == date ->
            timeFormatter.format(Instant.ofEpochMilli(event.begin).atZone(zone)) to "→"
        event.endDate == date ->
            "→" to timeFormatter.format(Instant.ofEpochMilli(event.end).atZone(zone))
        else -> "→" to null
    }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(36.dp)
                .background(eventColor(event), RoundedCornerShape(2.dp)),
        )
        Column(
            modifier = Modifier
                .padding(start = 12.dp)
                .widthIn(min = 52.dp),
        ) {
            Text(text = topLabel, style = MaterialTheme.typography.labelMedium)
            if (bottomLabel != null) {
                Text(
                    text = bottomLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Column(modifier = Modifier.padding(start = 12.dp)) {
            Text(
                text = event.title.ifBlank { stringResource(R.string.untitled) },
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            if (!event.location.isNullOrBlank()) {
                Text(
                    text = event.location,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }
    }
}

@Composable
private fun EventDetailPane(
    instance: EventInstance,
    detail: EventDetails?,
    calendars: List<CalendarInfo>,
    onClose: () -> Unit,
    onEdit: () -> Unit,
    onDuplicate: () -> Unit,
    onDelete: () -> Unit,
) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = rememberTimeFormatter()
    val dateFormatter = rememberPatternFormatter(R.string.sheet_date_pattern)
    val calendar = calendars.find { it.id == instance.calendarId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(horizontal = 20.dp),
    ) {
        Row(verticalAlignment = Alignment.Top) {
            Column(
                modifier = Modifier
                    .weight(1f)
                    .padding(top = 4.dp),
            ) {
                Text(
                    text = instance.title.ifBlank { stringResource(R.string.untitled) },
                    style = MaterialTheme.typography.titleLarge,
                )
                val timeText = buildString {
                    append(instance.startDate.format(dateFormatter))
                    if (instance.allDay) {
                        if (instance.endDate != instance.startDate) {
                            append(" – ").append(instance.endDate.format(dateFormatter))
                        }
                        append("  ").append(stringResource(R.string.all_day))
                    } else {
                        append("  ")
                        append(timeFormatter.format(Instant.ofEpochMilli(instance.begin).atZone(zone)))
                        append(" – ")
                        if (instance.endDate != instance.startDate) {
                            append(instance.endDate.format(dateFormatter)).append(" ")
                        }
                        append(timeFormatter.format(Instant.ofEpochMilli(instance.end).atZone(zone)))
                    }
                }
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 4.dp),
                )
                if (calendar != null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Box(
                            modifier = Modifier
                                .size(10.dp)
                                .background(eventColor(instance), CircleShape),
                        )
                        Text(
                            text = calendar.displayName,
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }
            IconButton(onClick = onClose) {
                Icon(
                    imageVector = Icons.Filled.Close,
                    contentDescription = stringResource(R.string.close),
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 8.dp),
        ) {
            if (!instance.location.isNullOrBlank()) {
                DetailRow(icon = { Icon(Icons.Outlined.Place, null, Modifier.size(18.dp)) }) {
                    Text(instance.location, style = MaterialTheme.typography.bodyMedium)
                }
            }
            if (!detail?.description.isNullOrBlank()) {
                DetailRow(icon = { Icon(Icons.Outlined.Notes, null, Modifier.size(18.dp)) }) {
                    Text(detail?.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            FilledTonalButton(onClick = onEdit, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.Edit, null, Modifier.size(18.dp))
                Text(stringResource(R.string.edit), modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(onClick = onDuplicate, modifier = Modifier.weight(1f)) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(18.dp))
                Text(stringResource(R.string.duplicate), modifier = Modifier.padding(start = 6.dp))
            }
            OutlinedButton(
                onClick = onDelete,
                modifier = Modifier.weight(1f),
                colors = androidx.compose.material3.ButtonDefaults.outlinedButtonColors(
                    contentColor = MaterialTheme.colorScheme.error,
                ),
            ) {
                Icon(Icons.Outlined.Delete, null, Modifier.size(18.dp))
                Text(stringResource(R.string.delete), modifier = Modifier.padding(start = 6.dp))
            }
        }
    }
}

@Composable
private fun DetailRow(icon: @Composable () -> Unit, content: @Composable () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.padding(end = 12.dp)) { icon() }
        content()
    }
}

@Composable
private fun rememberTimeFormatter(): DateTimeFormatter {
    val locale = Locale.getDefault()
    return androidx.compose.runtime.remember(locale) {
        DateTimeFormatter.ofPattern("HH:mm", locale)
    }
}

@Composable
private fun rememberPatternFormatter(patternRes: Int): DateTimeFormatter {
    val pattern = stringResource(patternRes)
    val locale = Locale.getDefault()
    return androidx.compose.runtime.remember(pattern, locale) {
        DateTimeFormatter.ofPattern(pattern, locale)
    }
}
