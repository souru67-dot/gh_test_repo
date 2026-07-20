package com.souru.colorhunt.domain.config

/**
 * One-tap "magazine" presets (雑誌風テンプレート).
 *
 * Curated from current SNS collage trends — minimal editorial carousels,
 * film/contact-sheet retro, 組写 (photo-pair storytelling), paper-margin
 * magazine spreads, and seamless zero-gap story layouts. Each preset is a
 * pure transform over [CollageStyle] plus an optional SNS size switch, so
 * applying one never touches state it doesn't own (photo order, focals).
 */
data class CollageTemplate(
    val id: String,
    /** Uses Pro-gated features (palette / HEX overlay). */
    val proOnly: Boolean,
    /** Optional SNS size the template switches to. */
    val size: SnsSize? = null,
    val apply: (CollageStyle) -> CollageStyle,
)

object CollageTemplates {

    val all: List<CollageTemplate> = listOf(
        // Clean white gallery — the Unfold-style minimal editorial look.
        CollageTemplate("white", proOnly = false, size = SnsSize.PORTRAIT_4_5) {
            it.copy(
                layout = CollageLayout.GRID,
                palette = PalettePlacement.NONE,
                hexOverlay = false,
                cellSpacingDp = 14f,
                cornerRadiusDp = 0f,
                borderWidthDp = 0f,
                backgroundFollowsTheme = false,
                backgroundColor = 0xFFFAF8F4.toInt(),
            )
        },
        // Film contact sheet — dark, vertical, HEX chips as "date stamps".
        CollageTemplate("film", proOnly = true, size = SnsSize.PORTRAIT_4_5) {
            it.copy(
                layout = CollageLayout.VERTICAL,
                palette = PalettePlacement.NONE,
                hexOverlay = true,
                cellSpacingDp = 10f,
                cornerRadiusDp = 0f,
                borderWidthDp = 0f,
                backgroundFollowsTheme = false,
                backgroundColor = 0xFF121212.toInt(),
            )
        },
        // 組写 — two columns split by the centre HEX palette (the reference look).
        CollageTemplate("kumisha", proOnly = true, size = SnsSize.PORTRAIT_4_5) {
            it.copy(
                layout = CollageLayout.TWO_COLUMN,
                palette = PalettePlacement.CENTER,
                hexOverlay = false,
                cellSpacingDp = 4f,
                cornerRadiusDp = 4f,
                borderWidthDp = 0f,
                backgroundFollowsTheme = false,
                backgroundColor = 0xFF0E0E12.toInt(),
            )
        },
        // Magazine spread — warm paper margins with the palette as a left sidebar.
        CollageTemplate("magazine", proOnly = true, size = SnsSize.PORTRAIT_4_5) {
            it.copy(
                layout = CollageLayout.GRID,
                palette = PalettePlacement.LEFT,
                hexOverlay = false,
                cellSpacingDp = 16f,
                cornerRadiusDp = 2f,
                borderWidthDp = 0f,
                backgroundFollowsTheme = false,
                backgroundColor = 0xFFF2EDE3.toInt(),
            )
        },
        // Seamless story — zero gaps, translucent palette melting into the photos.
        CollageTemplate("seamless", proOnly = true, size = SnsSize.STORY_9_16) {
            it.copy(
                layout = CollageLayout.TWO_COLUMN,
                palette = PalettePlacement.OVERLAY,
                hexOverlay = false,
                cellSpacingDp = 0f,
                cornerRadiusDp = 0f,
                borderWidthDp = 0f,
                backgroundFollowsTheme = false,
                backgroundColor = 0xFF000000.toInt(),
            )
        },
    )
}
