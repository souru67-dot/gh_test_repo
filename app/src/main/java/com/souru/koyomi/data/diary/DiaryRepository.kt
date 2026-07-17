package com.souru.koyomi.data.diary

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

/**
 * ひとこと日記 — one short note per day, kept entirely on-device.
 * "過去の今日" reads the same date in earlier years.
 */
class DiaryRepository(context: Context) {

    private val helper = DbHelper(context.applicationContext)

    private val _changes = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    /** Emits after every mutation (and once on collect) so UIs can reload. */
    val changes: Flow<Unit> = _changes.onStart { emit(Unit) }

    suspend fun entryFor(date: LocalDate): String? = withContext(Dispatchers.IO) {
        helper.readableDatabase.query(
            TABLE,
            arrayOf("text"),
            "epoch_day = ?",
            arrayOf(date.toEpochDay().toString()),
            null,
            null,
            null,
        ).use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        }
    }

    /** Blank [text] deletes the entry. */
    suspend fun save(date: LocalDate, text: String): Unit = withContext(Dispatchers.IO) {
        if (text.isBlank()) {
            helper.writableDatabase.delete(
                TABLE, "epoch_day = ?", arrayOf(date.toEpochDay().toString()),
            )
        } else {
            val values = ContentValues().apply {
                put("epoch_day", date.toEpochDay())
                put("text", text.trim())
            }
            helper.writableDatabase.insertWithOnConflict(
                TABLE, null, values, SQLiteDatabase.CONFLICT_REPLACE,
            )
        }
        _changes.tryEmit(Unit)
    }

    /** Every entry, for backup export. */
    suspend fun loadAll(): Map<LocalDate, String> = withContext(Dispatchers.IO) {
        val result = mutableMapOf<LocalDate, String>()
        helper.readableDatabase.query(
            TABLE, null, null, null, null, null, "epoch_day ASC",
        ).use { cursor ->
            val dayIdx = cursor.getColumnIndexOrThrow("epoch_day")
            val textIdx = cursor.getColumnIndexOrThrow("text")
            while (cursor.moveToNext()) {
                result[LocalDate.ofEpochDay(cursor.getLong(dayIdx))] =
                    cursor.getString(textIdx)
            }
        }
        result
    }

    private class DbHelper(context: Context) :
        SQLiteOpenHelper(context, "diary.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    epoch_day INTEGER PRIMARY KEY,
                    text TEXT NOT NULL
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val TABLE = "diary"
    }
}
