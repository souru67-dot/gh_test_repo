package com.souru.colorhunt.domain.color

/**
 * Pure-Kotlin HSV colour representation, kept free of any Android dependency so
 * the whole classification layer can be shared with a future Kotlin
 * Multiplatform / iOS target and exercised in plain JVM unit tests.
 *
 * @property hue        0f..360f (degrees). Undefined for achromatic colours; kept at 0f then.
 * @property saturation 0f..1f
 * @property value      0f..1f (a.k.a. brightness)
 */
data class Hsv(
    val hue: Float,
    val saturation: Float,
    val value: Float,
) {
    companion object {
        /**
         * Convert 8-bit sRGB channels to HSV without touching `android.graphics.Color`,
         * so this is usable from unit tests and non-Android modules.
         */
        fun fromRgb(red: Int, green: Int, blue: Int): Hsv {
            val r = red.coerceIn(0, 255) / 255f
            val g = green.coerceIn(0, 255) / 255f
            val b = blue.coerceIn(0, 255) / 255f

            val max = maxOf(r, g, b)
            val min = minOf(r, g, b)
            val delta = max - min

            val hue = when {
                delta == 0f -> 0f
                max == r -> 60f * (((g - b) / delta) % 6f)
                max == g -> 60f * (((b - r) / delta) + 2f)
                else -> 60f * (((r - g) / delta) + 4f)
            }.let { if (it < 0f) it + 360f else it }

            val saturation = if (max == 0f) 0f else delta / max
            return Hsv(hue, saturation, max)
        }

        /** Convenience overload for a packed 0xAARRGGBB / 0xFFRRGGBB colour int. */
        fun fromColorInt(colorInt: Int): Hsv = fromRgb(
            red = (colorInt shr 16) and 0xFF,
            green = (colorInt shr 8) and 0xFF,
            blue = colorInt and 0xFF,
        )
    }
}
