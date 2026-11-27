package com.souru.koyomi.data.holiday

import com.souru.koyomi.data.rokuyo.Kyureki
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * South Korean public holidays: fixed solar dates plus the three lunar
 * festivals (Seollal and Chuseok, three days each; Buddha's Birthday). The
 * lunar dates reuse こよみ's astronomical converter (Korea shares Japan's
 * UTC+9, so the new-moon dates match). Government-announced substitute
 * holidays are NOT included, as they can't be computed reliably.
 */
object KoreanHolidays : HolidayCalendar {

    private val cache = ConcurrentHashMap<Int, Map<LocalDate, String>>()

    override fun holidaysFor(year: Int): Map<LocalDate, String> =
        cache.getOrPut(year) { compute(year) }

    private fun compute(year: Int): Map<LocalDate, String> {
        if (year < 1900 || year > 2099) return emptyMap()
        val map = sortedMapOf<LocalDate, String>()

        map[LocalDate.of(year, 1, 1)] = "New Year's Day"
        map[LocalDate.of(year, 3, 1)] = "Independence Movement Day"
        map[LocalDate.of(year, 5, 5)] = "Children's Day"
        map[LocalDate.of(year, 6, 6)] = "Memorial Day"
        map[LocalDate.of(year, 8, 15)] = "Liberation Day"
        map[LocalDate.of(year, 10, 3)] = "National Foundation Day"
        map[LocalDate.of(year, 10, 9)] = "Hangeul Day"
        map[LocalDate.of(year, 12, 25)] = "Christmas Day"

        // Seollal (lunar new year): the day itself plus the day before/after.
        solarOf(year, lunarMonth = 1, lunarDay = 1)?.let { seollal ->
            map[seollal.minusDays(1)] = "Seollal Holiday"
            map[seollal] = "Seollal"
            map[seollal.plusDays(1)] = "Seollal Holiday"
        }
        // Chuseok (harvest festival): lunar 8/15, plus the day before/after.
        solarOf(year, lunarMonth = 8, lunarDay = 15)?.let { chuseok ->
            map[chuseok.minusDays(1)] = "Chuseok Holiday"
            map[chuseok] = "Chuseok"
            map[chuseok.plusDays(1)] = "Chuseok Holiday"
        }
        // Buddha's Birthday: lunar 4/8.
        solarOf(year, lunarMonth = 4, lunarDay = 8)?.let {
            map[it] = "Buddha's Birthday"
        }
        return map
    }

    /** The solar date in [year] for a (non-leap) lunar month/day, or null. */
    private fun solarOf(year: Int, lunarMonth: Int, lunarDay: Int): LocalDate? {
        var d = LocalDate.of(year, 1, 1)
        val end = LocalDate.of(year, 12, 31)
        while (!d.isAfter(end)) {
            val lunar = Kyureki.lunarDateFor(d)
            if (lunar != null && !lunar.isLeapMonth &&
                lunar.month == lunarMonth && lunar.day == lunarDay
            ) {
                return d
            }
            d = d.plusDays(1)
        }
        return null
    }
}
