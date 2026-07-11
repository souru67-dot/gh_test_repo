package com.souru.koyomi.data.model

import java.time.LocalDate

/** A calendar account row from CalendarContract.Calendars. */
data class CalendarInfo(
    val id: Long,
    val displayName: String,
    val accountName: String,
    val color: Int,
    val isWritable: Boolean,
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
)

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
