package com.souru.koyomi.ui.anniversary

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.rememberDatePickerState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.koyomi.R
import com.souru.koyomi.data.anniversary.Anniversary
import com.souru.koyomi.ui.common.KoyomiDatePickerDialog
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter
import java.util.Locale

/** 記念日: countdown list, soonest first. Tap to edit, + to add. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AnniversaryScreen(
    onBack: () -> Unit,
) {
    val viewModel: AnniversaryViewModel = viewModel(factory = AnniversaryViewModel.Factory)
    val items by viewModel.anniversaries.collectAsStateWithLifecycle()
    var editing by remember { mutableStateOf<Anniversary?>(null) }
    var showEditor by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.anniversaries_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            editing = null
                            showEditor = true
                        },
                    ) {
                        Icon(
                            Icons.Filled.Add,
                            contentDescription = stringResource(R.string.anniversary_add),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        if (items.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = stringResource(R.string.anniversaries_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        } else {
            val today = LocalDate.now()
            val sorted = remember(items) { items.sortedBy { it.daysUntil(today) } }
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(innerPadding),
            ) {
                items(sorted, key = { it.id }) { anniversary ->
                    AnniversaryRow(
                        anniversary = anniversary,
                        today = today,
                        onClick = {
                            editing = anniversary
                            showEditor = true
                        },
                        onDelete = { viewModel.delete(anniversary.id) },
                    )
                }
            }
        }
    }

    if (showEditor) {
        AnniversaryEditorDialog(
            initial = editing,
            onDismiss = { showEditor = false },
            onSave = { anniversary ->
                viewModel.save(anniversary)
                showEditor = false
            },
        )
    }
}

@Composable
private fun AnniversaryRow(
    anniversary: Anniversary,
    today: LocalDate,
    onClick: () -> Unit,
    onDelete: () -> Unit,
) {
    val pattern = stringResource(R.string.search_date_pattern)
    val formatter = remember(pattern) {
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    }
    val days = anniversary.daysUntil(today)
    val countdown = when {
        days == 0L -> stringResource(R.string.anniversary_today)
        days > 0L -> stringResource(R.string.anniversary_days_left, days)
        else -> stringResource(R.string.anniversary_days_ago, -days)
    }
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(start = 20.dp, end = 8.dp, top = 10.dp, bottom = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = anniversary.title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            val subText = buildString {
                append(anniversary.nextOccurrence(today).format(formatter))
                if (anniversary.repeatYearly) {
                    val years = anniversary.yearsOn(anniversary.nextOccurrence(today))
                    if (years > 0) append("  ${years}年目")
                }
            }
            Text(
                text = subText,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        Text(
            text = countdown,
            style = MaterialTheme.typography.titleMedium,
            color = if (days in 0..7) {
                MaterialTheme.colorScheme.tertiary
            } else {
                MaterialTheme.colorScheme.primary
            },
        )
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

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AnniversaryEditorDialog(
    initial: Anniversary?,
    onDismiss: () -> Unit,
    onSave: (Anniversary) -> Unit,
) {
    var title by remember { mutableStateOf(initial?.title.orEmpty()) }
    var date by remember { mutableStateOf(initial?.date ?: LocalDate.now()) }
    var repeatYearly by remember { mutableStateOf(initial?.repeatYearly ?: true) }
    var showDatePicker by remember { mutableStateOf(false) }
    val pattern = stringResource(R.string.search_date_pattern)
    val formatter = remember(pattern) {
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.anniversary_add else R.string.edit,
                ),
            )
        },
        text = {
            Column {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    placeholder = { Text(stringResource(R.string.anniversary_title_hint)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { showDatePicker = true }
                        .padding(vertical = 12.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.task_date),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = date.format(formatter),
                        style = MaterialTheme.typography.bodyLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = stringResource(R.string.anniversary_yearly),
                        style = MaterialTheme.typography.bodyLarge,
                        modifier = Modifier.weight(1f),
                    )
                    Switch(checked = repeatYearly, onCheckedChange = { repeatYearly = it })
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = title.isNotBlank(),
                onClick = {
                    onSave(
                        Anniversary(
                            id = initial?.id ?: 0L,
                            title = title,
                            date = date,
                            repeatYearly = repeatYearly,
                        ),
                    )
                },
            ) { Text(stringResource(R.string.save)) }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.cancel)) }
        },
    )

    if (showDatePicker) {
        val state = rememberDatePickerState(
            initialSelectedDateMillis = date.atStartOfDay(ZoneOffset.UTC)
                .toInstant().toEpochMilli(),
        )
        KoyomiDatePickerDialog(
            state = state,
            onDismiss = { showDatePicker = false },
            onConfirm = {
                state.selectedDateMillis?.let { millis ->
                    date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                }
                showDatePicker = false
            },
        )
    }
}
