package com.souru.colorhunt.domain.config

import kotlin.math.ceil
import kotlin.math.sqrt

/**
 * SNS canvas size presets. Ratios drive the on-screen canvas; the export uses
 * the exact [exportWidth] x [exportHeight] pixels so output resolution follows
 * the chosen preset (spec: "書き出し解像度はサイズプリセットに準拠").
 *
 * [proOnly] wires the Phase 4 pay-gate: the free tier gets a few common sizes,
 * Pro unlocks the rest plus custom.
 */
enum class SnsSize(
    val ratioWidth: Int,
    val ratioHeight: Int,
    val exportWidth: Int,
    val exportHeight: Int,
    val proOnly: Boolean,
) {
    SQUARE(1, 1, 1080, 1080, proOnly = false),
    PORTRAIT_4_5(4, 5, 1080, 1350, proOnly = false),
    STORY_9_16(9, 16, 1080, 1920, proOnly = false),
    LANDSCAPE_16_9(16, 9, 1920, 1080, proOnly = true);

    val aspectRatio: Float get() = ratioWidth.toFloat() / ratioHeight.toFloat()

    companion object {
        val DEFAULT = SQUARE
    }
}

/**
 * Number-of-cells presets for the collage. "Custom" means: use exactly as many
 * cells as there are selected photos.
 */
enum class CellCountPreset(val count: Int?) {
    THREE(3),
    FOUR(4),
    SIX(6),
    NINE(9),
    CUSTOM(null);
}

/**
 * Adjustable collage styling. Pure data so it can be snapshotted, previewed and
 * rendered identically for screen and export.
 */
data class CollageStyle(
    val cellSpacingDp: Float = 6f,
    val cornerRadiusDp: Float = 8f,
    val borderWidthDp: Float = 0f,
    val borderColor: Int = 0xFFFFFFFF.toInt(),
    val backgroundColor: Int = 0xFFFFFFFF.toInt(),
    /** When true the background follows the current theme colour instead of [backgroundColor]. */
    val backgroundFollowsTheme: Boolean = false,
    /** Pro template: append a bottom strip of the photos' dominant colours + hex codes. */
    val paletteStrip: Boolean = false,
)

/** Grid geometry for a given cell count, chosen to stay close to square. */
object CollageGrid {
    fun columnsFor(cellCount: Int): Int = when (cellCount) {
        1 -> 1
        2 -> 2
        3 -> 3
        4 -> 2
        6 -> 3
        9 -> 3
        else -> ceil(sqrt(cellCount.toDouble())).toInt().coerceAtLeast(1)
    }

    fun rowsFor(cellCount: Int): Int {
        val cols = columnsFor(cellCount)
        return ceil(cellCount.toDouble() / cols).toInt().coerceAtLeast(1)
    }
}
