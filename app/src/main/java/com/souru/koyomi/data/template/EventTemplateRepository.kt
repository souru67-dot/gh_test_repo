package com.souru.koyomi.data.template

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * A reusable event template — a frequently-created event saved once and placed
 * onto any day with one tap. Stored locally (SQLite); the actual events it
 * creates still live in CalendarProvider.
 */
data class EventTemplate(
    val id: Long,
    val title: String,
    val allDay: Boolean,
    /** Start time of day in minutes from midnight (ignored when all-day). */
    val startMinutes: Int,
    /** Event length in minutes (all-day uses whole days elsewhere). */
    val durationMinutes: Int,
    /** Preferred calendar, or null to use the last-used / first writable one. */
    val calendarId: Long?,
    /** ARGB, or null for the calendar's default color. */
    val color: Int?,
    val location: String?,
    val description: String?,
    val reminderMinutes: Int?,
)

class EventTemplateRepository(context: Context) {

    private val helper = TemplateDbHelper(context.applicationContext)
    private val _changes = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)
    val changes: Flow<Unit> = _changes.onStart { emit(Unit) }

    suspend fun loadTemplates(): List<EventTemplate> = withContext(Dispatchers.IO) {
        val list = mutableListOf<EventTemplate>()
        helper.readableDatabase.query(
            TABLE, null, null, null, null, null, "created_at ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) list += cursor.toTemplate()
        }
        list
    }

    /** Insert ([EventTemplate.id] == 0) or update. Returns the row id. */
    suspend fun save(template: EventTemplate): Long = withContext(Dispatchers.IO) {
        val values = ContentValues().apply {
            put("title", template.title.trim())
            put("all_day", if (template.allDay) 1 else 0)
            put("start_minutes", template.startMinutes)
            put("duration_minutes", template.durationMinutes)
            put("calendar_id", template.calendarId)
            put("color", template.color)
            put("location", template.location)
            put("description", template.description)
            put("reminder_minutes", template.reminderMinutes)
        }
        val id = if (template.id == 0L) {
            values.put("created_at", System.currentTimeMillis())
            helper.writableDatabase.insert(TABLE, null, values)
        } else {
            helper.writableDatabase.update(
                TABLE, values, "_id = ?", arrayOf(template.id.toString()),
            )
            template.id
        }
        _changes.tryEmit(Unit)
        id
    }

    suspend fun delete(id: Long) = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, "_id = ?", arrayOf(id.toString()))
        _changes.tryEmit(Unit)
        Unit
    }

    private fun Cursor.toTemplate() = EventTemplate(
        id = getLong(getColumnIndexOrThrow("_id")),
        title = getString(getColumnIndexOrThrow("title")),
        allDay = getInt(getColumnIndexOrThrow("all_day")) != 0,
        startMinutes = getInt(getColumnIndexOrThrow("start_minutes")),
        durationMinutes = getInt(getColumnIndexOrThrow("duration_minutes")),
        calendarId = getColumnIndexOrThrow("calendar_id")
            .let { if (isNull(it)) null else getLong(it) },
        color = getColumnIndexOrThrow("color").let { if (isNull(it)) null else getInt(it) },
        location = getColumnIndexOrThrow("location").let { if (isNull(it)) null else getString(it) },
        description = getColumnIndexOrThrow("description")
            .let { if (isNull(it)) null else getString(it) },
        reminderMinutes = getColumnIndexOrThrow("reminder_minutes")
            .let { if (isNull(it)) null else getInt(it) },
    )

    private class TemplateDbHelper(context: Context) :
        SQLiteOpenHelper(context, "templates.db", null, 1) {
        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    all_day INTEGER NOT NULL DEFAULT 0,
                    start_minutes INTEGER NOT NULL DEFAULT 540,
                    duration_minutes INTEGER NOT NULL DEFAULT 60,
                    calendar_id INTEGER,
                    color INTEGER,
                    location TEXT,
                    description TEXT,
                    reminder_minutes INTEGER,
                    created_at INTEGER NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val TABLE = "templates"
    }
}
