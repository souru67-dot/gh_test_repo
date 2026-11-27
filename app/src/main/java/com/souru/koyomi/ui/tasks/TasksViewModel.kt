package com.souru.koyomi.ui.tasks

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.koyomi.KoyomiApplication
import com.souru.koyomi.data.task.Task
import com.souru.koyomi.data.task.TaskRepository
import java.time.LocalDate
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class TasksUiState(
    val overdue: List<Task> = emptyList(),
    val today: List<Task> = emptyList(),
    val upcoming: List<Task> = emptyList(),
    val done: List<Task> = emptyList(),
    val loaded: Boolean = false,
) {
    val isEmpty: Boolean
        get() = overdue.isEmpty() && today.isEmpty() && upcoming.isEmpty() && done.isEmpty()
}

/** The full to-do list across all dates, grouped by urgency. */
@OptIn(ExperimentalCoroutinesApi::class)
class TasksViewModel(
    private val taskRepository: TaskRepository,
) : ViewModel() {

    val uiState: StateFlow<TasksUiState> = taskRepository.changes
        .mapLatest {
            val all = taskRepository.loadAllTasks()
            val today = LocalDate.now()
            TasksUiState(
                overdue = all.filter { !it.done && it.dueDate.isBefore(today) },
                today = all.filter { !it.done && it.dueDate == today },
                upcoming = all.filter { !it.done && it.dueDate.isAfter(today) },
                done = all.filter { it.done }.sortedByDescending { it.dueDate },
                loaded = true,
            )
        }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TasksUiState())

    /** Quick-add lands on today; details (date, time, color) via the editor. */
    fun addTask(title: String) {
        viewModelScope.launch { taskRepository.addTask(title, LocalDate.now()) }
    }

    fun setDone(taskId: Long, done: Boolean) {
        viewModelScope.launch { taskRepository.setDone(taskId, done) }
    }

    fun deleteTask(taskId: Long) {
        viewModelScope.launch { taskRepository.deleteTask(taskId) }
    }

    companion object {
        val Factory: ViewModelProvider.Factory = viewModelFactory {
            initializer {
                val app = this[ViewModelProvider.AndroidViewModelFactory.APPLICATION_KEY]
                    as KoyomiApplication
                TasksViewModel(taskRepository = app.container.taskRepository)
            }
        }
    }
}
