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
}
