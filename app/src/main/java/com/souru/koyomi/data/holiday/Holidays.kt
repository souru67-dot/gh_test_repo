package com.souru.koyomi.data.holiday

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.Locale

/** A country's public-holiday calendar. */
interface HolidayCalendar {
    fun holidaysFor(year: Int): Map<LocalDate, String>
    fun nameFor(date: LocalDate): String? = holidaysFor(date.year)[date]
}

/** Countries whose public holidays こよみ can highlight. */
enum class HolidayCountry(val code: String) {
    JAPAN("JP"),
    UNITED_STATES("US"),
    UNITED_KINGDOM("GB"),
    SOUTH_KOREA("KR"),
    NONE("NONE");

    companion object {
        fun fromCode(code: String?): HolidayCountry? = entries.find { it.code == code }

        /**
         * A sensible default from the device locale: the region if we support
         * it, else nothing (showing a country's holidays that aren't the
         * user's would be worse than showing none).
         */
        fun fromLocale(locale: Locale): HolidayCountry = when (locale.country) {
            "JP" -> JAPAN
            "US" -> UNITED_STATES
            "GB" -> UNITED_KINGDOM
            "KR" -> SOUTH_KOREA
            else -> when (locale.language) {
                "ja" -> JAPAN
                "ko" -> SOUTH_KOREA
                else -> NONE
            }
        }
    }
}

/**
 * The active holiday calendar. [country] is kept in sync with the user's
 * setting by an app-level observer, so the many static call sites (grids,
 * widgets, timeline) stay simple. Defaults to the device locale until the
 * stored preference loads.
 */
object Holidays {

    @Volatile
    var country: HolidayCountry = HolidayCountry.fromLocale(Locale.getDefault())

    private fun calendar(c: HolidayCountry): HolidayCalendar = when (c) {
        HolidayCountry.JAPAN -> JapaneseHolidays
        HolidayCountry.UNITED_STATES -> UsHolidays
        HolidayCountry.UNITED_KINGDOM -> UkHolidays
        HolidayCountry.SOUTH_KOREA -> KoreanHolidays
        HolidayCountry.NONE -> EmptyHolidays
    }

    fun nameFor(date: LocalDate): String? = calendar(country).nameFor(date)

    fun isHoliday(date: LocalDate): Boolean = nameFor(date) != null

    /** True when [date] should be tinted like Sunday (Sunday itself or a holiday). */
    fun isRedDay(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SUNDAY || isHoliday(date)
}

/** No holidays — for regions こよみ doesn't have data for yet. */
object EmptyHolidays : HolidayCalendar {
    override fun holidaysFor(year: Int): Map<LocalDate, String> = emptyMap()
}

// ---- Shared date helpers for the algorithmic calendars ----

internal fun nthWeekday(year: Int, month: Int, weekday: DayOfWeek, n: Int): LocalDate {
    var d = LocalDate.of(year, month, 1)
    while (d.dayOfWeek != weekday) d = d.plusDays(1)
    return d.plusWeeks((n - 1).toLong())
}

internal fun lastWeekday(year: Int, month: Int, weekday: DayOfWeek): LocalDate {
    var d = LocalDate.of(year, month, 1).plusMonths(1).minusDays(1)
    while (d.dayOfWeek != weekday) d = d.minusDays(1)
    return d
}

/** Gregorian Easter Sunday (Anonymous Gregorian / "computus" algorithm). */
internal fun easterSunday(year: Int): LocalDate {
    val a = year % 19
    val b = year / 100
    val c = year % 100
    val d = b / 4
    val e = b % 4
    val f = (b + 8) / 25
    val g = (b - f + 1) / 3
    val h = (19 * a + b - d - g + 15) % 30
    val i = c / 4
    val k = c % 4
    val l = (32 + 2 * e + 2 * i - h - k) % 7
    val m = (a + 11 * h + 22 * l) / 451
    val month = (h + l - 7 * m + 114) / 31
    val day = ((h + l - 7 * m + 114) % 31) + 1
    return LocalDate.of(year, month, day)
}
