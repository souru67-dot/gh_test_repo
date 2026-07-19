package com.souru.colorhunt.ui.map

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.colorhunt.data.PhotoRepository
import com.souru.colorhunt.domain.color.ColorBucket
import com.souru.colorhunt.domain.model.HuntPhoto
import com.souru.colorhunt.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn

data class MapUiState(
    /** Photos with GPS, honouring the active colour filter — these get map pins. */
    val located: List<HuntPhoto> = emptyList(),
    /** Photos without GPS — shown in a separate "no location" list. */
    val noLocation: List<HuntPhoto> = emptyList(),
    val availableFilters: List<ColorBucket> = emptyList(),
    val activeFilter: ColorBucket? = null,
)

/**
 * Phase 3 — colour map. Splits hunted photos into those with Exif GPS (plotted
 * as coloured pins) and those without (listed separately, never crashing).
 */
class MapViewModel(repository: PhotoRepository) : ViewModel() {

    private val activeFilter = MutableStateFlow<ColorBucket?>(null)

    val uiState: StateFlow<MapUiState> =
        combine(repository.photos, activeFilter) { photos, filter ->
            val withGps = photos.filter { it.hasLocation }
            val located = withGps.filter { filter == null || it.bucket == filter }
            val filters = ColorBucket.entries.filter { bucket -> withGps.any { it.bucket == bucket } }
            MapUiState(
                located = located,
                noLocation = photos.filter { !it.hasLocation },
                availableFilters = filters,
                activeFilter = filter,
            )
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), MapUiState())

    fun setFilter(bucket: ColorBucket?) {
        activeFilter.value = bucket
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { MapViewModel(appContainer().photoRepository) }
        }
    }
}
