package com.souru.colorhunt.domain.color

/**
 * Tunable thresholds and hue boundaries for [ColorClassifier].
 *
 * Everything the algorithm keys off of is collected here as named constants so
 * the buckets can be re-calibrated later without editing the classification
 * logic. All hues are in degrees (0..360).
 */
object ColorClassifierConfig {

    // --- Achromatic separation (applied before any hue matching) ---

    /** Below this value everything is treated as [ColorBucket.BLACK]. */
    const val BLACK_MAX_VALUE = 0.16f

    /** At/above this value with low saturation a colour is [ColorBucket.WHITE]. */
    const val WHITE_MIN_VALUE = 0.90f

    /** At/below this saturation a colour is considered achromatic (white/grey/black). */
    const val ACHROMATIC_MAX_SATURATION = 0.12f

    // --- Pink refinement -------------------------------------------------------
    // A light, softly-saturated red reads as "pink" rather than "red", so reds in
    // the pink-ish region are re-labelled before the plain hue lookup.

    const val PINK_MIN_VALUE = 0.80f
    const val PINK_MAX_SATURATION = 0.55f

    /**
     * Chromatic hue ranges, each `[startInclusive, endExclusive)` in degrees.
     * Red wraps around 0/360 and is expressed as two ranges below.
     * Order does not matter; ranges must not overlap.
     */
    val HUE_RANGES: List<HueRange> = listOf(
        HueRange(ColorBucket.RED, 345f, 360f),
        HueRange(ColorBucket.RED, 0f, 15f),
        HueRange(ColorBucket.ORANGE, 15f, 45f),
        HueRange(ColorBucket.YELLOW, 45f, 66f),
        HueRange(ColorBucket.YELLOW_GREEN, 66f, 90f),
        HueRange(ColorBucket.GREEN, 90f, 156f),
        HueRange(ColorBucket.CYAN, 156f, 200f),
        HueRange(ColorBucket.BLUE, 200f, 255f),
        HueRange(ColorBucket.PURPLE, 255f, 290f),
        HueRange(ColorBucket.PINK, 290f, 345f),
    )

    data class HueRange(
        val bucket: ColorBucket,
        val startInclusive: Float,
        val endExclusive: Float,
    )
}

/**
 * Assigns a dominant colour to one of the [ColorBucket]s.
 *
 * The order matters:
 *  1. Very dark colours -> BLACK.
 *  2. Low-saturation colours -> WHITE or GRAY (by value).
 *  3. Light, soft reds/magentas -> PINK.
 *  4. Everything else -> nearest hue range.
 *
 * Pure and deterministic — no Android imports — so it is unit-tested directly.
 */
object ColorClassifier {

    fun classify(hsv: Hsv): ColorBucket {
        val cfg = ColorClassifierConfig

        // 1 & 2: achromatic separation first.
        if (hsv.value <= cfg.BLACK_MAX_VALUE) return ColorBucket.BLACK
        if (hsv.saturation <= cfg.ACHROMATIC_MAX_SATURATION) {
            return if (hsv.value >= cfg.WHITE_MIN_VALUE) ColorBucket.WHITE else ColorBucket.GRAY
        }

        // 3: light + soft reds read as pink.
        val inRedZone = hsv.hue >= 330f || hsv.hue < 20f
        if (inRedZone && hsv.value >= cfg.PINK_MIN_VALUE && hsv.saturation <= cfg.PINK_MAX_SATURATION) {
            return ColorBucket.PINK
        }

        // 4: hue lookup.
        val hue = hsv.hue.let { if (it >= 360f) it - 360f else it }
        return cfg.HUE_RANGES.firstOrNull { hue >= it.startInclusive && hue < it.endExclusive }?.bucket
            ?: ColorBucket.RED // hue is continuous over the ranges; RED is a safe fallback.
    }

    /** Convenience overload from a packed colour int. */
    fun classify(colorInt: Int): ColorBucket = classify(Hsv.fromColorInt(colorInt))
}
