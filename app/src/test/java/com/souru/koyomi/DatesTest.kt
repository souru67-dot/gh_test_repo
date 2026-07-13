package com.souru.koyomi

import com.souru.koyomi.util.MonthPages
import com.souru.koyomi.util.monthGridDays
import com.souru.koyomi.util.orderedWeekDays
import java.time.DayOfWeek
import java.time.LocalDate
import java.time.YearMonth
import org.junit.Assert.assertEquals
import org.junit.Test

class DatesTest {

    @Test
    fun `page round trip`() {
        val month = YearMonth.of(2026, 7)
        assertEquals(month, MonthPages.monthAt(MonthPages.pageOf(month)))
        assertEquals(0, MonthPages.pageOf(YearMonth.of(1970, 1)))
    }

    @Test
    fun `grid starts on week start and has 42 days`() {
        // July 2026 starts on a Wednesday.
        val sundayGrid = monthGridDays(YearMonth.of(2026, 7), DayOfWeek.SUNDAY)
        assertEquals(42, sundayGrid.size)
        assertEquals(LocalDate.of(2026, 6, 28), sundayGrid.first()) // previous Sunday
        assertEquals(DayOfWeek.SUNDAY, sundayGrid.first().dayOfWeek)

        val mondayGrid = monthGridDays(YearMonth.of(2026, 7), DayOfWeek.MONDAY)
        assertEquals(LocalDate.of(2026, 6, 29), mondayGrid.first())
        assertEquals(DayOfWeek.MONDAY, mondayGrid.first().dayOfWeek)
    }

    @Test
    fun `grid for month starting on week start has no leading days`() {
        // Nov 2026 starts on a Sunday.
        val grid = monthGridDays(YearMonth.of(2026, 11), DayOfWeek.SUNDAY)
        assertEquals(LocalDate.of(2026, 11, 1), grid.first())
    }

    @Test
    fun `pages clamp at both ends`() {
        assertEquals(0, MonthPages.pageOf(YearMonth.of(1969, 12)))
        assertEquals(MonthPages.COUNT - 1, MonthPages.pageOf(YearMonth.of(2200, 1)))
        assertEquals(YearMonth.of(2169, 12), MonthPages.monthAt(MonthPages.COUNT - 1))
    }

    @Test
    fun `grid always covers the whole month`() {
        for (month in listOf(
            YearMonth.of(2026, 2), // starts on the week start (Sunday)
            YearMonth.of(2028, 2), // leap February
            YearMonth.of(2026, 12), // year boundary
        )) {
            for (weekStart in listOf(DayOfWeek.SUNDAY, DayOfWeek.MONDAY)) {
                val grid = monthGridDays(month, weekStart)
                assertEquals(42, grid.size)
                assertEquals(weekStart, grid.first().dayOfWeek)
                assertEquals(grid.first().plusDays(41), grid.last())
                assert(grid.first() <= month.atDay(1))
                assert(grid.last() >= month.atEndOfMonth())
            }
        }
    }

    @Test
    fun `ordered week days`() {
        assertEquals(
            listOf(
                DayOfWeek.SUNDAY, DayOfWeek.MONDAY, DayOfWeek.TUESDAY, DayOfWeek.WEDNESDAY,
                DayOfWeek.THURSDAY, DayOfWeek.FRIDAY, DayOfWeek.SATURDAY,
            ),
            orderedWeekDays(DayOfWeek.SUNDAY),
        )
        assertEquals(DayOfWeek.MONDAY, orderedWeekDays(DayOfWeek.MONDAY).first())
        assertEquals(DayOfWeek.SUNDAY, orderedWeekDays(DayOfWeek.MONDAY).last())
    }
}
