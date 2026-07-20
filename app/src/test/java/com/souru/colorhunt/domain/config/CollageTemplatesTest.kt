package com.souru.colorhunt.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class CollageTemplatesTest {

    @Test
    fun `five templates with unique ids`() {
        assertEquals(5, CollageTemplates.all.size)
        assertEquals(5, CollageTemplates.all.map { it.id }.toSet().size)
    }

    @Test
    fun `exactly one free template as the funnel entry`() {
        val free = CollageTemplates.all.filter { !it.proOnly }
        assertEquals(listOf("white"), free.map { it.id })
    }

    @Test
    fun `pro templates actually use pro features`() {
        CollageTemplates.all.filter { it.proOnly }.forEach { tpl ->
            val style = tpl.apply(CollageStyle())
            assertTrue(
                "template ${tpl.id} must use a Pro feature",
                style.palette != PalettePlacement.NONE || style.hexOverlay,
            )
        }
    }

    @Test
    fun `free template stays free-tier safe`() {
        val style = CollageTemplates.all.first { it.id == "white" }.apply(CollageStyle())
        assertEquals(PalettePlacement.NONE, style.palette)
        assertFalse(style.hexOverlay)
    }

    @Test
    fun `seamless is a zero-gap overlay story`() {
        val tpl = CollageTemplates.all.first { it.id == "seamless" }
        val style = tpl.apply(CollageStyle())
        assertEquals(0f, style.cellSpacingDp, 0f)
        assertEquals(PalettePlacement.OVERLAY, style.palette)
        assertEquals(SnsSize.STORY_9_16, tpl.size)
    }

    @Test
    fun `templates never enable theme-follow background`() {
        // Presets are curated colours; following the device theme would break them.
        CollageTemplates.all.forEach { tpl ->
            assertFalse(tpl.apply(CollageStyle(backgroundFollowsTheme = true)).backgroundFollowsTheme)
        }
    }
}
