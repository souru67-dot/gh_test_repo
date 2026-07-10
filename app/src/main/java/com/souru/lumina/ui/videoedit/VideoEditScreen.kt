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
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.systemBarsIgnoringVisibility
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
import androidx.compose.material3.Checkbox
import com.souru.lumina.ui.common.LuminaLoading
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RangeSlider
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
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
import com.souru.lumina.data.video.SdrPreviewRenderersFactory
import com.souru.lumina.data.video.VideoExportPreset
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
@kotlin.OptIn(ExperimentalLayoutApi::class)
@Composable
fun VideoEditScreen(
    mediaId: Long,
    onClose: () -> Unit,
    onOpenPostPreview: (() -> Unit)? = null,
    viewModel: VideoEditViewModel = viewModel(
        key = "videoEdit-$mediaId",
        factory = VideoEditViewModel.factory(mediaId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val effects by viewModel.videoEffects.collectAsStateWithLifecycle()
    val context = LocalContext.current
    var showDiscardDialog by rememberSaveable { mutableStateOf(false) }

    // 未保存の編集(LUT/調整/トリム)があり、まだ書き出していないときだけ
    // 戻る操作を確認ダイアログに差し替える。それ以外はBackHandlerを
    // 登録せず、システムの戻る(NavHostのpop)をそのまま通す
    val duration = state.item?.durationMs ?: 0L
    val trimmed = state.trimStartMs > 0 ||
        (duration > 0 && state.trimEndMs in 1 until duration)
    val hasUnsavedEdits = state.item != null && !state.exportEnqueued &&
        (state.selectedLut != null || !state.adjustments.isIdentity || trimmed)

    BackHandler(enabled = hasUnsavedEdits) { showDiscardDialog = true }

    fun requestClose() {
        if (hasUnsavedEdits) showDiscardDialog = true else onClose()
    }

    if (showDiscardDialog) {
        AlertDialog(
            onDismissRequest = { showDiscardDialog = false },
            title = { Text("編集を破棄しますか?") },
            text = { Text("LUT・調整・トリムの変更は保存されません。") },
            confirmButton = {
                TextButton(onClick = {
                    showDiscardDialog = false
                    onClose()
                }) { Text("破棄") }
            },
            dismissButton = {
                TextButton(onClick = { showDiscardDialog = false }) { Text("キャンセル") }
            },
        )
    }

    LaunchedEffect(state.message) {
        state.message?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            viewModel.consumeMessage()
        }
    }

    val item = state.item
    // ビューアのバー非表示状態を引き継がないよう必ず表示に戻す。
    // パディングも可視状態に依存しないInsetsで安定させる
    com.souru.lumina.util.EnsureSystemBarsVisible()
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .padding(WindowInsets.systemBarsIgnoringVisibility.asPaddingValues()),
    ) {
        if (state.loading || item == null) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                if (state.loading) {
                    LuminaLoading()
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
                onClose = { requestClose() },
                onOpenPostPreview = onOpenPostPreview,
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
    onOpenPostPreview: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    var showExportDialog by rememberSaveable { mutableStateOf(false) }
    var selectedParam by rememberSaveable { mutableStateOf(VideoAdjustParam.EXPOSURE.name) }
    var selectedTab by rememberSaveable { mutableIntStateOf(0) }

    // 課金ゲート: ProのLUTを当てた動画の書き出しと .cube インポートを案内する。
    // SNSセーフ書き出し自体は無料。プレビューは自由(書き出し/取り込み時にのみ案内)。
    val isPro = com.souru.lumina.ui.pro.rememberIsPro()
    val haptics = androidx.compose.ui.platform.LocalHapticFeedback.current
    var upsell by remember { mutableStateOf<com.souru.lumina.ui.pro.ProFeature?>(null) }
    val lutNeedsPro = state.selectedLut?.let { com.souru.lumina.ui.pro.isProLut(it) } == true && !isPro
    upsell?.let { feature ->
        com.souru.lumina.ui.pro.ProUpsellDialog(feature, onDismiss = { upsell = null })
    }

    // 再生位置・再生状態はプレイヤー再生成をまたいで保持する
    val playbackKeeper = remember(item.id) { PlaybackKeeper() }

    // MediaCodecVideoRendererのエフェクトパイプライン(videoSink)は
    // レンダラー初回有効化時にしか生成されない(hasSetVideoSinkでラッチ)ため、
    // stop→setVideoEffects→prepareの後差しは端末により反映されない。
    // 唯一契約が保証される「新規プレイヤーに prepare 前に適用」を毎回行う。
    //
    // HDR(HLG/PQ)素材は、書き出し(トーンマップ→LUT)と同じ順序になるよう
    // デコーダーにSDRトーンマップを要求するRenderersFactoryを使う。
    // これでプレビュー・書き出し・長押しA/Bのすべてが
    // 「SDR(BT.709)に正規化してからLUT適用」の同一パイプラインを通る
    val isHdrSource = state.colorInfo?.isHdr == true
    val player = remember(item.id, effects, isHdrSource) {
        val builder = if (isHdrSource) {
            ExoPlayer.Builder(context, SdrPreviewRenderersFactory(context))
        } else {
            ExoPlayer.Builder(context)
        }
        builder.build().apply {
            setVideoEffects(effects) // 必ず prepare より前
            setMediaItem(Media3Item.fromUri(item.uri))
            repeatMode = Player.REPEAT_MODE_ALL
            prepare()
            if (playbackKeeper.positionMs > 0) seekTo(playbackKeeper.positionMs)
            playWhenReady = playbackKeeper.playWhenReady
        }
    }
    DisposableEffect(player) {
        onDispose { player.release() }
    }
    // 再生位置・状態を継続的に控えておき、再生成時に復元する
    LaunchedEffect(player) {
        while (isActive) {
            if (player.playbackState != Player.STATE_IDLE) {
                playbackKeeper.positionMs = player.currentPosition.coerceAtLeast(0)
                playbackKeeper.playWhenReady = player.playWhenReady
            }
            delay(150)
        }
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
            state.colorInfo?.let { info ->
                // 入力の色特性バッジ(例: HLG 10bit)。推定を含む場合は「?」付き
                Text(
                    text = info.badgeLabel,
                    style = MaterialTheme.typography.labelSmall,
                    color = Color.White.copy(alpha = 0.8f),
                    modifier = Modifier
                        .padding(start = 8.dp)
                        .clip(RoundedCornerShape(4.dp))
                        .background(Color.White.copy(alpha = 0.12f))
                        .padding(horizontal = 6.dp, vertical = 2.dp),
                )
            }
            Spacer(Modifier.weight(1f))
            if (onOpenPostPreview != null) {
                TextButton(onClick = onOpenPostPreview) {
                    Text("投稿PV", color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            TextButton(onClick = {
                if (lutNeedsPro) {
                    upsell = com.souru.lumina.ui.pro.ProFeature.ALL_LUTS
                } else {
                    notificationLauncher.launch(android.Manifest.permission.POST_NOTIFICATIONS)
                }
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
            // タップで再生/一時停止、プレビューの長押し中だけ編集前(オリジナル)を表示。
            // A/B中はエフェクト変更でプレイヤーが再生成されるため、進行中の
            // ジェスチャーが切れないようキーはUnit固定+最新playerを参照する
            val currentPlayer by rememberUpdatedState(player)
            Box(
                Modifier
                    .fillMaxSize()
                    .pointerInput(Unit) {
                        detectTapGestures(
                            onTap = {
                                if (currentPlayer.isPlaying) {
                                    currentPlayer.pause()
                                } else {
                                    currentPlayer.play()
                                }
                            },
                            onLongPress = {
                                haptics.performHapticFeedback(
                                    androidx.compose.ui.hapticfeedback.HapticFeedbackType.LongPress,
                                )
                                viewModel.setComparing(true)
                            },
                            onPress = {
                                tryAwaitRelease()
                                viewModel.setComparing(false)
                            },
                        )
                    },
            )
            if (state.comparing) {
                Text(
                    text = "編集前",
                    style = MaterialTheme.typography.labelMedium,
                    color = Color.White,
                    modifier = Modifier
                        .align(Alignment.TopStart)
                        .padding(12.dp)
                        .clip(RoundedCornerShape(50))
                        .background(Color.Black.copy(alpha = 0.55f))
                        .padding(horizontal = 12.dp, vertical = 6.dp),
                )
            }
        }

        // 下部パネル: 再生コントロールは共通、編集操作はタブで分離。
        // 編集状態はViewModelが一元管理するためタブ切替でも保持される
        Column(Modifier.fillMaxWidth()) {
            TransportBar(player = player, durationMs = item.durationMs)
            TabRow(
                selectedTabIndex = selectedTab,
                containerColor = Color.Transparent,
                contentColor = Color.White,
            ) {
                Tab(
                    selected = selectedTab == 0,
                    onClick = { selectedTab = 0 },
                    text = { Text("トリム") },
                    selectedContentColor = Color.White,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Tab(
                    selected = selectedTab == 1,
                    onClick = { selectedTab = 1 },
                    text = { Text("カラー") },
                    selectedContentColor = Color.White,
                    unselectedContentColor = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(top = 8.dp),
            ) {
                if (selectedTab == 0) {
                    TrimBar(
                        durationMs = item.durationMs,
                        trimStartMs = state.trimStartMs,
                        trimEndMs = state.trimEndMs,
                        onTrimChange = viewModel::setTrim,
                        onSeek = { player.seekTo(it) },
                    )
                } else {
                    LutRow(
                        state = state,
                        onSelect = viewModel::selectLut,
                        onDelete = viewModel::deleteLut,
                        onImport = {
                            if (isPro) {
                                lutImportLauncher.launch(arrayOf("*/*"))
                            } else {
                                upsell = com.souru.lumina.ui.pro.ProFeature.CUBE_IMPORT
                            }
                        },
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
        }
    }

    if (showExportDialog) {
        ExportDialog(
            onDismiss = { showExportDialog = false },
            onExport = { preset, useHevc ->
                showExportDialog = false
                viewModel.export(preset, useHevc)
            },
        )
    }
}

/** プレイヤー再生成をまたいで再生位置・再生状態を引き継ぐためのホルダー。 */
private class PlaybackKeeper {
    var positionMs: Long = 0L
    var playWhenReady: Boolean = true
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
            modifier = Modifier.semantics { contentDescription = "トリム範囲" },
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
                // 標準プリセットは削除不可(長押しメニューを出さない)
                onLongClick = if (lut.isPreset) null else ({ deleteTarget = lut }),
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
                .padding(horizontal = 12.dp)
                .semantics { contentDescription = "LUT強度" },
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
                .padding(horizontal = 16.dp)
                // TalkBack にパラメータ名を読み上げさせる(視覚ラベルとの関連付け)
                .semantics { contentDescription = param.label },
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
    onExport: (preset: VideoExportPreset, useHevc: Boolean) -> Unit,
) {
    var preset by remember { mutableStateOf(VideoExportPreset.SNS_STANDARD) }
    var useHevc by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("書き出し設定") },
        text = {
            Column {
                VideoExportPreset.entries.forEach { option ->
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { preset = option },
                    ) {
                        RadioButton(
                            selected = preset == option,
                            onClick = { preset = option },
                        )
                        Column {
                            Text(option.label, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = option.description,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    Spacer(Modifier.height(4.dp))
                }
                if (preset == VideoExportPreset.HIGH_QUALITY_ARCHIVE) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier
                            .fillMaxWidth()
                            .clickable { useHevc = !useHevc },
                    ) {
                        Checkbox(checked = useHevc, onCheckedChange = { useHevc = it })
                        Text("HEVC (H.265) で書き出す", style = MaterialTheme.typography.bodyMedium)
                    }
                }
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "どちらもSDR / BT.709に正規化して書き出します(HDR素材は自動でトーンマップ)",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onExport(preset, useHevc) }) {
                Text("書き出す")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("キャンセル") }
        },
    )
}
