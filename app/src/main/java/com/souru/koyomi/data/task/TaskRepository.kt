package com.souru.koyomi.data.task

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.workDataOf
import com.souru.koyomi.notifications.TaskReminderWorker
import java.time.Duration
import java.time.LocalDate
import java.time.LocalTime
import java.time.ZoneId
import java.time.ZonedDateTime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * A local to-do bound to a date, independent of calendar events. Optional
 * time, color and reminder. (Syncing with Google Tasks needs the Tasks REST
 * API + an OAuth client ID; the store is designed so a sync layer can be
 * added on top once credentials exist.)
 */
data class Task(
    val id: Long,
    val title: String,
    val dueDate: LocalDate,
    /** Minutes from midnight, or null for a date-only task. */
    val timeMinutes: Int? = null,
    /** ARGB, or null to use the theme accent. */
    val color: Int? = null,
    /** Minutes before [timeMinutes] (or 9:00 for date-only) to notify. */
    val reminderMinutes: Int? = null,
    val done: Boolean = false,
) {
    val time: LocalTime? get() = timeMinutes?.let { LocalTime.ofSecondOfDay(it * 60L) }
}

class TaskRepository(private val context: Context) {

    private val helper = TaskDbHelper(context.applicationContext)

    private val _changes = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    /** Emits after every mutation (and once on collect) so UIs can reload. */
    val changes: Flow<Unit> = _changes.onStart { emit(Unit) }

    suspend fun loadTasksByDay(
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
    ): Map<LocalDate, List<Task>> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<LocalDate, MutableList<Task>>()
        helper.readableDatabase.query(
            TABLE,
            null,
            "due_epoch_day >= ? AND due_epoch_day < ?",
            arrayOf(
                rangeStart.toEpochDay().toString(),
                rangeEndExclusive.toEpochDay().toString(),
            ),
            null,
            null,
            "done ASC, time_minutes ASC, _id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                val task = cursor.toTask()
                result.getOrPut(task.dueDate) { mutableListOf() }.add(task)
            }
        }
        result
    }

    /** Every task, oldest due date first; open tasks before completed ones. */
    suspend fun loadAllTasks(): List<Task> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Task>()
        helper.readableDatabase.query(
            TABLE,
            null,
            null,
            null,
            null,
            null,
            "done ASC, due_epoch_day ASC, time_minutes ASC, _id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) result.add(cursor.toTask())
        }
        result
    }

    suspend fun getTask(taskId: Long): Task? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            TABLE,
            null,
            "_id = ?",
            arrayOf(taskId.toString()),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.toTask() else null
        }
    }

    /** Quick-add from the day sheet: date-only task. */
    suspend fun addTask(title: String, dueDate: LocalDate) {
        saveTask(Task(id = 0L, title = title, dueDate = dueDate))
    }

    /** Insert ([Task.id] == 0) or update; reschedules the reminder. */
    suspend fun saveTask(task: Task): Long = withContext(Dispatchers.IO) {
        if (task.title.isBlank()) return@withContext -1L
        val values = ContentValues().apply {
            put("title", task.title.trim())
            put("due_epoch_day", task.dueDate.toEpochDay())
            put("time_minutes", task.timeMinutes)
            put("color", task.color)
            put("reminder_minutes", task.reminderMinutes)
            put("done", if (task.done) 1 else 0)
        }
        val id = if (task.id == 0L) {
            values.put("created_at", System.currentTimeMillis())
            helper.writableDatabase.insert(TABLE, null, values)
        } else {
            helper.writableDatabase.update(TABLE, values, "_id = ?", arrayOf(task.id.toString()))
            task.id
        }
        scheduleReminder(task.copy(id = id))
        _changes.tryEmit(Unit)
        refreshWidgets()
        id
    }

    suspend fun setDone(taskId: Long, done: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("done", if (done) 1 else 0) }
        helper.writableDatabase.update(TABLE, values, "_id = ?", arrayOf(taskId.toString()))
        if (done) cancelReminder(taskId) else getTask(taskId)?.let { scheduleReminder(it) }
        _changes.tryEmit(Unit)
        refreshWidgets()
    }

    suspend fun deleteTask(taskId: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, "_id = ?", arrayOf(taskId.toString()))
        cancelReminder(taskId)
        _changes.tryEmit(Unit)
        refreshWidgets()
    }

    /** Tasks live outside CalendarProvider, so its widget trigger never fires. */
    private fun refreshWidgets() {
        WorkManager.getInstance(context).enqueueUniqueWork(
            "koyomi_widget_task_change",
            ExistingWorkPolicy.REPLACE,
            OneTimeWorkRequestBuilder<com.souru.koyomi.widget.WidgetUpdateWorker>().build(),
        )
    }

    /**
     * Reminder via a delayed WorkManager one-shot (inexact by a few minutes,
     * but survives reboots and needs no exact-alarm permission).
     */
    private fun scheduleReminder(task: Task) {
        cancelReminder(task.id)
        val reminderMinutes = task.reminderMinutes ?: return
        if (task.done) return
        val zone = ZoneId.systemDefault()
        val baseTime = task.time ?: DEFAULT_DATE_ONLY_REMINDER_TIME
        val fireAt = ZonedDateTime.of(task.dueDate, baseTime, zone)
            .minusMinutes(reminderMinutes.toLong())
        val delay = Duration.between(ZonedDateTime.now(zone), fireAt)
        if (delay.isNegative) return

        val request = OneTimeWorkRequestBuilder<TaskReminderWorker>()
            .setInitialDelay(delay)
            .setInputData(workDataOf(TaskReminderWorker.KEY_TASK_ID to task.id))
            .build()
        WorkManager.getInstance(context).enqueueUniqueWork(
            reminderWorkName(task.id),
            ExistingWorkPolicy.REPLACE,
            request,
        )
    }

    private fun cancelReminder(taskId: Long) {
        WorkManager.getInstance(context).cancelUniqueWork(reminderWorkName(taskId))
    }

    private fun reminderWorkName(taskId: Long) = "task_reminder_$taskId"

    private fun android.database.Cursor.toTask(): Task = Task(
        id = getLong(getColumnIndexOrThrow("_id")),
        title = getString(getColumnIndexOrThrow("title")),
        dueDate = LocalDate.ofEpochDay(getLong(getColumnIndexOrThrow("due_epoch_day"))),
        timeMinutes = getColumnIndexOrThrow("time_minutes")
            .let { if (isNull(it)) null else getInt(it) },
        color = getColumnIndexOrThrow("color")
            .let { if (isNull(it)) null else getInt(it) },
        reminderMinutes = getColumnIndexOrThrow("reminder_minutes")
            .let { if (isNull(it)) null else getInt(it) },
        done = getInt(getColumnIndexOrThrow("done")) != 0,
    )

    private class TaskDbHelper(context: Context) :
        SQLiteOpenHelper(context, "tasks.db", null, 2) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    due_epoch_day INTEGER NOT NULL,
                    time_minutes INTEGER,
                    color INTEGER,
                    reminder_minutes INTEGER,
                    done INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_tasks_due ON $TABLE (due_epoch_day)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
            if (oldVersion < 2) {
                // v1 (early builds) lacked time/color/reminder.
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN time_minutes INTEGER")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN color INTEGER")
                db.execSQL("ALTER TABLE $TABLE ADD COLUMN reminder_minutes INTEGER")
            }
        }
    }

    private companion object {
        const val TABLE = "tasks"
        val DEFAULT_DATE_ONLY_REMINDER_TIME: LocalTime = LocalTime.of(9, 0)
    }
}
