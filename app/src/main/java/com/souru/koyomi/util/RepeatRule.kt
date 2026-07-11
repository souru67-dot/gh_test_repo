package com.souru.koyomi.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.format.DateTimeFormatter

enum class RepeatFreq { NONE, DAILY, WEEKLY, MONTHLY, YEARLY, CUSTOM }

/**
 * The subset of RFC 5545 RRULE the editor can model: FREQ, weekly BYDAY and
 * UNTIL. Anything richer (INTERVAL>1, COUNT, monthly BYDAY...) round-trips
 * untouched as [RepeatFreq.CUSTOM] with the original string in [raw].
 */
data class RepeatRule(
    val freq: RepeatFreq = RepeatFreq.NONE,
    /** Weekly only. Empty means "on the start day". */
    val byDays: Set<DayOfWeek> = emptySet(),
    /** Last day (inclusive) the event repeats, or null for forever. */
    val until: LocalDate? = null,
    /** Original RRULE for CUSTOM. */
    val raw: String? = null,
) {

    fun toRRule(): String? = when (freq) {
        RepeatFreq.NONE -> null
        RepeatFreq.CUSTOM -> raw
        else -> buildString {
            append("FREQ=").append(freq.name)
            if (freq == RepeatFreq.WEEKLY && byDays.isNotEmpty()) {
                append(";BYDAY=")
                append(
                    byDays.sortedBy { it.value }.joinToString(",") { DAY_CODES.getValue(it) },
                )
            }
            until?.let {
                // CalendarProvider expects UNTIL as a UTC date-time.
                append(";UNTIL=")
                append(it.format(DateTimeFormatter.BASIC_ISO_DATE))
                append("T235959Z")
            }
        }
    }

    companion object {
        private val DAY_CODES = mapOf(
            DayOfWeek.MONDAY to "MO",
            DayOfWeek.TUESDAY to "TU",
            DayOfWeek.WEDNESDAY to "WE",
            DayOfWeek.THURSDAY to "TH",
            DayOfWeek.FRIDAY to "FR",
            DayOfWeek.SATURDAY to "SA",
            DayOfWeek.SUNDAY to "SU",
        )
        private val CODE_DAYS = DAY_CODES.entries.associate { (day, code) -> code to day }

        fun parse(rrule: String?): RepeatRule {
            if (rrule.isNullOrBlank()) return RepeatRule()
            val custom = RepeatRule(freq = RepeatFreq.CUSTOM, raw = rrule)

            val parts = rrule.split(";").filter { it.isNotBlank() }
            val map = mutableMapOf<String, String>()
            for (part in parts) {
                val pieces = part.split("=", limit = 2)
                if (pieces.size != 2) return custom
                map[pieces[0].uppercase()] = pieces[1]
            }
            if (map.keys.any { it !in setOf("FREQ", "BYDAY", "UNTIL", "WKST", "INTERVAL") }) {
                return custom
            }
            if ((map["INTERVAL"]?.toIntOrNull() ?: 1) != 1) return custom

            val freq = when (map["FREQ"]) {
                "DAILY" -> RepeatFreq.DAILY
                "WEEKLY" -> RepeatFreq.WEEKLY
                "MONTHLY" -> RepeatFreq.MONTHLY
                "YEARLY" -> RepeatFreq.YEARLY
                else -> return custom
            }

            val byDays = map["BYDAY"]?.let { value ->
                if (freq != RepeatFreq.WEEKLY) return custom
                val days = value.split(",").map { CODE_DAYS[it.trim()] ?: return custom }
                days.toSet()
            } ?: emptySet()

            val until = map["UNTIL"]?.let { value ->
                if (value.length < 8) return custom
                runCatching {
                    LocalDate.parse(value.take(8), DateTimeFormatter.BASIC_ISO_DATE)
                }.getOrNull() ?: return custom
            }

            return RepeatRule(freq = freq, byDays = byDays, until = until)
        }
    }
}
