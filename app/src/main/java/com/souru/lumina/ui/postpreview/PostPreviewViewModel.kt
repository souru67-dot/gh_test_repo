package com.souru.lumina.ui.postpreview

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.MediaRepository
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

data class PostPreviewUiState(
    val loading: Boolean = true,
    val item: MediaItem? = null,
    /** グリッドの他セル選択用のライブラリ写真。 */
    val libraryPhotos: List<MediaItem> = emptyList(),
    /** グリッドの他セルに置く写真(選択順、最大8件)。不足分はグレー。 */
    val neighbors: List<MediaItem> = emptyList(),
)

class PostPreviewViewModel(
    private val mediaRepository: MediaRepository,
    private val mediaId: Long,
) : ViewModel() {

    private val _uiState = MutableStateFlow(PostPreviewUiState())
    val uiState: StateFlow<PostPreviewUiState> = _uiState.asStateFlow()

    init {
        viewModelScope.launch {
            val item = mediaRepository.getItem(mediaId)
            _uiState.update { it.copy(loading = false, item = item) }
        }
        viewModelScope.launch {
            mediaRepository.observeMedia().collect { media ->
                _uiState.update { state ->
                    state.copy(
                        libraryPhotos = media.filter {
                            it.kind == MediaKind.IMAGE && it.id != mediaId
                        },
                    )
                }
            }
        }
    }

    fun toggleNeighbor(item: MediaItem) {
        _uiState.update { state ->
            val current = state.neighbors
            val next = if (current.any { it.id == item.id }) {
                current.filterNot { it.id == item.id }
            } else if (current.size < 8) {
                current + item
            } else {
                current
            }
            state.copy(neighbors = next)
        }
    }

    companion object {
        fun factory(mediaId: Long) = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LuminaApplication
                PostPreviewViewModel(
                    mediaRepository = app.container.mediaRepository,
                    mediaId = mediaId,
                )
            }
        }
    }
}
