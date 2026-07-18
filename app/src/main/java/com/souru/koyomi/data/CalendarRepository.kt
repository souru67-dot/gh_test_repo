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
import com.souru.koyomi.data.model.EventColor
import com.souru.koyomi.data.model.EventDetails
import com.souru.koyomi.data.model.EventDraft
import com.souru.koyomi.data.model.EventInstance
import com.souru.koyomi.util.Ics
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
        hiddenCalendarIds: Set<Long> = emptySet(),
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

        // DISPLAY_COLOR already prefers EVENT_COLOR over the calendar color,
        // but some sync adapters leave it 0 — fall back to the owning
        // calendar's color so every chip is tinted by its calendar.
        val calendarColors = loadCalendars().associate { it.id to it.color }

        val result = mutableMapOf<LocalDate, MutableList<EventInstance>>()
        // deleted=0: app-side deletes are soft (the sync adapter purges them
        // later), and Instances happily keeps returning those rows — that made
        // deleted events "come back" in the app while Google already dropped
        // them. STATUS != CANCELED hides cancelled occurrences the same way.
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Events.DELETED} = 0 AND " +
            "(${CalendarContract.Events.STATUS} IS NULL OR " +
            "${CalendarContract.Events.STATUS} != ${CalendarContract.Events.STATUS_CANCELED})"
        resolver.query(
            uriBuilder.build(),
            projection,
            selection,
            null,
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.getLong(6) in hiddenCalendarIds) continue
                val allDay = cursor.getInt(4) != 0
                val begin = cursor.getLong(2)
                val end = cursor.getLong(3)
                // CalendarProvider stores all-day instances at UTC midnight.
                val instanceZone = if (allDay) ZoneOffset.UTC else zone
                val startDate = Instant.ofEpochMilli(begin).atZone(instanceZone).toLocalDate()
                val lastMs = maxOf(begin, end - 1) // end is exclusive; empty events count as 1ms
                val endDate = Instant.ofEpochMilli(lastMs).atZone(instanceZone).toLocalDate()

                val calendarId = cursor.getLong(6)
                val displayColor = cursor.getInt(5)
                val instance = EventInstance(
                    eventId = cursor.getLong(0),
                    title = cursor.getString(1).orEmpty(),
                    begin = begin,
                    end = end,
                    allDay = allDay,
                    color = if (displayColor != 0) {
                        displayColor
                    } else {
                        calendarColors[calendarId] ?: 0
                    },
                    calendarId = calendarId,
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

    /**
     * Full-text search over event instances in [rangeStart, rangeEndExclusive):
     * title, location and description, case-insensitive for ASCII. Results
     * are distinct instances in chronological order.
     */
    suspend fun searchEvents(
        query: String,
        rangeStart: LocalDate,
        rangeEndExclusive: LocalDate,
        hiddenCalendarIds: Set<Long> = emptySet(),
    ): List<EventInstance> = withContext(Dispatchers.IO) {
        if (!hasReadPermission() || query.isBlank()) return@withContext emptyList()

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
        val like = "%" + query.trim()
            .replace("\\", "\\\\")
            .replace("%", "\\%")
            .replace("_", "\\_") + "%"
        val selection = "${CalendarContract.Instances.VISIBLE} = 1 AND " +
            "${CalendarContract.Events.DELETED} = 0 AND " +
            "(${CalendarContract.Events.STATUS} IS NULL OR " +
            "${CalendarContract.Events.STATUS} != ${CalendarContract.Events.STATUS_CANCELED}) " +
            "AND (${CalendarContract.Instances.TITLE} LIKE ? ESCAPE '\\' OR " +
            "${CalendarContract.Instances.EVENT_LOCATION} LIKE ? ESCAPE '\\' OR " +
            "${CalendarContract.Instances.DESCRIPTION} LIKE ? ESCAPE '\\')"

        val calendarColors = loadCalendars().associate { it.id to it.color }
        val results = mutableListOf<EventInstance>()
        resolver.query(
            uriBuilder.build(),
            projection,
            selection,
            arrayOf(like, like, like),
            "${CalendarContract.Instances.BEGIN} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                val calendarId = cursor.getLong(6)
                if (calendarId in hiddenCalendarIds) continue
                val allDay = cursor.getInt(4) != 0
                val begin = cursor.getLong(2)
                val end = cursor.getLong(3)
                val instanceZone = if (allDay) ZoneOffset.UTC else zone
                val displayColor = cursor.getInt(5)
                results += EventInstance(
                    eventId = cursor.getLong(0),
                    title = cursor.getString(1).orEmpty(),
                    begin = begin,
                    end = end,
                    allDay = allDay,
                    color = if (displayColor != 0) {
                        displayColor
                    } else {
                        calendarColors[calendarId] ?: 0
                    },
                    calendarId = calendarId,
                    location = cursor.getString(7),
                    startDate = Instant.ofEpochMilli(begin).atZone(instanceZone).toLocalDate(),
                    endDate = Instant.ofEpochMilli(maxOf(begin, end - 1))
                        .atZone(instanceZone).toLocalDate(),
                )
            }
        }
        results
    }

    /**
     * Every calendar the provider knows for all accounts — Google sub-calendars
     * ("仕事", "Instagram"...), subscribed/holiday calendars and local ones.
     * Always queried fresh so calendars created on the Google side appear as
     * soon as the device sync has pulled them; never cached.
     */
    suspend fun loadCalendars(): List<CalendarInfo> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        val projection = arrayOf(
            CalendarContract.Calendars._ID,
            CalendarContract.Calendars.CALENDAR_DISPLAY_NAME,
            CalendarContract.Calendars.ACCOUNT_NAME,
            CalendarContract.Calendars.CALENDAR_COLOR,
            CalendarContract.Calendars.CALENDAR_ACCESS_LEVEL,
            CalendarContract.Calendars.VISIBLE,
        )
        val list = mutableListOf<CalendarInfo>()
        resolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            null,
            null,
            "${CalendarContract.Calendars.ACCOUNT_NAME} ASC, " +
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
                    isVisible = cursor.getInt(5) != 0,
                )
            }
        }
        list
    }

    /**
     * Flips a calendar's provider-level visibility. Enabling also turns on
     * event sync so a freshly subscribed calendar starts pulling events.
     */
    suspend fun setCalendarVisible(calendarId: Long, visible: Boolean): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission()) return@withContext false
            val values = ContentValues().apply {
                put(CalendarContract.Calendars.VISIBLE, if (visible) 1 else 0)
                if (visible) put(CalendarContract.Calendars.SYNC_EVENTS, 1)
            }
            val uri = ContentUris.withAppendedId(
                CalendarContract.Calendars.CONTENT_URI,
                calendarId,
            )
            runCatching { resolver.update(uri, values, null, null) > 0 }.getOrDefault(false)
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
            CalendarContract.Events.EVENT_COLOR_KEY,
            CalendarContract.Events.EVENT_COLOR,
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
                    eventColorKey = cursor.getString(10),
                    eventColor = if (cursor.isNull(11)) 0 else cursor.getInt(11),
                )
            }
        }
        details
    }

    /**
     * The event color palette the account's sync adapter published to
     * CalendarContract.Colors — for Google accounts this is the same set the
     * Google Calendar app offers (Tomato, Sage...). Empty for local calendars.
     */
    suspend fun loadEventColors(accountName: String): List<EventColor> =
        withContext(Dispatchers.IO) {
            if (!hasReadPermission()) return@withContext emptyList()
            val colors = linkedMapOf<String, EventColor>()
            runCatching {
                resolver.query(
                    CalendarContract.Colors.CONTENT_URI,
                    arrayOf(
                        CalendarContract.Colors.COLOR_KEY,
                        CalendarContract.Colors.COLOR,
                    ),
                    "${CalendarContract.Colors.COLOR_TYPE} = ? AND " +
                        "${CalendarContract.Colors.ACCOUNT_NAME} = ?",
                    arrayOf(
                        CalendarContract.Colors.TYPE_EVENT.toString(),
                        accountName,
                    ),
                    CalendarContract.Colors.COLOR_KEY + " ASC",
                )?.use { cursor ->
                    while (cursor.moveToNext()) {
                        val key = cursor.getString(0) ?: continue
                        colors[key] = EventColor(key = key, color = cursor.getInt(1))
                    }
                }
            }
            colors.values.toList()
        }

    private fun queryCalendarId(eventId: Long): Long? {
        resolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            arrayOf(CalendarContract.Events.CALENDAR_ID),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return cursor.getLong(0)
        }
        return null
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
        val values = draft.toContentValues().apply {
            // Never rewrite CALENDAR_ID unless the user actually moved the
            // event — unconditionally writing it can detach a synced event
            // from its Google sub-calendar (name AND color then fall back to
            // the primary calendar).
            val currentCalendarId = queryCalendarId(id)
            if (currentCalendarId == draft.calendarId) {
                remove(CalendarContract.Events.CALENDAR_ID)
            }
        }
        val updated = resolver.update(uri, values, null, null) > 0
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

    /**
     * Deletes a single occurrence of a recurring event by inserting a
     * cancelled exception at the instance's original time.
     */
    suspend fun deleteEventInstance(
        eventId: Long,
        instanceBeginMs: Long,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        val values = ContentValues().apply {
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceBeginMs)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CANCELED)
        }
        val uri = android.net.Uri.withAppendedPath(
            CalendarContract.Events.CONTENT_EXCEPTION_URI,
            eventId.toString(),
        )
        resolver.insert(uri, values) != null
    }

    /**
     * Edits a single occurrence of a recurring event: inserts an exception
     * event carrying the modified fields. [draft] must not repeat.
     */
    suspend fun updateEventInstance(
        eventId: Long,
        instanceBeginMs: Long,
        draft: EventDraft,
    ): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        val values = draft.copy(rrule = null).toContentValues().apply {
            // Exceptions inherit calendar, recurrence and color from the parent;
            // the exception URI accepts only a limited set of columns.
            remove(CalendarContract.Events.CALENDAR_ID)
            remove(CalendarContract.Events.RRULE)
            remove(CalendarContract.Events.DURATION)
            remove(CalendarContract.Events.EVENT_COLOR_KEY)
            remove(CalendarContract.Events.EVENT_COLOR)
            put(CalendarContract.Events.ORIGINAL_INSTANCE_TIME, instanceBeginMs)
            put(CalendarContract.Events.STATUS, CalendarContract.Events.STATUS_CONFIRMED)
        }
        val uri = android.net.Uri.withAppendedPath(
            CalendarContract.Events.CONTENT_EXCEPTION_URI,
            eventId.toString(),
        )
        val inserted = resolver.insert(uri, values) ?: return@withContext false
        val exceptionId = ContentUris.parseId(inserted)
        draft.reminderMinutes?.let { minutes -> insertReminder(exceptionId, minutes) }
        true
    }

    /**
     * Shifts an event by whole days (month-view drag & drop). Recurring events
     * move the entire series; DURATION-based events only need DTSTART shifted.
     */
    suspend fun moveEventByDays(eventId: Long, days: Long): Boolean = withContext(Dispatchers.IO) {
        if (days == 0L) return@withContext true
        if (!hasWritePermission()) return@withContext false
        val details = loadEventDetails(eventId) ?: return@withContext false

        val values = ContentValues()
        if (details.allDay) {
            // All-day times are UTC midnights; plain day arithmetic keeps them aligned.
            values.put(CalendarContract.Events.DTSTART, details.dtStart + days * 86_400_000L)
            details.dtEnd?.let {
                values.put(CalendarContract.Events.DTEND, it + days * 86_400_000L)
            }
        } else {
            val zone = runCatching { ZoneId.of(details.timeZone) }
                .getOrDefault(ZoneId.systemDefault())
            fun shift(millis: Long): Long = Instant.ofEpochMilli(millis).atZone(zone)
                .plusDays(days).toInstant().toEpochMilli()
            values.put(CalendarContract.Events.DTSTART, shift(details.dtStart))
            details.dtEnd?.let { values.put(CalendarContract.Events.DTEND, shift(it)) }
        }
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.update(uri, values, null, null) > 0
    }

    suspend fun deleteEvent(eventId: Long): Boolean = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext false
        val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
        resolver.delete(uri, null, null) > 0
    }

    /** Partial update of just the DESCRIPTION column (task done-marker toggle). */
    suspend fun updateDescription(eventId: Long, description: String): Boolean =
        withContext(Dispatchers.IO) {
            if (!hasWritePermission()) return@withContext false
            val values = ContentValues().apply {
                put(CalendarContract.Events.DESCRIPTION, description)
            }
            val uri = ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId)
            resolver.update(uri, values, null, null) > 0
        }

    /** Current DESCRIPTION of an event (for marker toggles). */
    suspend fun loadDescription(eventId: Long): String? = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext null
        resolver.query(
            ContentUris.withAppendedId(CalendarContract.Events.CONTENT_URI, eventId),
            arrayOf(CalendarContract.Events.DESCRIPTION),
            null,
            null,
            null,
        )?.use { cursor ->
            if (cursor.moveToFirst()) return@withContext cursor.getString(0)
        }
        null
    }

    /**
     * Asks the sync framework to sync every calendar account now. The system
     * throttles this, so calling it on every app resume is fine.
     */
    fun requestSync() {
        val extras = android.os.Bundle().apply {
            putBoolean(android.content.ContentResolver.SYNC_EXTRAS_MANUAL, true)
            putBoolean(android.content.ContentResolver.SYNC_EXTRAS_EXPEDITED, true)
        }
        android.content.ContentResolver.requestSync(null, CalendarContract.AUTHORITY, extras)
    }

    /** Drag & drop copy: duplicates an event, shifted by [days] whole days. */
    suspend fun duplicateEventTo(eventId: Long, days: Long): Boolean =
        duplicateEventToReturningId(eventId, days) != null

    /** Like [duplicateEventTo] but returns the new event id (for batch undo). */
    suspend fun duplicateEventToReturningId(eventId: Long, days: Long): Long? {
        val newId = duplicateEvent(eventId) ?: return null
        if (days != 0L && !moveEventByDays(newId, days)) {
            deleteEvent(newId)
            return null
        }
        return newId
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
                eventColor = source.pickedColor(),
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
                eventColor = source.pickedColor(),
            )
        }
        return createEvent(draft)
    }

    // ---------- ICS import / export ----------

    /**
     * Events of the shown calendars as [Ics.Event]s, ready to be written to
     * an .ics file. Recurrence exceptions are skipped (the parent series
     * carries the rule); soft-deleted rows are skipped like everywhere else.
     */
    suspend fun exportIcsEvents(
        hiddenCalendarIds: Set<Long> = emptySet(),
    ): List<Ics.Event> = withContext(Dispatchers.IO) {
        if (!hasReadPermission()) return@withContext emptyList()
        val calendarIds = loadCalendars()
            .filter { it.isVisible && it.id !in hiddenCalendarIds }
            .map { it.id }
        if (calendarIds.isEmpty()) return@withContext emptyList()

        val projection = arrayOf(
            CalendarContract.Events._ID,
            CalendarContract.Events.TITLE,
            CalendarContract.Events.DESCRIPTION,
            CalendarContract.Events.EVENT_LOCATION,
            CalendarContract.Events.DTSTART,
            CalendarContract.Events.DTEND,
            CalendarContract.Events.DURATION,
            CalendarContract.Events.ALL_DAY,
            CalendarContract.Events.RRULE,
        )
        val selection = "${CalendarContract.Events.DELETED} = 0 AND " +
            "${CalendarContract.Events.ORIGINAL_ID} IS NULL AND " +
            "${CalendarContract.Events.CALENDAR_ID} IN " +
            "(${calendarIds.joinToString(",")})"

        val events = mutableListOf<Ics.Event>()
        resolver.query(
            CalendarContract.Events.CONTENT_URI,
            projection,
            selection,
            null,
            "${CalendarContract.Events.DTSTART} ASC",
        )?.use { cursor ->
            while (cursor.moveToNext()) {
                if (cursor.isNull(4)) continue
                val allDay = cursor.getInt(7) != 0
                val dtStart = cursor.getLong(4)
                val dtEnd = if (cursor.isNull(5)) null else cursor.getLong(5)
                val duration = cursor.getString(6)
                events += Ics.Event(
                    summary = cursor.getString(1).orEmpty(),
                    description = cursor.getString(2),
                    location = cursor.getString(3),
                    startMillis = dtStart,
                    endMillis = dtEnd
                        ?: (dtStart + parseDurationMillis(duration, allDay)),
                    allDay = allDay,
                    rrule = cursor.getString(8),
                    durationSpec = if (dtEnd == null) duration else null,
                    uid = "koyomi-${cursor.getLong(0)}@koyomi.app",
                )
            }
        }
        events
    }

    /** Inserts parsed .ics events into [calendarId]. Returns how many succeeded. */
    suspend fun importIcsEvents(
        events: List<Ics.Event>,
        calendarId: Long,
    ): Int = withContext(Dispatchers.IO) {
        if (!hasWritePermission()) return@withContext 0
        val zone = ZoneId.systemDefault()
        var imported = 0
        for (event in events) {
            val values = ContentValues().apply {
                put(CalendarContract.Events.CALENDAR_ID, calendarId)
                put(CalendarContract.Events.TITLE, event.summary)
                event.description?.takeIf { it.isNotBlank() }?.let {
                    put(CalendarContract.Events.DESCRIPTION, it)
                }
                event.location?.takeIf { it.isNotBlank() }?.let {
                    put(CalendarContract.Events.EVENT_LOCATION, it)
                }
                put(CalendarContract.Events.ALL_DAY, if (event.allDay) 1 else 0)
                put(CalendarContract.Events.DTSTART, event.startMillis)
                put(
                    CalendarContract.Events.EVENT_TIMEZONE,
                    if (event.allDay) "UTC" else zone.id,
                )
                if (event.rrule.isNullOrBlank()) {
                    put(CalendarContract.Events.DTEND, event.endMillis)
                } else {
                    put(CalendarContract.Events.RRULE, event.rrule)
                    val durationMillis = event.endMillis - event.startMillis
                    put(
                        CalendarContract.Events.DURATION,
                        if (event.allDay) {
                            "P${(durationMillis / 86_400_000L).coerceAtLeast(1)}D"
                        } else {
                            "P${(durationMillis / 1000L).coerceAtLeast(60)}S"
                        },
                    )
                }
            }
            val inserted = runCatching {
                resolver.insert(CalendarContract.Events.CONTENT_URI, values)
            }.getOrNull()
            if (inserted != null) imported++
        }
        imported
    }

    /** The user-picked event color of an existing event, if any. */
    private fun EventDetails.pickedColor(): EventColor? = when {
        !eventColorKey.isNullOrBlank() -> EventColor(eventColorKey, eventColor)
        eventColor != 0 -> EventColor(null, eventColor)
        else -> null
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

        // User-picked event color: prefer the synced palette key (kept in sync
        // with Google Calendar); fall back to a raw ARGB for local calendars.
        // Null clears both so the calendar's default color shows again.
        when {
            eventColor?.key != null -> {
                values.put(CalendarContract.Events.EVENT_COLOR_KEY, eventColor.key)
            }
            eventColor != null -> {
                values.putNull(CalendarContract.Events.EVENT_COLOR_KEY)
                values.put(CalendarContract.Events.EVENT_COLOR, eventColor.color)
            }
            else -> {
                values.putNull(CalendarContract.Events.EVENT_COLOR_KEY)
                values.putNull(CalendarContract.Events.EVENT_COLOR)
            }
        }

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
