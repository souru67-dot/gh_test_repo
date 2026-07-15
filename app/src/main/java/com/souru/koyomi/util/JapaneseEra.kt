package com.souru.koyomi.util

import java.time.chrono.JapaneseDate
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Formats a Gregorian year/month as a Japanese-era year, e.g. 令和8年. */
object JapaneseEraFormat {

    private val YEAR = DateTimeFormatter.ofPattern("Gy年", Locale.JAPANESE)

    /**
     * The era-year label for [year] (e.g. "令和8年"). [month] disambiguates the
     * era across a boundary year (2019: 平成→令和). Falls back to the Gregorian
     * year if conversion isn't possible.
     */
    fun yearLabel(year: Int, month: Int = 7): String = runCatching {
        JapaneseDate.of(year, month, 1).format(YEAR)
    }.getOrDefault("${year}年")
}
