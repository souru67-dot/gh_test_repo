package com.souru.lumina.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.SwapHoriz
import androidx.compose.material.icons.outlined.AutoFixHigh
import androidx.compose.material.icons.outlined.Share
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.util.formatViewerTitle
import com.souru.lumina.util.shareMediaItem

/**
 * 没入型ビューア。横スワイプで前後のメディアへ、タップでUI表示切替、
 * 下スワイプ/戻る操作で閉じる。
 */
@Composable
fun ViewerScreen(
    entries: List<GalleryEntry>,
    initialIndex: Int,
    onClose: () -> Unit,
    onEditPhoto: (MediaItem) -> Unit = {},
    onEditVideo: (MediaItem) -> Unit = {},
    onSendToLightroom: (GalleryEntry) -> Unit = {},
) {
    if (entries.isEmpty()) {
        onClose()
        return
    }
    val pagerState = rememberPagerState(
        initialPage = initialIndex.coerceIn(0, entries.size - 1),
    ) { entries.size }

    var chromeVisible by remember { mutableStateOf(true) }
    var dismissProgress by remember { mutableFloatStateOf(0f) }

    BackHandler(onBack = onClose)

    val backgroundAlpha = (1f - dismissProgress * 1.2f).coerceIn(0f, 1f)

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black.copy(alpha = backgroundAlpha)),
    ) {
        // RAW⇔JPEG切替: エントリIDごとに「ペア相手側を表示中」を保持する
        var swappedIds by remember { mutableStateOf(emptySet<Long>()) }

        fun displayItemOf(entry: GalleryEntry) =
            entry.counterpart?.takeIf { entry.id in swappedIds } ?: entry.item

        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val entry = entries[page]
            val displayItem = displayItemOf(entry)
            when (displayItem.kind) {
                MediaKind.IMAGE -> PhotoPage(
                    item = displayItem,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                    onDismiss = onClose,
                    onDismissProgress = { progress ->
                        if (page == pagerState.currentPage) dismissProgress = progress
                    },
                )

                MediaKind.VIDEO -> VideoPage(
                    entry = entry,
                    isActive = pagerState.settledPage == page,
                    chromeVisible = chromeVisible,
                    onToggleChrome = { chromeVisible = !chromeVisible },
                )
            }
        }

        AnimatedVisibility(
            visible = chromeVisible && dismissProgress == 0f,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.TopCenter),
        ) {
            val current = entries[pagerState.currentPage.coerceIn(entries.indices)]
            ViewerTopBar(
                displayItem = displayItemOf(current),
                isPaired = current.isPaired,
                onClose = onClose,
                onSwapRawJpeg = {
                    swappedIds = swappedIds.let {
                        if (current.id in it) it - current.id else it + current.id
                    }
                },
                onEditVideo = if (displayItemOf(current).kind == MediaKind.VIDEO) {
                    { onEditVideo(displayItemOf(current)) }
                } else {
                    null
                },
            )
        }

        val currentEntry = entries[pagerState.currentPage.coerceIn(entries.indices)]
        AnimatedVisibility(
            visible = chromeVisible && dismissProgress == 0f &&
                displayItemOf(currentEntry).kind == MediaKind.IMAGE,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomCenter),
        ) {
            ViewerBottomBar(
                entry = currentEntry,
                displayItem = displayItemOf(currentEntry),
                onEditPhoto = onEditPhoto,
                onSendToLightroom = { onSendToLightroom(currentEntry) },
            )
        }
    }
}

/**
 * 写真表示中のアクションバー。RAWは簡易編集対象外のため
 * 「Lrで現像」のみを出し、JPEGには「編集」も出す。
 */
@Composable
private fun ViewerBottomBar(
    entry: GalleryEntry,
    displayItem: MediaItem,
    onEditPhoto: (MediaItem) -> Unit,
    onSendToLightroom: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Transparent,
                    1f to Color.Black.copy(alpha = 0.65f),
                ),
            )
            .padding(WindowInsets.navigationBars.asPaddingValues()),
    ) {
        Row(
            horizontalArrangement = Arrangement.SpaceEvenly,
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 6.dp),
        ) {
            ViewerAction(
                icon = Icons.Outlined.Share,
                label = "共有",
                onClick = { shareMediaItem(context, displayItem) },
            )
            ViewerAction(
                icon = Icons.Outlined.AutoFixHigh,
                label = "Lrで現像",
                onClick = onSendToLightroom,
            )
            if (!displayItem.isRaw) {
                ViewerAction(
                    icon = Icons.Outlined.Tune,
                    label = "編集",
                    onClick = { onEditPhoto(displayItem) },
                )
            }
        }
    }
}

@Composable
private fun ViewerAction(
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    label: String,
    onClick: () -> Unit,
) {
    Column(
        horizontalAlignment = Alignment.CenterHorizontally,
        modifier = Modifier
            .clip(RoundedCornerShape(12.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    ) {
        Icon(icon, contentDescription = null, tint = Color.White, modifier = Modifier.size(22.dp))
        Spacer(Modifier.height(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
    }
}

@Composable
private fun ViewerTopBar(
    displayItem: MediaItem,
    isPaired: Boolean,
    onClose: () -> Unit,
    onSwapRawJpeg: () -> Unit,
    onEditVideo: (() -> Unit)? = null,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.6f),
                    1f to Color.Transparent,
                ),
            )
            .padding(WindowInsets.statusBars.asPaddingValues()),
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
        ) {
            IconButton(onClick = onClose) {
                Icon(
                    Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = "閉じる",
                    tint = Color.White,
                )
            }
            Text(
                text = formatViewerTitle(displayItem.dateTakenMs),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            if (displayItem.isRaw || isPaired) {
                Text(
                    text = if (displayItem.isRaw) "RAW" else "JPEG",
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.7f),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .background(Color.White.copy(alpha = 0.12f), RoundedCornerShape(4.dp))
                        .padding(horizontal = 5.dp, vertical = 1.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (onEditVideo != null) {
                // LUT適用・トリム・書き出しへ
                TextButton(onClick = onEditVideo) {
                    Icon(
                        Icons.Outlined.Tune,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "編集",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
            if (isPaired) {
                // 同一構図のままRAW⇔JPEGを切り替える
                TextButton(onClick = onSwapRawJpeg) {
                    Icon(
                        Icons.Default.SwapHoriz,
                        contentDescription = null,
                        tint = Color.White,
                        modifier = Modifier.size(18.dp),
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = if (displayItem.isRaw) "JPEGを表示" else "RAWを表示",
                        style = MaterialTheme.typography.labelMedium,
                        color = Color.White,
                    )
                }
            }
        }
    }
}
