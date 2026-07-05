package com.souru.lumina.ui.trash

import android.app.Activity
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.outlined.Circle
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import coil3.compose.AsyncImage
import coil3.request.ImageRequest
import coil3.request.crossfade
import com.souru.lumina.data.coil.MediaThumb
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.data.model.MediaTypeFilter
import com.souru.lumina.util.Trash
import com.souru.lumina.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/**
 * ゴミ箱画面。MediaStoreのIS_TRASHEDアイテムを表示し、
 * 復元(createTrashRequest(false))と完全削除(createDeleteRequest)を行う。
 * アイテムは約30日で自動削除される(残り日数はDATE_EXPIRESから算出)。
 */
@Composable
fun TrashScreen(
    onClose: () -> Unit,
    viewModel: TrashViewModel = viewModel(factory = TrashViewModel.Factory),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val snackbarHostState = remember { SnackbarHostState() }

    var pendingMessage by remember { mutableStateOf<String?>(null) }
    var showDeleteConfirm by remember { mutableStateOf(false) }

    val requestLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.StartIntentSenderForResult(),
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            viewModel.clearSelection()
            pendingMessage?.let { message ->
                scope.launch { snackbarHostState.showSnackbar(message) }
            }
        }
        pendingMessage = null
    }

    fun selectedItems(): List<MediaItem> = state.items.filter { it.id in state.selection }

    fun launchRestore() {
        val items = selectedItems()
        if (items.isEmpty()) return
        scope.launch {
            val request = withContext(Dispatchers.Default) {
                Trash.restoreRequest(context, items.map { it.uri })
            }
            pendingMessage = "${items.size}件を復元しました"
            requestLauncher.launch(request)
        }
    }

    fun launchPermanentDelete() {
        val items = selectedItems()
        if (items.isEmpty()) return
        scope.launch {
            val request = withContext(Dispatchers.Default) {
                Trash.deleteRequest(context, items.map { it.uri })
            }
            pendingMessage = "${items.size}件を完全に削除しました"
            requestLauncher.launch(request)
        }
    }

    BackHandler(enabled = state.selection.isNotEmpty()) {
        viewModel.clearSelection()
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        Column(Modifier.fillMaxSize()) {
            // トップバー
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(52.dp)
                    .padding(horizontal = 4.dp),
            ) {
                IconButton(onClick = onClose) {
                    Icon(
                        Icons.AutoMirrored.Filled.ArrowBack,
                        contentDescription = "戻る",
                        tint = Color.White,
                    )
                }
                Text(
                    text = if (state.selection.isEmpty()) {
                        "ゴミ箱"
                    } else {
                        "${state.selection.size}件を選択中"
                    },
                    style = MaterialTheme.typography.titleSmall,
                    color = Color.White,
                )
                Spacer(Modifier.weight(1f))
                TrashFilterPills(
                    selected = state.typeFilter,
                    onSelect = viewModel::setTypeFilter,
                )
            }

            Text(
                text = "ゴミ箱のアイテムは約30日後に自動的に削除されます",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
            )

            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f),
            ) {
                if (state.loading) {
                    CircularProgressIndicator(
                        modifier = Modifier.align(Alignment.Center),
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else if (state.items.isEmpty()) {
                    Text(
                        text = "ゴミ箱は空です",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.align(Alignment.Center),
                    )
                } else {
                    LazyVerticalGrid(
                        columns = GridCells.Fixed(3),
                        horizontalArrangement = Arrangement.spacedBy(2.dp),
                        verticalArrangement = Arrangement.spacedBy(2.dp),
                        modifier = Modifier.fillMaxSize(),
                    ) {
                        items(state.items, key = { it.id }) { item ->
                            TrashCell(
                                item = item,
                                selected = item.id in state.selection,
                                selectionMode = state.selection.isNotEmpty(),
                                onToggle = { viewModel.toggleSelection(item.id) },
                            )
                        }
                    }
                }
            }

            // 選択時の操作バー
            if (state.selection.isNotEmpty()) {
                Row(
                    horizontalArrangement = Arrangement.SpaceEvenly,
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(MaterialTheme.colorScheme.surfaceContainer)
                        .padding(vertical = 4.dp),
                ) {
                    TextButton(onClick = { launchRestore() }) {
                        Text("復元", color = MaterialTheme.colorScheme.primary)
                    }
                    TextButton(onClick = { showDeleteConfirm = true }) {
                        Text("完全削除", color = MaterialTheme.colorScheme.error)
                    }
                }
            }
        }

        SnackbarHost(
            hostState = snackbarHostState,
            modifier = Modifier.align(Alignment.BottomCenter),
        )
    }

    if (showDeleteConfirm) {
        AlertDialog(
            onDismissRequest = { showDeleteConfirm = false },
            title = { Text("完全に削除しますか?") },
            text = { Text("${state.selection.size}件のアイテムを完全に削除します。この操作は取り消せません。") },
            confirmButton = {
                TextButton(onClick = {
                    showDeleteConfirm = false
                    launchPermanentDelete()
                }) {
                    Text("完全削除", color = MaterialTheme.colorScheme.error)
                }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteConfirm = false }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun TrashFilterPills(
    selected: MediaTypeFilter,
    onSelect: (MediaTypeFilter) -> Unit,
) {
    val options = listOf(
        MediaTypeFilter.ALL to "すべて",
        MediaTypeFilter.PHOTO to "写真",
        MediaTypeFilter.VIDEO to "動画",
    )
    Row(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(RoundedCornerShape(50))
            .background(Color.White.copy(alpha = 0.08f)),
    ) {
        options.forEach { (mode, label) ->
            val isSelected = selected == mode
            Box(
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(if (isSelected) Color.White.copy(alpha = 0.16f) else Color.Transparent)
                    .clickable { onSelect(mode) },
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun TrashCell(
    item: MediaItem,
    selected: Boolean,
    selectionMode: Boolean,
    onToggle: () -> Unit,
) {
    val context = LocalContext.current
    Box(
        modifier = Modifier
            .aspectRatio(1f)
            .combinedClickable(onClick = onToggle, onLongClick = onToggle),
    ) {
        AsyncImage(
            model = ImageRequest.Builder(context)
                .data(MediaThumb(item.uri, item.id))
                .crossfade(true)
                .build(),
            contentDescription = item.displayName,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .fillMaxSize()
                .background(MaterialTheme.colorScheme.surfaceContainer),
        )

        // 自動削除までの残り日数
        Text(
            text = "残り${Trash.remainingDays(item.dateExpiresSec)}日",
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
            modifier = Modifier
                .align(Alignment.BottomStart)
                .padding(5.dp)
                .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(4.dp))
                .padding(horizontal = 5.dp, vertical = 1.dp),
        )

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
                Spacer(Modifier.size(3.dp))
                Text(
                    text = formatDuration(item.durationMs),
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
