package com.souru.lumina.ui.external

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import androidx.work.WorkManager
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.SettingsRepository
import com.souru.lumina.data.external.ExternalDeviceRepository
import com.souru.lumina.data.external.ExternalDeviceState
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.GridSlot
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.data.pairing.PairCandidate
import com.souru.lumina.data.pairing.RawJpegPairer
import com.souru.lumina.util.MediaGrouping
import com.souru.lumina.util.formatDateHeader
import com.souru.lumina.work.ImportWorker
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

sealed interface ExternalBrowseStatus {
    data object NotSelected : ExternalBrowseStatus
    data object Loading : ExternalBrowseStatus
    data object Ready : ExternalBrowseStatus
    data object Disconnected : ExternalBrowseStatus
}

data class ExternalBrowseUiState(
    val status: ExternalBrowseStatus = ExternalBrowseStatus.NotSelected,
    val slots: List<GridSlot> = emptyList(),
    val entries: List<GalleryEntry> = emptyList(),
    val columns: Int = SettingsRepository.DEFAULT_COLUMNS,
    val selection: Set<Long> = emptySet(),
    val message: String? = null,
)

class ExternalBrowseViewModel(
    private val context: Context,
    private val repository: ExternalDeviceRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val _uiState = MutableStateFlow(ExternalBrowseUiState())
    val uiState: StateFlow<ExternalBrowseUiState> = _uiState.asStateFlow()

    /** id -> ペア相手id。取り込み時のペア展開に使う。 */
    private var pairs: Map<Long, Long> = emptyMap()
    private var itemsById: Map<Long, MediaItem> = emptyMap()

    init {
        viewModelScope.launch {
            settingsRepository.gridColumns.collect { columns ->
                _uiState.update { it.copy(columns = columns) }
            }
        }
        refresh()
    }

    /** SAFツリー選択インテント(権限フラグ付き)。 */
    fun openTreeIntent(): android.content.Intent = repository.openTreeIntent()

    fun refresh() {
        viewModelScope.launch {
            _uiState.update { it.copy(status = ExternalBrowseStatus.Loading) }
            val state = withContext(Dispatchers.IO) { repository.scan() }
            applyState(state)
        }
    }

    /** ツリー選択結果を受け取り、永続化して走査する。 */
    fun onTreeSelected(treeUri: Uri) {
        viewModelScope.launch {
            _uiState.update { it.copy(status = ExternalBrowseStatus.Loading) }
            repository.persistTree(treeUri)
            val state = withContext(Dispatchers.IO) { repository.scan() }
            applyState(state)
        }
    }

    private fun applyState(state: ExternalDeviceState) {
        when (state) {
            ExternalDeviceState.NotSelected ->
                _uiState.update {
                    it.copy(status = ExternalBrowseStatus.NotSelected, slots = emptyList(), entries = emptyList())
                }

            ExternalDeviceState.Loading ->
                _uiState.update { it.copy(status = ExternalBrowseStatus.Loading) }

            is ExternalDeviceState.Disconnected ->
                _uiState.update {
                    it.copy(
                        status = ExternalBrowseStatus.Disconnected,
                        slots = emptyList(),
                        entries = emptyList(),
                        selection = emptySet(),
                    )
                }

            is ExternalDeviceState.Ready -> buildGrid(state.items)
        }
    }

    private fun buildGrid(media: List<MediaItem>) {
        val computedPairs = runCatching {
            RawJpegPairer.pair(
                media.filter { it.kind == MediaKind.IMAGE }
                    .map { PairCandidate(it.id, it.baseName, it.dateTakenMs, it.isRaw) },
            )
        }.getOrElse { emptyMap() }
        pairs = computedPairs
        itemsById = media.associateBy { it.id }

        // 「すべて」相当: ペアはJPEG側を代表に統合(RAW側は一覧から除外)
        val byId = itemsById
        val visible = media.filterNot { it.isRaw && computedPairs.containsKey(it.id) }
        val sections = MediaGrouping.byDateDescending(
            items = visible.map { GalleryEntry(it, computedPairs[it.id]?.let(byId::get)) },
            timeOf = { it.item.dateTakenMs },
            dateOf = { it.item.localDate },
        )
        val entries = ArrayList<GalleryEntry>(visible.size)
        val slots = ArrayList<GridSlot>(visible.size + 16)
        for ((date, sectionEntries) in sections) {
            slots += GridSlot.Header(date, formatDateHeader(date))
            for (entry in sectionEntries) {
                slots += GridSlot.Cell(entry, entries.size)
                entries += entry
            }
        }
        _uiState.update {
            it.copy(
                status = ExternalBrowseStatus.Ready,
                slots = slots,
                entries = entries,
                selection = it.selection.filter { id -> byId.containsKey(id) }.toSet(),
            )
        }
    }

    fun setColumns(columns: Int) {
        viewModelScope.launch { settingsRepository.setGridColumns(columns) }
    }

    fun toggleSelection(id: Long) {
        _uiState.update { state ->
            val next = if (id in state.selection) state.selection - id else state.selection + id
            state.copy(selection = next)
        }
    }

    fun clearSelection() = _uiState.update { it.copy(selection = emptySet()) }

    fun selectAll() {
        _uiState.update { state -> state.copy(selection = state.entries.map { it.id }.toSet()) }
    }

    fun consumeMessage() = _uiState.update { it.copy(message = null) }

    /** 選択項目(空なら全件)を Pictures/Lumina/Import へ取り込む。 */
    fun importSelectedOrAll() {
        val state = _uiState.value
        val targetEntries = if (state.selection.isEmpty()) {
            state.entries
        } else {
            state.entries.filter { it.id in state.selection }
        }
        if (targetEntries.isEmpty()) return

        // RAW+JPEGペアは必ず両方をコピーする(片方だけ選んでいても相手を含める)
        val toCopy = LinkedHashMap<Long, MediaItem>()
        for (entry in targetEntries) {
            toCopy[entry.item.id] = entry.item
            entry.counterpart?.let { toCopy[it.id] = it }
            // counterpartに載らないペア相手(RAW側除外で消えたもの)も拾う
            pairs[entry.item.id]?.let { pid -> itemsById[pid]?.let { toCopy[pid] = it } }
        }
        val items = toCopy.values.toList()

        val request = ImportWorker.buildRequest(
            uris = items.map { it.uri.toString() },
            names = items.map { it.displayName },
            mimes = items.map { it.mimeType },
            sizes = items.map { it.sizeBytes },
        )
        WorkManager.getInstance(context).enqueue(request)
        _uiState.update {
            it.copy(
                selection = emptySet(),
                message = "${items.size}件の取り込みを開始しました(進捗は通知に表示)",
            )
        }
    }

    fun disconnect() {
        viewModelScope.launch {
            repository.clearTree()
            _uiState.update {
                it.copy(status = ExternalBrowseStatus.NotSelected, slots = emptyList(), entries = emptyList())
            }
        }
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LuminaApplication
                ExternalBrowseViewModel(
                    context = app,
                    repository = app.container.externalDeviceRepository,
                    settingsRepository = app.container.settingsRepository,
                )
            }
        }
    }
}
