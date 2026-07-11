package com.souru.lumina.ui.trash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.MediaRepository
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.data.model.MediaTypeFilter
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class TrashUiState(
    val loading: Boolean = true,
    val items: List<MediaItem> = emptyList(),
    val typeFilter: MediaTypeFilter = MediaTypeFilter.ALL,
    val selection: Set<Long> = emptySet(),
)

class TrashViewModel(mediaRepository: MediaRepository) : ViewModel() {

    private val typeFilter = MutableStateFlow(MediaTypeFilter.ALL)
    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    val uiState: StateFlow<TrashUiState> =
        combine(
            mediaRepository.observeTrashed(),
            typeFilter,
            selection,
        ) { items, filter, selected ->
            val visible = when (filter) {
                MediaTypeFilter.ALL -> items
                MediaTypeFilter.PHOTO -> items.filter { it.kind == MediaKind.IMAGE }
                MediaTypeFilter.VIDEO -> items.filter { it.kind == MediaKind.VIDEO }
            }
            TrashUiState(
                loading = false,
                items = visible,
                typeFilter = filter,
                // 一覧から消えたアイテムの選択は残さない
                selection = selected.filterTo(HashSet()) { id -> visible.any { it.id == id } },
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), TrashUiState())

    fun setTypeFilter(filter: MediaTypeFilter) {
        typeFilter.value = filter
    }

    fun toggleSelection(id: Long) {
        selection.value = selection.value.let { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        selection.value = emptySet()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LuminaApplication
                TrashViewModel(mediaRepository = app.container.mediaRepository)
            }
        }
    }
}
