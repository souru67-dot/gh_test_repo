package com.souru.colorhunt.ui.grid

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.colorhunt.data.PhotoRepository
import com.souru.colorhunt.domain.model.HuntPhoto
import com.souru.colorhunt.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn

/** Editable, dummy Instagram-style profile header values. */
data class ProfileHeader(
    val name: String = "colorhunt",
    val followers: Int = 1,
    val following: Int = 1,
)

data class GridPreviewUiState(
    val profile: ProfileHeader = ProfileHeader(),
    val feed: List<HuntPhoto> = emptyList(),
) {
    val postCount: Int get() = feed.size
    val isEmpty: Boolean get() = feed.isEmpty()
}

/**
 * Phase 2 — Instagram grid preview.
 *
 * The feed is exactly the photos the user has **selected** (on the Sort tab):
 * you build your grid by choosing shots, then drag to arrange. Newly selected
 * photos enter at the top; deselected ones drop out; manual order is preserved.
 * No placeholder cells. No real posting.
 */
class GridPreviewViewModel(private val repository: PhotoRepository) : ViewModel() {

    private val order = MutableStateFlow<List<String>>(emptyList())
    private val profile = MutableStateFlow(ProfileHeader())

    init {
        // Reconcile our ordered id list with the current selection.
        repository.selectedIds
            .onEach { ids ->
                val known = order.value
                val knownSet = known.toHashSet()
                val stillSelected = known.filter { it in ids }
                val newlySelected = ids.filter { it !in knownSet }
                val next = newlySelected.toList() + stillSelected // new posts on top
                if (next != known) order.value = next
            }
            .launchIn(viewModelScope)
    }

    val uiState: StateFlow<GridPreviewUiState> =
        combine(order, repository.photos, profile) { ord, photos, prof ->
            val byId = photos.associateBy { it.id }
            GridPreviewUiState(profile = prof, feed = ord.mapNotNull { byId[it] })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridPreviewUiState())

    fun move(from: Int, to: Int) {
        val list = order.value
        if (from !in list.indices || to !in list.indices || from == to) return
        val mutable = list.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        order.value = mutable
    }

    fun updateProfile(name: String, followers: Int, following: Int) {
        profile.value = ProfileHeader(
            name = name.ifBlank { "colorhunt" },
            followers = followers,
            following = following,
        )
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { GridPreviewViewModel(appContainer().photoRepository) }
        }
    }
}
