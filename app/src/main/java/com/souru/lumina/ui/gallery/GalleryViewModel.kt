package com.souru.lumina.ui.gallery

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.MediaRepository
import com.souru.lumina.data.SettingsRepository
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.GridSlot
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.RawFilterMode
import com.souru.lumina.util.formatDateHeader
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GalleryUiState(
    val loading: Boolean = true,
    val slots: List<GridSlot> = emptyList(),
    val entries: List<GalleryEntry> = emptyList(),
    val slotIndexOfEntry: Map<Long, Int> = emptyMap(),
    val filter: RawFilterMode = RawFilterMode.JPEG,
    val columns: Int = SettingsRepository.DEFAULT_COLUMNS,
    val selection: Set<Long> = emptySet(),
) {
    val selectionMode: Boolean get() = selection.isNotEmpty()
}

class GalleryViewModel(
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val selection = MutableStateFlow<Set<Long>>(emptySet())
    val selectionFlow: StateFlow<Set<Long>> = selection.asStateFlow()

    val uiState: StateFlow<GalleryUiState> =
        combine(
            mediaRepository.observeMedia(),
            settingsRepository.rawFilter,
            settingsRepository.gridColumns,
            selection,
        ) { media, filter, columns, selected ->
            buildState(media, filter, columns, selected)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    private fun buildState(
        media: List<MediaItem>,
        filter: RawFilterMode,
        columns: Int,
        selected: Set<Long>,
    ): GalleryUiState {
        // フェーズ2でRAW+JPEGペアリングとフィルタを適用する。現状は全件表示。
        val entries = media.map { GalleryEntry(it) }

        val slots = ArrayList<GridSlot>(entries.size + 32)
        val slotIndexOfEntry = HashMap<Long, Int>(entries.size * 2)
        var entryIndex = 0
        var lastDate: java.time.LocalDate? = null
        for (entry in entries) {
            val date = entry.item.localDate
            if (date != lastDate) {
                slots += GridSlot.Header(date, formatDateHeader(date))
                lastDate = date
            }
            slotIndexOfEntry[entry.id] = slots.size
            slots += GridSlot.Cell(entry, entryIndex)
            entryIndex++
        }

        return GalleryUiState(
            loading = false,
            slots = slots,
            entries = entries,
            slotIndexOfEntry = slotIndexOfEntry,
            filter = filter,
            columns = columns,
            selection = selected.takeIf { sel -> sel.isEmpty() || entries.any { it.id in sel } }
                ?: emptySet(),
        )
    }

    fun setColumns(columns: Int) {
        viewModelScope.launch { settingsRepository.setGridColumns(columns) }
    }

    fun setFilter(mode: RawFilterMode) {
        viewModelScope.launch { settingsRepository.setRawFilter(mode) }
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
                GalleryViewModel(
                    mediaRepository = app.container.mediaRepository,
                    settingsRepository = app.container.settingsRepository,
                )
            }
        }
    }
}
