package com.souru.koyomi

import com.souru.koyomi.util.RepeatFreq
import com.souru.koyomi.util.RepeatRule
import java.time.DayOfWeek
import java.time.LocalDate
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class RepeatRuleTest {

    @Test
    fun `none produces null rrule`() {
        assertNull(RepeatRule().toRRule())
        assertEquals(RepeatFreq.NONE, RepeatRule.parse(null).freq)
        assertEquals(RepeatFreq.NONE, RepeatRule.parse("").freq)
    }

    @Test
    fun `simple frequencies round trip`() {
        for (freq in listOf(
            RepeatFreq.DAILY, RepeatFreq.WEEKLY, RepeatFreq.MONTHLY, RepeatFreq.YEARLY,
        )) {
            val rule = RepeatRule(freq = freq)
            val parsed = RepeatRule.parse(rule.toRRule())
            assertEquals(freq, parsed.freq)
        }
        assertEquals("FREQ=DAILY", RepeatRule(freq = RepeatFreq.DAILY).toRRule())
    }

    @Test
    fun `weekly byday round trips`() {
        val rule = RepeatRule(
            freq = RepeatFreq.WEEKLY,
            byDays = setOf(DayOfWeek.TUESDAY, DayOfWeek.THURSDAY),
        )
        assertEquals("FREQ=WEEKLY;BYDAY=TU,TH", rule.toRRule())
        val parsed = RepeatRule.parse(rule.toRRule())
        assertEquals(rule.byDays, parsed.byDays)
        assertEquals(RepeatFreq.WEEKLY, parsed.freq)
    }

    @Test
    fun `until round trips as inclusive local date`() {
        val rule = RepeatRule(freq = RepeatFreq.DAILY, until = LocalDate.of(2026, 12, 31))
        assertEquals("FREQ=DAILY;UNTIL=20261231T235959Z", rule.toRRule())
        assertEquals(LocalDate.of(2026, 12, 31), RepeatRule.parse(rule.toRRule()).until)
    }

    @Test
    fun `unsupported rules stay custom and untouched`() {
        for (raw in listOf(
            "FREQ=WEEKLY;INTERVAL=2",
            "FREQ=DAILY;COUNT=10",
            "FREQ=MONTHLY;BYDAY=2MO",
            "FREQ=MONTHLY;BYMONTHDAY=15",
        )) {
            val parsed = RepeatRule.parse(raw)
            assertEquals(RepeatFreq.CUSTOM, parsed.freq)
            assertEquals(raw, parsed.toRRule())
        }
    }

    @Test
    fun `byday outside weekly is custom`() {
        assertEquals(RepeatFreq.CUSTOM, RepeatRule.parse("FREQ=DAILY;BYDAY=MO").freq)
    }
}
