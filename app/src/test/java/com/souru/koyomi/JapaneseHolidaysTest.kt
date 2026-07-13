package com.souru.koyomi

import com.souru.koyomi.data.holiday.JapaneseHolidays
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class JapaneseHolidaysTest {

    @Test
    fun `fixed holidays 2026`() {
        val holidays = JapaneseHolidays.holidaysFor(2026)
        assertEquals("元日", holidays[LocalDate.of(2026, 1, 1)])
        assertEquals("建国記念の日", holidays[LocalDate.of(2026, 2, 11)])
        assertEquals("天皇誕生日", holidays[LocalDate.of(2026, 2, 23)])
        assertEquals("憲法記念日", holidays[LocalDate.of(2026, 5, 3)])
        assertEquals("こどもの日", holidays[LocalDate.of(2026, 5, 5)])
        assertEquals("山の日", holidays[LocalDate.of(2026, 8, 11)])
        assertEquals("文化の日", holidays[LocalDate.of(2026, 11, 3)])
        assertEquals("勤労感謝の日", holidays[LocalDate.of(2026, 11, 23)])
    }

    @Test
    fun `happy monday holidays 2026`() {
        val holidays = JapaneseHolidays.holidaysFor(2026)
        assertEquals("成人の日", holidays[LocalDate.of(2026, 1, 12)]) // 2nd Monday of Jan
        assertEquals("海の日", holidays[LocalDate.of(2026, 7, 20)]) // 3rd Monday of Jul
        assertEquals("敬老の日", holidays[LocalDate.of(2026, 9, 21)]) // 3rd Monday of Sep
        assertEquals("スポーツの日", holidays[LocalDate.of(2026, 10, 12)]) // 2nd Monday of Oct
    }

    @Test
    fun `equinoxes 2026`() {
        val holidays = JapaneseHolidays.holidaysFor(2026)
        assertEquals("春分の日", holidays[LocalDate.of(2026, 3, 20)])
        assertEquals("秋分の日", holidays[LocalDate.of(2026, 9, 23)])
    }

    @Test
    fun `substitute holiday when holiday falls on sunday`() {
        // 2026-05-03 (Constitution Day) is a Sunday; 5/4 and 5/5 are already
        // holidays, so the substitute rolls to 5/6.
        val holidays = JapaneseHolidays.holidaysFor(2026)
        assertEquals("振替休日", holidays[LocalDate.of(2026, 5, 6)])
    }

    @Test
    fun `citizens holiday in silver week 2026`() {
        // 敬老の日 9/21 (Mon) and 秋分の日 9/23 (Wed) sandwich 9/22.
        val holidays = JapaneseHolidays.holidaysFor(2026)
        assertEquals("国民の休日", holidays[LocalDate.of(2026, 9, 22)])
    }

    @Test
    fun `2019 one-off holidays`() {
        val holidays = JapaneseHolidays.holidaysFor(2019)
        assertEquals("天皇の即位の日", holidays[LocalDate.of(2019, 5, 1)])
        assertEquals("国民の休日", holidays[LocalDate.of(2019, 4, 30)])
        assertEquals("国民の休日", holidays[LocalDate.of(2019, 5, 2)])
        assertEquals("即位礼正殿の儀", holidays[LocalDate.of(2019, 10, 22)])
        // No Emperor's Birthday at all in 2019.
        assertFalse(holidays.containsValue("天皇誕生日"))
    }

    @Test
    fun `2021 olympic moves`() {
        val holidays = JapaneseHolidays.holidaysFor(2021)
        assertEquals("海の日", holidays[LocalDate.of(2021, 7, 22)])
        assertEquals("スポーツの日", holidays[LocalDate.of(2021, 7, 23)])
        assertEquals("山の日", holidays[LocalDate.of(2021, 8, 8)])
        assertEquals("振替休日", holidays[LocalDate.of(2021, 8, 9)])
        // The regular dates must NOT be holidays that year.
        assertFalse(holidays.containsKey(LocalDate.of(2021, 7, 19)))
        assertFalse(holidays.containsKey(LocalDate.of(2021, 8, 11)))
        assertFalse(holidays.containsKey(LocalDate.of(2021, 10, 11)))
    }

    @Test
    fun `red day includes sundays and holidays`() {
        assertTrue(JapaneseHolidays.isRedDay(LocalDate.of(2026, 7, 5))) // Sunday
        assertTrue(JapaneseHolidays.isRedDay(LocalDate.of(2026, 8, 11))) // 山の日 (Tue)
        assertFalse(JapaneseHolidays.isRedDay(LocalDate.of(2026, 7, 4))) // Saturday
    }

    @Test
    fun `substitute holidays 2025`() {
        val holidays = JapaneseHolidays.holidaysFor(2025)
        // 天皇誕生日 2/23, みどりの日 5/4 and 勤労感謝の日 11/23 all fall on Sundays.
        assertEquals("振替休日", holidays[LocalDate.of(2025, 2, 24)])
        assertEquals("振替休日", holidays[LocalDate.of(2025, 5, 6)]) // 5/5 already a holiday
        assertEquals("振替休日", holidays[LocalDate.of(2025, 11, 24)])
        assertEquals("春分の日", holidays[LocalDate.of(2025, 3, 20)])
        assertEquals("秋分の日", holidays[LocalDate.of(2025, 9, 23)])
    }

    @Test
    fun `silver week 2032`() {
        // 敬老の日 9/20 (Mon) and 秋分の日 9/22 (Wed) sandwich 9/21.
        val holidays = JapaneseHolidays.holidaysFor(2032)
        assertEquals("敬老の日", holidays[LocalDate.of(2032, 9, 20)])
        assertEquals("国民の休日", holidays[LocalDate.of(2032, 9, 21)])
        assertEquals("秋分の日", holidays[LocalDate.of(2032, 9, 22)])
    }

    @Test
    fun `historical rules before happy monday`() {
        val holidays = JapaneseHolidays.holidaysFor(1999)
        assertEquals("成人の日", holidays[LocalDate.of(1999, 1, 15)])
        assertEquals("体育の日", holidays[LocalDate.of(1999, 10, 10)])
        // 2000 switches both to the happy-monday rule.
        val y2000 = JapaneseHolidays.holidaysFor(2000)
        assertEquals("成人の日", y2000[LocalDate.of(2000, 1, 10)])
        assertFalse(y2000.containsKey(LocalDate.of(2000, 1, 15)))
    }

    @Test
    fun `emperor birthday moves between eras`() {
        assertEquals("天皇誕生日", JapaneseHolidays.nameFor(LocalDate.of(2018, 12, 23)))
        // 2019 has neither the Heisei (12/23) nor the Reiwa (2/23) birthday.
        assertFalse(JapaneseHolidays.holidaysFor(2019).containsKey(LocalDate.of(2019, 12, 23)))
        assertEquals("天皇誕生日", JapaneseHolidays.nameFor(LocalDate.of(2020, 2, 23)))
    }

    @Test
    fun `out of range years have no holidays`() {
        assertTrue(JapaneseHolidays.holidaysFor(1979).isEmpty())
        assertTrue(JapaneseHolidays.holidaysFor(2100).isEmpty())
        // In-range boundaries still work.
        assertEquals("元日", JapaneseHolidays.nameFor(LocalDate.of(1980, 1, 1)))
        assertEquals("元日", JapaneseHolidays.nameFor(LocalDate.of(2099, 1, 1)))
    }
}
