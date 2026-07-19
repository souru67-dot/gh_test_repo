package com.souru.colorhunt.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.drawscope.Stroke
import com.souru.colorhunt.domain.color.Hsv
import kotlin.math.cos
import kotlin.math.sin

/**
 * The signature "color wheel" of a hunt (matches the Instagram color-wheel trend):
 * a rainbow ring with a dot for every photo, placed by its dominant colour —
 * angle = hue, distance from centre = saturation (greys sit in the middle).
 */
@Composable
fun HueRing(colors: List<Int>, modifier: Modifier = Modifier) {
    val rainbow = remember { (0..12).map { Color.hsv((it * 30f) % 360f, 0.9f, 1f) } }
    Canvas(modifier) {
        val dim = size.minDimension
        val stroke = dim * 0.11f
        val ringRadius = dim / 2f - stroke / 2f
        val center = Offset(size.width / 2f, size.height / 2f)

        drawCircle(
            brush = Brush.sweepGradient(rainbow, center),
            radius = ringRadius,
            center = center,
            style = Stroke(width = stroke),
        )

        val minR = dim * 0.06f
        val maxR = ringRadius - stroke * 0.7f
        val dotRadius = dim * 0.028f
        colors.forEach { colorInt ->
            val hsv = Hsv.fromColorInt(colorInt)
            val r = minR + (maxR - minR) * hsv.saturation.coerceIn(0f, 1f)
            val angle = Math.toRadians(hsv.hue.toDouble())
            val p = Offset(
                x = center.x + (cos(angle) * r).toFloat(),
                y = center.y + (sin(angle) * r).toFloat(),
            )
            drawCircle(color = Color(colorInt), radius = dotRadius, center = p)
            drawCircle(color = Color.White.copy(alpha = 0.85f), radius = dotRadius, center = p, style = Stroke(width = dim * 0.006f))
        }
    }
}
