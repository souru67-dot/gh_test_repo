package com.souru.lumina.ui.photoedit

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.graphics.RectF
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.MediaRepository
import com.souru.lumina.data.edit.Adjustments
import com.souru.lumina.data.edit.PhotoExporter
import com.souru.lumina.data.model.MediaItem
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class EditMode { ADJUST, CROP }

/** アスペクト比プリセット。ratio=nullはフリー。 */
enum class AspectPreset(val label: String, val ratio: Float?) {
    FREE("フリー", null),
    ORIGINAL("元画像", -1f), // 実際の比率はビットマップから解決
    SQUARE("1:1", 1f),
    RATIO_3_2("3:2", 3f / 2f),
    RATIO_4_3("4:3", 4f / 3f),
    RATIO_16_9("16:9", 16f / 9f),
}

data class PhotoEditUiState(
    val loading: Boolean = true,
    val item: MediaItem? = null,
    val previewBitmap: Bitmap? = null,
    val adjustments: Adjustments = Adjustments(),
    val mode: EditMode = EditMode.ADJUST,
    val rotationDeg: Int = 0,
    val cropRect: RectF = RectF(0f, 0f, 1f, 1f),
    val aspect: AspectPreset = AspectPreset.FREE,
    val saving: Boolean = false,
    val savedUri: Uri? = null,
    val error: String? = null,
)

class PhotoEditViewModel(
    private val context: Context,
    private val mediaRepository: MediaRepository,
    private val mediaId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PhotoEditUiState())
    val uiState: StateFlow<PhotoEditUiState> = _uiState.asStateFlow()

    private var originalPreview: Bitmap? = null

    init {
        viewModelScope.launch {
            val item = mediaRepository.getItem(mediaId)
            if (item == null) {
                _uiState.update { it.copy(loading = false, error = "ファイルが見つかりません") }
                return@launch
            }
            runCatching { PhotoExporter.decodePreview(context, item) }
                .onSuccess { bitmap ->
                    originalPreview = bitmap
                    _uiState.update {
                        it.copy(loading = false, item = item, previewBitmap = bitmap)
                    }
                }
                .onFailure { t ->
                    _uiState.update {
                        it.copy(loading = false, item = item, error = "読み込みに失敗しました: ${t.message}")
                    }
                }
        }
    }

    fun setMode(mode: EditMode) = _uiState.update { it.copy(mode = mode) }

    fun setAdjustments(adjustments: Adjustments) =
        _uiState.update { it.copy(adjustments = adjustments) }

    fun resetAdjustments() = _uiState.update { it.copy(adjustments = Adjustments()) }

    fun rotate() {
        val base = originalPreview ?: return
        val newDeg = (_uiState.value.rotationDeg + 90) % 360
        val rotated = if (newDeg == 0) {
            base
        } else {
            val matrix = Matrix().apply { postRotate(newDeg.toFloat()) }
            Bitmap.createBitmap(base, 0, 0, base.width, base.height, matrix, true)
        }
        _uiState.update {
            it.copy(
                rotationDeg = newDeg,
                previewBitmap = rotated,
                cropRect = RectF(0f, 0f, 1f, 1f),
                aspect = AspectPreset.FREE,
            )
        }
    }

    fun setCropRect(rect: RectF) = _uiState.update { it.copy(cropRect = rect) }

    fun setAspect(preset: AspectPreset) {
        val state = _uiState.value
        val bitmap = state.previewBitmap ?: return
        val imageAspect = bitmap.width.toFloat() / bitmap.height
        val targetRatio = when {
            preset.ratio == null -> null
            preset == AspectPreset.ORIGINAL -> imageAspect
            else -> preset.ratio
        }
        val newRect = if (targetRatio == null) {
            state.cropRect
        } else {
            // 現在のクロップ中心を保ちつつ指定比率で最大の矩形を作る
            centeredRectWithAspect(state.cropRect, targetRatio, imageAspect)
        }
        _uiState.update { it.copy(aspect = preset, cropRect = newRect) }
    }

    fun resetCrop() = _uiState.update {
        it.copy(cropRect = RectF(0f, 0f, 1f, 1f), aspect = AspectPreset.FREE)
    }

    /** 正規化空間はアスペクト比が非等方なので、画像比率で補正しながら計算する。 */
    private fun centeredRectWithAspect(current: RectF, targetRatio: Float, imageAspect: Float): RectF {
        val cx = current.centerX()
        val cy = current.centerY()
        // 正規化幅wに対する実アスペクト = (w * imageW) / (h * imageH)
        // → h = w * imageAspect / targetRatio
        var w = current.width()
        var h = w * imageAspect / targetRatio
        if (h > 1f) {
            h = 1f
            w = h * targetRatio / imageAspect
        }
        if (w > 1f) {
            w = 1f
            h = w * imageAspect / targetRatio
        }
        var left = cx - w / 2f
        var top = cy - h / 2f
        left = left.coerceIn(0f, 1f - w)
        top = top.coerceIn(0f, 1f - h)
        return RectF(left, top, left + w, top + h)
    }

    fun save() {
        val state = _uiState.value
        val item = state.item ?: return
        if (state.saving) return
        _uiState.update { it.copy(saving = true, error = null) }
        viewModelScope.launch {
            runCatching {
                val fullRect = RectF(0f, 0f, 1f, 1f)
                PhotoExporter.export(
                    context = context,
                    source = item,
                    adjustments = state.adjustments,
                    cropRect = state.cropRect.takeIf { it != fullRect },
                    rotationDeg = state.rotationDeg,
                )
            }.onSuccess { uri ->
                _uiState.update { it.copy(saving = false, savedUri = uri) }
            }.onFailure { t ->
                _uiState.update { it.copy(saving = false, error = "保存に失敗しました: ${t.message}") }
            }
        }
    }

    override fun onCleared() {
        originalPreview = null
    }

    companion object {
        fun factory(mediaId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LuminaApplication
                PhotoEditViewModel(
                    context = app,
                    mediaRepository = app.container.mediaRepository,
                    mediaId = mediaId,
                )
            }
        }
    }
}
