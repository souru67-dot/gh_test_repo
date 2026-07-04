package com.souru.koyomi.util

import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import java.time.temporal.ChronoUnit

/** Month pager: page index <-> YearMonth, anchored at 1970-01 (page 0). */
object MonthPages {
    private val BASE: YearMonth = YearMonth.of(1970, 1)

    /** 1970-01 .. 2169-12 — plenty for a hand-held calendar. */
    const val COUNT = 200 * 12

    fun pageOf(month: YearMonth): Int =
        ChronoUnit.MONTHS.between(BASE, month).toInt().coerceIn(0, COUNT - 1)

    fun monthAt(page: Int): YearMonth = BASE.plusMonths(page.toLong())
}

/**
 * The 42 dates (6 fixed weeks) shown for [month] when the week starts on [weekStart].
 * A fixed 6-row grid keeps cell heights stable while paging.
 */
fun monthGridDays(month: YearMonth, weekStart: DayOfWeek): List<LocalDate> {
    val first = month.atDay(1)
    val lead = ((first.dayOfWeek.value - weekStart.value) % 7 + 7) % 7
    val gridStart = first.minusDays(lead.toLong())
    return List(42) { gridStart.plusDays(it.toLong()) }
}

/** Weekday header order for the given start day. */
fun orderedWeekDays(weekStart: DayOfWeek): List<DayOfWeek> =
    List(7) { weekStart.plus(it.toLong()) }
