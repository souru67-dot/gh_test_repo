package com.souru.lumina.ui.gallery

import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput

/**
 * 旧Xperiaアルバム風の「ピンチイン/アウトでグリッド列数変更」。
 * 2本指ピンチ中はグリッドスクロールに渡さず(Initialパスで消費)、
 * 累積ズームがしきい値を超えるたびに1段ずつ列数を変える。
 * 1回のジェスチャで連続して複数段変えられる。
 */
fun Modifier.pinchToChangeColumns(
    columns: () -> Int,
    minColumns: Int,
    maxColumns: Int,
    onColumnsChange: (Int) -> Unit,
): Modifier = pointerInput(minColumns, maxColumns) {
    awaitEachGesture {
        var zoom = 1f
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) break
            if (event.changes.count { it.pressed } >= 2) {
                zoom *= event.calculateZoom()
                val current = columns()
                if (zoom >= STEP_UP) {
                    // ピンチアウト=拡大=列を減らす
                    if (current > minColumns) onColumnsChange(current - 1)
                    zoom = 1f
                } else if (zoom <= STEP_DOWN) {
                    // ピンチイン=縮小=列を増やす
                    if (current < maxColumns) onColumnsChange(current + 1)
                    zoom = 1f
                }
                event.changes.forEach { it.consume() }
            }
        }
    }
}

private const val STEP_UP = 1.3f
private const val STEP_DOWN = 1f / 1.3f

@Composable
fun rememberColumnsProvider(columns: Int): () -> Int {
    val state by rememberUpdatedState(columns)
    return { state }
}
