package com.souru.lumina.ui.videoedit

import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.common.MediaItem as Media3Item
import androidx.media3.common.Player
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.souru.lumina.data.edit.Adjustments
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.util.formatDuration
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive

private enum class VideoAdjustParam(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (Adjustments) -> Float,
    val set: (Adjustments, Float) -> Adjustments,
) {
    EXPOSURE("露出", -2f..2f, { it.exposure }, { a, v -> a.copy(exposure = v) }),
    CONTRAST("コントラスト", -1f..1f, { it.contrast }, { a, v -> a.copy(contrast = v) }),
    HIGHLIGHTS("ハイライト", -1f..1f, { it.highlights }, { a, v -> a.copy(highlights = v) }),
    SHADOWS("シャドウ", -1f..1f, { it.shadows }, { a, v -> a.copy(shadows = v) }),
    SATURATION("彩度", -1f..1f, { it.saturation }, { a, v -> a.copy(saturation = v) }),
    TEMPERATURE("色温度", -1f..1f, { it.temperature }, { a, v -> a.copy(temperature = v) }),
}

/**
 * 動画編集画面。S-Cinetone for Mobile などLog系素材への3D LUT適用が主目的。
 * LUTはリアルタイムにプレビューへ適用され、強度スライダーとA/B比較を備える。
 */
@UnstableApi
@Composable
fun VideoEditScreen(
    mediaId: Long,
    onClose: () -> Unit,
    viewModel: VideoEditViewModel = viewModel(
        key = "videoEdit-$mediaId",
        factory = VideoEditViewModel.factory(mediaId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val effects by viewModel.videoEffects.collectAsStateWithLifecycle()
    val context = LocalContext.current

    BackHandler(onBack = onClose)

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val item = state.item
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
        if (state.loading || item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) {
                    CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
                } else {
                    Text("動画を読み込めませんでした", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
        } else {
            VideoEditContent(
                item = item,
                state = state,
                effects = effects,
                viewModel = viewModel,
                onClose = onClose,
            )
        }
    }
}

@UnstableApi
@Composable
private fun VideoEditContent(
    item: MediaItem,
    state: VideoEditUiState,
    effects: List<androidx.media3.common.Effect>,
    viewModel: VideoEditViewModel,
    onClose: () -> Unit,
) {
    val context = LocalContext.current
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var selectedParam by rememberSaveable { mutableStateOf(VideoAdjustParam.EXPOSURE.name) }

    val player = remember(item.id) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(Media3Item.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            playWhenReady = true
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    LaunchedEffect(effects) {
        player.setVideoEffects(effects)
    }

    val lutImportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> uri?.let(viewModel::importLut) }

    val notificationLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { showExportDialog = true }

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
                Icon(Icons.Default.Close, contentDescription = "閉じる", tint = Color.White)
            }
            Text("動画編集", style = MaterialTheme.typography.titleSmall, color = Color.White)
            Spacer(Modifier.weight(1f))
            TextButton(onClick = {
                notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
            }) {
                Text("書き出し", color = MaterialTheme.colorScheme.primary)
            }
        }

        // プレビュー
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        useController = false
                        resizeMode = AspectRatioFrameLayout.RESIZE_MODE_FIT
                        setBackgroundColor(android.graphics.Color.TRANSPARENT)
                    }
                },
                update = { it.player = player },
                modifier = Modifier.fillMaxSize(),
            )
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(onTap = {
                            if (player.isPlaying) player.pause() else player.play()
                        })
                    },
            )
            // A/B比較(押している間だけ元の映像)
            Text(
                text = if (state.comparing) "元の映像" else "A/B",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(12.dp)
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = if (state.comparing) 0.3f else 0.12f))
                    .pointerInput(Unit) {
                        detectTapGestures(onPress = {
                            viewModel.setComparing(true)
                            tryAwaitRelease()
                            viewModel.setComparing(false)
                        })
                    }
                    .padding(horizontal = 14.dp, vertical = 8.dp),
            )
        }

        // 下部パネル
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState()),
        ) {
            TransportBar(player = player, durationMs = item.durationMs)
            TrimBar(
                durationMs = item.durationMs,
                trimStartMs = state.trimStartMs,
                trimEndMs = state.trimEndMs,
                onTrimChange = viewModel::setTrim,
                onSeek = { player.seekTo(it) },
            )
            LutRow(
                state = state,
                onSelect = viewModel::selectLut,
                onDelete = viewModel::deleteLut,
                onImport = { lutImportLauncher.launch(arrayOf("*/*")) },
            )
            if (state.selectedLut != null) {
                StrengthSlider(
                    strength = state.strength,
                    onStrengthChange = viewModel::setStrength,
                )
            }
            VideoAdjustPanel(
                adjustments = state.adjustments,
                selectedParamName = selectedParam,
                onSelectParam = { selectedParam = it },
                onAdjustmentsChange = viewModel::setAdjustments,
                onReset = viewModel::resetAdjustments,
            )
        }
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { height, bitrate ->
                showExportDialog = false
                viewModel.export(height, bitrate)
            },
        )
    }
}

@Composable
private fun TransportBar(player: Player, durationMs: Long) {
    var playing by remember { mutableStateOf(true) }
    var positionMs by remember { mutableLongStateOf(0L) }
    var dragFraction by remember { mutableFloatStateOf(-1f) }

    LaunchedEffect(player) {
        while (isActive) {
            playing = player.isPlaying
            positionMs = player.currentPosition.coerceAtLeast(0)
            delay(200)
        }
    }

    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp),
    ) {
        IconButton(onClick = { if (player.isPlaying) player.pause() else player.play() }) {
            Icon(
                imageVector = if (playing) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = if (playing) "一時停止" else "再生",
                tint = Color.White,
            )
        }
        Text(
            text = formatDuration(positionMs),
            style = MaterialTheme.typography.labelSmall,
            color = Color.White,
        )
        Slider(
            value = if (dragFraction >= 0f) {
                dragFraction
            } else if (durationMs > 0) {
                (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
            } else {
                0f
            },
            onValueChange = { dragFraction = it },
            onValueChangeFinished = {
                if (durationMs > 0 && dragFraction >= 0f) {
                    player.seekTo((dragFraction * durationMs).toLong())
                }
                dragFraction = -1f
            },
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 8.dp),
        )
        Text(
            text = formatDuration(durationMs),
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
            modifier = Modifier.padding(end = 8.dp),
        )
    }
}

@Composable
private fun TrimBar(
    durationMs: Long,
    trimStartMs: Long,
    trimEndMs: Long,
    onTrimChange: (Long, Long) -> Unit,
    onSeek: (Long) -> Unit,
) {
    if (durationMs <= 0) return
    Column(Modifier.padding(horizontal = 16.dp)) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text(
                text = "トリム",
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "${formatDuration(trimStartMs)} 〜 ${formatDuration(trimEndMs)}",
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
        RangeSlider(
            value = trimStartMs.toFloat()..trimEndMs.toFloat().coerceAtLeast(trimStartMs.toFloat()),
            onValueChange = { range ->
                val newStart = range.start.toLong()
                val changedStart = newStart != trimStartMs
                onTrimChange(newStart, range.endInclusive.toLong())
                // IN/OUT点の確認用に、動かした側へシーク
                onSeek(if (changedStart) newStart else range.endInclusive.toLong())
            },
            valueRange = 0f..durationMs.toFloat(),
            colors = SliderDefaults.colors(
                thumbColor = MaterialTheme.colorScheme.primary,
                activeTrackColor = MaterialTheme.colorScheme.primary,
                inactiveTrackColor = Color.White.copy(alpha = 0.2f),
            ),
        )
    }
}

@Composable
private fun LutRow(
    state: VideoEditUiState,
    onSelect: (com.souru.lumina.data.luts.LutInfo?) -> Unit,
    onDelete: (com.souru.lumina.data.luts.LutInfo) -> Unit,
    onImport: () -> Unit,
) {
    var deleteTarget by remember { mutableStateOf<com.souru.lumina.data.luts.LutInfo?>(null) }

    Text(
        text = "LUT",
        style = MaterialTheme.typography.labelMedium,
        color = Color.White,
        modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
    )
    LazyRow(
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        contentPadding = PaddingValues(horizontal = 16.dp),
        modifier = Modifier.fillMaxWidth(),
    ) {
        item(key = "none") {
            LutChip(
                label = "なし",
                selected = state.selectedLut == null,
                onClick = { onSelect(null) },
            )
        }
        items(state.luts, key = { it.id }) { lut ->
            LutChip(
                label = lut.name,
                selected = state.selectedLut?.id == lut.id,
                onClick = { onSelect(lut) },
                onLongClick = { deleteTarget = lut },
            )
        }
        item(key = "import") {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier
                    .clip(RoundedCornerShape(50))
                    .background(Color.White.copy(alpha = 0.08f))
                    .clickable(onClick = onImport)
                    .padding(horizontal = 12.dp, vertical = 8.dp),
            ) {
                Icon(
                    Icons.Default.Add,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.primary,
                    modifier = Modifier.size(16.dp),
                )
                Spacer(Modifier.width(4.dp))
                Text(
                    text = ".cubeをインポート",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.primary,
                )
            }
        }
    }

    deleteTarget?.let { target ->
        AlertDialog(
            onDismissRequest = { deleteTarget = null },
            title = { Text("LUTを削除") },
            text = { Text("「${target.name}」をライブラリから削除しますか?") },
            confirmButton = {
                TextButton(onClick = {
                    onDelete(target)
                    deleteTarget = null
                }) { Text("削除") }
            },
            dismissButton = {
                TextButton(onClick = { deleteTarget = null }) { Text("キャンセル") }
            },
        )
    }
}

@Composable
private fun LutChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (selected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
        maxLines = 1,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick?.invoke() },
                )
            }
            .padding(horizontal = 14.dp, vertical = 8.dp),
    )
}

@Composable
private fun StrengthSlider(
    strength: Float,
    onStrengthChange: (Float) -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp),
    ) {
        Text(
            text = "強度",
            style = MaterialTheme.typography.labelMedium,
            color = Color.White,
        )
        Slider(
            value = strength,
            onValueChange = onStrengthChange,
            valueRange = 0f..1f,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier
                .weight(1f)
                .padding(horizontal = 12.dp),
        )
        Text(
            text = "${(strength * 100).toInt()}%",
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun VideoAdjustPanel(
    adjustments: Adjustments,
    selectedParamName: String,
    onSelectParam: (String) -> Unit,
    onAdjustmentsChange: (Adjustments) -> Unit,
    onReset: () -> Unit,
) {
    val param = VideoAdjustParam.entries.firstOrNull { it.name == selectedParamName }
        ?: VideoAdjustParam.EXPOSURE
    Column(Modifier.fillMaxWidth().padding(bottom = 12.dp)) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        ) {
            Text(
                text = param.label,
                style = MaterialTheme.typography.labelMedium,
                color = Color.White,
            )
            Spacer(Modifier.width(8.dp))
            Text(
                text = "%+.2f".format(param.get(adjustments)),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Spacer(Modifier.weight(1f))
            IconButton(onClick = onReset) {
                Icon(
                    Icons.Default.Refresh,
                    contentDescription = "調整をリセット",
                    tint = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
        Slider(
            value = param.get(adjustments),
            onValueChange = { onAdjustmentsChange(param.set(adjustments, it)) },
            valueRange = param.range,
            colors = SliderDefaults.colors(
                thumbColor = Color.White,
                activeTrackColor = Color.White,
                inactiveTrackColor = Color.White.copy(alpha = 0.25f),
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp),
        )
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            contentPadding = PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(VideoAdjustParam.entries) { p ->
                val selected = p == param
                val changed = p.get(adjustments) != 0f
                Text(
                    text = p.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = when {
                        selected -> Color.Black
                        changed -> MaterialTheme.colorScheme.primary
                        else -> MaterialTheme.colorScheme.onSurfaceVariant
                    },
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (selected) Color.White else Color.White.copy(alpha = 0.08f))
                        .clickable { onSelectParam(p.name) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
    }
}

@Composable
private fun ExportDialog(
    onDismiss: () -> Unit,
    onExport: (targetHeight: Int, bitrate: Int) -> Unit,
) {
    val resolutions = listOf("元の解像度" to 0, "4K (2160p)" to 2160, "1080p" to 1080, "720p" to 720)
    val bitrates = listOf(
        "自動" to 0,
        "高 (50 Mbps)" to 50_000_000,
        "標準 (25 Mbps)" to 25_000_000,
        "低 (12 Mbps)" to 12_000_000,
    )
    var resolution by remember { mutableStateOf(resolutions.first()) }
    var bitrate by remember { mutableStateOf(bitrates.first()) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("書き出し設定") },
        text = {
            Column {
                Text("解像度", style = MaterialTheme.typography.labelMedium)
                resolutions.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { resolution = option },
                    ) {
                        RadioButton(
                            selected = resolution == option,
                            onClick = { resolution = option },
                        )
                        Text(option.first, style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(8.dp))
                Text("ビットレート", style = MaterialTheme.typography.labelMedium)
                bitrates.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { bitrate = option },
                    ) {
                        RadioButton(
                            selected = bitrate == option,
                            onClick = { bitrate = option },
                        )
                        Text(option.first, style = MaterialTheme.typography.bodyMedium)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(resolution.second, bitrate.second) }) {
                Text("書き出す")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
