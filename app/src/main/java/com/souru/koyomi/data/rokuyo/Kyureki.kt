package com.souru.koyomi.data.rokuyo

import java.time.LocalDate
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.PI
import kotlin.math.floor
import kotlin.math.sin

/**
 * 旧暦 (Japanese lunisolar calendar) conversion, accurate for 1900..2099.
 *
 * Month boundaries are astronomical new moons (朔) and month numbers follow
 * the 中気 rule (the month containing the solar term at 330° is month 1,
 * winter solstice 270° falls in month 11; a month without a 中気 is a leap
 * month carrying the previous month's number). New moon and solar longitude
 * times use truncated Meeus series evaluated in JST, which matches the
 * officially published 旧暦 dates for this range.
 */
object Kyureki {

    data class LunarDate(val month: Int, val day: Int, val isLeapMonth: Boolean)

    /** 六曜 keyed by (lunar month + lunar day) % 6; lunar 1/1 is 先勝. */
    private val ROKUYO = listOf("大安", "赤口", "先勝", "友引", "先負", "仏滅")

    fun rokuyoFor(date: LocalDate): String? {
        val lunar = lunarDateFor(date) ?: return null
        // 閏月 keeps the number of the month it follows, which the formula
        // already receives because leap months reuse that number here.
        return ROKUYO[(lunar.month + lunar.day) % 6]
    }

    /** 二十四節気, indexed by (solar longitude / 15): 0°=春分 … 315°=立春. */
    private val SEKKI = listOf(
        "春分", "清明", "穀雨", "立夏", "小満", "芒種",
        "夏至", "小暑", "大暑", "立秋", "処暑", "白露",
        "秋分", "寒露", "霜降", "立冬", "小雪", "大雪",
        "冬至", "小寒", "大寒", "立春", "雨水", "啓蟄",
    )

    /**
     * The 二十四節気 (24 solar term) name if [date] is the day the sun reaches a
     * 15° longitude multiple in JST; otherwise null. Uses the same astronomy
     * as the 旧暦 conversion. Accurate for 1900..2099.
     */
    fun solarTermFor(date: LocalDate): String? {
        if (date.year < 1900 || date.year > 2099) return null
        val startJde = jdeAtJstMidnight(date)
        val longitude = sunLongitude(startJde)
        val target = ((floor(longitude / 15.0).toInt() + 1) * 15) % 360
        val termDate = solarTermJstDate(startJde, target)
        return if (termDate == date) SEKKI[target / 15] else null
    }

    /** 和風月名 (traditional lunar month names), index 0 = 睦月 (month 1). */
    private val WAFU_MONTH = listOf(
        "睦月", "如月", "弥生", "卯月", "皐月", "水無月",
        "文月", "葉月", "長月", "神無月", "霜月", "師走",
    )

    /** 旧暦 label like "神無月 十五日" (閏 prefix for a leap month), or null. */
    fun lunarDateLabel(date: LocalDate): String? {
        val lunar = lunarDateFor(date) ?: return null
        val month = WAFU_MONTH[(lunar.month - 1).coerceIn(0, 11)]
        val prefix = if (lunar.isLeapMonth) "閏" else ""
        return "$prefix$month${kanjiDay(lunar.day)}"
    }

    private val KANJI_DIGIT = listOf("", "一", "二", "三", "四", "五", "六", "七", "八", "九")

    /** 1..31 as a 漢数字 day, e.g. 15 → 十五日, 21 → 二十一日. */
    private fun kanjiDay(day: Int): String {
        val d = day.coerceIn(1, 31)
        val body = when {
            d < 10 -> KANJI_DIGIT[d]
            d == 10 -> "十"
            d < 20 -> "十" + KANJI_DIGIT[d - 10]
            d % 10 == 0 -> KANJI_DIGIT[d / 10] + "十"
            else -> KANJI_DIGIT[d / 10] + "十" + KANJI_DIGIT[d % 10]
        }
        return body + "日"
    }

    /**
     * Moon age (日) at JST noon of [date]: days elapsed since the previous new
     * moon. 0 ≈ 新月, ~7.4 ≈ 上弦, ~14.8 ≈ 満月, ~22.1 ≈ 下弦. 1900..2099.
     */
    fun moonAgeFor(date: LocalDate): Double? {
        if (date.year < 1900 || date.year > 2099) return null
        val jdeNoon = jdeAtJstMidnight(date) + 0.5
        var k = floor((date.toEpochDay() - NEW_MOON_2000_EPOCH_DAY) / SYNODIC_MONTH)
        while (newMoonJde(k) > jdeNoon) k -= 1
        while (newMoonJde(k + 1) <= jdeNoon) k += 1
        return jdeNoon - newMoonJde(k)
    }

    /** A phase name when [age] is near a quarter, else null (show the number). */
    fun moonPhaseName(age: Double): String? = when {
        age < 1.0 -> "新月"
        age in 6.9..7.9 -> "上弦"
        age in 14.2..15.4 -> "満月"
        age in 21.6..22.6 -> "下弦"
        else -> null
    }

    // ---------- 開運日 (lucky days) ----------

    /**
     * Sexagenary (干支) index of the day, 0..59 with 0 = 甲子. Anchored on
     * 2024-01-01 = 甲子, cross-checked against published 天赦日/一粒万倍日
     * lists for 2024 and 2026.
     */
    fun sexagenaryDayIndex(date: LocalDate): Int =
        (((date.toEpochDay() + 2440637) % 60 + 60) % 60).toInt()

    /**
     * 節月 1..12 (正月 = from 立春). The 節 day itself belongs to the new
     * month, so the boundary is read from the sun's longitude at the END of
     * the day (the term moment falls somewhere inside it).
     */
    private fun setsugetsuFor(date: LocalDate): Int {
        val lonAtDayEnd = sunLongitude(jdeAtJstMidnight(date.plusDays(1)))
        return (floor(angleForward(315.0, lonAtDayEnd) / 30.0).toInt() % 12) + 1
    }

    /**
     * 一粒万倍日: two zodiac day-signs per 節月. The table is the classical
     * one, verified against the published 2026 dates.
     */
    private val MANBAI_SHI = mapOf(
        1 to setOf(1, 6),   // 正月(立春〜): 丑・午
        2 to setOf(2, 9),   // 二月(啓蟄〜): 寅・酉
        3 to setOf(0, 3),   // 三月(清明〜): 子・卯
        4 to setOf(3, 4),   // 四月(立夏〜): 卯・辰
        5 to setOf(5, 6),   // 五月(芒種〜): 巳・午
        6 to setOf(6, 9),   // 六月(小暑〜): 午・酉
        7 to setOf(0, 7),   // 七月(立秋〜): 子・未
        8 to setOf(3, 8),   // 八月(白露〜): 卯・申
        9 to setOf(6, 9),   // 九月(寒露〜): 午・酉
        10 to setOf(9, 10), // 十月(立冬〜): 酉・戌
        11 to setOf(11, 0), // 十一月(大雪〜): 亥・子
        12 to setOf(0, 3),  // 十二月(小寒〜): 子・卯
    )

    /** 天赦日: one fixed 干支 per season (season = 節月 group). */
    private fun tenshaIndexFor(setsugetsu: Int): Int = when (setsugetsu) {
        1, 2, 3 -> 14   // 春 (立春〜立夏前): 戊寅
        4, 5, 6 -> 30   // 夏 (立夏〜立秋前): 甲午
        7, 8, 9 -> 44   // 秋 (立秋〜立冬前): 戊申
        else -> 0       // 冬 (立冬〜立春前): 甲子
    }

    /**
     * 開運日 labels for [date]: 天赦日 / 一粒万倍日 / 寅の日 / 巳の日 (most
     * auspicious first). Empty when none. 1900..2099.
     */
    fun luckyDaysFor(date: LocalDate): List<String> {
        if (date.year < 1900 || date.year > 2099) return emptyList()
        val kanshi = sexagenaryDayIndex(date)
        val shi = kanshi % 12
        val setsugetsu = setsugetsuFor(date)
        return buildList {
            if (kanshi == tenshaIndexFor(setsugetsu)) add("天赦日")
            if (shi in MANBAI_SHI.getValue(setsugetsu)) add("一粒万倍日")
            if (shi == 2) add("寅の日")
            if (shi == 5) add("巳の日")
        }
    }

    /** Short month-grid marker for the day's best 開運日, or null. */
    fun luckyMarkFor(date: LocalDate): String? {
        val lucky = luckyDaysFor(date)
        return when {
            "天赦日" in lucky -> "天赦"
            "一粒万倍日" in lucky -> "万倍"
            "寅の日" in lucky -> "寅の日"
            "巳の日" in lucky -> "巳の日"
            else -> null
        }
    }

    private val cache = ConcurrentHashMap<Long, LunarDate>()

    fun lunarDateFor(date: LocalDate): LunarDate? {
        if (date.year < 1900 || date.year > 2099) return null
        cache[date.toEpochDay()]?.let { return it }
        val result = compute(date)
        if (cache.size > 4096) cache.clear()
        cache[date.toEpochDay()] = result
        return result
    }

    private fun compute(date: LocalDate): LunarDate {
        // Locate the new moon on or before the date (start of the lunar month).
        var k = floor(
            (date.toEpochDay() - NEW_MOON_2000_EPOCH_DAY) / SYNODIC_MONTH,
        )
        while (newMoonJstDate(k) > date) k -= 1
        while (newMoonJstDate(k + 1) <= date) k += 1
        val monthStart = newMoonJstDate(k)
        val nextStart = newMoonJstDate(k + 1)
        val day = (date.toEpochDay() - monthStart.toEpochDay()).toInt() + 1

        // First 中気 (solar longitude multiple of 30°) at or after month start.
        val startJde = jdeAtJstMidnight(monthStart)
        val startLongitude = sunLongitude(startJde)
        val target = ((floor(startLongitude / 30.0).toInt() + 1) * 30) % 360
        val chukiDate = solarTermJstDate(startJde, target)

        return if (chukiDate < nextStart) {
            LunarDate(month = monthNumberFor(target), day = day, isLeapMonth = false)
        } else {
            // No 中気 inside this month: leap month named after the previous
            // month, which is one before the month the next 中気 belongs to.
            val next = monthNumberFor(target)
            val month = if (next == 1) 12 else next - 1
            LunarDate(month = month, day = day, isLeapMonth = true)
        }
    }

    /** 330°=雨水→1月, 0°=春分→2月, ..., 270°=冬至→11月. */
    private fun monthNumberFor(longitudeDeg: Int): Int =
        ((longitudeDeg / 30 + 1) % 12) + 1

    // ---------- Astronomy (truncated Meeus) ----------

    private const val SYNODIC_MONTH = 29.530588861

    /** Epoch day of the first new moon of 2000 (2000-01-06, k = 0). */
    private const val NEW_MOON_2000_EPOCH_DAY = 10962.0

    /** JD of 1970-01-01T00:00 UT. */
    private const val JD_UNIX_EPOCH = 2440587.5

    /** ΔT ≈ 70 s expressed in days — plenty accurate for 1900..2099 dates. */
    private const val DELTA_T_DAYS = 70.0 / 86400.0

    private const val JST_OFFSET_DAYS = 9.0 / 24.0

    private fun rad(deg: Double): Double = deg / 180.0 * PI

    /** The JST calendar date containing the k-th new moon since 2000-01-06. */
    private fun newMoonJstDate(k: Double): LocalDate = jdeToJstDate(newMoonJde(k))

    private fun jdeToJstDate(jde: Double): LocalDate {
        val jdJst = jde - DELTA_T_DAYS + JST_OFFSET_DAYS
        return LocalDate.ofEpochDay(floor(jdJst - JD_UNIX_EPOCH).toLong())
    }

    private fun jdeAtJstMidnight(date: LocalDate): Double =
        date.toEpochDay() + JD_UNIX_EPOCH - JST_OFFSET_DAYS + DELTA_T_DAYS

    /** Instant (JDE, TT) of the k-th mean new moon after 2000-01-06 (Meeus ch. 49). */
    private fun newMoonJde(k: Double): Double {
        val t = k / 1236.85
        val jde = 2451550.09766 + SYNODIC_MONTH * k +
            t * t * (0.00015437 + t * (-0.000000150 + 0.00000000073 * t))
        val e = 1 - 0.002516 * t - 0.0000074 * t * t
        val m = rad(2.5534 + 29.10535670 * k - t * t * (0.0000014 + 0.00000011 * t))
        val mp = rad(
            201.5643 + 385.81693528 * k +
                t * t * (0.0107582 + t * (0.00001238 - 0.000000058 * t)),
        )
        val f = rad(
            160.7108 + 390.67050284 * k -
                t * t * (0.0016118 + t * (0.00000227 - 0.000000011 * t)),
        )
        val om = rad(124.7746 - 1.56375588 * k + t * t * (0.0020672 + 0.00000215 * t))

        val corrections = -0.40720 * sin(mp) +
            0.17241 * e * sin(m) +
            0.01608 * sin(2 * mp) +
            0.01039 * sin(2 * f) +
            0.00739 * e * sin(mp - m) -
            0.00514 * e * sin(mp + m) +
            0.00208 * e * e * sin(2 * m) -
            0.00111 * sin(mp - 2 * f) -
            0.00057 * sin(mp + 2 * f) +
            0.00056 * e * sin(2 * mp + m) -
            0.00042 * sin(3 * mp) +
            0.00042 * e * sin(m + 2 * f) +
            0.00038 * e * sin(m - 2 * f) -
            0.00024 * e * sin(2 * mp - m) -
            0.00017 * sin(om) -
            0.00007 * sin(mp + 2 * m) +
            0.00004 * sin(2 * mp - 2 * f) +
            0.00004 * sin(3 * m) +
            0.00003 * sin(mp + m - 2 * f) +
            0.00003 * sin(2 * mp + 2 * f) -
            0.00003 * sin(mp + m + 2 * f) +
            0.00003 * sin(mp - m + 2 * f) -
            0.00002 * sin(mp - m - 2 * f) -
            0.00002 * sin(3 * mp + m) +
            0.00002 * sin(4 * mp)

        val planetary = 0.000325 * sin(rad(299.77 + 0.107408 * k - 0.009173 * t * t)) +
            0.000165 * sin(rad(251.88 + 0.016321 * k)) +
            0.000164 * sin(rad(251.83 + 26.651886 * k)) +
            0.000126 * sin(rad(349.42 + 36.412478 * k)) +
            0.000110 * sin(rad(84.66 + 18.206239 * k)) +
            0.000062 * sin(rad(141.74 + 53.303771 * k)) +
            0.000060 * sin(rad(207.14 + 2.453732 * k)) +
            0.000056 * sin(rad(154.84 + 7.306860 * k)) +
            0.000047 * sin(rad(34.52 + 27.261239 * k)) +
            0.000042 * sin(rad(207.19 + 0.121824 * k)) +
            0.000040 * sin(rad(291.34 + 1.844379 * k)) +
            0.000037 * sin(rad(161.72 + 24.198154 * k)) +
            0.000035 * sin(rad(239.56 + 25.513099 * k)) +
            0.000023 * sin(rad(331.55 + 3.592518 * k))

        return jde + corrections + planetary
    }

    /** Apparent solar longitude in degrees, 0..360 (Meeus ch. 25, low precision). */
    private fun sunLongitude(jde: Double): Double {
        val t = (jde - 2451545.0) / 36525.0
        val l0 = 280.46646 + 36000.76983 * t + 0.0003032 * t * t
        val m = rad(357.52911 + 35999.05029 * t - 0.0001537 * t * t)
        val c = (1.914602 - 0.004817 * t - 0.000014 * t * t) * sin(m) +
            (0.019993 - 0.000101 * t) * sin(2 * m) +
            0.000289 * sin(3 * m)
        val omega = rad(125.04 - 1934.136 * t)
        val lambda = l0 + c - 0.00569 - 0.00478 * sin(omega)
        return ((lambda % 360.0) + 360.0) % 360.0
    }

    /** JST date on which the sun reaches [targetDeg], searching forward of [fromJde]. */
    private fun solarTermJstDate(fromJde: Double, targetDeg: Int): LocalDate {
        val start = sunLongitude(fromJde)
        var jde = fromJde + angleForward(start, targetDeg.toDouble()) / MEAN_SOLAR_MOTION
        repeat(5) {
            jde -= angleSigned(sunLongitude(jde), targetDeg.toDouble()) / MEAN_SOLAR_MOTION
        }
        return jdeToJstDate(jde)
    }

    private const val MEAN_SOLAR_MOTION = 0.9856473 // degrees per day

    /** Degrees to travel forward from [from] to reach [target] (0..360). */
    private fun angleForward(from: Double, target: Double): Double =
        (((target - from) % 360.0) + 360.0) % 360.0

    /** Signed smallest angle from [target] to [current] (-180..180). */
    private fun angleSigned(current: Double, target: Double): Double {
        var d = (current - target) % 360.0
        if (d > 180.0) d -= 360.0
        if (d < -180.0) d += 360.0
        return d
    }
}
