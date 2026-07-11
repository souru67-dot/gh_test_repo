package com.souru.lumina.ui.library

import android.graphics.Bitmap
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.luts.LutInfo
import com.souru.lumina.data.luts.LutRepository
import com.souru.lumina.data.luts.LutThumbnails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

class LutManagerViewModel(private val lutRepository: LutRepository) : ViewModel() {

    val luts: StateFlow<List<LutInfo>> = lutRepository.luts

    private val _message = MutableStateFlow<String?>(null)
    val message: StateFlow<String?> = _message.asStateFlow()

    /** LUT id → サンプル適用サムネイル。IOで逐次生成して追記する。 */
    private val _thumbnails = MutableStateFlow<Map<String, Bitmap>>(emptyMap())
    val thumbnails: StateFlow<Map<String, Bitmap>> = _thumbnails.asStateFlow()

    init {
        viewModelScope.launch {
            lutRepository.refresh()
            luts.collect { list ->
                // 未生成のサムネイルだけIOで作る(パース失敗はスキップ)
                list.forEach { info ->
                    if (!_thumbnails.value.containsKey(info.id)) {
                        val bitmap = withContext(Dispatchers.IO) {
                            runCatching { LutThumbnails.render(lutRepository.load(info)) }
                                .getOrNull()
                        }
                        if (bitmap != null) {
                            _thumbnails.value = _thumbnails.value + (info.id to bitmap)
                        }
                    }
                }
            }
        }
    }

    fun import(uri: Uri) {
        viewModelScope.launch {
            runCatching { lutRepository.import(uri) }
                .onSuccess { _message.value = "「${it.name}」を読み込みました" }
                .onFailure { _message.value = "LUTを読み込めませんでした: ${it.message}" }
        }
    }

    fun delete(info: LutInfo) {
        viewModelScope.launch { lutRepository.delete(info) }
    }

    fun consumeMessage() {
        _message.value = null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LuminaApplication
                LutManagerViewModel(lutRepository = app.container.lutRepository)
            }
        }
    }
}
