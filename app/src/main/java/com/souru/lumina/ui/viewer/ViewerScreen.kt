package com.souru.lumina.ui.viewer

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.weight
import androidx.compose.foundation.pager.HorizontalPager
import androidx.compose.foundation.pager.rememberPagerState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.util.formatViewerTitle

/**
 * 没入型ビューア。横スワイプで前後のメディアへ、タップでUI表示切替、
 * 下スワイプ/戻る操作で閉じる。
 */
@Composable
fun ViewerScreen(
    entries: List<GalleryEntry>,
    initialIndex: Int,
    onClose: () -> Unit,
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
        HorizontalPager(
            state = pagerState,
            beyondViewportPageCount = 1,
            pageSpacing = 16.dp,
            modifier = Modifier.fillMaxSize(),
        ) { page ->
            val entry = entries[page]
            when (entry.item.kind) {
                MediaKind.IMAGE -> PhotoPage(
                    entry = entry,
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
            ViewerTopBar(entry = current, onClose = onClose)
        }
    }
}

@Composable
private fun ViewerTopBar(
    entry: GalleryEntry,
    onClose: () -> Unit,
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
                text = formatViewerTitle(entry.item.dateTakenMs),
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
        }
    }
}
