package com.souru.koyomi.data.model

import java.time.LocalDate

/** A calendar row from CalendarContract.Calendars. */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
    val isWritable: Boolean,
    /** Provider-level visibility (Calendars.VISIBLE). */
    val isVisible: Boolean = true,
)

/**
 * One occurrence of an event, as returned by CalendarContract.Instances.
 * [startDate]/[endDate] are the local dates the instance covers (inclusive),
 * already resolved for the all-day/UTC convention of CalendarProvider.
 */
data class EventInstance(
    val eventId: Long,
    val title: String,
    val begin: Long,
    val end: Long,
    val allDay: Boolean,
    val color: Int,
    val calendarId: Long,
    val location: String?,
    val startDate: LocalDate,
    val endDate: LocalDate,
    /** Marker-tagged task events (see [TaskMarker]); shown as to-dos, not chips. */
    val isTask: Boolean = false,
    val isDone: Boolean = false,
)

/**
 * Tasks are ordinary calendar events carrying these markers in DESCRIPTION,
 * so they sync through Google Calendar like everything else. Completion is
 * toggled by adding/removing [DONE].
 */
object TaskMarker {
    const val TASK = "#koyomi-task"
    const val DONE = "#koyomi-done"

    fun isTask(description: String?): Boolean = description?.contains(TASK) == true
    fun isDone(description: String?): Boolean = description?.contains(DONE) == true

    fun withDone(description: String?, done: Boolean): String {
        val base = (description.orEmpty()).replace(DONE, "").trimEnd()
        return if (done) "$base $DONE".trim() else base
    }

    /**
     * Splits a description into (visible memo, marker suffix) so the editor
     * never shows the machine markers — and can't accidentally erase them.
     */
    fun split(description: String?): Pair<String, String> {
        val text = description.orEmpty()
        val markers = buildList {
            if (text.contains(TASK)) add(TASK)
            if (text.contains(DONE)) add(DONE)
        }.joinToString(" ")
        val memo = text.replace(TASK, "").replace(DONE, "").trim()
        return memo to markers
    }

    fun join(memo: String, markers: String): String =
        listOf(memo.trim(), markers.trim()).filter { it.isNotEmpty() }.joinToString("\n")
}

/** Full event row, loaded lazily for the detail view / editor. */
data class EventDetails(
    val id: Long,
    val calendarId: Long,
    val title: String,
    val allDay: Boolean,
    val dtStart: Long,
    val dtEnd: Long?,
    val duration: String?,
    val timeZone: String?,
    val location: String?,
    val description: String?,
    val rrule: String?,
    val reminderMinutes: Int?,
    /** Sync adapter's color key (Google user palette) if the event has one. */
    val eventColorKey: String? = null,
    val eventColor: Int = 0,
)

/**
 * One selectable event color. [key] identifies a color from the account's
 * synced palette (CalendarContract.Colors); a null key is a plain ARGB value
 * for accounts without a palette (e.g. the local calendar).
 */
data class EventColor(
    val key: String?,
    val color: Int,
)

/**
 * What the editor writes back to CalendarProvider.
 *
 * Timed events: [startMillis]/[endMillis] are plain epoch millis (end exclusive).
 * All-day events: both are local-zone midnights of calendar dates, and [endMillis]
 * is the LAST covered day (inclusive) — the repository converts to the provider's
 * exclusive-UTC-midnight convention on write.
 */
data class EventDraft(
    val id: Long? = null,
    val calendarId: Long,
    val title: String,
    val allDay: Boolean,
    val startMillis: Long,
    val endMillis: Long,
    val location: String = "",
    val description: String = "",
    val rrule: String? = null,
    val reminderMinutes: Int? = null,
    /** null = use the calendar's default color. */
    val eventColor: EventColor? = null,
)
