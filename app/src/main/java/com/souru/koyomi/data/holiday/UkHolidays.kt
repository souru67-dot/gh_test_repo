package com.souru.koyomi.data.holiday

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * UK bank holidays (England & Wales). Fixed-date holidays landing on a
 * weekend get a substitute day on the next free weekday, as the government
 * schedules them.
 */
object UkHolidays : HolidayCalendar {

    private val cache = ConcurrentHashMap<Int, Map<LocalDate, String>>()

    override fun holidaysFor(year: Int): Map<LocalDate, String> =
        cache.getOrPut(year) { compute(year) }

    private fun compute(year: Int): Map<LocalDate, String> {
        if (year < 1900 || year > 2100) return emptyMap()
        val map = sortedMapOf<LocalDate, String>()

        fun substitute(date: LocalDate, name: String) {
            if (date.dayOfWeek == DayOfWeek.SATURDAY || date.dayOfWeek == DayOfWeek.SUNDAY) {
                var sub = date
                while (sub.dayOfWeek == DayOfWeek.SATURDAY ||
                    sub.dayOfWeek == DayOfWeek.SUNDAY ||
                    map.containsKey(sub)
                ) {
                    sub = sub.plusDays(1)
                }
                map[sub] = "$name (substitute day)"
            }
        }

        val newYear = LocalDate.of(year, 1, 1)
        map[newYear] = "New Year's Day"
        substitute(newYear, "New Year's Day")

        val easter = easterSunday(year)
        map[easter.minusDays(2)] = "Good Friday"
        map[easter.plusDays(1)] = "Easter Monday"

        map[nthWeekday(year, 5, DayOfWeek.MONDAY, 1)] = "Early May bank holiday"
        map[lastWeekday(year, 5, DayOfWeek.MONDAY)] = "Spring bank holiday"
        map[lastWeekday(year, 8, DayOfWeek.MONDAY)] = "Summer bank holiday"

        val christmas = LocalDate.of(year, 12, 25)
        val boxing = LocalDate.of(year, 12, 26)
        map[christmas] = "Christmas Day"
        map[boxing] = "Boxing Day"
        substitute(christmas, "Christmas Day")
        substitute(boxing, "Boxing Day")
        return map
    }
}
