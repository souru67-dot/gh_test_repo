package com.souru.lumina.ui.photoedit

import android.graphics.RectF
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.unit.dp

private enum class DragTarget { NONE, INSIDE, TOP_LEFT, TOP_RIGHT, BOTTOM_LEFT, BOTTOM_RIGHT }

private const val MIN_SIZE = 0.10f

/**
 * トリミング枠オーバーレイ。表示中の画像とまったく同じ領域に重ねる前提で、
 * 正規化(0..1)のクロップ矩形を編集する。
 *
 * @param aspectRatio 固定する実アスペクト比(幅/高さ)。nullでフリー
 * @param imageAspect 画像自体のアスペクト比(幅/高さ)。正規化空間の補正に使う
 */
@Composable
fun CropOverlay(
    cropRect: RectF,
    aspectRatio: Float?,
    imageAspect: Float,
    onCropChange: (RectF) -> Unit,
    modifier: Modifier = Modifier,
) {
    val rect by rememberUpdatedState(cropRect)
    val aspect by rememberUpdatedState(aspectRatio)
    var dragTarget by remember { mutableStateOf(DragTarget.NONE) }

    Canvas(
        modifier = modifier
            .fillMaxSize()
            .pointerInput(imageAspect) {
                detectDragGestures(
                    onDragStart = { position ->
                        dragTarget = hitTest(position, rect, size.width.toFloat(), size.height.toFloat())
                    },
                    onDragEnd = { dragTarget = DragTarget.NONE },
                    onDragCancel = { dragTarget = DragTarget.NONE },
                ) { change, dragAmount ->
                    change.consume()
                    val dx = dragAmount.x / size.width
                    val dy = dragAmount.y / size.height
                    val updated = applyDrag(rect, dragTarget, dx, dy, aspect, imageAspect)
                    if (updated != null) onCropChange(updated)
                }
            },
    ) {
        val w = size.width
        val h = size.height
        val left = rect.left * w
        val top = rect.top * h
        val right = rect.right * w
        val bottom = rect.bottom * h

        val scrim = Color.Black.copy(alpha = 0.55f)
        // クロップ外を暗くする
        drawRect(scrim, topLeft = Offset.Zero, size = Size(w, top))
        drawRect(scrim, topLeft = Offset(0f, bottom), size = Size(w, h - bottom))
        drawRect(scrim, topLeft = Offset(0f, top), size = Size(left, bottom - top))
        drawRect(scrim, topLeft = Offset(right, top), size = Size(w - right, bottom - top))

        val border = Color.White
        val stroke = 1.5.dp.toPx()
        drawRect(
            color = border,
            topLeft = Offset(left, top),
            size = Size(right - left, bottom - top),
            style = androidx.compose.ui.graphics.drawscope.Stroke(stroke),
        )

        // 三分割線
        val thin = 0.5.dp.toPx()
        val third = Color.White.copy(alpha = 0.4f)
        for (i in 1..2) {
            val x = left + (right - left) * i / 3f
            drawLine(third, Offset(x, top), Offset(x, bottom), thin)
            val y = top + (bottom - top) * i / 3f
            drawLine(third, Offset(left, y), Offset(right, y), thin)
        }

        // コーナーハンドル
        val handleLen = 18.dp.toPx()
        val handleStroke = 3.dp.toPx()
        val corners = listOf(
            Offset(left, top) to Pair(Offset(handleLen, 0f), Offset(0f, handleLen)),
            Offset(right, top) to Pair(Offset(-handleLen, 0f), Offset(0f, handleLen)),
            Offset(left, bottom) to Pair(Offset(handleLen, 0f), Offset(0f, -handleLen)),
            Offset(right, bottom) to Pair(Offset(-handleLen, 0f), Offset(0f, -handleLen)),
        )
        corners.forEach { (corner, dirs) ->
            drawLine(border, corner, corner + dirs.first, handleStroke)
            drawLine(border, corner, corner + dirs.second, handleStroke)
        }
    }
}

private fun hitTest(position: Offset, rect: RectF, width: Float, height: Float): DragTarget {
    val touchRadius = 0.07f * maxOf(width, height)
    val corners = mapOf(
        DragTarget.TOP_LEFT to Offset(rect.left * width, rect.top * height),
        DragTarget.TOP_RIGHT to Offset(rect.right * width, rect.top * height),
        DragTarget.BOTTOM_LEFT to Offset(rect.left * width, rect.bottom * height),
        DragTarget.BOTTOM_RIGHT to Offset(rect.right * width, rect.bottom * height),
    )
    corners.forEach { (target, corner) ->
        if ((position - corner).getDistance() <= touchRadius) return target
    }
    val inside = Rect(
        rect.left * width,
        rect.top * height,
        rect.right * width,
        rect.bottom * height,
    )
    return if (inside.contains(position)) DragTarget.INSIDE else DragTarget.NONE
}

private fun applyDrag(
    rect: RectF,
    target: DragTarget,
    dx: Float,
    dy: Float,
    aspectRatio: Float?,
    imageAspect: Float,
): RectF? {
    if (target == DragTarget.NONE) return null
    val r = RectF(rect)
    when (target) {
        DragTarget.INSIDE -> {
            val w = r.width()
            val h = r.height()
            var left = (r.left + dx).coerceIn(0f, 1f - w)
            var top = (r.top + dy).coerceIn(0f, 1f - h)
            return RectF(left, top, left + w, top + h)
        }

        DragTarget.TOP_LEFT -> {
            r.left = (r.left + dx).coerceIn(0f, r.right - MIN_SIZE)
            r.top = (r.top + dy).coerceIn(0f, r.bottom - MIN_SIZE)
        }

        DragTarget.TOP_RIGHT -> {
            r.right = (r.right + dx).coerceIn(r.left + MIN_SIZE, 1f)
            r.top = (r.top + dy).coerceIn(0f, r.bottom - MIN_SIZE)
        }

        DragTarget.BOTTOM_LEFT -> {
            r.left = (r.left + dx).coerceIn(0f, r.right - MIN_SIZE)
            r.bottom = (r.bottom + dy).coerceIn(r.top + MIN_SIZE, 1f)
        }

        DragTarget.BOTTOM_RIGHT -> {
            r.right = (r.right + dx).coerceIn(r.left + MIN_SIZE, 1f)
            r.bottom = (r.bottom + dy).coerceIn(r.top + MIN_SIZE, 1f)
        }

        DragTarget.NONE -> return null
    }

    if (aspectRatio != null) {
        // 高さをアスペクト比に合わせて再計算(正規化空間の非等方性を補正)
        val targetHeightN = r.width() * imageAspect / aspectRatio
        when (target) {
            DragTarget.TOP_LEFT, DragTarget.TOP_RIGHT ->
                r.top = r.bottom - targetHeightN

            else ->
                r.bottom = r.top + targetHeightN
        }
        if (r.top < 0f || r.bottom > 1f || r.height() < MIN_SIZE) return null
    }
    return r
}
