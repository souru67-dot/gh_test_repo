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
import com.souru.colorhunt.domain.config.FocalPoint
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
        focals: List<FocalPoint> = emptyList(),
        /** Per-cell dominant colours for the on-photo HEX chip overlay (Pro). */
        cellColors: List<Int?> = emptyList(),
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
                drawCenterCropped(canvas, bitmap, dest, radius, imagePaint, focals.getOrNull(index) ?: FocalPoint())
            }
            if (borderWidth > 0f) {
                canvas.drawRoundRect(dest, radius, radius, borderPaint)
            }
            if (style.hexOverlay && bitmap != null) {
                cellColors.getOrNull(index)?.let { drawHexChip(canvas, it, dest, density) }
            }
        }

        layout.palette?.let {
            drawPalette(canvas, paletteColors, it, density, translucent = style.palette == PalettePlacement.OVERLAY)
        }
        if (addWatermark) drawWatermark(canvas, widthPx, heightPx, density)
        return output
    }

    /**
     * The HEX palette column (組写風). One block per photo, its hex code centred
     * in a refined serif with generous letter-spacing, auto-sized to never
     * overflow. Works as a centre column or a side rail — the caller's rectangle
     * decides. With [translucent] the blocks are alpha-blended so the column
     * floats over the photos and melts into them (OVERLAY placement); a soft
     * text shadow keeps the codes legible on busy backgrounds.
     */
    private fun drawPalette(
        canvas: Canvas,
        colors: List<Int>,
        rect: CollageGeometry.Rect,
        density: Float,
        translucent: Boolean = false,
    ) {
        val n = colors.size
        if (n == 0) return
        val left = rect.left
        val right = rect.right
        val railWidth = rect.width
        val blockH = rect.height / n
        val fillAlpha = if (translucent) 150 else 255

        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        // An elegant serif (mincho-like) face for the hex codes — feels editorial.
        val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textAlign = Paint.Align.CENTER
            letterSpacing = 0.06f
            if (translucent) {
                setShadowLayer(2.5f * density, 0f, 1f * density, Color.argb(150, 0, 0, 0))
            }
        }
        val sep = Paint().apply {
            color = Color.argb(if (translucent) 18 else 30, 0, 0, 0)
            strokeWidth = (1f * density).coerceAtLeast(1f)
        }

        val cx = left + railWidth / 2f
        // Reference look (組写): a small, quiet serif that sits inside the block
        // rather than filling it — sized to the block but never wider than ~70%.
        val maxTextWidth = railWidth * 0.70f
        colors.forEachIndexed { i, color ->
            val top = rect.top + i * blockH
            fill.color = Color.argb(fillAlpha, Color.red(color), Color.green(color), Color.blue(color))
            canvas.drawRect(left, top, right, top + blockH, fill)

            val hex = "#%06X".format(0xFFFFFF and color)
            text.textSize = (blockH * 0.14f).coerceAtLeast(6f * density)
            val measured = text.measureText(hex)
            if (measured > maxTextWidth) text.textSize *= maxTextWidth / measured

            val luminance = 0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)
            text.color = if (!translucent && luminance > 135) {
                Color.argb(205, 25, 25, 32)
            } else {
                Color.argb(if (translucent) 235 else 205, 240, 240, 244)
            }
            val cy = top + blockH / 2f - (text.ascent() + text.descent()) / 2f
            canvas.drawText(hex, cx, cy, text)

            if (i > 0) canvas.drawLine(left, top, right, top, sep)
        }
    }

    /**
     * A small pill overlaid on the photo's bottom-left: colour dot + HEX code —
     * the same look as the Hunt tab's thumbnails, carried onto the collage (Pro).
     */
    private fun drawHexChip(canvas: Canvas, color: Int, cell: RectF, density: Float) {
        val chipH = (cell.height() * 0.11f).coerceIn(9f * density, 16f * density)
        val margin = chipH * 0.45f
        val text = Paint(Paint.ANTI_ALIAS_FLAG or Paint.SUBPIXEL_TEXT_FLAG).apply {
            typeface = Typeface.create(Typeface.SERIF, Typeface.NORMAL)
            textSize = chipH * 0.58f
            letterSpacing = 0.05f
            this.color = Color.argb(240, 255, 255, 255)
        }
        val hex = "#%06X".format(0xFFFFFF and color)
        val dotR = chipH * 0.28f
        val padH = chipH * 0.42f
        val chipW = padH + dotR * 2 + padH * 0.7f + text.measureText(hex) + padH

        val left = cell.left + margin
        val bottom = cell.bottom - margin
        val rect = RectF(left, bottom - chipH, left + chipW, bottom)
        if (rect.right > cell.right - margin) return // cell too small for a legible chip

        val scrim = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = Color.argb(115, 10, 10, 14) }
        canvas.drawRoundRect(rect, chipH / 2f, chipH / 2f, scrim)

        val dot = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.color = color or (0xFF shl 24) }
        val dotCx = rect.left + padH + dotR
        val dotCy = rect.centerY()
        canvas.drawCircle(dotCx, dotCy, dotR, dot)
        canvas.drawCircle(dotCx, dotCy, dotR, Paint(Paint.ANTI_ALIAS_FLAG).apply {
            this.style = Paint.Style.STROKE
            strokeWidth = (1f * density).coerceAtLeast(1f)
            this.color = Color.argb(200, 255, 255, 255)
        })

        val tx = dotCx + dotR + padH * 0.7f
        val ty = rect.centerY() - (text.ascent() + text.descent()) / 2f
        canvas.drawText(hex, tx, ty, text)
    }

    /** Crops [bitmap] to fill [dest] (clipped to rounded corners), positioning the
     *  crop window by [focal] (0.5/0.5 = centre). */
    private fun drawCenterCropped(
        canvas: Canvas,
        bitmap: Bitmap,
        dest: RectF,
        radius: Float,
        paint: Paint,
        focal: FocalPoint,
    ) {
        val srcRatio = bitmap.width.toFloat() / bitmap.height
        val dstRatio = dest.width() / dest.height()
        val src = if (srcRatio > dstRatio) {
            // Source is wider: crop the sides, panned by focal.x.
            val cropW = (bitmap.height * dstRatio).toInt().coerceAtMost(bitmap.width)
            val x = ((bitmap.width - cropW) * focal.x).toInt().coerceIn(0, bitmap.width - cropW)
            Rect(x, 0, x + cropW, bitmap.height)
        } else {
            // Source is taller: crop top/bottom, panned by focal.y.
            val cropH = (bitmap.width / dstRatio).toInt().coerceAtMost(bitmap.height)
            val y = ((bitmap.height - cropH) * focal.y).toInt().coerceIn(0, bitmap.height - cropH)
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
