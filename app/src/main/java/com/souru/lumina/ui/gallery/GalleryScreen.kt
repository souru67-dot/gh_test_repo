package com.souru.lumina.ui.gallery

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibilityScope
import androidx.compose.animation.ExperimentalSharedTransitionApi
import androidx.compose.animation.SharedTransitionLayout
import androidx.compose.animation.SharedTransitionScope
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
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.grid.LazyGridState
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Circle
import androidx.compose.material.icons.outlined.DeleteOutline
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.rememberCoroutineScope
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
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.data.model.MediaTypeFilter
import com.souru.lumina.data.model.RawFilterMode
import androidx.lifecycle.compose.LifecycleResumeEffect
import com.souru.lumina.ui.viewer.ViewerScreen
import com.souru.lumina.util.Lightroom
import com.souru.lumina.util.MediaAccess
import com.souru.lumina.util.PairDeleteChoice
import com.souru.lumina.util.Trash
import com.souru.lumina.util.canRequestMediaPermissionAgain
import com.souru.lumina.util.formatDuration
import com.souru.lumina.util.mediaAccessState
import com.souru.lumina.util.mediaPermissions
import com.souru.lumina.util.openAppSettings
import com.souru.lumina.util.resolveDeletionItems
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

fun sharedMediaKey(id: Long): String = "media-$id"

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
fun GalleryRoute(
    onOpenPhotoEditor: (Long) -> Unit,
    onOpenVideoEditor: (Long) -> Unit,
    onOpenTrash: () -> Unit,
    viewModel: GalleryViewModel = viewModel(factory = GalleryViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val selection by viewModel.selectionFlow.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var viewerIndex by rememberSaveable { mutableStateOf<Int?>(null) }
    // ビューアで現在表示中のエントリID(共有要素の対応付けと戻りスクロールに使う)
    var focusedId by rememberSaveable { mutableStateOf<Long?>(null) }
    // ビューアを閉じた後、グリッド側で消化する戻り先スクロール要求
    var pendingScrollSlot by remember { mutableStateOf<Int?>(null) }
    var showLightroomDialog by remember { mutableStateOf(false) }
    // RAW+JPEGペアを含む削除の対象選択待ち
    var pendingDeleteEntries by remember { mutableStateOf<List<GalleryEntry>?>(null) }

    // 権限状態は一覧側で管理し、復帰(設定変更・追加選択)のたびに再評価する
    var access by remember { mutableStateOf(mediaAccessState(context)) }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) {
        access = mediaAccessState(context)
        viewModel.retryLoad()
    }
    LifecycleResumeEffect(Unit) {
        access = mediaAccessState(context)
        viewModel.retryLoad()
        onPauseOrDispose { }
    }

    fun requestPermission() {
        if (canRequestMediaPermissionAgain(context)) {
            permissionLauncher.launch(mediaPermissions)
        } else {
            // 完全拒否: システムダイアログを出せないためアプリ設定へ誘導
            openAppSettings(context)
        }
    }

    val trashLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.clearSelection()
            scope.launch { snackbarHostState.showSnackbar("ゴミ箱に移動しました") }
        }
    }

    fun requestTrash(items: List<MediaItem>) {
        if (items.isEmpty()) return
        scope.launch {
            // 大量選択でもUIスレッドを塞がないようURIリスト構築はバックグラウンド
            val request = withContext(Dispatchers.Default) {
                Trash.trashRequest(context, items.map { it.uri })
            }
            trashLauncher.launch(request)
        }
    }

    fun onDeleteRequest(entries: List<GalleryEntry>) {
        if (entries.isEmpty()) return
        if (entries.any { it.isPaired }) {
            // 誤ってRAWだけ残る/消える事故を防ぐため対象を選ばせる
            pendingDeleteEntries = entries
        } else {
            requestTrash(entries.map { it.item })
        }
    }

    // グリッドのスクロール位置はビューア表示をまたいで保持する
    val gridState = rememberLazyGridState()

    BackHandler(enabled = selection.isNotEmpty() && viewerIndex == null) {
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

    fun closeViewer() {
        // ここでは閉じるだけにする。未アタッチのLazyGridStateへ
        // scrollToItemすると永久にサスペンドし、戻る操作が失われるため、
        // スクロールはグリッドが構成された後にpendingScrollSlotで消化する
        pendingScrollSlot = focusedId?.let { state.slotIndexOfEntry[it] }
        viewerIndex = null
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        SharedTransitionLayout {
            AnimatedContent(
                targetState = viewerIndex,
                transitionSpec = {
                    if (targetState != null) {
                        (fadeIn(tween(220)) + scaleIn(initialScale = 0.96f, animationSpec = tween(220)))
                            .togetherWith(fadeOut(tween(160)))
                    } else {
                        fadeIn(tween(200))
                            .togetherWith(fadeOut(tween(200)) + scaleOut(targetScale = 0.98f, animationSpec = tween(200)))
                    }
                },
                label = "galleryViewer",
            ) { index ->
                if (index == null) {
                    GalleryGridScreen(
                        state = state,
                        selection = selection,
                        gridState = gridState,
                        focusedId = focusedId,
                        access = access,
                        pendingScrollSlot = pendingScrollSlot,
                        onPendingScrollHandled = { pendingScrollSlot = null },
                        onRequestPermission = ::requestPermission,
                        onManagePartial = { permissionLauncher.launch(mediaPermissions) },
                        sharedScope = this@SharedTransitionLayout,
                        animatedScope = this@AnimatedContent,
                        viewModel = viewModel,
                        onOpenViewer = { entry ->
                            focusedId = entry.entry.id
                            viewerIndex = entry.entryIndex
                        },
                        onSendSelectionToLightroom = {
                            sendToLightroom(state.entries.filter { it.id in selection })
                        },
                        onDeleteSelection = {
                            onDeleteRequest(state.entries.filter { it.id in selection })
                        },
                        onOpenTrash = onOpenTrash,
                    )
                } else {
                    ViewerScreen(
                        entries = state.entries,
                        initialIndex = index,
                        sharedScope = this@SharedTransitionLayout,
                        animatedScope = this@AnimatedContent,
                        onFocusedIdChange = { focusedId = it },
                        onClose = ::closeViewer,
                        onEditPhoto = { item -> onOpenPhotoEditor(item.id) },
                        onEditVideo = { item -> onOpenVideoEditor(item.id) },
                        onSendToLightroom = { entry -> sendToLightroom(listOf(entry)) },
                        onDelete = { entry -> onDeleteRequest(listOf(entry)) },
                    )
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )

        pendingDeleteEntries?.let { entries ->
            val pairCount = entries.count { it.isPaired }
            AlertDialog(
                onDismissRequest = { pendingDeleteEntries = null },
                title = { Text("RAW+JPEGペアの削除") },
                text = {
                    Text("選択にRAW+JPEGペアが${pairCount}件含まれています。ペアのどちらを削除するか選んでください(ペア以外の項目はそのまま削除されます)。")
                },
                confirmButton = {
                    Column(horizontalAlignment = Alignment.End) {
                        TextButton(onClick = {
                            pendingDeleteEntries = null
                            requestTrash(resolveDeletionItems(entries, PairDeleteChoice.JPEG_ONLY))
                        }) { Text("JPEGのみ削除") }
                        TextButton(onClick = {
                            pendingDeleteEntries = null
                            requestTrash(resolveDeletionItems(entries, PairDeleteChoice.RAW_ONLY))
                        }) { Text("RAWのみ削除") }
                        TextButton(onClick = {
                            pendingDeleteEntries = null
                            requestTrash(resolveDeletionItems(entries, PairDeleteChoice.BOTH))
                        }) { Text("両方削除", color = MaterialTheme.colorScheme.error) }
                    }
                },
                dismissButton = {
                    TextButton(onClick = { pendingDeleteEntries = null }) { Text("キャンセル") }
                },
            )
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

@OptIn(ExperimentalSharedTransitionApi::class)
@Composable
private fun GalleryGridScreen(
    state: GalleryUiState,
    selection: Set<Long>,
    gridState: LazyGridState,
    focusedId: Long?,
    access: MediaAccess,
    pendingScrollSlot: Int?,
    onPendingScrollHandled: () -> Unit,
    onRequestPermission: () -> Unit,
    onManagePartial: () -> Unit,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
    viewModel: GalleryViewModel,
    onOpenViewer: (GridSlot.Cell) -> Unit,
    onSendSelectionToLightroom: () -> Unit,
    onDeleteSelection: () -> Unit,
    onOpenTrash: () -> Unit,
) {
    val columnsProvider = rememberColumnsProvider(state.columns)
    val layoutDirection = LocalLayoutDirection.current
    val systemBarPadding = WindowInsets.systemBars.asPaddingValues()
    val selectionMode = selection.isNotEmpty()

    // ビューアから戻ったとき、表示していたセルが画面外なら追従スクロールする
    LaunchedEffect(pendingScrollSlot) {
        val slot = pendingScrollSlot ?: return@LaunchedEffect
        if (slot in state.slots.indices &&
            gridState.layoutInfo.visibleItemsInfo.none { it.index == slot }
        ) {
            runCatching { gridState.scrollToItem(slot) }
        }
        onPendingScrollHandled()
    }

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
                        selected = slot.entry.id in selection,
                        selectionMode = selectionMode,
                        isSharedElement = slot.entry.id == focusedId,
                        sharedScope = sharedScope,
                        animatedScope = animatedScope,
                        onClick = {
                            if (selectionMode) {
                                viewModel.toggleSelection(slot.entry.id)
                            } else {
                                onOpenViewer(slot)
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
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            ) {
                when {
                    access == MediaAccess.Denied -> {
                        Text(
                            text = "写真と動画へのアクセスを許可すると表示されます",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            textAlign = androidx.compose.ui.text.style.TextAlign.Center,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onRequestPermission) {
                            Text("許可する", color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    state.loadError -> {
                        Text(
                            text = "読み込みに失敗しました",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = viewModel::retryLoad) {
                            Text("再試行", color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    access == MediaAccess.Partial -> {
                        Text(
                            text = "選択されたメディアがありません",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Spacer(Modifier.height(16.dp))
                        TextButton(onClick = onManagePartial) {
                            Text("メディアを選択", color = MaterialTheme.colorScheme.primary)
                        }
                    }

                    else -> Text(
                        text = "写真・動画が見つかりません",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        // 一部許可のときは常設の案内バナー(さらに選択できる導線)
        if (access == MediaAccess.Partial && state.slots.isNotEmpty()) {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .padding(top = systemBarPadding.calculateTopPadding() + 52.dp)
                    .fillMaxWidth()
                    .background(MaterialTheme.colorScheme.surfaceContainer)
                    .padding(start = 16.dp, end = 4.dp),
            ) {
                Text(
                    text = "一部のメディアのみアクセス許可中",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Spacer(Modifier.weight(1f))
                TextButton(onClick = onManagePartial) {
                    Text("さらに選択", color = MaterialTheme.colorScheme.primary)
                }
            }
        }

        GalleryTopBar(
            state = state,
            selection = selection,
            onClearSelection = viewModel::clearSelection,
            onSelectFilter = viewModel::setFilter,
            onSelectTypeFilter = viewModel::setTypeFilter,
            onSendSelectionToLightroom = onSendSelectionToLightroom,
            onDeleteSelection = onDeleteSelection,
            onOpenTrash = onOpenTrash,
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
    selection: Set<Long>,
    onClearSelection: () -> Unit,
    onSelectFilter: (RawFilterMode) -> Unit,
    onSelectTypeFilter: (MediaTypeFilter) -> Unit,
    onSendSelectionToLightroom: () -> Unit,
    onDeleteSelection: () -> Unit,
    onOpenTrash: () -> Unit,
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
        if (selection.isNotEmpty()) {
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
                    text = "${selection.size}件を選択中",
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                // 選択項目をゴミ箱へ(ペアは対象選択ダイアログを挟む)
                IconButton(onClick = onDeleteSelection) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "削除",
                        tint = Color.White,
                    )
                }
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
                    .padding(horizontal = 12.dp),
            ) {
                // メディア種別(すべて/写真/動画)
                FilterPillRow(
                    options = listOf(
                        MediaTypeFilter.ALL to "すべて",
                        MediaTypeFilter.PHOTO to "写真",
                        MediaTypeFilter.VIDEO to "動画",
                    ),
                    selected = state.filter.type,
                    onSelect = onSelectTypeFilter,
                )
                Spacer(Modifier.weight(1f))
                // 写真の形式(すべて/JPEG/RAW)。動画のみ表示中は無効化
                FilterPillRow(
                    options = listOf(
                        RawFilterMode.ALL to "すべて",
                        RawFilterMode.JPEG to "JPEG",
                        RawFilterMode.RAW to "RAW",
                    ),
                    selected = state.filter.format,
                    enabled = state.filter.type != MediaTypeFilter.VIDEO,
                    onSelect = onSelectFilter,
                )
                IconButton(onClick = onOpenTrash) {
                    Icon(
                        Icons.Outlined.DeleteOutline,
                        contentDescription = "ゴミ箱",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

/** ピル型の排他フィルタ切替。enabled=false時はグレーアウトして操作を無視する。 */
@Composable
private fun <T> FilterPillRow(
    options: List<Pair<T, String>>,
    selected: T,
    onSelect: (T) -> Unit,
    enabled: Boolean = true,
) {
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = if (enabled) 0.08f else 0.04f)),
    ) {
        options.forEach { (mode, label) ->
            val isSelected = selected == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(
                        if (isSelected && enabled) Color.White.copy(alpha = 0.16f) else Color.Transparent,
                    )
                    .clickable(enabled = enabled) { onSelect(mode) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        !enabled -> Color.White.copy(alpha = 0.25f)
                        isSelected -> Color.White
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalSharedTransitionApi::class)
@Composable
private fun LazyGridItemScope.MediaCell(
    entry: GalleryEntry,
    selected: Boolean,
    selectionMode: Boolean,
    isSharedElement: Boolean,
    sharedScope: SharedTransitionScope,
    animatedScope: AnimatedVisibilityScope,
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
        var imageModifier: Modifier = Modifier.fillMaxSize()
        if (isSharedElement) {
            with(sharedScope) {
                imageModifier = imageModifier.sharedElement(
                    rememberSharedContentState(sharedMediaKey(entry.id)),
                    animatedVisibilityScope = animatedScope,
                )
            }
        }
        if (selected) {
            imageModifier = imageModifier
                .padding(8.dp)
                .clip(RoundedCornerShape(12.dp))
        }
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(
                    MediaThumb(
                        uri = entry.item.uri,
                        id = entry.item.id,
                        // DNGはloadThumbnailが向きを適用しないことがあるため補正する
                        rotationDeg = if (entry.item.isRaw) entry.item.orientationDeg else 0,
                    ),
                )
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
