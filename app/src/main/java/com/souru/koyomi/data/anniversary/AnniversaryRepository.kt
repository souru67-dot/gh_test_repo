package com.souru.koyomi.data.anniversary

import android.content.ContentValues
import android.content.Context
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper
import java.time.LocalDate
import java.time.temporal.ChronoUnit
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.onStart
import kotlinx.coroutines.withContext

/**
 * A local anniversary/countdown: 結婚記念日, 推しのライブ, 試験日…
 * Yearly ones repeat forever; one-shot ones simply count down (and then up).
 */
data class Anniversary(
    val id: Long,
    val title: String,
    val date: LocalDate,
    val repeatYearly: Boolean,
) {
    /** The next occurrence on/after [today] (yearly wraps; Feb 29 → Feb 28). */
    fun nextOccurrence(today: LocalDate): LocalDate {
        if (!repeatYearly) return date
        fun inYear(year: Int): LocalDate = if (date.monthValue == 2 && date.dayOfMonth == 29) {
            if (java.time.Year.isLeap(year.toLong())) {
                LocalDate.of(year, 2, 29)
            } else {
                LocalDate.of(year, 2, 28)
            }
        } else {
            LocalDate.of(year, date.monthValue, date.dayOfMonth)
        }
        val thisYear = inYear(today.year)
        return if (thisYear < today) inYear(today.year + 1) else thisYear
    }

    /** Days until the next occurrence (negative = a passed one-shot). */
    fun daysUntil(today: LocalDate): Long =
        ChronoUnit.DAYS.between(today, nextOccurrence(today))

    /** Whether [day] is this anniversary's day (same month/day for yearly). */
    fun fallsOn(day: LocalDate): Boolean = if (repeatYearly) {
        nextOccurrence(day) == day || (day.monthValue == date.monthValue &&
            day.dayOfMonth == date.dayOfMonth)
    } else {
        date == day
    }

    /** Elapsed years for a yearly anniversary on [day] (e.g. 3 = 3周年). */
    fun yearsOn(day: LocalDate): Int = (day.year - date.year).coerceAtLeast(0)
}

class AnniversaryRepository(context: Context) {

    private val helper = DbHelper(context.applicationContext)

    private val _changes = MutableSharedFlow<Unit>(replay = 1, extraBufferCapacity = 1)

    /** Emits after every mutation (and once on collect) so UIs can reload. */
    val changes: Flow<Unit> = _changes.onStart { emit(Unit) }

    suspend fun loadAll(): List<Anniversary> = withContext(Dispatchers.IO) {
        val result = mutableListOf<Anniversary>()
        helper.readableDatabase.query(
            TABLE, null, null, null, null, null, "epoch_day ASC, _id ASC",
        ).use { cursor ->
            while (cursor.moveToNext()) {
                result += Anniversary(
                    id = cursor.getLong(cursor.getColumnIndexOrThrow("_id")),
                    title = cursor.getString(cursor.getColumnIndexOrThrow("title")),
                    date = LocalDate.ofEpochDay(
                        cursor.getLong(cursor.getColumnIndexOrThrow("epoch_day")),
                    ),
                    repeatYearly =
                        cursor.getInt(cursor.getColumnIndexOrThrow("repeat_yearly")) != 0,
                )
            }
        }
        result
    }

    /** Insert (id == 0) or update. */
    suspend fun save(anniversary: Anniversary): Long = withContext(Dispatchers.IO) {
        if (anniversary.title.isBlank()) return@withContext -1L
        val values = ContentValues().apply {
            put("title", anniversary.title.trim())
            put("epoch_day", anniversary.date.toEpochDay())
            put("repeat_yearly", if (anniversary.repeatYearly) 1 else 0)
        }
        val id = if (anniversary.id == 0L) {
            helper.writableDatabase.insert(TABLE, null, values)
        } else {
            helper.writableDatabase.update(
                TABLE, values, "_id = ?", arrayOf(anniversary.id.toString()),
            )
            anniversary.id
        }
        _changes.tryEmit(Unit)
        id
    }

    suspend fun delete(id: Long): Unit = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, "_id = ?", arrayOf(id.toString()))
        _changes.tryEmit(Unit)
    }

    /** Backup restore: wipe before re-inserting the imported set. */
    suspend fun deleteAll(): Unit = withContext(Dispatchers.IO) {
        helper.writableDatabase.delete(TABLE, null, null)
        _changes.tryEmit(Unit)
    }

    private class DbHelper(context: Context) :
        SQLiteOpenHelper(context, "anniversaries.db", null, 1) {

        override fun onCreate(db: SQLiteDatabase) {
            db.execSQL(
                """
                CREATE TABLE $TABLE (
                    _id INTEGER PRIMARY KEY AUTOINCREMENT,
                    title TEXT NOT NULL,
                    epoch_day INTEGER NOT NULL,
                    repeat_yearly INTEGER NOT NULL DEFAULT 1
                )
                """.trimIndent(),
            )
        }

        override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) = Unit
    }

    private companion object {
        const val TABLE = "anniversaries"
    }
}
