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
import androidx.compose.material.icons.outlined.BookmarkAdd
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.ContentCopy
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material.icons.outlined.Edit
import androidx.compose.material.icons.outlined.Notes
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.souru.koyomi.R
import com.souru.koyomi.data.holiday.JapaneseHolidays
import com.souru.koyomi.data.model.CalendarInfo
import com.souru.koyomi.data.model.EventDetails
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.data.task.Task
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
    rokuyo: String?,
    solarTerm: String?,
    luckyDays: List<String> = emptyList(),
    weather: String? = null,
    anniversaryLabels: List<String> = emptyList(),
    diaryText: String? = null,
    diaryPast: List<Pair<Int, String>> = emptyList(),
    onEditDiary: () -> Unit = {},
    lunarDate: String?,
    moonAge: String?,
    events: List<EventInstance>,
    tasks: List<Task>,
    calendars: List<CalendarInfo>,
    detailInstance: EventInstance?,
    detail: EventDetails?,
    onEventClick: (EventInstance) -> Unit,
    onCloseDetail: () -> Unit,
    onAdd: () -> Unit,
    onEdit: (EventInstance) -> Unit,
    onDuplicate: (EventInstance) -> Unit,
    onBatchDuplicate: (EventInstance) -> Unit,
    onSaveTemplate: (EventInstance) -> Unit,
    onDeleteRequest: (EventInstance) -> Unit,
    hasTemplates: Boolean,
    onOpenTemplates: () -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
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
                rokuyo = rokuyo,
                solarTerm = solarTerm,
                luckyDays = luckyDays,
                weather = weather,
                anniversaryLabels = anniversaryLabels,
                diaryText = diaryText,
                diaryPast = diaryPast,
                onEditDiary = onEditDiary,
                lunarDate = lunarDate,
                moonAge = moonAge,
                events = events,
                tasks = tasks,
                hasTemplates = hasTemplates,
                onOpenTemplates = onOpenTemplates,
                onEventClick = onEventClick,
                onAdd = onAdd,
                onAddTask = onAddTask,
                onToggleTask = onToggleTask,
                onDeleteTask = onDeleteTask,
                onEditTask = onEditTask,
            )
        } else {
            EventDetailPane(
                instance = shownDetail,
                detail = detail,
                calendars = calendars,
                onClose = onCloseDetail,
                onEdit = { onEdit(shownDetail) },
                onDuplicate = { onDuplicate(shownDetail) },
                onBatchDuplicate = { onBatchDuplicate(shownDetail) },
                onSaveTemplate = { onSaveTemplate(shownDetail) },
                onDelete = { onDeleteRequest(shownDetail) },
            )
        }
    }
}

@OptIn(androidx.compose.foundation.layout.ExperimentalLayoutApi::class)
@Composable
private fun DayEventList(
    date: LocalDate,
    rokuyo: String?,
    solarTerm: String?,
    luckyDays: List<String>,
    weather: String?,
    anniversaryLabels: List<String>,
    diaryText: String?,
    diaryPast: List<Pair<Int, String>>,
    onEditDiary: () -> Unit,
    lunarDate: String?,
    moonAge: String?,
    events: List<EventInstance>,
    tasks: List<Task>,
    hasTemplates: Boolean,
    onOpenTemplates: () -> Unit,
    onEventClick: (EventInstance) -> Unit,
    onAdd: () -> Unit,
    onAddTask: (String) -> Unit,
    onToggleTask: (Task) -> Unit,
    onDeleteTask: (Task) -> Unit,
    onEditTask: (Task) -> Unit,
) {
    val calendarColors = LocalCalendarColors.current
    val dateFormatter = rememberPatternFormatter(R.string.sheet_date_pattern)
    val holidayName = JapaneseHolidays.nameFor(date)

    Column(modifier = Modifier.fillMaxSize()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 8.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            // Date as a tab-like pill — the sheet's own identity.
            androidx.compose.material3.Surface(
                color = MaterialTheme.colorScheme.secondaryContainer,
                contentColor = MaterialTheme.colorScheme.onSecondaryContainer,
                shape = RoundedCornerShape(50),
            ) {
                Text(
                    text = date.format(dateFormatter),
                    style = MaterialTheme.typography.titleSmall,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 6.dp),
                )
            }
            if (holidayName != null) {
                Text(
                    text = holidayName,
                    style = MaterialTheme.typography.labelMedium,
                    color = calendarColors.sunday,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            Spacer(modifier = Modifier.weight(1f))
            if (hasTemplates) {
                IconButton(onClick = onOpenTemplates) {
                    Icon(
                        imageVector = Icons.Outlined.Bookmarks,
                        contentDescription = stringResource(R.string.template_add),
                    )
                }
            }
            IconButton(onClick = onAdd) {
                Icon(
                    imageVector = Icons.Filled.Add,
                    contentDescription = stringResource(R.string.add_event),
                )
            }
        }

        // 暦のラベル (二十四節気・六曜・旧暦・月齢): only what the user enabled,
        // wrapping so any combination fits.
        val almanac = buildList {
            weather?.let { add(it to MaterialTheme.colorScheme.onSurfaceVariant) }
            for (label in anniversaryLabels) add("🎉$label" to MaterialTheme.colorScheme.tertiary)
            solarTerm?.let { add(it to MaterialTheme.colorScheme.tertiary) }
            for (lucky in luckyDays) add(lucky to MaterialTheme.colorScheme.tertiary)
            rokuyo?.let { add(it to MaterialTheme.colorScheme.onSurfaceVariant) }
            lunarDate?.let { add(it to MaterialTheme.colorScheme.onSurfaceVariant) }
            moonAge?.let { add(it to MaterialTheme.colorScheme.onSurfaceVariant) }
        }
        if (almanac.isNotEmpty()) {
            androidx.compose.foundation.layout.FlowRow(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 16.dp, end = 16.dp, top = 2.dp, bottom = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                for ((label, color) in almanac) {
                    Text(
                        text = label,
                        style = MaterialTheme.typography.labelMedium,
                        color = color,
                    )
                }
            }
        }

        LazyColumn(modifier = Modifier.fillMaxSize()) {
            if (events.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 24.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        Text(
                            text = stringResource(R.string.no_events),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    }
                }
            } else {
                items(events, key = { "${it.eventId}-${it.begin}" }) { event ->
                    EventRow(date = date, event = event, onClick = { onEventClick(event) })
                }
            }

            item(key = "task-header") {
                Text(
                    text = stringResource(R.string.tasks),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
                )
            }
            items(tasks, key = { "task-${it.id}" }) { task ->
                TaskRow(
                    task = task,
                    onToggle = { onToggleTask(task) },
                    onDelete = { onDeleteTask(task) },
                    onEdit = { onEditTask(task) },
                )
            }
            item(key = "task-add") {
                AddTaskRow(onAddTask = onAddTask)
            }
            if (hasTemplates) {
                item(key = "template-add") {
                    androidx.compose.material3.TextButton(
                        onClick = onOpenTemplates,
                        modifier = Modifier.padding(start = 8.dp),
                    ) {
                        Icon(
                            imageVector = Icons.Outlined.ContentCopy,
                            contentDescription = null,
                            modifier = Modifier.size(16.dp),
                        )
                        Text(
                            text = stringResource(R.string.template_add),
                            modifier = Modifier.padding(start = 6.dp),
                        )
                    }
                }
            }

            // ひとこと日記 + 過去の今日.
            item(key = "diary-header") {
                Text(
                    text = stringResource(R.string.diary_title),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.padding(start = 20.dp, top = 12.dp, bottom = 2.dp),
                )
            }
            item(key = "diary-entry") {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onEditDiary)
                        .padding(horizontal = 20.dp, vertical = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = diaryText ?: stringResource(R.string.diary_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = if (diaryText != null) {
                            MaterialTheme.colorScheme.onSurface
                        } else {
                            MaterialTheme.colorScheme.onSurfaceVariant
                        },
                        modifier = Modifier.weight(1f),
                    )
                    Icon(
                        imageVector = Icons.Outlined.Edit,
                        contentDescription = stringResource(R.string.edit),
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
            items(diaryPast, key = { "diary-past-${it.first}" }) { (yearsAgo, text) ->
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 4.dp),
                ) {
                    Text(
                        text = if (yearsAgo == 1) {
                            stringResource(R.string.diary_last_year)
                        } else {
                            stringResource(R.string.diary_years_ago, yearsAgo)
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.tertiary,
                    )
                    Text(
                        text = text,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

@Composable
private fun TaskRow(
    task: Task,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    // Tapping the row opens the event editor (time / notification / color);
    // the checkbox toggles completion.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onEdit)
            .padding(start = 8.dp, end = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Checkbox(checked = task.done, onCheckedChange = { onToggle() })
        task.color?.let { colorInt ->
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .background(
                        com.souru.koyomi.util.providerColor(colorInt)
                            ?: MaterialTheme.colorScheme.primary,
                        CircleShape,
                    ),
            )
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = if (task.color != null) 8.dp else 0.dp),
        ) {
            Text(
                text = task.title,
                style = MaterialTheme.typography.bodyLarge,
                textDecoration = if (task.done) TextDecoration.LineThrough else null,
                color = if (task.done) {
                    MaterialTheme.colorScheme.onSurfaceVariant
                } else {
                    MaterialTheme.colorScheme.onSurface
                },
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            task.time?.let { time ->
                Text(
                    text = rememberTimeFormatter().format(time),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        IconButton(onClick = onDelete) {
            Icon(
                imageVector = Icons.Outlined.Delete,
                contentDescription = stringResource(R.string.delete),
                modifier = Modifier.size(18.dp),
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun AddTaskRow(onAddTask: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onAddTask(text)
            text = ""
        }
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 12.dp, end = 8.dp, bottom = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        TextField(
            value = text,
            onValueChange = { text = it },
            placeholder = { Text(stringResource(R.string.add_task_hint)) },
            singleLine = true,
            keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
            keyboardActions = KeyboardActions(onDone = { submit() }),
            colors = TextFieldDefaults.colors(
                focusedContainerColor = Color.Transparent,
                unfocusedContainerColor = Color.Transparent,
                focusedIndicatorColor = Color.Transparent,
                unfocusedIndicatorColor = Color.Transparent,
            ),
            modifier = Modifier.weight(1f),
        )
        IconButton(onClick = ::submit, enabled = text.isNotBlank()) {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = stringResource(R.string.add_task_hint),
            )
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
    onBatchDuplicate: () -> Unit,
    onSaveTemplate: () -> Unit,
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

        // Actions right under the header so they are visible at the sheet's
        // peek height — no scrolling needed to edit or delete.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 10.dp, bottom = 4.dp),
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

        // Secondary actions: 複数日への一括複製 と テンプレート保存。
        Row(verticalAlignment = Alignment.CenterVertically) {
            androidx.compose.material3.TextButton(onClick = onBatchDuplicate) {
                Icon(Icons.Outlined.ContentCopy, null, Modifier.size(16.dp))
                Text(
                    stringResource(R.string.batch_copy),
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
            androidx.compose.material3.TextButton(onClick = onSaveTemplate) {
                Icon(Icons.Outlined.BookmarkAdd, null, Modifier.size(16.dp))
                Text(
                    stringResource(R.string.template_save),
                    modifier = Modifier.padding(start = 6.dp),
                    style = MaterialTheme.typography.labelLarge,
                )
            }
        }

        Column(
            modifier = Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(top = 4.dp, bottom = 12.dp),
        ) {
            if (!instance.location.isNullOrBlank()) {
                val context = androidx.compose.ui.platform.LocalContext.current
                DetailRow(
                    icon = { Icon(Icons.Outlined.Place, null, Modifier.size(18.dp)) },
                    modifier = Modifier.clickable {
                        openLocationInMaps(context, instance.location)
                    },
                ) {
                    Text(
                        text = instance.location,
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.primary,
                        textDecoration = TextDecoration.Underline,
                    )
                }
            }
            if (!detail?.description.isNullOrBlank()) {
                DetailRow(icon = { Icon(Icons.Outlined.Notes, null, Modifier.size(18.dp)) }) {
                    Text(detail?.description.orEmpty(), style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
    }
}

@Composable
private fun DetailRow(
    icon: @Composable () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(modifier = Modifier.padding(end = 12.dp)) { icon() }
        content()
    }
}

/** Opens [location] in a maps app (Google Maps when installed), or the browser. */
private fun openLocationInMaps(context: android.content.Context, location: String) {
    val encoded = android.net.Uri.encode(location)
    val geoIntent = android.content.Intent(
        android.content.Intent.ACTION_VIEW,
        android.net.Uri.parse("geo:0,0?q=$encoded"),
    )
    runCatching { context.startActivity(geoIntent) }.onFailure {
        // No maps app — fall back to Google Maps in the browser.
        runCatching {
            context.startActivity(
                android.content.Intent(
                    android.content.Intent.ACTION_VIEW,
                    android.net.Uri.parse("https://www.google.com/maps/search/?api=1&query=$encoded"),
                ),
            )
        }
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
