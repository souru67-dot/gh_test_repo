package com.souru.lumina.ui.favorites

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.MediaRepository
import com.souru.lumina.data.model.GalleryEntry
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

data class FavoritesUiState(
    val loading: Boolean = true,
    val entries: List<GalleryEntry> = emptyList(),
)

class FavoritesViewModel(private val mediaRepository: MediaRepository) : ViewModel() {

    val uiState: StateFlow<FavoritesUiState> =
        mediaRepository.observeFavorites()
            .map { items -> FavoritesUiState(loading = false, entries = items.map { GalleryEntry(it) }) }
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), FavoritesUiState())

    fun retryLoad() {
        mediaRepository.refresh()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LuminaApplication
                FavoritesViewModel(mediaRepository = app.container.mediaRepository)
            }
        }
    }
}
