package com.souru.koyomi

import com.souru.koyomi.util.Ics
import java.time.LocalDate
import java.time.LocalDateTime
import java.time.ZoneId
import java.time.ZoneOffset
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class IcsTest {

    private val tokyo = ZoneId.of("Asia/Tokyo")

    private fun utcMillis(y: Int, m: Int, d: Int, h: Int = 0, min: Int = 0): Long =
        LocalDateTime.of(y, m, d, h, min).toInstant(ZoneOffset.UTC).toEpochMilli()

    @Test
    fun `timed event round trips`() {
        val original = Ics.Event(
            summary = "打ち合わせ",
            location = "会議室A",
            description = "議題:\n1. 進捗, 確認; など",
            startMillis = utcMillis(2026, 7, 20, 1, 0),
            endMillis = utcMillis(2026, 7, 20, 2, 30),
            allDay = false,
        )
        val text = Ics.write(listOf(original))
        val parsed = Ics.parse(text, tokyo)
        assertEquals(1, parsed.size)
        val event = parsed[0]
        assertEquals(original.summary, event.summary)
        assertEquals(original.location, event.location)
        assertEquals(original.description, event.description)
        assertEquals(original.startMillis, event.startMillis)
        assertEquals(original.endMillis, event.endMillis)
        assertEquals(false, event.allDay)
        assertNull(event.rrule)
    }

    @Test
    fun `all day event round trips`() {
        val start = LocalDate.of(2026, 8, 11)
        val original = Ics.Event(
            summary = "夏休み",
            startMillis = start.atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            endMillis = start.plusDays(3).atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli(),
            allDay = true,
        )
        val text = Ics.write(listOf(original))
        assertTrue(text.contains("DTSTART;VALUE=DATE:20260811"))
        assertTrue(text.contains("DTEND;VALUE=DATE:20260814"))
        val event = Ics.parse(text, tokyo).single()
        assertTrue(event.allDay)
        assertEquals(original.startMillis, event.startMillis)
        assertEquals(original.endMillis, event.endMillis)
    }

    @Test
    fun `rrule and duration round trip`() {
        val original = Ics.Event(
            summary = "毎週会議",
            startMillis = utcMillis(2026, 7, 20, 1, 0),
            endMillis = utcMillis(2026, 7, 20, 2, 0),
            allDay = false,
            rrule = "FREQ=WEEKLY;BYDAY=MO",
            durationSpec = "PT3600S",
        )
        val text = Ics.write(listOf(original))
        assertTrue(text.contains("RRULE:FREQ=WEEKLY;BYDAY=MO"))
        assertTrue(text.contains("DURATION:PT3600S"))
        val event = Ics.parse(text, tokyo).single()
        assertEquals("FREQ=WEEKLY;BYDAY=MO", event.rrule)
        assertEquals(original.startMillis, event.startMillis)
        assertEquals(original.endMillis, event.endMillis) // from DURATION
    }

    @Test
    fun `tzid datetimes parse in their zone`() {
        val text = """
            BEGIN:VCALENDAR
            VERSION:2.0
            BEGIN:VEVENT
            DTSTART;TZID=Asia/Tokyo:20260720T100000
            DTEND;TZID=Asia/Tokyo:20260720T113000
            SUMMARY:Local meeting
            END:VEVENT
            END:VCALENDAR
        """.trimIndent()
        val event = Ics.parse(text, ZoneId.of("UTC")).single()
        assertEquals(utcMillis(2026, 7, 20, 1, 0), event.startMillis)
        assertEquals(utcMillis(2026, 7, 20, 2, 30), event.endMillis)
    }

    @Test
    fun `missing dtend defaults to one hour or one day`() {
        val timed = """
            BEGIN:VEVENT
            DTSTART:20260720T010000Z
            SUMMARY:x
            END:VEVENT
        """.trimIndent()
        val event = Ics.parse(timed, tokyo).single()
        assertEquals(utcMillis(2026, 7, 20, 2, 0), event.endMillis)

        val allDay = """
            BEGIN:VEVENT
            DTSTART;VALUE=DATE:20260720
            SUMMARY:x
            END:VEVENT
        """.trimIndent()
        val dayEvent = Ics.parse(allDay, tokyo).single()
        assertTrue(dayEvent.allDay)
        assertEquals(dayEvent.startMillis + 86_400_000L, dayEvent.endMillis)
    }

    @Test
    fun `long lines are folded and unfolded`() {
        val longTitle = "とても長いタイトル".repeat(10)
        val text = Ics.write(
            listOf(
                Ics.Event(
                    summary = longTitle,
                    startMillis = utcMillis(2026, 7, 20, 1, 0),
                    endMillis = utcMillis(2026, 7, 20, 2, 0),
                    allDay = false,
                ),
            ),
        )
        // No physical line may exceed 75 octets.
        for (line in text.split("\r\n")) {
            assertTrue(
                "line too long: $line",
                line.toByteArray(Charsets.UTF_8).size <= 75,
            )
        }
        assertEquals(longTitle, Ics.parse(text, tokyo).single().summary)
    }

    @Test
    fun `escaping round trips special characters`() {
        val tricky = "a,b;c\\d\ne"
        val text = Ics.write(
            listOf(
                Ics.Event(
                    summary = tricky,
                    startMillis = utcMillis(2026, 1, 1, 0, 0),
                    endMillis = utcMillis(2026, 1, 1, 1, 0),
                    allDay = false,
                ),
            ),
        )
        assertEquals(tricky, Ics.parse(text, tokyo).single().summary)
    }

    @Test
    fun `events without dtstart are skipped`() {
        val text = """
            BEGIN:VEVENT
            SUMMARY:broken
            END:VEVENT
        """.trimIndent()
        assertTrue(Ics.parse(text, tokyo).isEmpty())
    }

    @Test
    fun `provider style durations parse`() {
        assertEquals(86_400_000L, Ics.parseDuration("P1D"))
        assertEquals(3_600_000L, Ics.parseDuration("PT3600S"))
        assertEquals(900_000L, Ics.parseDuration("P900S"))
        assertEquals(604_800_000L, Ics.parseDuration("P1W"))
        assertNull(Ics.parseDuration("garbage"))
    }
}
