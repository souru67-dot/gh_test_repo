package com.souru.koyomi.ui.tasks

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.outlined.Delete
import androidx.compose.material3.Checkbox
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
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.koyomi.R
import com.souru.koyomi.data.task.Task
import java.time.LocalDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/**
 * The full ToDo list: overdue, today, upcoming and completed tasks across
 * all dates. Quick-add creates a task for today; tapping a row opens the
 * editor for date/time/color/reminder.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun TasksScreen(
    onBack: () -> Unit,
    onEditTask: (taskId: Long) -> Unit,
) {
    val viewModel: TasksViewModel = viewModel(factory = TasksViewModel.Factory)
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.tasks_list)) },
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
            AddTaskField(onAdd = viewModel::addTask)
            if (state.loaded && state.isEmpty) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.tasks_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            } else {
                LazyColumn(modifier = Modifier.fillMaxSize()) {
                    taskSection(
                        key = "overdue",
                        title = { stringResource(R.string.tasks_overdue) },
                        titleColor = { MaterialTheme.colorScheme.tertiary },
                        tasks = state.overdue,
                        showDate = true,
                        viewModel = viewModel,
                        onEditTask = onEditTask,
                    )
                    taskSection(
                        key = "today",
                        title = { stringResource(R.string.back_to_today) },
                        titleColor = { MaterialTheme.colorScheme.primary },
                        tasks = state.today,
                        showDate = false,
                        viewModel = viewModel,
                        onEditTask = onEditTask,
                    )
                    taskSection(
                        key = "upcoming",
                        title = { stringResource(R.string.tasks_upcoming) },
                        titleColor = { MaterialTheme.colorScheme.primary },
                        tasks = state.upcoming,
                        showDate = true,
                        viewModel = viewModel,
                        onEditTask = onEditTask,
                    )
                    taskSection(
                        key = "done",
                        title = { stringResource(R.string.tasks_done) },
                        titleColor = { MaterialTheme.colorScheme.onSurfaceVariant },
                        tasks = state.done,
                        showDate = true,
                        viewModel = viewModel,
                        onEditTask = onEditTask,
                    )
                }
            }
        }
    }
}

private fun LazyListScope.taskSection(
    key: String,
    title: @Composable () -> String,
    titleColor: @Composable () -> Color,
    tasks: List<Task>,
    showDate: Boolean,
    viewModel: TasksViewModel,
    onEditTask: (taskId: Long) -> Unit,
) {
    if (tasks.isEmpty()) return
    item(key = "header-$key") {
        Text(
            text = title(),
            style = MaterialTheme.typography.labelLarge,
            color = titleColor(),
            modifier = Modifier.padding(start = 20.dp, top = 14.dp, bottom = 2.dp),
        )
    }
    items(tasks, key = { "$key-${it.id}" }) { task ->
        TaskListRow(
            task = task,
            showDate = showDate,
            onToggle = { viewModel.setDone(task.id, !task.done) },
            onDelete = { viewModel.deleteTask(task.id) },
            onEdit = { onEditTask(task.id) },
        )
    }
}

@Composable
private fun TaskListRow(
    task: Task,
    showDate: Boolean,
    onToggle: () -> Unit,
    onDelete: () -> Unit,
    onEdit: () -> Unit,
) {
    val datePattern = stringResource(R.string.sheet_date_pattern)
    val dateFormatter = remember(datePattern) {
        DateTimeFormatter.ofPattern(datePattern, Locale.getDefault())
    }
    val timeFormatter = remember { DateTimeFormatter.ofPattern("HH:mm", Locale.getDefault()) }
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
            val subText = listOfNotNull(
                if (showDate) task.dueDate.format(dateFormatter) else null,
                task.time?.let { timeFormatter.format(it) },
            ).joinToString("  ")
            if (subText.isNotEmpty()) {
                Text(
                    text = subText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (!task.done && task.dueDate.isBefore(LocalDate.now())) {
                        MaterialTheme.colorScheme.tertiary
                    } else {
                        MaterialTheme.colorScheme.onSurfaceVariant
                    },
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
private fun AddTaskField(onAdd: (String) -> Unit) {
    var text by remember { mutableStateOf("") }
    fun submit() {
        if (text.isNotBlank()) {
            onAdd(text)
            text = ""
        }
    }
    TextField(
        value = text,
        onValueChange = { text = it },
        placeholder = { Text(stringResource(R.string.add_task_hint)) },
        singleLine = true,
        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
        keyboardActions = KeyboardActions(onDone = { submit() }),
        leadingIcon = {
            Icon(
                imageVector = Icons.Filled.Add,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        },
        trailingIcon = {
            if (text.isNotBlank()) {
                IconButton(onClick = ::submit) {
                    Icon(
                        imageVector = Icons.Filled.Add,
                        contentDescription = stringResource(R.string.add_task_hint),
                    )
                }
            }
        },
        colors = TextFieldDefaults.colors(
            focusedContainerColor = Color.Transparent,
            unfocusedContainerColor = Color.Transparent,
        ),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    )
}
