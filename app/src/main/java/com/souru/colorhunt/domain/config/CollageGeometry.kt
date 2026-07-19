package com.souru.colorhunt.domain.config

import kotlin.math.ceil

/**
 * Normalised focal point for a cell's crop window (0..1 on each axis). 0.5/0.5 is
 * the centre crop; users can pan it so the aspect-ratio crop keeps what matters.
 */
data class FocalPoint(val x: Float = 0.5f, val y: Float = 0.5f) {
    fun shifted(dx: Float, dy: Float) =
        FocalPoint((x + dx).coerceIn(0f, 1f), (y + dy).coerceIn(0f, 1f))
}

/**
 * Single source of truth for collage layout maths. Both the on-screen drag
 * overlay ([com.souru.colorhunt.ui.collage] preview) and the pixel renderer
 * ([com.souru.colorhunt.data.export.CollageRenderer]) compute their rectangles
 * here, so the interactive hit-zones line up exactly with what gets drawn —
 * WYSIWYG stays true no matter the layout or palette placement.
 *
 * All maths is in the target coordinate space (px for the export bitmap, px for
 * the preview box). Spacing is expressed as a fraction of width so it scales.
 */
object CollageGeometry {

    /** Fraction of the total width taken by the palette column/rail. */
    const val PALETTE_RATIO = 0.16f

    data class Rect(val left: Float, val top: Float, val right: Float, val bottom: Float) {
        val width: Float get() = right - left
        val height: Float get() = bottom - top
    }

    data class Layout(
        /** Photo cell rectangles, in cell order. */
        val cells: List<Rect>,
        /** Palette column rectangle, or null when there's no palette. */
        val palette: Rect?,
    )

    fun compute(
        cellCount: Int,
        layout: CollageLayout,
        placement: PalettePlacement,
        spacingFrac: Float,
        width: Float,
        height: Float,
    ): Layout {
        val count = cellCount.coerceAtLeast(1)
        val columns = CollageGrid.columnsFor(count, layout).coerceAtLeast(1)
        val rows = ceil(count.toDouble() / columns).toInt().coerceAtLeast(1)

        val spacing = spacingFrac * width
        val hasPalette = placement != PalettePlacement.NONE
        val railW = if (hasPalette) width * PALETTE_RATIO else 0f
        // OVERLAY floats above the photos, so it reserves no horizontal space.
        val overlay = placement == PalettePlacement.OVERLAY
        val reserved = if (overlay) 0f else railW
        // The centre column only makes sense with >= 2 columns; otherwise fall back to a side rail.
        val center = placement == PalettePlacement.CENTER && columns >= 2
        val leftRail = placement == PalettePlacement.LEFT

        val cellW = ((width - reserved - spacing * (columns + 1)) / columns).coerceAtLeast(1f)
        val cellH = ((height - spacing * (rows + 1)) / rows).coerceAtLeast(1f)

        val leftCols = if (center) columns / 2 else columns

        val cells = ArrayList<Rect>(count)
        for (i in 0 until count) {
            val col = i % columns
            val row = i / columns
            var x = spacing + col * (cellW + spacing)
            if (center && col >= leftCols) x += railW
            if (leftRail) x += railW
            val y = spacing + row * (cellH + spacing)
            cells += Rect(x, y, x + cellW, y + cellH)
        }

        val palette: Rect? = when {
            !hasPalette -> null
            overlay -> Rect((width - railW) / 2f, 0f, (width + railW) / 2f, height)
            center -> {
                // Sit in the gap between the left and right groups.
                val leftEnd = spacing + (leftCols - 1) * (cellW + spacing) + cellW
                val railLeft = leftEnd + spacing / 2f
                Rect(railLeft, 0f, railLeft + railW, height)
            }
            leftRail -> Rect(0f, 0f, railW, height)
            else -> Rect(width - railW, 0f, width, height) // SIDE (and centre fallback)
        }

        return Layout(cells, palette)
    }
}
