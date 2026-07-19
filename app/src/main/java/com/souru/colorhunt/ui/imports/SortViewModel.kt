package com.souru.colorhunt.ui.imports

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.colorhunt.data.MediaImageSource
import com.souru.colorhunt.data.PhotoRepository
import com.souru.colorhunt.domain.color.ColorBucket
import com.souru.colorhunt.domain.model.AnalysisState
import com.souru.colorhunt.domain.model.HuntPhoto
import com.souru.colorhunt.ui.appContainer
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/** One colour bucket plus the photos sorted into it. */
data class BucketGroup(val bucket: ColorBucket, val photos: List<HuntPhoto>)

data class SortUiState(
    val groups: List<BucketGroup> = emptyList(),
    /** Photos still being analysed (colour not yet known). */
    val processing: List<HuntPhoto> = emptyList(),
    /** Photos whose colour genuinely could not be read. */
    val uncategorized: List<HuntPhoto> = emptyList(),
    val availableFilters: List<ColorBucket> = emptyList(),
    val activeFilter: ColorBucket? = null,
    val selectedIds: Set<String> = emptySet(),
    val analyzingCount: Int = 0,
    val totalCount: Int = 0,
) {
    val isEmpty: Boolean get() = totalCount == 0
    val hasSelection: Boolean get() = selectedIds.isNotEmpty()
}

const val RECENT_IMPORT_LIMIT = 200

class SortViewModel(private val repository: PhotoRepository) : ViewModel() {

    private val activeFilter = MutableStateFlow<ColorBucket?>(null)

    val uiState: StateFlow<SortUiState> =
        combine(repository.photos, repository.selectedIds, activeFilter) { photos, selected, filter ->
            buildState(photos, selected, filter)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SortUiState())

    private fun buildState(
        photos: List<HuntPhoto>,
        selected: Set<String>,
        filter: ColorBucket?,
    ): SortUiState {
        val byBucket = photos.filter { it.bucket != null }.groupBy { it.bucket!! }
        val availableFilters = ColorBucket.entries.filter { byBucket.containsKey(it) }

        val groups = ColorBucket.entries
            .filter { filter == null || it == filter }
            .mapNotNull { bucket -> byBucket[bucket]?.let { BucketGroup(bucket, it) } }

        val showExtras = filter == null
        val processing = if (showExtras) photos.filter { it.analysis == AnalysisState.Pending } else emptyList()
        val uncategorized = if (showExtras) photos.filter { it.analysis == AnalysisState.Failed } else emptyList()

        return SortUiState(
            groups = groups,
            processing = processing,
            uncategorized = uncategorized,
            availableFilters = availableFilters,
            activeFilter = filter,
            selectedIds = selected,
            analyzingCount = photos.count { it.analysis == AnalysisState.Pending },
            totalCount = photos.size,
        )
    }

    fun addPhotos(uris: List<Uri>) = repository.addPhotos(uris)

    /** Grant-photo-access flow: pull the most recent device photos and auto-sort them. */
    fun importRecentDevicePhotos(context: Context, limit: Int = RECENT_IMPORT_LIMIT) {
        viewModelScope.launch {
            val uris = MediaImageSource.recentImages(context.applicationContext, limit)
            repository.addPhotos(uris)
        }
    }

    fun toggleSelection(id: String) = repository.toggleSelection(id)

    /** Manual override: move a photo into the bucket the hunter thinks is right. */
    fun reassignBucket(id: String, bucket: ColorBucket) = repository.reassignBucket(id, bucket)

    fun setFilter(bucket: ColorBucket?) {
        activeFilter.value = bucket
    }

    fun clearAll() {
        activeFilter.value = null
        repository.clearAll()
    }

    companion object {
        val Factory = viewModelFactory {
            initializer { SortViewModel(appContainer().photoRepository) }
        }
    }
}
