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
 * 旧Xperiaアルバム風の「ピンチで列数変更」を、離散ジャンプではなく
 * **連続スケール追従 + シームレスなスナップ**で行う。
 *
 * 指の動きに合わせてグリッド全体を [onScale] のスケールで拡大縮小し(呼び出し側が
 * graphicsLayer に適用)、拡大率が「隣の列数のセルサイズ」に達した瞬間に列数を
 * 切り替え、同時にスケールを1へ再ベースする。切替時に見かけのセルサイズが連続する
 * ため段差が出ない。ジェスチャ終了時は [onSettle] で残りのスケールをばね収束させる。
 *
 * 列数の限界(min/max)では列を切り替えられないぶんスケールが伸び、
 * [0.6, 1.8] にクランプしてゴムのような抵抗感を出す。
 */
fun Modifier.pinchToChangeColumns(
    columns: () -> Int,
    minColumns: Int,
    maxColumns: Int,
    onColumnsChange: (Int) -> Unit,
    onScale: (Float) -> Unit = {},
    onSettle: () -> Unit = {},
): Modifier = pointerInput(minColumns, maxColumns) {
    awaitEachGesture {
        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
        var scale = 1f
        var pinching = false
        while (true) {
            val event = awaitPointerEvent(PointerEventPass.Initial)
            if (event.changes.none { it.pressed }) break
            if (event.changes.count { it.pressed } >= 2) {
                pinching = true
                scale *= event.calculateZoom()
                val current = columns()
                // 列数を1段変えたときのセルサイズ比。ここへ達したら切替+再ベース
                val toFewer = current.toFloat() / (current - 1) // ピンチアウト(拡大)
                val toMore = current.toFloat() / (current + 1)  // ピンチイン(縮小)
                if (current > minColumns && scale >= toFewer) {
                    onColumnsChange(current - 1)
                    scale /= toFewer
                } else if (current < maxColumns && scale <= toMore) {
                    onColumnsChange(current + 1)
                    scale /= toMore
                }
                scale = scale.coerceIn(0.6f, 1.8f)
                onScale(scale)
                event.changes.forEach { it.consume() }
            }
        }
        if (pinching) onSettle()
    }
}

@Composable
fun rememberColumnsProvider(columns: Int): () -> Int {
    val state by rememberUpdatedState(columns)
    return { state }
}
