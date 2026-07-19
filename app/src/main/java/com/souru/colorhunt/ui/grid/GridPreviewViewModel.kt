package com.souru.colorhunt.ui.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.colorhunt.data.PhotoRepository
import com.souru.colorhunt.domain.model.HuntPhoto
import com.souru.colorhunt.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach

/** Editable, dummy Instagram-style profile header values. */
data class ProfileHeader(
    val name: String = "colorhunt",
    val followers: Int = 1280,
    val following: Int = 348,
)

data class GridPreviewUiState(
    val profile: ProfileHeader = ProfileHeader(),
    val feed: List<HuntPhoto> = emptyList(),
) {
    /** Posts count mirrors the number of photos lined up. */
    val postCount: Int get() = feed.size
}

/**
 * Phase 2 — Instagram grid preview.
 *
 * Shows the upcoming posts as a 3-wide feed so the overall look can be checked
 * and reordered. Newest post sits top-left; dragging reorders. No real posting.
 * The feed is seeded from the shared photo store (selection first, else all).
 */
class GridPreviewViewModel(private val repository: PhotoRepository) : ViewModel() {

    private val _state = MutableStateFlow(GridPreviewUiState())
    val uiState: StateFlow<GridPreviewUiState> = _state.asStateFlow()

    init {
        // Seed once, then keep newly imported photos flowing in without clobbering
        // a manual reorder the user has already made.
        repository.photos
            .onEach { all ->
                val current = _state.value.feed
                if (current.isEmpty()) {
                    val selected = repository.selectedPhotos()
                    _state.value = _state.value.copy(feed = selected.ifEmpty { all })
                } else {
                    val known = current.mapTo(HashSet()) { it.id }
                    val additions = all.filter { it.id !in known }
                    // New posts enter at the top of the feed.
                    val updated = (additions + current).map { photo ->
                        all.firstOrNull { it.id == photo.id } ?: photo
                    }
                    if (updated != current) _state.value = _state.value.copy(feed = updated)
                }
            }
            .launchIn(viewModelScope)
    }

    /** Move the item at [from] to [to], shifting the rest (drag reorder). */
    fun move(from: Int, to: Int) {
        val list = _state.value.feed
        if (from !in list.indices || to !in list.indices || from == to) return
        val mutable = list.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        _state.value = _state.value.copy(feed = mutable)
    }

    fun updateProfile(name: String, followers: Int, following: Int) {
        _state.value = _state.value.copy(
            profile = ProfileHeader(name = name.ifBlank { "colorhunt" }, followers = followers, following = following),
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { GridPreviewViewModel(appContainer().photoRepository) }
        }
    }
}
