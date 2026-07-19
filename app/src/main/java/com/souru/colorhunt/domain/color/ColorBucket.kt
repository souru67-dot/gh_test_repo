package com.souru.colorhunt.domain.color

/**
 * The colour "buckets" a hunted photo can be sorted into.
 *
 * Twelve buckets: nine chromatic hues plus three achromatic ones
 * (white / black / grey). Names, ordering and the boundary values used to
 * assign a colour live here and in [ColorClassifierConfig] as plain constants
 * so they can be tuned later without touching the algorithm.
 *
 * [swatch] is a packed 0xFFRRGGBB representative colour used for chips, dots and
 * (Phase 3) map pins. It is a plain Int to stay Android-free; the UI layer wraps
 * it in a Compose `Color`.
 */
enum class ColorBucket(val swatch: Int) {
    RED(0xFFE53935.toInt()),
    ORANGE(0xFFFB8C00.toInt()),
    YELLOW(0xFFFDD835.toInt()),
    YELLOW_GREEN(0xFFC0CA33.toInt()),
    GREEN(0xFF43A047.toInt()),
    CYAN(0xFF26C6DA.toInt()),
    BLUE(0xFF1E88E5.toInt()),
    PURPLE(0xFF8E24AA.toInt()),
    PINK(0xFFEC407A.toInt()),
    WHITE(0xFFFAFAFA.toInt()),
    BLACK(0xFF212121.toInt()),
    GRAY(0xFF9E9E9E.toInt());

    val isAchromatic: Boolean
        get() = this == WHITE || this == BLACK || this == GRAY
}
