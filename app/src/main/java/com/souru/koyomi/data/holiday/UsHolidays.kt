package com.souru.koyomi.data.holiday

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * US federal holidays. Fixed-date holidays that land on a weekend get an
 * "(observed)" day (Sat → Friday, Sun → Monday), the federal convention.
 */
object UsHolidays : HolidayCalendar {

    private val cache = ConcurrentHashMap<Int, Map<LocalDate, String>>()

    override fun holidaysFor(year: Int): Map<LocalDate, String> =
        cache.getOrPut(year) { compute(year) }

    private fun compute(year: Int): Map<LocalDate, String> {
        if (year < 1900 || year > 2100) return emptyMap()
        val map = sortedMapOf<LocalDate, String>()

        fun fixed(month: Int, day: Int, name: String) {
            val date = LocalDate.of(year, month, day)
            map[date] = name
            when (date.dayOfWeek) {
                DayOfWeek.SATURDAY -> map.putIfAbsent(date.minusDays(1), "$name (observed)")
                DayOfWeek.SUNDAY -> map.putIfAbsent(date.plusDays(1), "$name (observed)")
                else -> {}
            }
        }

        fixed(1, 1, "New Year's Day")
        if (year >= 1986) map[nthWeekday(year, 1, DayOfWeek.MONDAY, 3)] = "Martin Luther King Jr. Day"
        map[nthWeekday(year, 2, DayOfWeek.MONDAY, 3)] = "Presidents' Day"
        map[lastWeekday(year, 5, DayOfWeek.MONDAY)] = "Memorial Day"
        if (year >= 2021) fixed(6, 19, "Juneteenth")
        fixed(7, 4, "Independence Day")
        map[nthWeekday(year, 9, DayOfWeek.MONDAY, 1)] = "Labor Day"
        map[nthWeekday(year, 10, DayOfWeek.MONDAY, 2)] = "Columbus Day"
        fixed(11, 11, "Veterans Day")
        map[nthWeekday(year, 11, DayOfWeek.THURSDAY, 4)] = "Thanksgiving"
        fixed(12, 25, "Christmas Day")
        return map
    }
}
