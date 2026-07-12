package com.souru.koyomi.data.task

import com.souru.koyomi.data.CalendarRepository
import com.souru.koyomi.data.SettingsRepository
import com.souru.koyomi.data.model.EventDraft
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.data.model.TaskMarker
import java.time.LocalDate
import java.time.ZoneId
import kotlinx.coroutines.flow.first

/**
 * A to-do bound to a date. Tasks are stored as marker-tagged calendar events
 * (see [TaskMarker]) in the user's task calendar, so they sync through Google
 * Calendar and can carry times, reminders and colors — tapping a task opens
 * the normal event editor.
 */
data class Task(
    val id: Long,
    val title: String,
    val dueDate: LocalDate,
    val done: Boolean,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
)

fun EventInstance.toTask(): Task = Task(
    id = eventId,
    title = title,
    dueDate = startDate,
    done = isDone,
    begin = begin,
    end = end,
    allDay = allDay,
)

class TaskRepository(
    private val calendarRepository: CalendarRepository,
    private val settingsRepository: SettingsRepository,
) {

    /** The calendar new tasks are created in. */
    private suspend fun taskCalendarId(): Long? {
        val configured = settingsRepository.taskCalendarId.first()
        val writable = calendarRepository.loadCalendars().filter { it.isWritable }
        return writable.find { it.id == configured }?.id
            ?: writable.find { it.id == settingsRepository.lastUsedCalendarId.first() }?.id
            ?: writable.firstOrNull()?.id
    }

    /** Quick-add: an all-day marker event on [dueDate]. */
    suspend fun addTask(title: String, dueDate: LocalDate) {
        if (title.isBlank()) return
        val calendarId = taskCalendarId() ?: return
        val dayMillis = dueDate.atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli()
        calendarRepository.createEvent(
            EventDraft(
                calendarId = calendarId,
                title = title.trim(),
                allDay = true,
                startMillis = dayMillis,
                endMillis = dayMillis,
                description = TaskMarker.TASK,
            ),
        )
    }

    suspend fun setDone(taskId: Long, done: Boolean) {
        val description = calendarRepository.loadDescription(taskId)
        calendarRepository.updateDescription(
            taskId,
            TaskMarker.withDone(description, done),
        )
    }

    suspend fun deleteTask(taskId: Long) {
        calendarRepository.deleteEvent(taskId)
    }
}
