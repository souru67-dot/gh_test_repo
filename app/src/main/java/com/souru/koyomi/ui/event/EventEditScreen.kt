package com.souru.koyomi.ui.event

import android.text.format.DateFormat
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
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.outlined.CalendarMonth
import androidx.compose.material.icons.outlined.Palette
import androidx.compose.material.icons.outlined.NotificationsNone
import androidx.compose.material.icons.outlined.Place
import androidx.compose.material.icons.outlined.Repeat
import androidx.compose.material.icons.outlined.Schedule
import androidx.compose.material.icons.outlined.Subject
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TimePicker
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.material3.rememberTimePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.koyomi.R
import com.souru.koyomi.ui.common.KoyomiDatePickerDialog
import com.souru.koyomi.util.RepeatFreq
import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

private val ReminderChoices: List<Int?> = listOf(null, 0, 5, 10, 15, 30, 60, 1440)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EventEditScreen(onClose: () -> Unit) {
    val viewModel: EventEditViewModel = viewModel(factory = EventEditViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    var showScopeDialog by remember { mutableStateOf(false) }

    LaunchedEffect(state.saved) {
        if (state.saved) onClose()
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(if (state.isNew) R.string.new_event else R.string.edit_event),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onClose) {
                        Icon(Icons.Filled.Close, contentDescription = stringResource(R.string.close))
                    }
                },
            )
        },
        bottomBar = {
            // The main action sits at the bottom, in thumb reach.
            Surface {
                Button(
                    onClick = {
                        // Editing a recurring event: ask how far the change reaches.
                        if (state.isRecurring && !state.isNew) {
                            showScopeDialog = true
                        } else {
                            viewModel.save()
                        }
                    },
                    enabled = !state.loading && !state.saving && when (state.mode) {
                        EditorMode.TASK -> state.title.isNotBlank()
                        EditorMode.EVENT ->
                            state.calendars.find { it.id == state.calendarId }
                                ?.isWritable == true
                    },
                    modifier = Modifier
                        .navigationBarsPadding()
                        .fillMaxWidth()
                        .padding(horizontal = 20.dp, vertical = 12.dp)
                        .height(52.dp),
                ) {
                    Text(stringResource(R.string.save))
                }
            }
        },
    ) { innerPadding ->
        if (state.loading) return@Scaffold

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState()),
        ) {
            // New items can be either an event or a task.
            if (!state.modeLocked) {
                androidx.compose.material3.TabRow(
                    selectedTabIndex = if (state.mode == EditorMode.EVENT) 0 else 1,
                ) {
                    androidx.compose.material3.Tab(
                        selected = state.mode == EditorMode.EVENT,
                        onClick = { viewModel.setMode(EditorMode.EVENT) },
                        text = { Text(stringResource(R.string.event_label)) },
                    )
                    androidx.compose.material3.Tab(
                        selected = state.mode == EditorMode.TASK,
                        onClick = { viewModel.setMode(EditorMode.TASK) },
                        text = { Text(stringResource(R.string.tasks)) },
                    )
                }
            }
            TextField(
                value = state.title,
                onValueChange = viewModel::setTitle,
                placeholder = {
                    Text(
                        stringResource(R.string.title_hint),
                        style = MaterialTheme.typography.headlineSmall,
                    )
                },
                textStyle = MaterialTheme.typography.headlineSmall,
                singleLine = true,
                colors = transparentTextFieldColors(),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp),
            )
            HorizontalDivider(color = MaterialTheme.colorScheme.outlineVariant)

            if (state.mode == EditorMode.TASK) {
                TaskFields(state = state, viewModel = viewModel)
                Spacer(modifier = Modifier.height(24.dp))
                return@Column
            }

            // All-day toggle
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 20.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Icon(
                    Icons.Outlined.Schedule,
                    contentDescription = null,
                    modifier = Modifier.size(20.dp),
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    stringResource(R.string.all_day),
                    modifier = Modifier
                        .padding(start = 16.dp)
                        .weight(1f),
                    style = MaterialTheme.typography.bodyLarge,
                )
                Switch(checked = state.allDay, onCheckedChange = viewModel::setAllDay)
            }

            DateTimeRow(
                label = stringResource(R.string.starts),
                value = state.start,
                showTime = !state.allDay,
                onChange = viewModel::setStart,
            )
            DateTimeRow(
                label = stringResource(R.string.ends),
                value = state.end,
                showTime = !state.allDay,
                onChange = viewModel::setEnd,
            )
            HorizontalDivider(
                color = MaterialTheme.colorScheme.outlineVariant,
                modifier = Modifier.padding(vertical = 4.dp),
            )

            // Calendar picker — every calendar the device knows; read-only
            // ones (e.g. holiday subscriptions) are visible but disabled.
            if (state.calendars.none { it.isWritable }) {
                Text(
                    text = stringResource(R.string.no_writable_calendar),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error,
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 8.dp),
                )
            }
            if (state.calendars.isNotEmpty()) {
                val selected = state.calendars.find { it.id == state.calendarId }
                PickerRow(
                    icon = { Icon(Icons.Outlined.CalendarMonth, null, Modifier.size(20.dp)) },
                    label = stringResource(R.string.calendar),
                    value = selected?.displayName.orEmpty(),
                    valueTint = selected?.let { com.souru.koyomi.util.providerColor(it.color) },
                ) { close ->
                    state.calendars.forEach { calendar ->
                        DropdownMenuItem(
                            enabled = calendar.isWritable,
                            leadingIcon = {
                                Box(
                                    modifier = Modifier
                                        .size(12.dp)
                                        .background(
                                            com.souru.koyomi.util.providerColor(calendar.color)
                                                ?: MaterialTheme.colorScheme.primary,
                                            androidx.compose.foundation.shape.CircleShape,
                                        ),
                                )
                            },
                            text = {
                                Column {
                                    Text(calendar.displayName)
                                    Text(
                                        text = if (calendar.isWritable) {
                                            calendar.accountName
                                        } else {
                                            calendar.accountName + " · " +
                                                stringResource(R.string.read_only)
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                                    )
                                }
                            },
                            onClick = {
                                viewModel.setCalendar(calendar.id)
                                close()
                            },
                        )
                    }
                }
            }

            // Location
            IconTextField(
                icon = { Icon(Icons.Outlined.Place, null, Modifier.size(20.dp)) },
                value = state.location,
                onValueChange = viewModel::setLocation,
                placeholder = stringResource(R.string.location),
            )

            // Event color (Google user palette or standard fallback)
            EventColorRow(
                colors = state.eventColors,
                selected = state.eventColor,
                calendarColor = state.calendars.find { it.id == state.calendarId }
                    ?.let { com.souru.koyomi.util.providerColor(it.color) },
                onSelect = viewModel::setEventColor,
            )

            // Reminder
            PickerRow(
                icon = { Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(20.dp)) },
                label = stringResource(R.string.notification),
                value = reminderLabel(state.reminderMinutes),
            ) { close ->
                ReminderChoices.forEach { minutes ->
                    DropdownMenuItem(
                        text = { Text(reminderLabel(minutes)) },
                        onClick = {
                            viewModel.setReminder(minutes)
                            close()
                        },
                    )
                }
            }

            // Repeat
            PickerRow(
                icon = { Icon(Icons.Outlined.Repeat, null, Modifier.size(20.dp)) },
                label = stringResource(R.string.repeat),
                value = repeatLabel(state.repeat.freq),
            ) { close ->
                listOf(
                    RepeatFreq.NONE, RepeatFreq.DAILY, RepeatFreq.WEEKLY,
                    RepeatFreq.MONTHLY, RepeatFreq.YEARLY,
                ).forEach { freq ->
                    DropdownMenuItem(
                        text = { Text(repeatLabel(freq)) },
                        onClick = {
                            viewModel.setRepeatFreq(freq)
                            close()
                        },
                    )
                }
            }

            if (state.repeat.freq == RepeatFreq.WEEKLY) {
                WeekdayChipsRow(
                    selected = state.repeat.byDays,
                    onToggle = viewModel::toggleRepeatDay,
                )
            }
            if (state.repeat.freq in setOf(
                    RepeatFreq.DAILY, RepeatFreq.WEEKLY, RepeatFreq.MONTHLY, RepeatFreq.YEARLY,
                )
            ) {
                RepeatUntilRow(
                    until = state.repeat.until,
                    onChange = viewModel::setRepeatUntil,
                )
            }

            // Memo
            IconTextField(
                icon = { Icon(Icons.Outlined.Subject, null, Modifier.size(20.dp)) },
                value = state.description,
                onValueChange = viewModel::setDescription,
                placeholder = stringResource(R.string.memo),
                singleLine = false,
            )
            Spacer(modifier = Modifier.height(24.dp))
        }
    }

    if (showScopeDialog) {
        AlertDialog(
            onDismissRequest = { showScopeDialog = false },
            title = { Text(stringResource(R.string.save_scope_title)) },
            text = { Text(stringResource(R.string.save_scope_message)) },
            confirmButton = {
                Column(horizontalAlignment = Alignment.End) {
                    TextButton(
                        onClick = {
                            showScopeDialog = false
                            viewModel.save(SaveScope.THIS_ONLY)
                        },
                    ) { Text(stringResource(R.string.save_this_occurrence)) }
                    TextButton(
                        onClick = {
                            showScopeDialog = false
                            viewModel.save(SaveScope.ALL)
                        },
                    ) { Text(stringResource(R.string.save_all_occurrences)) }
                    TextButton(onClick = { showScopeDialog = false }) {
                        Text(stringResource(R.string.cancel))
                    }
                }
            },
        )
    }
}

@Composable
private fun WeekdayChipsRow(
    selected: Set<java.time.DayOfWeek>,
    onToggle: (java.time.DayOfWeek) -> Unit,
) {
    val locale = Locale.getDefault()
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 20.dp, top = 2.dp, bottom = 2.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        for (day in java.time.DayOfWeek.entries) {
            FilterChip(
                selected = day in selected,
                onClick = { onToggle(day) },
                label = {
                    Text(
                        day.getDisplayName(TextStyle.NARROW, locale),
                        style = MaterialTheme.typography.labelSmall,
                    )
                },
                modifier = Modifier.weight(1f),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RepeatUntilRow(
    until: LocalDate?,
    onChange: (LocalDate?) -> Unit,
) {
    var showPicker by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val formatter = remember(locale) {
        DateTimeFormatter.ofPattern(
            if (locale.language == "ja") "yyyy年M月d日" else "MMM d, yyyy",
            locale,
        )
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(start = 56.dp, end = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = stringResource(R.string.repeat_until),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { showPicker = true }) {
            Text(until?.format(formatter) ?: stringResource(R.string.repeat_until_none))
        }
        if (until != null) {
            IconButton(onClick = { onChange(null) }) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = stringResource(R.string.repeat_until_clear),
                    modifier = Modifier.size(16.dp),
                )
            }
        }
    }

    if (showPicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = (until ?: LocalDate.now().plusMonths(1))
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        KoyomiDatePickerDialog(
            state = pickerState,
            onDismiss = { showPicker = false },
            onConfirm = {
                pickerState.selectedDateMillis?.let { millis ->
                    onChange(
                        Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate(),
                    )
                }
                showPicker = false
            },
        )
    }
}

/** Task mode: date, optional time, color and notification. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun TaskFields(state: EditorUiState, viewModel: EventEditViewModel) {
    val context = LocalContext.current
    var showTimePicker by remember { mutableStateOf(false) }

    // Due date (reuses the date half of DateTimeRow).
    DateTimeRow(
        label = stringResource(R.string.task_date),
        value = state.start,
        showTime = false,
        onChange = viewModel::setStart,
    )

    // Optional time of day.
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Schedule,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.task_time),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        )
        val taskTime = state.taskTime
        if (taskTime != null) {
            TextButton(onClick = { showTimePicker = true }) {
                Text(
                    taskTime.format(
                        DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()),
                    ),
                )
            }
        }
        Switch(
            checked = state.taskTime != null,
            onCheckedChange = { enabled ->
                if (enabled) {
                    viewModel.setTaskTime(java.time.LocalTime.of(9, 0))
                    showTimePicker = true
                } else {
                    viewModel.setTaskTime(null)
                }
            },
        )
    }

    // Color
    EventColorRow(
        colors = state.eventColors,
        selected = state.eventColor,
        calendarColor = null,
        onSelect = viewModel::setEventColor,
    )

    // Notification
    PickerRow(
        icon = { Icon(Icons.Outlined.NotificationsNone, null, Modifier.size(20.dp)) },
        label = stringResource(R.string.notification),
        value = reminderLabel(state.reminderMinutes),
    ) { close ->
        ReminderChoices.forEach { minutes ->
            DropdownMenuItem(
                text = { Text(reminderLabel(minutes)) },
                onClick = {
                    viewModel.setReminder(minutes)
                    close()
                },
            )
        }
    }

    if (showTimePicker) {
        val current = state.taskTime ?: java.time.LocalTime.of(9, 0)
        val pickerState = rememberTimePickerState(
            initialHour = current.hour,
            initialMinute = current.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.select_time)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        viewModel.setTaskTime(
                            java.time.LocalTime.of(pickerState.hour, pickerState.minute),
                        )
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DateTimeRow(
    label: String,
    value: LocalDateTime,
    showTime: Boolean,
    onChange: (LocalDateTime) -> Unit,
) {
    val context = LocalContext.current
    var showDatePicker by remember { mutableStateOf(false) }
    var showTimePicker by remember { mutableStateOf(false) }
    val locale = Locale.getDefault()
    val dateFormatter = remember(locale) {
        DateTimeFormatter.ofPattern(
            if (locale.language == "ja") "yyyy年M月d日(EEE)" else "EEE, MMM d, yyyy",
            locale,
        )
    }
    val timeFormatter = remember(locale) { DateTimeFormatter.ofPattern("HH:mm", locale) }

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier.weight(1f),
        )
        TextButton(onClick = { showDatePicker = true }) {
            Text(value.toLocalDate().format(dateFormatter))
        }
        if (showTime) {
            TextButton(onClick = { showTimePicker = true }) {
                Text(value.toLocalTime().format(timeFormatter))
            }
        }
    }

    if (showDatePicker) {
        val pickerState = rememberDatePickerState(
            initialSelectedDateMillis = value.toLocalDate()
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
        )
        KoyomiDatePickerDialog(
            state = pickerState,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                pickerState.selectedDateMillis?.let { millis ->
                    val date = Instant.ofEpochMilli(millis)
                        .atZone(ZoneOffset.UTC).toLocalDate()
                    onChange(LocalDateTime.of(date, value.toLocalTime()))
                }
                showDatePicker = false
            },
        )
    }

    if (showTimePicker) {
        val pickerState = rememberTimePickerState(
            initialHour = value.hour,
            initialMinute = value.minute,
            is24Hour = DateFormat.is24HourFormat(context),
        )
        AlertDialog(
            onDismissRequest = { showTimePicker = false },
            title = { Text(stringResource(R.string.select_time)) },
            text = { TimePicker(state = pickerState) },
            confirmButton = {
                TextButton(
                    onClick = {
                        onChange(
                            LocalDateTime.of(
                                value.toLocalDate(),
                                java.time.LocalTime.of(pickerState.hour, pickerState.minute),
                            ),
                        )
                        showTimePicker = false
                    },
                ) { Text(stringResource(R.string.ok)) }
            },
            dismissButton = {
                TextButton(onClick = { showTimePicker = false }) {
                    Text(stringResource(R.string.cancel))
                }
            },
        )
    }
}

/** Color picker: swatch grid in a dropdown, first entry = calendar default. */
@Composable
private fun EventColorRow(
    colors: List<com.souru.koyomi.data.model.EventColor>,
    selected: com.souru.koyomi.data.model.EventColor?,
    calendarColor: Color?,
    onSelect: (com.souru.koyomi.data.model.EventColor?) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Outlined.Palette,
            contentDescription = null,
            modifier = Modifier.size(20.dp),
            tint = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(
            text = stringResource(R.string.event_color),
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        )
        Column {
            TextButton(onClick = { expanded = true }) {
                // No explicit event color: preview the calendar's own color so
                // "仕事=青" is visible at a glance, matching Google Calendar.
                val swatchDescription = stringResource(R.string.chosen_color)
                Box(
                    modifier = Modifier
                        .size(20.dp)
                        .background(
                            selected?.let { com.souru.koyomi.util.providerColor(it.color) }
                                ?: calendarColor
                                ?: MaterialTheme.colorScheme.primary,
                            androidx.compose.foundation.shape.CircleShape,
                        )
                        .semantics { contentDescription = swatchDescription },
                )
                if (selected == null) {
                    Text(
                        stringResource(R.string.event_color_default),
                        modifier = Modifier.padding(start = 8.dp),
                    )
                }
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                DropdownMenuItem(
                    leadingIcon = {
                        Box(
                            modifier = Modifier
                                .size(16.dp)
                                .background(
                                    calendarColor ?: MaterialTheme.colorScheme.primary,
                                    androidx.compose.foundation.shape.CircleShape,
                                ),
                        )
                    },
                    text = { Text(stringResource(R.string.event_color_default)) },
                    onClick = {
                        onSelect(null)
                        expanded = false
                    },
                )
                colors.chunked(4).forEach { rowColors ->
                    Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)) {
                        rowColors.forEach { color ->
                            val isSelected = selected?.color == color.color
                            Box(
                                modifier = Modifier
                                    .padding(4.dp)
                                    .size(32.dp)
                                    .background(
                                        com.souru.koyomi.util.providerColor(color.color)
                                            ?: MaterialTheme.colorScheme.primary,
                                        androidx.compose.foundation.shape.CircleShape,
                                    )
                                    .clickable {
                                        onSelect(color)
                                        expanded = false
                                    },
                                contentAlignment = Alignment.Center,
                            ) {
                                if (isSelected) {
                                    Icon(
                                        Icons.Filled.Check,
                                        contentDescription = null,
                                        tint = Color.White,
                                        modifier = Modifier.size(16.dp),
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

/** A tappable "label ... value" row that opens a dropdown of choices. */
@Composable
private fun PickerRow(
    icon: @Composable () -> Unit,
    label: String,
    value: String,
    valueTint: Color? = null,
    menuContent: @Composable (close: () -> Unit) -> Unit,
) {
    var expanded by remember { mutableStateOf(false) }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            modifier = Modifier
                .padding(start = 16.dp)
                .weight(1f),
        )
        Column {
            TextButton(onClick = { expanded = true }) {
                Text(text = value, color = valueTint ?: MaterialTheme.colorScheme.primary)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                menuContent { expanded = false }
            }
        }
    }
}

@Composable
private fun IconTextField(
    icon: @Composable () -> Unit,
    value: String,
    onValueChange: (String) -> Unit,
    placeholder: String,
    singleLine: Boolean = true,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        icon()
        TextField(
            value = value,
            onValueChange = onValueChange,
            placeholder = { Text(placeholder) },
            singleLine = singleLine,
            colors = transparentTextFieldColors(),
            modifier = Modifier
                .weight(1f)
                .padding(start = 4.dp),
        )
    }
}

@Composable
private fun transparentTextFieldColors() = TextFieldDefaults.colors(
    focusedContainerColor = Color.Transparent,
    unfocusedContainerColor = Color.Transparent,
    disabledContainerColor = Color.Transparent,
    focusedIndicatorColor = Color.Transparent,
    unfocusedIndicatorColor = Color.Transparent,
)

@Composable
private fun reminderLabel(minutes: Int?): String = when {
    minutes == null -> stringResource(R.string.reminder_none)
    minutes == 0 -> stringResource(R.string.reminder_at_time)
    minutes < 60 -> stringResource(R.string.reminder_minutes, minutes)
    minutes < 1440 -> stringResource(R.string.reminder_hours, minutes / 60)
    else -> stringResource(R.string.reminder_days, minutes / 1440)
}

@Composable
private fun repeatLabel(freq: RepeatFreq): String = when (freq) {
    RepeatFreq.NONE -> stringResource(R.string.repeat_none)
    RepeatFreq.DAILY -> stringResource(R.string.repeat_daily)
    RepeatFreq.WEEKLY -> stringResource(R.string.repeat_weekly)
    RepeatFreq.MONTHLY -> stringResource(R.string.repeat_monthly)
    RepeatFreq.YEARLY -> stringResource(R.string.repeat_yearly)
    RepeatFreq.CUSTOM -> stringResource(R.string.repeat_custom)
}
