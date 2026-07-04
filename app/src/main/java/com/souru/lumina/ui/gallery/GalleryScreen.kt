package com.souru.lumina.ui.gallery

import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBars
import androidx.compose.foundation.layout.systemBars
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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.souru.lumina.data.SettingsRepository
import com.souru.lumina.data.coil.MediaThumb
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.GridSlot
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.data.model.RawFilterMode
import com.souru.lumina.ui.viewer.ViewerScreen
import com.souru.lumina.util.Lightroom
import com.souru.lumina.util.formatDuration

@Composable
fun GalleryRoute(
    onOpenPhotoEditor: (Long) -> Unit,
    onOpenVideoEditor: (Long) -> Unit,
    viewModel: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    var showLightroomDialog by remember { mutableStateOf(false) }

    BackHandler(enabled = state.selectionMode && viewerIndex == null) {
        viewModel.clearSelection()
    }

    fun sendToLightroom(entries: List<GalleryEntry>) {
        val items = entries
            .map { Lightroom.rawSideOf(it) }
            .filter { it.kind == MediaKind.IMAGE }
        if (items.isEmpty()) return
        if (!Lightroom.openMultiple(context, items)) {
            showLightroomDialog = true
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        AnimatedContent(
            targetState = viewerIndex,
            transitionSpec = {
                if (targetState != null) {
                    (fadeIn(tween(220)) + scaleIn(initialScale = 0.94f, animationSpec = tween(220)))
                        .togetherWith(fadeOut(tween(160)))
                } else {
                    fadeIn(tween(200))
                        .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.96f, animationSpec = tween(200)))
                }
            },
            label = "galleryViewer",
        ) { index ->
            if (index == null) {
                GalleryGridScreen(
                    state = state,
                    viewModel = viewModel,
                    onOpenViewer = { entryIndex -> viewerIndex = entryIndex },
                    onSendSelectionToLightroom = {
                        sendToLightroom(state.entries.filter { it.id in state.selection })
                    },
                )
            } else {
                ViewerScreen(
                    entries = state.entries,
                    initialIndex = index,
                    onClose = { viewerIndex = null },
                    onEditPhoto = { item -> onOpenPhotoEditor(item.id) },
                    onEditVideo = { item -> onOpenVideoEditor(item.id) },
                    onSendToLightroom = { entry -> sendToLightroom(listOf(entry)) },
                )
            }
        }

        if (showLightroomDialog) {
            AlertDialog(
                onDismissRequest = { showLightroomDialog = false },
                title = { Text("Lightroom Mobile が見つかりません") },
                text = {
                    Text("RAW現像には Adobe Lightroom Mobile(無料)が必要です。Playストアからインストールしてください。")
                },
                confirmButton = {
                    TextButton(onClick = {
                        showLightroomDialog = false
                        Lightroom.openPlayStore(context)
                    }) {
                        Text("Playストアを開く")
                    }
                },
                dismissButton = {
                    TextButton(onClick = { showLightroomDialog = false }) {
                        Text("キャンセル")
                    }
                },
            )
        }
    }
}

@Composable
private fun GalleryGridScreen(
    state: GalleryUiState,
    viewModel: GalleryViewModel,
    onOpenViewer: (Int) -> Unit,
    onSendSelectionToLightroom: () -> Unit,
) {
    val gridState = rememberLazyGridState()
    val columnsProvider = rememberColumnsProvider(state.columns)
    val layoutDirection = LocalLayoutDirection.current
    val systemBarPadding = WindowInsets.systemBars.asPaddingValues()

    Box(Modifier.fillMaxSize()) {
        LazyVerticalGrid(
            columns = GridCells.Fixed(state.columns),
            state = gridState,
            horizontalArrangement = Arrangement.spacedBy(2.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp),
            contentPadding = PaddingValues(
                start = systemBarPadding.calculateStartPadding(layoutDirection),
                end = systemBarPadding.calculateEndPadding(layoutDirection),
                top = systemBarPadding.calculateTopPadding() + 52.dp,
                bottom = systemBarPadding.calculateBottomPadding() + 16.dp,
            ),
            modifier = Modifier
                .fillMaxSize()
                .pinchToChangeColumns(
                    columns = columnsProvider,
                    minColumns = SettingsRepository.MIN_COLUMNS,
                    maxColumns = SettingsRepository.MAX_COLUMNS,
                    onColumnsChange = viewModel::setColumns,
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
                    is GridSlot.Header -> DateHeader(slot.label)
                    is GridSlot.Cell -> MediaCell(
                        entry = slot.entry,
                        selected = slot.entry.id in state.selection,
                        selectionMode = state.selectionMode,
                        onClick = {
                            if (state.selectionMode) {
                                viewModel.toggleSelection(slot.entry.id)
                            } else {
                                onOpenViewer(slot.entryIndex)
                            }
                        },
                        onLongClick = { viewModel.toggleSelection(slot.entry.id) },
                    )
                }
            }
        }

        if (state.loading) {
            CircularProgressIndicator(
                modifier = Modifier.align(Alignment.Center),
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        } else if (state.slots.isEmpty()) {
            Text(
                text = "写真・動画が見つかりません",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.align(Alignment.Center),
            )
        }

        GalleryTopBar(
            state = state,
            onClearSelection = viewModel::clearSelection,
            onSelectFilter = viewModel::setFilter,
            onSendSelectionToLightroom = onSendSelectionToLightroom,
            modifier = Modifier.align(Alignment.TopCenter),
        )

        FastScrubber(
            gridState = gridState,
            slots = state.slots,
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .padding(
                    top = systemBarPadding.calculateTopPadding() + 56.dp,
                    bottom = systemBarPadding.calculateBottomPadding() + 16.dp,
                ),
        )
    }
}

@Composable
private fun GalleryTopBar(
    state: GalleryUiState,
    onClearSelection: () -> Unit,
    onSelectFilter: (RawFilterMode) -> Unit,
    onSendSelectionToLightroom: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val statusBarPadding = WindowInsets.statusBars.asPaddingValues()
    Box(
        modifier = modifier
            .fillMaxWidth()
            .background(
                Brush.verticalGradient(
                    0f to Color.Black.copy(alpha = 0.72f),
                    1f to Color.Transparent,
                ),
            )
            .padding(statusBarPadding),
    ) {
        if (state.selectionMode) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(onClick = onClearSelection) {
                    Icon(
                        Icons.Default.Close,
                        contentDescription = "選択を解除",
                        tint = Color.White,
                    )
                }
                Text(
                    text = "${state.selection.size}件を選択中",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                // 選択した写真(ペアはRAW側)をまとめてLightroomへ
                TextButton(onClick = onSendSelectionToLightroom) {
                    Text(
                        text = "Lrで現像",
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }
        } else {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 16.dp),
            ) {
                Text(
                    text = "Lumina",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Medium,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                RawFilterSwitcher(filter = state.filter, onSelect = onSelectFilter)
            }
        }
    }
}

/** JPEG / RAW / すべて の表示モード切替(グリッド上部に常設)。 */
@Composable
private fun RawFilterSwitcher(
    filter: RawFilterMode,
    onSelect: (RawFilterMode) -> Unit,
) {
    val options = listOf(
        RawFilterMode.JPEG to "JPEG",
        RawFilterMode.RAW to "RAW",
        RawFilterMode.ALL to "すべて",
    )
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        options.forEach { (mode, label) ->
            val selected = filter == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (selected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(mode) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun DateHeader(label: String) {
    Text(
        text = label,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.onBackground,
        modifier = Modifier.padding(start = 14.dp, top = 20.dp, bottom = 8.dp, end = 14.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun LazyGridItemScope.MediaCell(
    entry: GalleryEntry,
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .animateItem()
            .aspectRatio(1f)
            .combinedClickable(onClick = onClick, onLongClick = onLongClick),
    ) {
        val imageModifier = if (selected) {
            Modifier
                .fillMaxSize()
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
        } else {
            Modifier.fillMaxSize()
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(MediaThumb(entry.item.uri, entry.item.id))
                .crossfade(true)
                .build(),
            contentDescription = entry.item.displayName,
            contentScale = ContentScale.Crop,
            modifier = imageModifier.background(MaterialTheme.colorScheme.surfaceContainer),
        )

        val badge = when {
            entry.isPaired -> "RAW+J"
            entry.item.isRaw -> "RAW"
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

        if (entry.item.kind == MediaKind.VIDEO) {
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
                Spacer(Modifier.size(3.dp))
                Text(
                    text = formatDuration(entry.item.durationMs),
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White,
                )
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
