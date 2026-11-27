package com.souru.koyomi.data.holiday

import java.time.DayOfWeek
import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap

/**
 * Japanese national holidays computed from the Public Holiday Law
 * (fixed dates, happy-monday rules, equinox approximation, substitute
 * holidays and citizens' holidays). Accurate for 1980..2099, including
 * the 2019-2021 one-off changes (enthronement, Olympic moves).
 */
object JapaneseHolidays : HolidayCalendar {

    private val cache = ConcurrentHashMap<Int, Map<LocalDate, String>>()

    override fun holidaysFor(year: Int): Map<LocalDate, String> =
        cache.getOrPut(year) { compute(year) }

    override fun nameFor(date: LocalDate): String? = holidaysFor(date.year)[date]

    fun isHoliday(date: LocalDate): Boolean = nameFor(date) != null

    private fun compute(year: Int): Map<LocalDate, String> {
        if (year < 1980 || year > 2099) return emptyMap()
        val base = sortedMapOf<LocalDate, String>()
        fun add(month: Int, day: Int, name: String) {
            base[LocalDate.of(year, month, day)] = name
        }
        fun nthMonday(month: Int, n: Int): LocalDate {
            var d = LocalDate.of(year, month, 1)
            while (d.dayOfWeek != DayOfWeek.MONDAY) d = d.plusDays(1)
            return d.plusWeeks((n - 1).toLong())
        }

        add(1, 1, "元日")
        if (year >= 2000) {
            base[nthMonday(1, 2)] = "成人の日"
        } else {
            add(1, 15, "成人の日")
        }
        add(2, 11, "建国記念の日")
        if (year >= 2020) add(2, 23, "天皇誕生日")
        add(3, equinoxDay(year, vernal = true), "春分の日")
        when {
            year >= 2007 -> add(4, 29, "昭和の日")
            year >= 1989 -> add(4, 29, "みどりの日")
            else -> add(4, 29, "天皇誕生日")
        }
        add(5, 3, "憲法記念日")
        if (year >= 2007) add(5, 4, "みどりの日")
        add(5, 5, "こどもの日")
        when {
            year == 2020 -> add(7, 23, "海の日")
            year == 2021 -> add(7, 22, "海の日")
            year >= 2003 -> base[nthMonday(7, 3)] = "海の日"
            year >= 1996 -> add(7, 20, "海の日")
        }
        when {
            year == 2020 -> add(8, 10, "山の日")
            year == 2021 -> add(8, 8, "山の日")
            year >= 2016 -> add(8, 11, "山の日")
        }
        if (year >= 2003) {
            base[nthMonday(9, 3)] = "敬老の日"
        } else if (year >= 1966) {
            add(9, 15, "敬老の日")
        }
        add(9, equinoxDay(year, vernal = false), "秋分の日")
        when {
            year == 2020 -> add(7, 24, "スポーツの日")
            year == 2021 -> add(7, 23, "スポーツの日")
            year >= 2020 -> base[nthMonday(10, 2)] = "スポーツの日"
            year >= 2000 -> base[nthMonday(10, 2)] = "体育の日"
            else -> add(10, 10, "体育の日")
        }
        add(11, 3, "文化の日")
        add(11, 23, "勤労感謝の日")
        if (year in 1989..2018) add(12, 23, "天皇誕生日")

        // One-off holidays.
        when (year) {
            1989 -> add(2, 24, "昭和天皇の大喪の礼")
            1990 -> add(11, 12, "即位礼正殿の儀")
            1993 -> add(6, 9, "皇太子徳仁親王の結婚の儀")
            2019 -> {
                add(5, 1, "天皇の即位の日")
                add(10, 22, "即位礼正殿の儀")
            }
        }

        val result = base.toMutableMap()

        // 国民の休日: a weekday sandwiched between two holidays becomes a holiday
        // (introduced 1986; e.g. 2019-04-30, 2019-05-02, silver week Septembers).
        if (year >= 1986) {
            for ((date, _) in base) {
                val next = date.plusDays(2)
                if (base.containsKey(next)) {
                    val between = date.plusDays(1)
                    if (!result.containsKey(between) && between.dayOfWeek != DayOfWeek.SUNDAY) {
                        result[between] = "国民の休日"
                    }
                }
            }
        }

        // 振替休日: a holiday falling on Sunday rolls to the next non-holiday day
        // (since 1973; "next non-holiday" form since 2007, identical for earlier years).
        for ((date, _) in base) {
            if (date.dayOfWeek == DayOfWeek.SUNDAY) {
                var sub = date.plusDays(1)
                while (result.containsKey(sub)) sub = sub.plusDays(1)
                result[sub] = "振替休日"
            }
        }
        return result
    }

    /**
     * Vernal/autumnal equinox day approximation, valid for 1980..2099.
     * (The official day is gazetted each February; this formula matches it.)
     */
    private fun equinoxDay(year: Int, vernal: Boolean): Int {
        val constant = if (vernal) 20.8431 else 23.2488
        return (constant + 0.242194 * (year - 1980) - (year - 1980) / 4).toInt()
    }

    /** True when [date] should be tinted like Sunday (Sunday itself or a holiday). */
    fun isRedDay(date: LocalDate): Boolean =
        date.dayOfWeek == DayOfWeek.SUNDAY || isHoliday(date)
}
