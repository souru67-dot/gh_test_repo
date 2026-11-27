package com.souru.koyomi.util

import java.time.Instant
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/**
 * Minimal RFC 5545 (.ics) reader/writer covering what a calendar exchange
 * actually needs: VEVENT with DTSTART/DTEND (date or date-time, TZID or UTC),
 * SUMMARY/LOCATION/DESCRIPTION with text escaping, RRULE passthrough and
 * line folding/unfolding. Pure Kotlin, unit-testable.
 */
object Ics {

    data class Event(
        val summary: String,
        val description: String? = null,
        val location: String? = null,
        /** For all-day events: UTC midnight of the first day. */
        val startMillis: Long,
        /** Exclusive end. For all-day events: UTC midnight after the last day. */
        val endMillis: Long,
        val allDay: Boolean,
        val rrule: String? = null,
        /** Raw RFC 5545 DURATION to emit instead of DTEND (recurring exports). */
        val durationSpec: String? = null,
        val uid: String? = null,
    )

    private val DATE = DateTimeFormatter.BASIC_ISO_DATE
    private val UTC_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss'Z'")
    private val LOCAL_STAMP = DateTimeFormatter.ofPattern("yyyyMMdd'T'HHmmss")

    // ---------- Writing ----------

    fun write(
        events: List<Event>,
        nowMillis: Long = System.currentTimeMillis(),
    ): String {
        val out = StringBuilder()
        fun line(s: String) = out.append(fold(s)).append("\r\n")

        line("BEGIN:VCALENDAR")
        line("VERSION:2.0")
        line("PRODID:-//Koyomi//Koyomi Calendar//JA")
        line("CALSCALE:GREGORIAN")
        val stamp = UTC_STAMP.format(Instant.ofEpochMilli(nowMillis).atOffset(ZoneOffset.UTC))
        events.forEachIndexed { index, event ->
            line("BEGIN:VEVENT")
            line("UID:" + (event.uid ?: "koyomi-$nowMillis-$index@koyomi.app"))
            line("DTSTAMP:$stamp")
            if (event.allDay) {
                val start = Instant.ofEpochMilli(event.startMillis)
                    .atOffset(ZoneOffset.UTC).toLocalDate()
                line("DTSTART;VALUE=DATE:" + DATE.format(start))
                if (event.durationSpec != null) {
                    line("DURATION:" + event.durationSpec)
                } else {
                    val end = Instant.ofEpochMilli(event.endMillis)
                        .atOffset(ZoneOffset.UTC).toLocalDate()
                    line("DTEND;VALUE=DATE:" + DATE.format(maxOf(end, start.plusDays(1))))
                }
            } else {
                line(
                    "DTSTART:" +
                        UTC_STAMP.format(Instant.ofEpochMilli(event.startMillis).atOffset(ZoneOffset.UTC)),
                )
                if (event.durationSpec != null) {
                    line("DURATION:" + event.durationSpec)
                } else {
                    line(
                        "DTEND:" +
                            UTC_STAMP.format(Instant.ofEpochMilli(event.endMillis).atOffset(ZoneOffset.UTC)),
                    )
                }
            }
            line("SUMMARY:" + escape(event.summary))
            event.location?.takeIf { it.isNotBlank() }?.let { line("LOCATION:" + escape(it)) }
            event.description?.takeIf { it.isNotBlank() }?.let {
                line("DESCRIPTION:" + escape(it))
            }
            event.rrule?.takeIf { it.isNotBlank() }?.let { line("RRULE:$it") }
            line("END:VEVENT")
        }
        line("END:VCALENDAR")
        return out.toString()
    }

    private fun escape(text: String): String = buildString {
        for (ch in text) {
            when (ch) {
                '\\' -> append("\\\\")
                ';' -> append("\\;")
                ',' -> append("\\,")
                '\n' -> append("\\n")
                '\r' -> Unit
                else -> append(ch)
            }
        }
    }

    /** RFC 5545 folding: lines longer than 75 octets continue after CRLF+space. */
    private fun fold(line: String): String {
        val bytes = line.toByteArray(Charsets.UTF_8)
        if (bytes.size <= MAX_OCTETS) return line
        val out = StringBuilder()
        var octets = 0
        var index = 0
        while (index < line.length) {
            val codePoint = line.codePointAt(index)
            val chars = Character.charCount(codePoint)
            val size = String(Character.toChars(codePoint)).toByteArray(Charsets.UTF_8).size
            if (octets + size > MAX_OCTETS) {
                out.append("\r\n ")
                octets = 1 // the leading space counts
            }
            out.appendCodePoint(codePoint)
            octets += size
            index += chars
        }
        return out.toString()
    }

    private const val MAX_OCTETS = 74

    // ---------- Parsing ----------

    fun parse(text: String, defaultZone: ZoneId = ZoneId.systemDefault()): List<Event> {
        val lines = unfold(text)
        val events = mutableListOf<Event>()
        var current: MutableMap<String, Pair<Map<String, String>, String>>? = null

        for (raw in lines) {
            val colon = raw.indexOf(':')
            if (colon < 0) continue
            val head = raw.substring(0, colon)
            val value = raw.substring(colon + 1)
            val parts = head.split(';')
            val name = parts[0].uppercase()
            val params = parts.drop(1).mapNotNull { param ->
                val eq = param.indexOf('=')
                if (eq < 0) null else param.substring(0, eq).uppercase() to param.substring(eq + 1)
            }.toMap()

            when {
                name == "BEGIN" && value.equals("VEVENT", ignoreCase = true) ->
                    current = mutableMapOf()
                name == "END" && value.equals("VEVENT", ignoreCase = true) -> {
                    current?.let { fields ->
                        toEvent(fields, defaultZone)?.let(events::add)
                    }
                    current = null
                }
                else -> current?.put(name, params to value)
            }
        }
        return events
    }

    private fun unfold(text: String): List<String> {
        val result = mutableListOf<String>()
        for (line in text.split("\r\n", "\n")) {
            if ((line.startsWith(" ") || line.startsWith("\t")) && result.isNotEmpty()) {
                result[result.size - 1] = result.last() + line.substring(1)
            } else {
                result.add(line)
            }
        }
        return result
    }

    private fun toEvent(
        fields: Map<String, Pair<Map<String, String>, String>>,
        defaultZone: ZoneId,
    ): Event? {
        val (startParams, startValue) = fields["DTSTART"] ?: return null
        val start = parseDateTime(startValue, startParams, defaultZone) ?: return null

        val end = fields["DTEND"]?.let { (params, value) ->
            parseDateTime(value, params, defaultZone)
        }
        val durationMillis = fields["DURATION"]?.second?.let(::parseDuration)

        val endMillis = when {
            end != null -> end.millis
            durationMillis != null -> start.millis + durationMillis
            start.allDay -> start.millis + DAY_MILLIS
            else -> start.millis + HOUR_MILLIS
        }

        return Event(
            summary = fields["SUMMARY"]?.second?.let(::unescape).orEmpty(),
            description = fields["DESCRIPTION"]?.second?.let(::unescape),
            location = fields["LOCATION"]?.second?.let(::unescape),
            startMillis = start.millis,
            endMillis = maxOf(endMillis, start.millis),
            allDay = start.allDay,
            rrule = fields["RRULE"]?.second?.takeIf { it.isNotBlank() },
            uid = fields["UID"]?.second,
        )
    }

    private data class ParsedTime(val millis: Long, val allDay: Boolean)

    private fun parseDateTime(
        value: String,
        params: Map<String, String>,
        defaultZone: ZoneId,
    ): ParsedTime? = runCatching {
        val v = value.trim()
        when {
            params["VALUE"] == "DATE" || !v.contains('T') -> {
                val date = LocalDate.parse(v.take(8), DATE)
                ParsedTime(date.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(), true)
            }
            v.endsWith("Z") -> {
                val local = LocalDateTime.parse(v.dropLast(1), LOCAL_STAMP)
                ParsedTime(local.toInstant(ZoneOffset.UTC).toEpochMilli(), false)
            }
            else -> {
                val zone = params["TZID"]?.let { tzid ->
                    runCatching { ZoneId.of(tzid) }.getOrNull()
                } ?: defaultZone
                val local = LocalDateTime.parse(v, LOCAL_STAMP)
                ParsedTime(local.atZone(zone).toInstant().toEpochMilli(), false)
            }
        }
    }.getOrNull()

    /** RFC 5545 durations: P1D, PT1H30M, P1W, P900S (CalendarProvider style)... */
    fun parseDuration(spec: String): Long? {
        val d = spec.trim().uppercase()
        return runCatching {
            when {
                d.matches(Regex("-?P\\d+W")) ->
                    d.removePrefix("-").drop(1).dropLast(1).toLong() * 7 * DAY_MILLIS
                d.matches(Regex("-?P\\d+S")) ->
                    d.removePrefix("-").drop(1).dropLast(1).toLong() * 1000L
                else -> java.time.Duration.parse(d).toMillis()
            }
        }.getOrNull()
    }

    private fun unescape(text: String): String = buildString {
        var i = 0
        while (i < text.length) {
            val ch = text[i]
            if (ch == '\\' && i + 1 < text.length) {
                when (val next = text[i + 1]) {
                    'n', 'N' -> append('\n')
                    else -> append(next)
                }
                i += 2
            } else {
                append(ch)
                i += 1
            }
        }
    }

    private const val DAY_MILLIS = 86_400_000L
    private const val HOUR_MILLIS = 3_600_000L
}
