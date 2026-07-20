package com.souru.colorhunt.domain.config

/**
 * Swift-friendly facade over [CollageGeometry] for the iOS app.
 *
 * Kotlin enum entries bridge awkwardly into Swift (e.g. `NONE` collides with
 * `Optional.none`), so this API takes plain ordinals and primitives and returns
 * the same [CollageGeometry.Layout] Android uses — keeping the two apps
 * pixel-compatible without fighting the ObjC bridge.
 */
object CollageBridge {

    /** Ordinals matching [CollageLayout]: 0=GRID, 1=VERTICAL, 2=TWO_COLUMN. */
    fun layoutOf(ordinal: Int): CollageLayout =
        CollageLayout.entries.getOrElse(ordinal) { CollageLayout.GRID }

    /** Ordinals matching [PalettePlacement]: 0=NONE, 1=CENTER, 2=SIDE, 3=LEFT, 4=OVERLAY. */
    fun placementOf(ordinal: Int): PalettePlacement =
        PalettePlacement.entries.getOrElse(ordinal) { PalettePlacement.NONE }

    fun compute(
        cellCount: Int,
        layoutOrdinal: Int,
        placementOrdinal: Int,
        spacingFrac: Float,
        width: Float,
        height: Float,
        overlayHorizontal: Boolean,
        overlayPosFrac: Float,
        overlayWidthFrac: Float,
    ): CollageGeometry.Layout = CollageGeometry.compute(
        cellCount = cellCount,
        layout = layoutOf(layoutOrdinal),
        placement = placementOf(placementOrdinal),
        spacingFrac = spacingFrac,
        width = width,
        height = height,
        overlayHorizontal = overlayHorizontal,
        overlayPosFrac = overlayPosFrac,
        overlayWidthFrac = overlayWidthFrac,
    )

    /**
     * Same computation, flattened into a plain `FloatArray` for maximum Swift
     * interop safety (no nested Kotlin classes to bridge). Layout:
     *
     * ```
     * [0]      = cell count N
     * [1]      = palette present (1 or 0)
     * [2..5]   = palette rect: left, top, right, bottom (valid only if present)
     * [6 ...]  = N * 4 floats per cell: left, top, right, bottom
     * ```
     */
    fun computeFlat(
        cellCount: Int,
        layoutOrdinal: Int,
        placementOrdinal: Int,
        spacingFrac: Float,
        width: Float,
        height: Float,
        overlayHorizontal: Boolean,
        overlayPosFrac: Float,
        overlayWidthFrac: Float,
    ): FloatArray {
        val layout = compute(
            cellCount, layoutOrdinal, placementOrdinal, spacingFrac, width, height,
            overlayHorizontal, overlayPosFrac, overlayWidthFrac,
        )
        val n = layout.cells.size
        val out = FloatArray(6 + n * 4)
        out[0] = n.toFloat()
        val p = layout.palette
        out[1] = if (p != null) 1f else 0f
        out[2] = p?.left ?: 0f
        out[3] = p?.top ?: 0f
        out[4] = p?.right ?: 0f
        out[5] = p?.bottom ?: 0f
        layout.cells.forEachIndexed { i, r ->
            val b = 6 + i * 4
            out[b] = r.left
            out[b + 1] = r.top
            out[b + 2] = r.right
            out[b + 3] = r.bottom
        }
        return out
    }
}
