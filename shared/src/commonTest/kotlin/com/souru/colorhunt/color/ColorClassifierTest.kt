package com.souru.colorhunt.color

import com.souru.colorhunt.domain.color.ColorBucket
import com.souru.colorhunt.domain.color.ColorClassifier
import com.souru.colorhunt.domain.color.Hsv
import kotlin.test.Test
import kotlin.test.assertEquals

/**
 * Verifies the pure colour-classification logic. As multiplatform commonTest it
 * runs on every target (JVM/Android and iOS), so the shared core is validated
 * the same way everywhere.
 */
class ColorClassifierTest {

    private fun bucketOf(r: Int, g: Int, b: Int): ColorBucket =
        ColorClassifier.classify(Hsv.fromRgb(r, g, b))

    @Test fun pureRed() = assertEquals(ColorBucket.RED, bucketOf(255, 0, 0))

    @Test fun orange() = assertEquals(ColorBucket.ORANGE, bucketOf(255, 140, 0))

    @Test fun yellow() = assertEquals(ColorBucket.YELLOW, bucketOf(255, 235, 59))

    @Test fun yellowGreen() = assertEquals(ColorBucket.YELLOW_GREEN, bucketOf(154, 205, 50))

    @Test fun green() = assertEquals(ColorBucket.GREEN, bucketOf(67, 160, 71))

    @Test fun cyan() = assertEquals(ColorBucket.CYAN, bucketOf(38, 198, 218))

    @Test fun blue() = assertEquals(ColorBucket.BLUE, bucketOf(30, 136, 229))

    @Test fun purple() = assertEquals(ColorBucket.PURPLE, bucketOf(142, 36, 170))

    @Test fun magentaIsPink() = assertEquals(ColorBucket.PINK, bucketOf(236, 64, 122))

    @Test fun lightSoftRedBecomesPink() = assertEquals(ColorBucket.PINK, bucketOf(255, 200, 200))

    @Test fun white() = assertEquals(ColorBucket.WHITE, bucketOf(245, 245, 245))

    @Test fun black() = assertEquals(ColorBucket.BLACK, bucketOf(18, 18, 18))

    @Test fun gray() = assertEquals(ColorBucket.GRAY, bucketOf(158, 158, 158))

    @Test fun veryDarkColorIsBlackNotHue() = assertEquals(ColorBucket.BLACK, bucketOf(10, 0, 0))

    @Test fun hueOfPureRedIsZero() {
        assertEquals(0f, Hsv.fromRgb(255, 0, 0).hue, 0.001f)
    }

    // --- Accuracy regressions (device feedback) ---

    /** A deep teal-leaning green (a green cafe door) should be GREEN, not CYAN. */
    @Test fun tealGreenIsGreen() = assertEquals(ColorBucket.GREEN, bucketOf(40, 90, 70))

    /** Hue ~169 still falls in GREEN after the boundary shift. */
    @Test fun tealHue169IsGreen() = assertEquals(ColorBucket.GREEN, bucketOf(30, 140, 120))

    /** True light sky-blue stays CYAN. */
    @Test fun skyIsCyan() = assertEquals(ColorBucket.CYAN, bucketOf(38, 198, 218))

    /** A muted blue-grey city scene should be GRAY, not BLUE. */
    @Test fun mutedBlueGrayIsGray() = assertEquals(ColorBucket.GRAY, bucketOf(100, 110, 122))

    @Test fun colorIntRoundTrip() {
        // 0xFF3F51B5 (indigo) should land in blue/purple neighbourhood, not achromatic.
        val bucket = ColorClassifier.classify(0xFF3F51B5.toInt())
        assertEquals(ColorBucket.BLUE, bucket)
    }
}
