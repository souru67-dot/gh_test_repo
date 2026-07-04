package com.souru.koyomi.data

import android.Manifest
import android.content.ContentUris
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.os.Handler
import android.os.Looper
import android.provider.CalendarContract
import androidx.core.content.ContextCompat
import com.souru.koyomi.data.model.CalendarInfo
import com.souru.koyomi.data.model.EventDetails
import com.souru.koyomi.data.model.EventDraft
import com.souru.koyomi.data.model.EventInstance
import java.time.Duration
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.ZoneOffset
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.withContext

/**
 * All reads/writes go straight to the device CalendarProvider so the app stays
 * in sync with Google Calendar / Exchange accounts. No local event database.
 */
class CalendarRepository(private val context: Context) {

    private val resolver get() = context.contentResolver

    fun hasReadPermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    fun hasWritePermission(): Boolean =
        ContextCompat.checkSelfPermission(context, Manifest.permission.WRITE_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    /** Emits whenever anything under CalendarContract changes (provider sync, our own writes...). */
    val changes: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        if (hasReadPermission()) {
            resolver.registerContentObserver(CalendarContract.CONTENT_URI, true, observer)
        }
        trySend(Unit)
        awaitClose { resolver.unregisterContentObserver(observer) }
    }.conflate()

    /**
     * Loads every event instance overlapping [rangeStart, rangeEndExclusive) and
     * groups it by the local dates it covers.
     */
    suspend fun loadEventsByDay(
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
    ): Map<LocalDate, List<EventInstance>> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyMap()

        val zone = ZoneId.systemDefault()
        val beginMs = rangeStart.atStartOfDay(zone).toInstant().toEpochMilli()
        val endMs = rangeEndExclusive.atStartOfDay(zone).toInstant().toEpochMilli()

        val uriBuilder = CalendarContract.Instances.CONTENT_URI.buildUpon()
        ContentUris.appendId(uriBuilder, beginMs)
        ContentUris.appendId(uriBuilder, endMs)

        val projection = arrayOf(
            CalendarContract.Instances.EVENT_ID,
            CalendarContract.Instances.TITLE,
            CalendarContract.Instances.BEGIN,
            CalendarContract.Instances.END,
            CalendarContract.Instances.ALL_DAY,
            CalendarContract.Instances.DISPLAY_COLOR,
            CalendarContract.Instances.CALENDAR_ID,
            CalendarContract.Instances.EVENT_LOCATION,
        )

        val result = mutableMapOf<LocalDate, MutableList<EventInstance>>()
        resolver.query(
            uriBuilder.build(),
            projection,
            "${CalendarContract.Instances.VISIBLE} = 1",
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val allDay = cursor.getInt(4) != 0
                val begin = cursor.getLong(2)
                val end = cursor.getLong(3)
                // CalendarProvider stores all-day instances at UTC midnight.
                val instanceZone = if (allDay) ZoneOffset.UTC else zone
                val startDate = Instant.ofEpochMilli(begin).atZone(instanceZone).toLocalDate()
                val lastMs = maxOf(begin, end - 1) // end is exclusive; empty events count as 1ms
                val endDate = Instant.ofEpochMilli(lastMs).atZone(instanceZone).toLocalDate()

                val instance = EventInstance(
                    eventId = cursor.getLong(0),
                    title = cursor.getString(1).orEmpty(),
                    begin = begin,
                    end = end,
                    allDay = allDay,
                    color = cursor.getInt(5),
                    calendarId = cursor.getLong(6),
                    location = cursor.getString(7),
                    startDate = startDate,
                    endDate = endDate,
                )
                var day = maxOf(startDate, rangeStart)
                val lastDay = minOf(endDate, rangeEndExclusive.minusDays(1))
                while (!day.isAfter(lastDay)) {
                    result.getOrPut(day) { mutableListOf() }.add(instance)
                    day = day.plusDays(1)
                }
            }
        }
        result.forEach { (_, list) ->
            list.sortWith(compareByDescending<EventInstance> { it.allDay }.thenBy { it.begin })
        }
        result
    }

    suspend fun loadCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
        )
        val list = mutableListOf<CalendarInfo>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.VISIBLE} = 1",
            null,
            "${CalendarContract.Calendars.CALENDAR_DISPLAY_NAME} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                list += CalendarInfo(
                    id = cursor.getLong(0),
                    displayName = cursor.getString(1).orEmpty(),
                    accountName = cursor.getString(2).orEmpty(),
                    color = cursor.getInt(3),
                    isWritable = cursor.getInt(4) >=
                        CalendarContract.Calendars.CAL_ACCESS_CONTRIBUTOR,
                )
            }
        }
        list
    }

    suspend fun loadEventDetails(eventId: Long): EventDetails? = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext null
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        val projection = arrayOf(
            CalendarContract.Events.CALENDAR_ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.EVENT_TIMEZONE,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.RRULE,
        )
        var details: EventDetails? = null
        resolver.query(uri, projection, null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                details = EventDetails(
                    id = eventId,
                    calendarId = cursor.getLong(0),
                    title = cursor.getString(1).orEmpty(),
                    allDay = cursor.getInt(2) != 0,
                    dtStart = cursor.getLong(3),
                    dtEnd = if (cursor.isNull(4)) null else cursor.getLong(4),
                    duration = cursor.getString(5),
                    timeZone = cursor.getString(6),
                    location = cursor.getString(7),
                    description = cursor.getString(8),
                    rrule = cursor.getString(9),
                    reminderMinutes = loadFirstReminderMinutes(eventId),
                )
            }
        }
        details
    }

    private fun loadFirstReminderMinutes(eventId: Long): Int? {
        resolver.query(
            CalendarContract.Reminders.CONTENT_URI,
            arrayOf(CalendarContract.Reminders.MINUTES),
            "${CalendarContract.Reminders.EVENT_ID} = ?",
            arrayOf(eventId.toString()),
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getInt(0)
        }
        return null
    }

    /** Inserts a new event. Returns the new event id, or null on failure. */
    suspend fun createEvent(draft: EventDraft): Long? = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext null
        val uri = resolver.insert(CalendarContract.Events.CONTENT_URI, draft.toContentValues())
            ?: return@withContext null
        val eventId = ContentUris.parseId(uri)
        draft.reminderMinutes?.let { minutes -> insertReminder(eventId, minutes) }
        eventId
    }

    /** Updates an existing event (recurring events are updated as a whole series). */
    suspend fun updateEvent(draft: EventDraft): Boolean = withContext(Dispatchers.IO) {
        val id = draft.id ?: return@withContext false
        if (!hasWritePermission()) return@withContext false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, id)
        val updated = resolver.update(uri, draft.toContentValues(), null, null) > 0
        if (updated) {
            resolver.delete(
                CalendarContract.Reminders.CONTENT_URI,
                "${CalendarContract.Reminders.EVENT_ID} = ?",
                arrayOf(id.toString()),
            )
            draft.reminderMinutes?.let { minutes -> insertReminder(id, minutes) }
        }
        updated
    }

    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.delete(uri, null, null) > 0
    }

    /** Copies an event (single copy at the same time; the copy does not repeat). */
    suspend fun duplicateEvent(eventId: Long): Long? {
        val source = loadEventDetails(eventId) ?: return null
        val endUtc = source.dtEnd
            ?: (source.dtStart + parseDurationMillis(source.duration, source.allDay))
        val draft = if (source.allDay) {
            val zone = ZoneId.systemDefault()
            val startDay = Instant.ofEpochMilli(source.dtStart).atZone(ZoneOffset.UTC).toLocalDate()
            // DTEND is the exclusive UTC midnight; the last covered day is one before it.
            val lastDay = Instant.ofEpochMilli(endUtc).atZone(ZoneOffset.UTC)
                .toLocalDate().minusDays(1)
            EventDraft(
                calendarId = source.calendarId,
                title = source.title,
                allDay = true,
                startMillis = startDay.atStartOfDay(zone).toInstant().toEpochMilli(),
                endMillis = maxOf(startDay, lastDay).atStartOfDay(zone).toInstant().toEpochMilli(),
                location = source.location.orEmpty(),
                description = source.description.orEmpty(),
                reminderMinutes = source.reminderMinutes,
            )
        } else {
            EventDraft(
                calendarId = source.calendarId,
                title = source.title,
                allDay = false,
                startMillis = source.dtStart,
                endMillis = endUtc,
                location = source.location.orEmpty(),
                description = source.description.orEmpty(),
                reminderMinutes = source.reminderMinutes,
            )
        }
        return createEvent(draft)
    }

    private fun insertReminder(eventId: Long, minutes: Int) {
        val values = ContentValues().apply {
            put(CalendarContract.Reminders.EVENT_ID, eventId)
            put(CalendarContract.Reminders.MINUTES, minutes)
            put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
        }
        resolver.insert(CalendarContract.Reminders.CONTENT_URI, values)
    }

    private fun EventDraft.toContentValues(): ContentValues {
        val zone = ZoneId.systemDefault()
        val values = ContentValues()
        values.put(CalendarContract.Events.CALENDAR_ID, calendarId)
        values.put(CalendarContract.Events.TITLE, title)
        values.put(CalendarContract.Events.EVENT_LOCATION, location)
        values.put(CalendarContract.Events.DESCRIPTION, description)
        values.put(CalendarContract.Events.ALL_DAY, if (allDay) 1 else 0)

        val startUtc: Long
        val endUtc: Long
        if (allDay) {
            // All-day events live at UTC midnight of the calendar date.
            val startDay = Instant.ofEpochMilli(startMillis).atZone(zone).toLocalDate()
            val endDay = Instant.ofEpochMilli(endMillis).atZone(zone).toLocalDate()
            startUtc = startDay.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            // DTEND is exclusive: an all-day event on one day ends at the next UTC midnight.
            endUtc = endDay.plusDays(1).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
            values.put(CalendarContract.Events.EVENT_TIMEZONE, "UTC")
        } else {
            startUtc = startMillis
            endUtc = endMillis
            values.put(CalendarContract.Events.EVENT_TIMEZONE, zone.id)
        }
        values.put(CalendarContract.Events.DTSTART, startUtc)

        if (rrule.isNullOrBlank()) {
            values.put(CalendarContract.Events.RRULE, null as String?)
            values.put(CalendarContract.Events.DURATION, null as String?)
            values.put(CalendarContract.Events.DTEND, endUtc)
        } else {
            // Recurring events must use DURATION instead of DTEND.
            values.put(CalendarContract.Events.RRULE, rrule)
            values.put(CalendarContract.Events.DTEND, null as Long?)
            values.put(
                CalendarContract.Events.DURATION,
                if (allDay) {
                    "P${Duration.ofMillis(endUtc - startUtc).toDays()}D"
                } else {
                    "P${(endUtc - startUtc) / 1000}S"
                },
            )
        }
        return values
    }

    /** RFC 2445 durations as written by CalendarProvider: P1D, P900S, PT3600S, P1W... */
    private fun parseDurationMillis(duration: String?, allDay: Boolean): Long {
        val fallback = if (allDay) 86_400_000L else 3_600_000L
        if (duration.isNullOrBlank()) return fallback
        val d = duration.trim().uppercase()
        return runCatching {
            when {
                d.matches(Regex("P\\d+W")) -> d.drop(1).dropLast(1).toLong() * 7 * 86_400_000L
                d.matches(Regex("P\\d+S")) -> d.drop(1).dropLast(1).toLong() * 1_000L
                else -> Duration.parse(d).toMillis()
            }
        }.getOrDefault(fallback)
    }
}
