package com.souru.colorhunt.domain.config

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The preview drag overlay and the export renderer both consume these rects,
 * so this maths is release-critical: any drift breaks WYSIWYG.
 */
class CollageGeometryTest {

    private val w = 1080f
    private val h = 1350f

    private fun compute(
        count: Int,
        layout: CollageLayout = CollageLayout.GRID,
        placement: PalettePlacement = PalettePlacement.NONE,
        spacingFrac: Float = 0f,
    ) = CollageGeometry.compute(count, layout, placement, spacingFrac, w, h)

    @Test
    fun `grid of four is two by two`() {
        val cells = compute(4).cells
        assertEquals(4, cells.size)
        // Two columns: cells 0/2 share a left edge, 1/3 share a left edge to the right.
        assertEquals(cells[0].left, cells[2].left, 0.5f)
        assertEquals(cells[1].left, cells[3].left, 0.5f)
        assertTrue(cells[1].left > cells[0].left)
        // Zero spacing fills the full width.
        assertEquals(w, cells[1].right, 0.5f)
    }

    @Test
    fun `vertical layout is a single column`() {
        val cells = compute(3, layout = CollageLayout.VERTICAL).cells
        cells.forEach { assertEquals(w, it.width, 0.5f) }
        assertTrue(cells[0].top < cells[1].top && cells[1].top < cells[2].top)
    }

    @Test
    fun `no palette yields null rect`() {
        assertNull(compute(4).palette)
    }

    @Test
    fun `center palette splits two columns and sits between them`() {
        val layout = compute(4, layout = CollageLayout.TWO_COLUMN, placement = PalettePlacement.CENTER)
        val palette = layout.palette
        assertNotNull(palette)
        val left = layout.cells[0]
        val right = layout.cells[1]
        assertTrue(palette!!.left >= left.right - 0.5f)
        assertTrue(palette.right <= right.left + 0.5f)
    }

    @Test
    fun `left palette shifts every cell right of the rail`() {
        val layout = compute(4, placement = PalettePlacement.LEFT)
        val railRight = layout.palette!!.right
        layout.cells.forEach { assertTrue(it.left >= railRight - 0.5f) }
        assertEquals(0f, layout.palette!!.left, 0.5f)
    }

    @Test
    fun `side palette reserves the right edge`() {
        val layout = compute(4, placement = PalettePlacement.SIDE)
        assertEquals(w, layout.palette!!.right, 0.5f)
        layout.cells.forEach { assertTrue(it.right <= layout.palette!!.left + 0.5f) }
    }

    @Test
    fun `overlay palette floats over full-width cells`() {
        val plain = compute(4)
        val overlay = compute(4, placement = PalettePlacement.OVERLAY)
        // Cells identical to the no-palette layout: overlay reserves no space.
        plain.cells.zip(overlay.cells).forEach { (a, b) ->
            assertEquals(a.left, b.left, 0.5f)
            assertEquals(a.right, b.right, 0.5f)
        }
        // And the rail is centred on the seam.
        val p = overlay.palette!!
        assertEquals(w / 2f, (p.left + p.right) / 2f, 0.5f)
    }

    @Test
    fun `overlay position slides the band from flush left to flush right`() {
        val flushLeft = CollageGeometry.compute(
            4, CollageLayout.GRID, PalettePlacement.OVERLAY, 0f, w, h, overlayPosFrac = 0f,
        ).palette!!
        assertEquals(0f, flushLeft.left, 0.5f)

        val flushRight = CollageGeometry.compute(
            4, CollageLayout.GRID, PalettePlacement.OVERLAY, 0f, w, h, overlayPosFrac = 1f,
        ).palette!!
        assertEquals(w, flushRight.right, 0.5f)
    }

    @Test
    fun `horizontal overlay is a full-width band positioned vertically`() {
        val top = CollageGeometry.compute(
            4, CollageLayout.GRID, PalettePlacement.OVERLAY, 0f, w, h,
            overlayHorizontal = true, overlayPosFrac = 0f, overlayWidthFrac = 0.2f,
        ).palette!!
        assertEquals(0f, top.left, 0.5f)
        assertEquals(w, top.right, 0.5f)
        assertEquals(0f, top.top, 0.5f)
        assertEquals(h * 0.2f, top.height, 0.5f)

        val bottom = CollageGeometry.compute(
            4, CollageLayout.GRID, PalettePlacement.OVERLAY, 0f, w, h,
            overlayHorizontal = true, overlayPosFrac = 1f,
        ).palette!!
        assertEquals(h, bottom.bottom, 0.5f)
    }

    @Test
    fun `overlay width fraction controls band thickness`() {
        val wide = CollageGeometry.compute(
            4, CollageLayout.GRID, PalettePlacement.OVERLAY, 0f, w, h, overlayWidthFrac = 0.4f,
        ).palette!!
        assertEquals(w * 0.4f, wide.width, 0.5f)
    }

    @Test
    fun `center palette with a single column falls back to a side rail`() {
        val layout = compute(2, layout = CollageLayout.VERTICAL, placement = PalettePlacement.CENTER)
        assertEquals(w, layout.palette!!.right, 0.5f)
    }

    @Test
    fun `focal point clamps to unit range`() {
        assertEquals(0f, FocalPoint(0.1f, 0.5f).shifted(-1f, 0f).x, 0f)
        assertEquals(1f, FocalPoint(0.9f, 0.5f).shifted(1f, 0f).x, 0f)
        assertEquals(0.5f, FocalPoint().x, 0f)
    }
}
