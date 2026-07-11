package com.souru.lumina.ui.albums

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.albums.Album
import com.souru.lumina.data.albums.AlbumSortOrder
import com.souru.lumina.data.albums.AlbumsSource
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class AlbumsUiState(
    val loading: Boolean = true,
    val albums: List<Album> = emptyList(),
    val sortOrder: AlbumSortOrder = AlbumSortOrder.UPDATED,
)

class AlbumsViewModel(albumsSource: AlbumsSource) : ViewModel() {

    private val sortOrder = MutableStateFlow(AlbumSortOrder.UPDATED)

    val uiState: StateFlow<AlbumsUiState> =
        combine(albumsSource.observeAlbums(), sortOrder) { albums, order ->
            AlbumsUiState(
                loading = false,
                albums = when (order) {
                    AlbumSortOrder.UPDATED -> albums.sortedByDescending { it.updatedAtMs }
                    AlbumSortOrder.NAME -> albums.sortedBy { it.name.lowercase() }
                },
                sortOrder = order,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), AlbumsUiState())

    fun setSortOrder(order: AlbumSortOrder) {
        sortOrder.value = order
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LuminaApplication
                AlbumsViewModel(albumsSource = app.container.albumsSource)
            }
        }
    }
}
