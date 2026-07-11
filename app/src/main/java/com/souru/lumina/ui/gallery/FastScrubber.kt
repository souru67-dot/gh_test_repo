package com.souru.lumina.ui.gallery

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectVerticalDragGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.souru.lumina.data.model.GridSlot
import kotlinx.coroutines.launch
import kotlin.math.roundToInt

/**
 * 右端の高速スクロール用スクラバー。ドラッグ中は日付バブルを表示する。
 */
@Composable
fun FastScrubber(
    gridState: LazyGridState,
    slots: List<GridSlot>,
    modifier: Modifier = Modifier,
) {
    if (slots.isEmpty()) return

    val scope = rememberCoroutineScope()
    var dragging by remember { mutableStateOf(false) }
    var dragFraction by remember { mutableFloatStateOf(0f) }
    var trackHeightPx by remember { mutableFloatStateOf(1f) }

    val scrollFraction by remember(slots) {
        derivedStateOf {
            val total = gridState.layoutInfo.totalItemsCount
            if (total <= 1) 0f else gridState.firstVisibleItemIndex.toFloat() / (total - 1)
        }
    }
    val fraction = if (dragging) dragFraction else scrollFraction

    val visible = dragging || gridState.isScrollInProgress
    val alpha by animateFloatAsState(
        targetValue = if (visible) 1f else 0f,
        animationSpec = tween(if (visible) 80 else 600),
        label = "scrubberAlpha",
    )

    val thumbHeight = 48.dp
    val density = LocalDensity.current
    val thumbHeightPx = with(density) { thumbHeight.toPx() }

    Box(
        modifier = modifier
            .fillMaxHeight()
            .width(40.dp)
            .alpha(alpha)
            .onSizeChanged { trackHeightPx = it.height.toFloat().coerceAtLeast(1f) }
            .pointerInput(slots) {
                detectVerticalDragGestures(
                    onDragStart = { offset ->
                        dragging = true
                        dragFraction = (offset.y / size.height).coerceIn(0f, 1f)
                    },
                    onDragEnd = { dragging = false },
                    onDragCancel = { dragging = false },
                ) { change, _ ->
                    change.consume()
                    dragFraction = (change.position.y / size.height).coerceIn(0f, 1f)
                    val target = (dragFraction * (slots.size - 1)).roundToInt()
                    scope.launch { gridState.scrollToItem(target) }
                }
            },
    ) {
        val yOffset = (fraction * (trackHeightPx - thumbHeightPx)).roundToInt()

        if (dragging) {
            val index = (fraction * (slots.size - 1)).roundToInt().coerceIn(slots.indices)
            val label = when (val slot = slots[index]) {
                is GridSlot.Header -> slot.label
                is GridSlot.Cell -> com.souru.lumina.util.formatDateHeader(slot.entry.item.localDate)
            }
            Surface(
                shape = RoundedCornerShape(16.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .offset { IntOffset(x = with(density) { (-48).dp.roundToPx() }, y = yOffset) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSurface,
                    modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                    maxLines = 1,
                    softWrap = false,
                )
            }
        }

        Box(
            modifier = Modifier
                .align(Alignment.TopEnd)
                .offset { IntOffset(0, yOffset) }
                .padding(end = 4.dp)
                .width(4.dp)
                .height(thumbHeight)
                .graphicsLayer { this.alpha = 0.9f }
                .background(
                    MaterialTheme.colorScheme.onSurfaceVariant,
                    RoundedCornerShape(2.dp),
                ),
        )
    }
}
