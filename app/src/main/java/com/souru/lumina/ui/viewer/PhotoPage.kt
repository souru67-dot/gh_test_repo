package com.souru.lumina.ui.viewer

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.spring
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChanged
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.souru.lumina.data.model.GalleryEntry
import kotlinx.coroutines.launch
import kotlin.math.abs

private const val MAX_SCALE = 8f
private const val DOUBLE_TAP_SCALE = 2.5f

/**
 * 写真1枚のページ。ピンチ/ダブルタップでズーム、等倍時の縦ドラッグで閉じる。
 */
@Composable
fun PhotoPage(
    entry: GalleryEntry,
    onToggleChrome: () -> Unit,
    onDismiss: () -> Unit,
    onDismissProgress: (Float) -> Unit,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val density = LocalDensity.current

    val scale = remember { Animatable(1f) }
    val offsetX = remember { Animatable(0f) }
    val offsetY = remember { Animatable(0f) }
    val dismissY = remember { Animatable(0f) }

    val currentOnDismiss by rememberUpdatedState(onDismiss)
    val currentOnDismissProgress by rememberUpdatedState(onDismissProgress)
    val dismissThresholdPx = with(density) { 120.dp.toPx() }

    fun maxOffsetX(containerWidth: Float) = (containerWidth * (scale.value - 1f)) / 2f
    fun maxOffsetY(containerHeight: Float) = (containerHeight * (scale.value - 1f)) / 2f

    Box(
        modifier = Modifier
            .fillMaxSize()
            .pointerInput(entry.id) {
                detectTapGestures(
                    onTap = { onToggleChrome() },
                    onDoubleTap = { tapOffset ->
                        scope.launch {
                            if (scale.value > 1.5f) {
                                launch { scale.animateTo(1f, spring()) }
                                launch { offsetX.animateTo(0f, spring()) }
                                launch { offsetY.animateTo(0f, spring()) }
                            } else {
                                val target = DOUBLE_TAP_SCALE
                                val cx = (size.width / 2f - tapOffset.x) * (target - 1f)
                                val cy = (size.height / 2f - tapOffset.y) * (target - 1f)
                                launch { scale.animateTo(target, spring()) }
                                launch {
                                    offsetX.animateTo(
                                        cx.coerceIn(-size.width * (target - 1f) / 2f, size.width * (target - 1f) / 2f),
                                        spring(),
                                    )
                                }
                                launch {
                                    offsetY.animateTo(
                                        cy.coerceIn(-size.height * (target - 1f) / 2f, size.height * (target - 1f) / 2f),
                                        spring(),
                                    )
                                }
                            }
                        }
                    },
                )
            }
            .pointerInput(entry.id) {
                awaitEachGesture {
                    var dismissMode = false
                    var totalDrag = Offset.Zero
                    awaitFirstDown(requireUnconsumed = false)
                    do {
                        val event = awaitPointerEvent()
                        val pointerCount = event.changes.count { it.pressed }
                        val zoomChange = event.calculateZoom()
                        val panChange = event.calculatePan()

                        if (pointerCount >= 2 || scale.value > 1f) {
                            // ズーム/パン
                            dismissMode = false
                            val newScale = (scale.value * zoomChange).coerceIn(1f, MAX_SCALE)
                            val maxX = (size.width * (newScale - 1f)) / 2f
                            val maxY = (size.height * (newScale - 1f)) / 2f
                            val newX = (offsetX.value + panChange.x).coerceIn(-maxX, maxX)
                            val newY = (offsetY.value + panChange.y).coerceIn(-maxY, maxY)
                            scope.launch {
                                scale.snapTo(newScale)
                                offsetX.snapTo(newX)
                                offsetY.snapTo(newY)
                            }
                            event.changes.forEach { if (it.positionChanged()) it.consume() }
                        } else {
                            // 等倍・1本指: 縦方向優位なら「下スワイプで閉じる」
                            totalDrag += panChange
                            if (!dismissMode &&
                                abs(totalDrag.y) > viewConfiguration.touchSlop &&
                                abs(totalDrag.y) > abs(totalDrag.x) * 1.4f
                            ) {
                                dismissMode = true
                            }
                            if (dismissMode) {
                                val newY = (dismissY.value + panChange.y).coerceAtLeast(-dismissThresholdPx / 2f)
                                scope.launch { dismissY.snapTo(newY) }
                                currentOnDismissProgress(
                                    (newY / (dismissThresholdPx * 2f)).coerceIn(0f, 1f),
                                )
                                event.changes.forEach { if (it.positionChanged()) it.consume() }
                            }
                        }
                    } while (event.changes.any { it.pressed })

                    if (dismissMode) {
                        if (dismissY.value > dismissThresholdPx) {
                            currentOnDismiss()
                        } else {
                            scope.launch {
                                dismissY.animateTo(0f, spring())
                                currentOnDismissProgress(0f)
                            }
                        }
                    }
                    if (scale.value <= 1.02f && !dismissMode) {
                        scope.launch {
                            scale.snapTo(1f)
                            offsetX.animateTo(0f, spring())
                            offsetY.animateTo(0f, spring())
                        }
                    }
                }
            },
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(entry.item.uri)
                .crossfade(true)
                .build(),
            contentDescription = entry.item.displayName,
            contentScale = ContentScale.Fit,
            modifier = Modifier
                .fillMaxSize()
                .graphicsLayer {
                    val dismissScale = 1f - (dismissY.value / (dismissThresholdPx * 8f)).coerceIn(0f, 0.15f)
                    scaleX = scale.value * dismissScale
                    scaleY = scale.value * dismissScale
                    translationX = offsetX.value
                    translationY = offsetY.value + dismissY.value
                },
        )
    }
}
