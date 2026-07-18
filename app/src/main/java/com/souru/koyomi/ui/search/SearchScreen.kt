package com.souru.koyomi.ui.search

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextField
import androidx.compose.material3.TextFieldDefaults
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.koyomi.R
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.util.mutedColor
import com.souru.koyomi.util.providerColor
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * Search + agenda: opens on everything coming up, narrows as you type.
 * Tapping a result opens it in the editor.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SearchScreen(
    onBack: () -> Unit,
    onOpenEvent: (eventId: Long, beginMs: Long, endMs: Long) -> Unit,
) {
    val viewModel: SearchViewModel = viewModel(factory = SearchViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { focusRequester.requestFocus() }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    TextField(
                        value = query,
                        onValueChange = viewModel::setQuery,
                        placeholder = { Text(stringResource(R.string.search_hint)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        colors = TextFieldDefaults.colors(
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent,
                            focusedIndicatorColor = Color.Transparent,
                            unfocusedIndicatorColor = Color.Transparent,
                        ),
                        trailingIcon = {
                            if (query.isNotEmpty()) {
                                IconButton(onClick = { viewModel.setQuery("") }) {
                                    Icon(
                                        Icons.Filled.Close,
                                        contentDescription = stringResource(R.string.close),
                                    )
                                }
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .focusRequester(focusRequester),
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.close),
                        )
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (state.isAgenda) {
                Text(
                    text = stringResource(R.string.agenda_upcoming),
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.tertiary,
                    modifier = Modifier.padding(start = 20.dp, top = 8.dp, bottom = 2.dp),
                )
            }
            if (state.results.isEmpty()) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = if (state.searched) {
                            stringResource(R.string.search_no_results)
                        } else {
                            stringResource(R.string.no_events)
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                SearchResultList(results = state.results, onOpenEvent = onOpenEvent)
            }
        }
    }
}

@Composable
private fun SearchResultList(
    results: List<EventInstance>,
    onOpenEvent: (eventId: Long, beginMs: Long, endMs: Long) -> Unit,
) {
    val grouped = remember(results) { results.groupBy { it.startDate } }
    LazyColumn(modifier = Modifier.fillMaxSize()) {
        grouped.forEach { (date, events) ->
            item(key = "header-${date.toEpochDay()}") {
                DateHeader(date)
            }
            items(events, key = { "${it.eventId}-${it.begin}" }) { event ->
                ResultRow(
                    event = event,
                    onClick = { onOpenEvent(event.eventId, event.begin, event.end) },
                )
            }
        }
    }
}

@Composable
private fun DateHeader(date: LocalDate) {
    val pattern = stringResource(R.string.search_date_pattern)
    val formatter = remember(pattern) {
        DateTimeFormatter.ofPattern(pattern, Locale.getDefault())
    }
    Text(
        text = date.format(formatter),
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp),
    )
}

@Composable
private fun ResultRow(event: EventInstance, onClick: () -> Unit) {
    val zone = ZoneId.systemDefault()
    val timeFormatter = com.souru.koyomi.util.rememberDeviceTimeFormatter()
    val darkTheme = MaterialTheme.colorScheme.background.luminance() < 0.5f
    val color = providerColor(event.color)?.let { mutedColor(it, darkTheme) }
        ?: MaterialTheme.colorScheme.primary

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 20.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(4.dp)
                .height(34.dp)
                .background(color, RoundedCornerShape(2.dp)),
        )
        Text(
            text = if (event.allDay) {
                stringResource(R.string.all_day)
            } else {
                timeFormatter.format(Instant.ofEpochMilli(event.begin).atZone(zone))
            },
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier
                .padding(start = 12.dp)
                .widthIn(min = 44.dp),
        )
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
