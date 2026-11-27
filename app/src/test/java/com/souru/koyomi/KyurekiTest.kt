package com.souru.koyomi

import com.souru.koyomi.data.rokuyo.Kyureki
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class KyurekiTest {

    @Test
    fun `lunar new years match the published calendar`() {
        for (date in listOf(
            LocalDate.of(2021, 2, 12),
            LocalDate.of(2022, 2, 1),
            LocalDate.of(2023, 1, 22),
            LocalDate.of(2024, 2, 10),
            LocalDate.of(2025, 1, 29),
            LocalDate.of(2026, 2, 17),
        )) {
            val lunar = Kyureki.lunarDateFor(date)
            assertNotNull("$date", lunar)
            assertEquals("$date", 1, lunar!!.month)
            assertEquals("$date", 1, lunar.day)
            assertFalse("$date", lunar.isLeapMonth)
        }
    }

    @Test
    fun `lunar new year day is sensho`() {
        // 旧暦1月1日は必ず先勝.
        assertEquals("先勝", Kyureki.rokuyoFor(LocalDate.of(2025, 1, 29)))
        assertEquals("先勝", Kyureki.rokuyoFor(LocalDate.of(2026, 2, 17)))
        // ...and the following day is 友引.
        assertEquals("友引", Kyureki.rokuyoFor(LocalDate.of(2025, 1, 30)))
    }

    @Test
    fun `2023 has a leap second month`() {
        // 閏2月 (2023-03-22 .. 2023-04-19 in the published 旧暦).
        val lunar = Kyureki.lunarDateFor(LocalDate.of(2023, 4, 1))
        assertNotNull(lunar)
        assertTrue(lunar!!.isLeapMonth)
        assertEquals(2, lunar.month)
    }

    @Test
    fun `2025 has a leap sixth month`() {
        val lunar = Kyureki.lunarDateFor(LocalDate.of(2025, 8, 10))
        assertNotNull(lunar)
        assertTrue(lunar!!.isLeapMonth)
        assertEquals(6, lunar.month)
    }

    @Test
    fun `days advance within a lunar month`() {
        val first = Kyureki.lunarDateFor(LocalDate.of(2026, 2, 17))!!
        val tenth = Kyureki.lunarDateFor(LocalDate.of(2026, 2, 26))!!
        assertEquals(first.month, tenth.month)
        assertEquals(first.day + 9, tenth.day)
    }

    @Test
    fun `rokuyo cycles day by day inside a month`() {
        // 六曜 within one lunar month is a fixed 6-day cycle.
        val sequence = listOf("先勝", "友引", "先負", "仏滅", "大安", "赤口")
        val start = LocalDate.of(2026, 2, 17) // lunar 1/1 → 先勝
        for (offset in 0 until 6) {
            assertEquals(
                sequence[offset % 6],
                Kyureki.rokuyoFor(start.plusDays(offset.toLong())),
            )
        }
    }

    @Test
    fun `months are always valid`() {
        // Structural sanity across two years of consecutive days.
        var date = LocalDate.of(2025, 1, 1)
        val end = LocalDate.of(2027, 1, 1)
        var previous = Kyureki.lunarDateFor(date)!!
        date = date.plusDays(1)
        while (date < end) {
            val lunar = Kyureki.lunarDateFor(date)!!
            assertTrue("$date month", lunar.month in 1..12)
            assertTrue("$date day", lunar.day in 1..30)
            // Day either advances by one or resets to 1 on a new month.
            if (lunar.day != 1) {
                assertEquals("$date", previous.day + 1, lunar.day)
            }
            previous = lunar
            date = date.plusDays(1)
        }
    }

    @Test
    fun `out of range returns null`() {
        assertNull(Kyureki.lunarDateFor(LocalDate.of(1899, 12, 31)))
        assertNull(Kyureki.rokuyoFor(LocalDate.of(2100, 1, 1)))
    }
}
