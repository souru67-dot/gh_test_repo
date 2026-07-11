package com.souru.koyomi.data.task

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

data class Task(
    val id: Long,
    val title: String,
    val dueDate: LocalDate,
    val done: Boolean,
)

/**
 * Local to-do items tied to a calendar date. Deliberately a tiny hand-rolled
 * SQLite table: Android has no public provider for Google Tasks, so these
 * stay on-device (Google Tasks sync would require the network API + OAuth).
 */
class TaskRepository(context: Context) {

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
            "done ASC, _id ASC",
        ).use { cursor ->
            val idIdx = cursor.getColumnIndexOrThrow("_id")
            val titleIdx = cursor.getColumnIndexOrThrow("title")
            val dueIdx = cursor.getColumnIndexOrThrow("due_epoch_day")
            val doneIdx = cursor.getColumnIndexOrThrow("done")
            while (cursor.moveToNext()) {
                val task = Task(
                    id = cursor.getLong(idIdx),
                    title = cursor.getString(titleIdx),
                    dueDate = LocalDate.ofEpochDay(cursor.getLong(dueIdx)),
                    done = cursor.getInt(doneIdx) != 0,
                )
                result.getOrPut(task.dueDate) { mutableListOf() }.add(task)
            }
        }
        result
    }

    suspend fun addTask(title: String, dueDate: LocalDate) = withContext(Dispatchers.IO) {
        if (title.isBlank()) return@withContext
        val values = ContentValues().apply {
            put("title", title.trim())
            put("due_epoch_day", dueDate.toEpochDay())
            put("done", 0)
            put("created_at", System.currentTimeMillis())
        }
        helper.writableDatabase.insert(TABLE, null, values)
        _changes.tryEmit(Unit)
    }

    suspend fun setDone(taskId: Long, done: Boolean) = withContext(Dispatchers.IO) {
        val values = ContentValues().apply { put("done", if (done) 1 else 0) }
        helper.writableDatabase.update(TABLE, values, "_id = ?", arrayOf(taskId.toString()))
        _changes.tryEmit(Unit)
    }

    suspend fun deleteTask(taskId: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, "_id = ?", arrayOf(taskId.toString()))
        _changes.tryEmit(Unit)
    }

    private class TaskDbHelper(context: Context) :
        SQLiteOpenHelper(context, "tasks.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    due_epoch_day INTEGER NOT NULL,
                    done INTEGER NOT NULL DEFAULT 0,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
            db.execSQL("CREATE INDEX idx_tasks_due ON $TABLE (due_epoch_day)")
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val TABLE = "tasks"
    }
}
