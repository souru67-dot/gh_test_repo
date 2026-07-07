package com.souru.lumina.ui.external

import android.app.Activity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyGridItemScope
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.Download
import androidx.compose.material.icons.outlined.Usb
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.souru.lumina.data.SettingsRepository
import com.souru.lumina.data.coil.DngPreview
import com.souru.lumina.data.coil.ExternalThumb
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.GridSlot
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.ui.gallery.pinchToChangeColumns
import com.souru.lumina.ui.gallery.rememberColumnsProvider
import com.souru.lumina.ui.viewer.ViewerScreen
import com.souru.lumina.util.formatDuration

/**
 * 外部デバイス(USB/SDカード)ブラウザ。SAFで選択したツリーの写真・動画を
 * 端末ライブラリと同じグリッド(サムネイル・RAW+JPEGペア統合・ビューア・
 * EXIF)で表示する。お気に入り・ゴミ箱はMediaStore前提のため非対応。
 */
@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun ExternalBrowseScreen(
    onClose: () -> Unit,
    viewModel: ExternalBrowseViewModel = viewModel(factory = ExternalBrowseViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }

    val treeLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            result.data?.data?.let(viewModel::onTreeSelected)
        }
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            snackbarHostState.showSnackbar(it)
            viewModel.consumeMessage()
        }
    }

    fun pickFolder() = treeLauncher.launch(viewModel.openTreeIntent())

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding(),
        ) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
            ) {
                if (state.selection.isEmpty()) {
                    IconButton(onClick = onClose) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "戻る", tint = Color.White)
                    }
                    Text("外部デバイス", style = MaterialTheme.typography.titleSmall, color = Color.White)
                    Spacer(Modifier.weight(1f))
                    if (state.status == ExternalBrowseStatus.Ready && state.entries.isNotEmpty()) {
                        TextButton(onClick = viewModel::selectAll) {
                            Text("全選択", color = MaterialTheme.colorScheme.primary)
                        }
                    }
                } else {
                    IconButton(onClick = viewModel::clearSelection) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "選択解除", tint = Color.White)
                    }
                    Text(
                        "${state.selection.size}件を選択中",
                        style = MaterialTheme.typography.titleSmall,
                        color = Color.White,
                    )
                    Spacer(Modifier.weight(1f))
                }
            }

            when (state.status) {
                ExternalBrowseStatus.Loading -> CenterBox {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                }

                ExternalBrowseStatus.NotSelected -> EmptyState(
                    showIcon = true,
                    title = "USB/SDカードを読み込む",
                    body = "USB-C接続のSDカードリーダーやUSBストレージのフォルダ(DCIM等)を選択すると、写真・動画を表示します。",
                    actionLabel = "フォルダを選択",
                    onAction = ::pickFolder,
                )

                ExternalBrowseStatus.Disconnected -> EmptyState(
                    showIcon = true,
                    title = "デバイスが切断されました",
                    body = "外部デバイスが取り外されたか、アクセス権限が失効しました。再接続してフォルダを選び直してください。",
                    actionLabel = "フォルダを選択",
                    onAction = ::pickFolder,
                )

                ExternalBrowseStatus.Ready -> {
                    if (state.entries.isEmpty()) {
                        EmptyState(
                            showIcon = false,
                            title = "写真・動画が見つかりません",
                            body = "選択したフォルダに対応する写真・動画がありませんでした。別のフォルダを選択できます。",
                            actionLabel = "フォルダを選択",
                            onAction = ::pickFolder,
                        )
                    } else {
                        ExternalGrid(
                            state = state,
                            onColumnsChange = viewModel::setColumns,
                            onOpen = { index -> viewerIndex = index },
                            onToggleSelection = viewModel::toggleSelection,
                        )
                    }
                }
            }
        }

        if (state.status == ExternalBrowseStatus.Ready &&
            state.entries.isNotEmpty() &&
            viewerIndex == null
        ) {
            ImportBar(
                selectionCount = state.selection.size,
                onImport = viewModel::importSelectedOrAll,
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        // ビューア(EXIF・共有のみ。お気に入り/ゴミ箱/編集は非対応)
        viewerIndex?.let { index ->
            if (state.entries.isNotEmpty()) {
                ViewerScreen(
                    entries = state.entries,
                    initialIndex = index,
                    onClose = { viewerIndex = null },
                )
            } else {
                viewerIndex = null
            }
        }
    }
}

@Composable
private fun ExternalGrid(
    state: ExternalBrowseUiState,
    onColumnsChange: (Int) -> Unit,
    onOpen: (Int) -> Unit,
    onToggleSelection: (Long) -> Unit,
) {
    val gridState = rememberLazyGridState()
    val columnsProvider = rememberColumnsProvider(state.columns)
    val selectionMode = state.selection.isNotEmpty()

    LazyVerticalGrid(
        columns = GridCells.Fixed(state.columns),
        state = gridState,
        horizontalArrangement = Arrangement.spacedBy(2.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
        contentPadding = PaddingValues(bottom = 96.dp),
        modifier = Modifier
            .fillMaxSize()
            .pinchToChangeColumns(
                columns = columnsProvider,
                minColumns = SettingsRepository.MIN_COLUMNS,
                maxColumns = SettingsRepository.MAX_COLUMNS,
                onColumnsChange = onColumnsChange,
            ),
    ) {
        items(
            count = state.slots.size,
            key = { i -> state.slots[i].key },
            span = { i ->
                if (state.slots[i] is GridSlot.Header) GridItemSpan(maxLineSpan) else GridItemSpan(1)
            },
            contentType = { i -> if (state.slots[i] is GridSlot.Header) "header" else "cell" },
        ) { i ->
            when (val slot = state.slots[i]) {
                is GridSlot.Header -> Text(
                    text = slot.label,
                    style = MaterialTheme.typography.titleSmall,
                    color = MaterialTheme.colorScheme.onBackground,
                    modifier = Modifier.padding(start = 14.dp, top = 20.dp, bottom = 8.dp, end = 14.dp),
                )

                is GridSlot.Cell -> ExternalCell(
                    entry = slot.entry,
                    selected = slot.entry.id in state.selection,
                    selectionMode = selectionMode,
                    onClick = {
                        if (selectionMode) onToggleSelection(slot.entry.id) else onOpen(slot.entryIndex)
                    },
                    onLongClick = { onToggleSelection(slot.entry.id) },
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyGridItemScope.ExternalCell(
    entry: GalleryEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    val item = entry.item
    Box(
        modifier = Modifier
            .animateItem()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        var imageModifier: Modifier = Modifier.fillMaxSize()
        if (selected) {
            imageModifier = imageModifier
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
        }
        val model = if (item.isRaw) {
            DngPreview(item.uri, item.id, item.orientationDeg)
        } else {
            ExternalThumb(item.uri, item.uri.toString(), item.kind)
        }
        AsyncImage(
            model = ImageRequest.Builder(context).data(model).crossfade(true).build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceContainer),
        )

        val badge = when {
            entry.isPaired -> "RAW+J"
            item.isRaw -> "RAW"
            else -> null
        }
        if (badge != null) {
            Text(
                text = badge,
                style = MaterialTheme.typography.labelSmall,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.BottomStart)
                    .padding(5.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                    .padding(horizontal = 5.dp, vertical = 1.dp),
            )
        }

        if (item.kind == MediaKind.VIDEO) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .padding(6.dp),
            ) {
                Icon(
                    Icons.Default.PlayCircle,
                    contentDescription = "動画",
                    tint = Color.White,
                    modifier = Modifier.size(14.dp),
                )
                if (item.durationMs > 0) {
                    Spacer(Modifier.size(3.dp))
                    Text(
                        text = formatDuration(item.durationMs),
                        style = MaterialTheme.typography.labelSmall,
                        color = Color.White,
                    )
                }
            }
        }

        if (selectionMode) {
            Icon(
                imageVector = if (selected) Icons.Default.CheckCircle else Icons.Outlined.Circle,
                contentDescription = if (selected) "選択中" else "未選択",
                tint = if (selected) MaterialTheme.colorScheme.primary else Color.White.copy(alpha = 0.8f),
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(8.dp)
                    .size(20.dp)
                    .background(Color.Black.copy(alpha = 0.3f), CircleShape),
            )
        }
    }
}

@Composable
private fun CenterBox(content: @Composable BoxScope.() -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center, content = content)
}

@Composable
private fun EmptyState(
    showIcon: Boolean,
    title: String,
    body: String,
    actionLabel: String,
    onAction: () -> Unit,
) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(horizontal = 40.dp),
        ) {
            if (showIcon) {
                Icon(
                    Icons.Outlined.Usb,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.size(48.dp),
                )
                Spacer(Modifier.height(16.dp))
            }
            Text(
                text = title,
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(8.dp))
            Text(
                text = body,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
            Spacer(Modifier.height(20.dp))
            TextButton(onClick = onAction) {
                Text(actionLabel, color = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun ImportBar(
    selectionCount: Int,
    onImport: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center,
        modifier = modifier
            .fillMaxWidth()
            .background(MaterialTheme.colorScheme.surfaceContainer)
            .navigationBarsPadding()
            .padding(vertical = 10.dp),
    ) {
        Icon(Icons.Outlined.Download, contentDescription = null, tint = Color.White, modifier = Modifier.size(20.dp))
        Spacer(Modifier.size(8.dp))
        TextButton(onClick = onImport) {
            Text(
                text = if (selectionCount > 0) "選択した${selectionCount}件を取り込む" else "すべて取り込む",
                color = Color.White,
            )
        }
    }
}
