package com.souru.colorhunt.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import android.graphics.Typeface
import com.souru.colorhunt.domain.config.CollageGeometry
import com.souru.colorhunt.domain.config.CollageStyle
import com.souru.colorhunt.domain.config.PalettePlacement

/**
 * Renders a collage to a [Bitmap] purely from data, so the exact same code path
 * produces both the on-screen preview and the exported image (WYSIWYG). No
 * Compose or view hierarchy involved — just a [Canvas].
 *
 * Cells are filled left-to-right, top-to-bottom; any cell without a source
 * bitmap is left as background so under-filled presets still look intentional.
 */
object CollageRenderer {

    /**
     * @param cells      source bitmaps in cell order; a null entry = empty cell.
     * @param cellCount  total number of cells (>= cells.size).
     * @param widthPx/heightPx  output size, taken from the chosen SNS preset.
     * @param addWatermark  draw the free-tier watermark (Phase 4 gate).
     */
    private const val REFERENCE_WIDTH_DP = 360f

    /**
     * @param cells         source bitmaps in cell order; a null entry = empty cell.
     * @param cellCount     total number of cells (>= cells.size).
     * @param widthPx/heightPx  output size, taken from the chosen SNS preset.
     * @param addWatermark  draw the free-tier watermark (Phase 4 gate).
     * @param paletteColors dominant colours (cell order) for the Pro palette.
     */
    fun render(
        cells: List<Bitmap?>,
        cellCount: Int,
        style: CollageStyle,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        addWatermark: Boolean,
        paletteColors: List<Int> = emptyList(),
    ): Bitmap {
        val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(style.backgroundColor)

        val placement = if (paletteColors.isNotEmpty()) style.palette else PalettePlacement.NONE
        val spacingFrac = style.cellSpacingDp / REFERENCE_WIDTH_DP
        val layout = CollageGeometry.compute(
            cellCount = cellCount,
            layout = style.layout,
            placement = placement,
            spacingFrac = spacingFrac,
            width = widthPx.toFloat(),
            height = heightPx.toFloat(),
        )

        val radius = style.cornerRadiusDp * density
        val borderWidth = style.borderWidthDp * density

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = borderWidth
            color = style.borderColor
        }

        layout.cells.forEachIndexed { index, r ->
            val dest = RectF(r.left, r.top, r.right, r.bottom)
            val bitmap = cells.getOrNull(index)
            if (bitmap != null && !bitmap.isRecycled) {
                drawCenterCropped(canvas, bitmap, dest, radius, imagePaint)
            }
            if (borderWidth > 0f) {
                canvas.drawRoundRect(dest, radius, radius, borderPaint)
            }
        }

        layout.palette?.let { drawPalette(canvas, paletteColors, it, density) }
        if (addWatermark) drawWatermark(canvas, widthPx, heightPx, density)
        return output
    }

    /**
     * The HEX palette column (組写風). One block per photo, its hex code centred
     * in a refined light sans-serif with generous letter-spacing, auto-sized to
     * never overflow. Works as a centre column or a side rail — the caller's
     * rectangle decides.
     */
    private fun drawPalette(
        canvas: Canvas,
        colors: List<Int>,
        rect: CollageGeometry.Rect,
        density: Float,
    ) {
        val n = colors.size
        if (n == 0) return
        val left = rect.left
        val right = rect.right
        val railWidth = rect.width
        val blockH = rect.height / n

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        // An elegant serif (mincho-like) face for the hex codes — feels editorial.
        val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.12f
        }
        val sep = Paint().apply {
            color = Color.argb(30, 0, 0, 0)
            strokeWidth = (1f * density).coerceAtLeast(1f)
        }

        val cx = left + railWidth / 2f
        val maxTextWidth = railWidth * 0.82f
        colors.forEachIndexed { i, color ->
            val top = rect.top + i * blockH
            fill.color = color or (0xFF shl 24)
            canvas.drawRect(left, top, right, top + blockH, fill)

            val hex = "#%06X".format(0xFFFFFF and color)
            text.textSize = (blockH * 0.20f).coerceAtLeast(6f * density)
            val measured = text.measureText(hex)
            if (measured > maxTextWidth) text.textSize *= maxTextWidth / measured

            val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
            text.color = if (luminance > 135) Color.argb(235, 20, 20, 26) else Color.argb(235, 245, 245, 250)
            val cy = top + blockH / 2f - (text.ascent() + text.descent()) / 2f
            canvas.drawText(hex, cx, cy, text)

            if (i > 0) canvas.drawLine(left, top, right, top, sep)
        }
    }

    /** Center-crops [bitmap] to fill [dest], clipped to rounded corners. */
    private fun drawCenterCropped(
        canvas: Canvas,
        bitmap: Bitmap,
        dest: RectF,
        radius: Float,
        paint: Paint,
    ) {
        val srcRatio = bitmap.width.toFloat() / bitmap.height
        val dstRatio = dest.width() / dest.height()
        val src = if (srcRatio > dstRatio) {
            // Source is wider: crop the sides.
            val cropW = (bitmap.height * dstRatio).toInt().coerceAtMost(bitmap.width)
            val x = (bitmap.width - cropW) / 2
            Rect(x, 0, x + cropW, bitmap.height)
        } else {
            // Source is taller: crop top/bottom.
            val cropH = (bitmap.width / dstRatio).toInt().coerceAtMost(bitmap.height)
            val y = (bitmap.height - cropH) / 2
            Rect(0, y, bitmap.width, y + cropH)
        }

        val save = canvas.save()
        val clip = android.graphics.Path().apply { addRoundRect(dest, radius, radius, android.graphics.Path.Direction.CW) }
        canvas.clipPath(clip)
        canvas.drawBitmap(bitmap, src, dest, paint)
        canvas.restoreToCount(save)
    }

    private fun drawWatermark(canvas: Canvas, width: Int, height: Int, density: Float) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.argb(140, 255, 255, 255)
            textSize = 14f * density
            setShadowLayer(2f * density, 0f, 0f, Color.argb(120, 0, 0, 0))
        }
        val text = WATERMARK_TEXT
        val margin = 10f * density
        val x = width - paint.measureText(text) - margin
        val y = height - margin
        canvas.drawText(text, x, y, paint)
    }

    private const val WATERMARK_TEXT = "ColorHunt"
}
