package com.souru.lumina.ui.videoedit

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.media3.common.Effect
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.SingleColorLut
import androidx.work.WorkManager
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.MediaRepository
import com.souru.lumina.data.edit.Adjustments
import com.souru.lumina.data.luts.LutBaker
import com.souru.lumina.data.luts.LutInfo
import com.souru.lumina.data.luts.LutRepository
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.work.VideoExportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

data class VideoEditUiState(
    val loading: Boolean = true,
    val item: MediaItem? = null,
    val luts: List<LutInfo> = emptyList(),
    val selectedLut: LutInfo? = null,
    val strength: Float = 1f,
    val adjustments: Adjustments = Adjustments(),
    val comparing: Boolean = false,
    val trimStartMs: Long = 0L,
    val trimEndMs: Long = 0L,
    val exportEnqueued: Boolean = false,
    val message: String? = null,
)

private data class ColorParams(
    val lut: LutInfo?,
    val strength: Float,
    val adjustments: Adjustments,
    val comparing: Boolean,
)

/**
 * 動画編集。LUT+強度+簡易調整を単一の3D LUTに焼き込み、
 * ExoPlayerプレビューとTransformer書き出しの両方に同じ効果を適用する。
 */
@UnstableApi
class VideoEditViewModel(
    private val context: Context,
    private val mediaRepository: MediaRepository,
    private val lutRepository: LutRepository,
    private val mediaId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(VideoEditUiState())
    val uiState: StateFlow<VideoEditUiState> = _uiState.asStateFlow()

    private val colorParams = MutableStateFlow(ColorParams(null, 1f, Adjustments(), false))

    /** プレビュー用エフェクト。A/B比較中は空リスト(=元の映像)。 */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val videoEffects: StateFlow<List<Effect>> = colorParams
        .debounce(80)
        .mapLatest { params -> buildEffects(params) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), emptyList())

    init {
        viewModelScope.launch {
            val item = mediaRepository.getItem(mediaId)
            _uiState.update {
                it.copy(
                    loading = false,
                    item = item,
                    trimEndMs = item?.durationMs ?: 0L,
                    message = if (item == null) "ファイルが見つかりません" else null,
                )
            }
        }
        viewModelScope.launch {
            lutRepository.refresh()
            lutRepository.luts.collect { luts ->
                _uiState.update { it.copy(luts = luts) }
            }
        }
    }

    private suspend fun buildEffects(params: ColorParams): List<Effect> {
        if (params.comparing) return emptyList()
        val hasLut = params.lut != null && params.strength > 0f
        if (!hasLut && params.adjustments.isIdentity) return emptyList()
        val cube = withContext(Dispatchers.Default) {
            val lut = params.lut?.let { runCatching { lutRepository.load(it) }.getOrNull() }
            LutBaker.bake(lut, params.strength, params.adjustments)
        }
        return listOf(SingleColorLut.createFromCube(cube))
    }

    fun selectLut(lut: LutInfo?) {
        _uiState.update { it.copy(selectedLut = lut) }
        colorParams.update { it.copy(lut = lut) }
    }

    fun setStrength(strength: Float) {
        val s = strength.coerceIn(0f, 1f)
        _uiState.update { it.copy(strength = s) }
        colorParams.update { it.copy(strength = s) }
    }

    fun setAdjustments(adjustments: Adjustments) {
        _uiState.update { it.copy(adjustments = adjustments) }
        colorParams.update { it.copy(adjustments = adjustments) }
    }

    fun resetAdjustments() = setAdjustments(Adjustments())

    /** A/B比較。押している間だけ元の映像を表示する。 */
    fun setComparing(comparing: Boolean) {
        _uiState.update { it.copy(comparing = comparing) }
        colorParams.update { it.copy(comparing = comparing) }
    }

    fun setTrim(startMs: Long, endMs: Long) {
        val duration = _uiState.value.item?.durationMs ?: return
        val start = startMs.coerceIn(0L, duration)
        val end = endMs.coerceIn(start, duration)
        _uiState.update { it.copy(trimStartMs = start, trimEndMs = end) }
    }

    fun importLut(uri: Uri) {
        viewModelScope.launch {
            runCatching { lutRepository.import(uri) }
                .onSuccess { info ->
                    selectLut(info)
                    _uiState.update { it.copy(message = "「${info.name}」を読み込みました") }
                }
                .onFailure { t ->
                    _uiState.update { it.copy(message = "LUTを読み込めませんでした: ${t.message}") }
                }
        }
    }

    fun deleteLut(info: LutInfo) {
        viewModelScope.launch {
            if (_uiState.value.selectedLut?.id == info.id) selectLut(null)
            lutRepository.delete(info)
        }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** WorkManagerで書き出しをバックグラウンド実行する。 */
    fun export(targetHeight: Int, bitrate: Int) {
        val state = _uiState.value
        val item = state.item ?: return
        val trimStart = state.trimStartMs.takeIf { it > 0 } ?: 0L
        val trimEnd = state.trimEndMs.takeIf { it in 1 until item.durationMs } ?: 0L
        val request = VideoExportWorker.buildRequest(
            uri = item.uri,
            baseName = item.baseName,
            lutPath = state.selectedLut?.takeIf { state.strength > 0f }?.file?.absolutePath,
            strength = state.strength,
            adjustments = state.adjustments,
            trimStartMs = trimStart,
            trimEndMs = trimEnd,
            targetHeight = targetHeight,
            bitrate = bitrate,
        )
        WorkManager.getInstance(context).enqueue(request)
        _uiState.update {
            it.copy(exportEnqueued = true, message = "バックグラウンドで書き出しを開始しました(進捗は通知に表示)")
        }
    }

    companion object {
        fun factory(mediaId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LuminaApplication
                VideoEditViewModel(
                    context = app,
                    mediaRepository = app.container.mediaRepository,
                    lutRepository = app.container.lutRepository,
                    mediaId = mediaId,
                )
            }
        }
    }
}
