package com.souru.colorhunt.ui.grid

import android.net.Uri
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
 * The grid has its **own** photo set: you pick images here (independent of the
 * Sort tab), then drag to arrange or remove them. Picked photos are also fed to
 * the shared store so their dominant colour is analysed for the corner dot.
 */
class GridPreviewViewModel(private val repository: PhotoRepository) : ViewModel() {

    private val order = MutableStateFlow<List<String>>(emptyList())
    private val profile = MutableStateFlow(ProfileHeader())

    val uiState: StateFlow<GridPreviewUiState> =
        combine(order, repository.photos, profile) { ord, photos, prof ->
            val byId = photos.associateBy { it.id }
            GridPreviewUiState(profile = prof, feed = ord.mapNotNull { byId[it] })
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GridPreviewUiState())

    /** Add photos chosen directly on the grid tab. */
    fun addUris(uris: List<Uri>) {
        if (uris.isEmpty()) return
        repository.addPhotos(uris) // analyse for colour dots
        val known = order.value.toHashSet()
        val fresh = uris.map { it.toString() }.filter { it !in known }
        if (fresh.isNotEmpty()) order.value = order.value + fresh
    }

    fun remove(id: String) {
        order.value = order.value - id
    }

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
