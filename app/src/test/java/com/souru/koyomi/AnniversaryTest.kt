package com.souru.koyomi

import com.souru.koyomi.data.anniversary.Anniversary
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AnniversaryTest {

    private fun yearly(date: LocalDate) = Anniversary(1, "t", date, repeatYearly = true)

    @Test
    fun yearlyWrapsToNextYear() {
        val a = yearly(LocalDate.of(2020, 3, 1))
        assertEquals(LocalDate.of(2027, 3, 1), a.nextOccurrence(LocalDate.of(2026, 3, 2)))
        assertEquals(LocalDate.of(2026, 3, 1), a.nextOccurrence(LocalDate.of(2026, 3, 1)))
        assertEquals(0L, a.daysUntil(LocalDate.of(2026, 3, 1)))
    }

    @Test
    fun leapDayFallsBackToFeb28() {
        val a = yearly(LocalDate.of(2020, 2, 29))
        assertEquals(LocalDate.of(2027, 2, 28), a.nextOccurrence(LocalDate.of(2027, 1, 1)))
        assertTrue(a.fallsOn(LocalDate.of(2027, 2, 28)))
        assertTrue(a.fallsOn(LocalDate.of(2028, 2, 29)))
        assertFalse(a.fallsOn(LocalDate.of(2028, 2, 28)))
    }

    @Test
    fun futureYearlyCountsDownToItsFirstDate() {
        val a = yearly(LocalDate.of(2028, 6, 1))
        val today = LocalDate.of(2026, 5, 1)
        assertEquals(LocalDate.of(2028, 6, 1), a.nextOccurrence(today))
        assertFalse(a.fallsOn(LocalDate.of(2026, 6, 1)))
        assertTrue(a.fallsOn(LocalDate.of(2028, 6, 1)))
        assertTrue(a.fallsOn(LocalDate.of(2029, 6, 1)))
        assertEquals(1, a.yearsOn(LocalDate.of(2029, 6, 1)))
    }

    @Test
    fun oneShotOnlyOnItsDay() {
        val a = Anniversary(2, "t", LocalDate.of(2026, 7, 7), repeatYearly = false)
        assertTrue(a.fallsOn(LocalDate.of(2026, 7, 7)))
        assertFalse(a.fallsOn(LocalDate.of(2027, 7, 7)))
        assertEquals(-6L, a.daysUntil(LocalDate.of(2026, 7, 13)))
    }
}
