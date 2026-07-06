package com.souru.lumina.ui.photoedit

import android.graphics.RenderEffect
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Rotate90DegreesCw
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
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
import androidx.compose.ui.graphics.asComposeRenderEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import com.souru.lumina.data.edit.Adjustments
import com.souru.lumina.data.edit.AdjustmentShader
import com.souru.lumina.data.edit.applyAdjustments

private enum class AdjustParam(
    val label: String,
    val range: ClosedFloatingPointRange<Float>,
    val get: (Adjustments) -> Float,
    val set: (Adjustments, Float) -> Adjustments,
) {
    EXPOSURE("露出", -3f..3f, { it.exposure }, { a, v -> a.copy(exposure = v) }),
    CONTRAST("コントラスト", -1f..1f, { it.contrast }, { a, v -> a.copy(contrast = v) }),
    HIGHLIGHTS("ハイライト", -1f..1f, { it.highlights }, { a, v -> a.copy(highlights = v) }),
    SHADOWS("シャドウ", -1f..1f, { it.shadows }, { a, v -> a.copy(shadows = v) }),
    WHITES("白レベル", -1f..1f, { it.whites }, { a, v -> a.copy(whites = v) }),
    BLACKS("黒レベル", -1f..1f, { it.blacks }, { a, v -> a.copy(blacks = v) }),
    TEMPERATURE("色温度", -1f..1f, { it.temperature }, { a, v -> a.copy(temperature = v) }),
    TINT("色かぶり", -1f..1f, { it.tint }, { a, v -> a.copy(tint = v) }),
    SATURATION("彩度", -1f..1f, { it.saturation }, { a, v -> a.copy(saturation = v) }),
    VIBRANCE("自然な彩度", -1f..1f, { it.vibrance }, { a, v -> a.copy(vibrance = v) }),
    SHARPEN("シャープ", 0f..1f, { it.sharpen }, { a, v -> a.copy(sharpen = v) }),
}

/**
 * JPEG向けの簡易編集画面。調整はGPU(AGSL RuntimeShader)で即時プレビュー、
 * 保存は常に別名コピー(非破壊)。
 */
@Composable
fun PhotoEditScreen(
    mediaId: Long,
    onClose: () -> Unit,
    viewModel: PhotoEditViewModel = viewModel(
        key = "photoEdit-$mediaId",
        factory = PhotoEditViewModel.factory(mediaId),
    ),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val context = LocalContext.current
    val shader = remember { AdjustmentShader.create() }
    var selectedParam by rememberSaveable { mutableStateOf(AdjustParam.EXPOSURE.name) }

    BackHandler(onBack = onClose)

    LaunchedEffect(state.savedUri) {
        if (state.savedUri != null) {
            Toast.makeText(context, "Pictures/Lumina に保存しました", Toast.LENGTH_SHORT).show()
            onClose()
        }
    }
    LaunchedEffect(state.error) {
        state.error?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show() }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black)
            .systemBarsPadding(),
    ) {
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
            Text(
                text = "編集",
                style = MaterialTheme.typography.titleSmall,
                color = Color.White,
            )
            Spacer(Modifier.weight(1f))
            TextButton(onClick = viewModel::save, enabled = !state.saving && state.previewBitmap != null) {
                Text("保存", color = if (state.saving) Color.Gray else MaterialTheme.colorScheme.primary)
            }
        }

        // プレビュー
        // 長押ししている間だけ調整をバイパスして編集前を表示する
        // (Lightroom方式のA/B比較。キャッシュ済みビットマップの
        // renderEffectを外すだけなので再デコードは発生しない)
        var showOriginal by remember { mutableStateOf(false) }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f),
            contentAlignment = Alignment.Center,
        ) {
            val bitmap = state.previewBitmap
            if (state.loading) {
                CircularProgressIndicator(color = MaterialTheme.colorScheme.onSurfaceVariant)
            } else if (bitmap != null) {
                val imageAspect = bitmap.width.toFloat() / bitmap.height
                Box(
                    modifier = Modifier
                        .padding(8.dp)
                        .aspectRatio(imageAspect)
                        .align(Alignment.Center),
                ) {
                    Image(
                        bitmap = bitmap.asImageBitmap(),
                        contentDescription = null,
                        modifier = Modifier
                            .fillMaxSize()
                            // トリミングモードではCropOverlayのドラッグと競合する
                            // ため、長押しA/Bは調整モードのみで受け付ける
                            .pointerInput(state.mode) {
                                if (state.mode == EditMode.ADJUST) {
                                    detectTapGestures(
                                        onLongPress = { showOriginal = true },
                                        onPress = {
                                            tryAwaitRelease()
                                            showOriginal = false
                                        },
                                    )
                                }
                            }
                            .graphicsLayer {
                                if (showOriginal) {
                                    renderEffect = null
                                } else {
                                    shader.applyAdjustments(state.adjustments)
                                    renderEffect = RenderEffect
                                        .createRuntimeShaderEffect(
                                            shader,
                                            AdjustmentShader.CONTENT_SHADER_NAME,
                                        )
                                        .asComposeRenderEffect()
                                }
                                // エフェクトの出力を画像レイヤーの境界内に限定し、
                                // 余白(レターボックス)へ効果が漏れないようにする
                                clip = true
                            },
                    )
                    if (state.mode == EditMode.CROP) {
                        val aspectValue = when {
                            state.aspect.ratio == null -> null
                            state.aspect == AspectPreset.ORIGINAL -> imageAspect
                            else -> state.aspect.ratio
                        }
                        CropOverlay(
                            cropRect = state.cropRect,
                            aspectRatio = aspectValue,
                            imageAspect = imageAspect,
                            onCropChange = viewModel::setCropRect,
                        )
                    }
                }
            } else {
                Text(
                    text = state.error ?: "読み込めませんでした",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            if (showOriginal) {
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

        // 下部パネル
        Column(Modifier.fillMaxWidth()) {
            when (state.mode) {
                EditMode.ADJUST -> AdjustPanel(
                    adjustments = state.adjustments,
                    selectedParamName = selectedParam,
                    onSelectParam = { selectedParam = it },
                    onAdjustmentsChange = viewModel::setAdjustments,
                    onReset = viewModel::resetAdjustments,
                )

                EditMode.CROP -> CropPanel(
                    selected = state.aspect,
                    onSelectAspect = viewModel::setAspect,
                    onRotate = viewModel::rotate,
                    onReset = viewModel::resetCrop,
                )
            }

            // モード切替タブ
            Row(
                horizontalArrangement = Arrangement.Center,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 10.dp),
            ) {
                ModeTab("調整", state.mode == EditMode.ADJUST) { viewModel.setMode(EditMode.ADJUST) }
                Spacer(Modifier.width(12.dp))
                ModeTab("トリミング", state.mode == EditMode.CROP) { viewModel.setMode(EditMode.CROP) }
            }
        }

        if (state.saving) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(bottom = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = "書き出し中…",
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ModeTab(label: String, selected: Boolean, onClick: () -> Unit) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = if (selected) Color.White else MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(if (selected) Color.White.copy(alpha = 0.14f) else Color.Transparent)
            .clickable(onClick = onClick)
            .padding(horizontal = 18.dp, vertical = 8.dp),
    )
}

@Composable
private fun AdjustPanel(
    adjustments: Adjustments,
    selectedParamName: String,
    onSelectParam: (String) -> Unit,
    onAdjustmentsChange: (Adjustments) -> Unit,
    onReset: () -> Unit,
) {
    val param = AdjustParam.entries.firstOrNull { it.name == selectedParamName }
        ?: AdjustParam.EXPOSURE
    Column(Modifier.fillMaxWidth()) {
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
            contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 16.dp),
            modifier = Modifier.fillMaxWidth(),
        ) {
            items(AdjustParam.entries) { p ->
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
private fun CropPanel(
    selected: AspectPreset,
    onSelectAspect: (AspectPreset) -> Unit,
    onRotate: () -> Unit,
    onReset: () -> Unit,
) {
    Row(
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp),
    ) {
        IconButton(onClick = onRotate) {
            Icon(
                Icons.Default.Rotate90DegreesCw,
                contentDescription = "90度回転",
                tint = Color.White,
            )
        }
        LazyRow(
            horizontalArrangement = Arrangement.spacedBy(8.dp),
            modifier = Modifier.weight(1f),
        ) {
            items(AspectPreset.entries) { preset ->
                val isSelected = preset == selected
                Text(
                    text = preset.label,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) Color.Black else MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier
                        .clip(RoundedCornerShape(50))
                        .background(if (isSelected) Color.White else Color.White.copy(alpha = 0.08f))
                        .clickable { onSelectAspect(preset) }
                        .padding(horizontal = 12.dp, vertical = 7.dp),
                )
            }
        }
        IconButton(onClick = onReset) {
            Icon(
                Icons.Default.Refresh,
                contentDescription = "トリミングをリセット",
                tint = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
