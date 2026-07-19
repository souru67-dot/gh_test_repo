package com.souru.colorhunt.data.export

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Rect
import android.graphics.RectF
import com.souru.colorhunt.domain.config.CollageGrid
import com.souru.colorhunt.domain.config.CollageStyle

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
    fun render(
        cells: List<Bitmap?>,
        cellCount: Int,
        style: CollageStyle,
        widthPx: Int,
        heightPx: Int,
        density: Float,
        addWatermark: Boolean,
    ): Bitmap {
        val output = Bitmap.createBitmap(widthPx, heightPx, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(output)
        canvas.drawColor(style.backgroundColor)

        val columns = CollageGrid.columnsFor(cellCount)
        val rows = CollageGrid.rowsFor(cellCount)

        val spacing = style.cellSpacingDp * density
        val radius = style.cornerRadiusDp * density
        val borderWidth = style.borderWidthDp * density

        val cellWidth = (widthPx - spacing * (columns + 1)) / columns
        val cellHeight = (heightPx - spacing * (rows + 1)) / rows

        val imagePaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
        val borderPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = borderWidth
            color = style.borderColor
        }

        for (index in 0 until cellCount) {
            val col = index % columns
            val row = index / columns
            val left = spacing + col * (cellWidth + spacing)
            val top = spacing + row * (cellHeight + spacing)
            val dest = RectF(left, top, left + cellWidth, top + cellHeight)

            val bitmap = cells.getOrNull(index)
            if (bitmap != null && !bitmap.isRecycled) {
                drawCenterCropped(canvas, bitmap, dest, radius, imagePaint)
            }
            if (borderWidth > 0f) {
                canvas.drawRoundRect(dest, radius, radius, borderPaint)
            }
        }

        if (addWatermark) drawWatermark(canvas, widthPx, heightPx, density)
        return output
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
