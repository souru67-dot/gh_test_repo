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
import com.souru.lumina.data.model.GalleryFilter
import com.souru.lumina.data.model.GridSlot
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.data.model.MediaTypeFilter
import com.souru.lumina.data.model.RawFilterMode
import com.souru.lumina.data.pairing.PairCandidate
import com.souru.lumina.data.pairing.RawJpegPairer
import com.souru.lumina.util.MediaGrouping
import com.souru.lumina.util.formatDateHeader
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

data class GalleryUiState(
    val loading: Boolean = true,
    val slots: List<GridSlot> = emptyList(),
    val entries: List<GalleryEntry> = emptyList(),
    val slotIndexOfEntry: Map<Long, Int> = emptyMap(),
    val filter: GalleryFilter = GalleryFilter.DEFAULT,
    val columns: Int = SettingsRepository.DEFAULT_COLUMNS,
    val loadError: Boolean = false,
)

/** メディア一覧とペアリング結果。ペアリングはメディア変更時のみ再計算する。 */
private data class PairedMedia(
    val media: List<MediaItem>,
    val pairs: Map<Long, Long>,
)

class GalleryViewModel(
    private val mediaRepository: MediaRepository,
    private val settingsRepository: SettingsRepository,
) : ViewModel() {

    private val selection = MutableStateFlow<Set<Long>>(emptySet())

    /** 選択状態はグリッド構造と独立して流す(選択のたびのスロット再構築を避ける)。 */
    val selectionFlow: StateFlow<Set<Long>> = selection.asStateFlow()

    private val pairedMedia: Flow<PairedMedia> = mediaRepository.observeMedia()
        .map { media ->
            // ペアリング失敗が一覧全体を巻き込まないよう防御する
            val pairs = runCatching {
                RawJpegPairer.pair(
                    media.filter { it.kind == MediaKind.IMAGE }
                        .map { PairCandidate(it.id, it.baseName, it.dateTakenMs, it.isRaw) },
                )
            }.getOrElse { emptyMap() }
            PairedMedia(media, pairs)
        }
        .flowOn(Dispatchers.Default)

    val uiState: StateFlow<GalleryUiState> =
        combine(
            pairedMedia,
            settingsRepository.galleryFilter,
            settingsRepository.gridColumns,
            mediaRepository.loadError,
        ) { paired, filter, columns, loadError ->
            // 状態構築のどんな失敗も「起動できない」に波及させない
            runCatching { buildState(paired, filter, columns, loadError) }
                .getOrElse { GalleryUiState(loading = false, loadError = true) }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), GalleryUiState())

    private fun buildState(
        paired: PairedMedia,
        filter: GalleryFilter,
        columns: Int,
        loadError: Boolean,
    ): GalleryUiState {
        val byId = paired.media.associateBy { it.id }

        // 動画は種別フィルタのみ、写真形式フィルタは写真にのみ作用する
        val visible = paired.media.filter { item ->
            filter.matches(
                isVideo = item.kind == MediaKind.VIDEO,
                isRaw = item.isRaw,
                isJpeg = item.isJpeg,
            )
        }

        // SQLのソート(生DATE_TAKEN)と表示日付(DATE_ADDEDフォールバック)は
        // 一致しないことがある(動画はDATE_TAKENがnullのことが多い)。
        // 並び順に依存せず日付ごとに必ず1セクションになるようgroupByで構築し、
        // LazyGridのキー重複(クラッシュ)を構造的に防ぐ
        val sections = MediaGrouping.byDateDescending(
            items = visible.map { GalleryEntry(it, paired.pairs[it.id]?.let(byId::get)) },
            timeOf = { it.item.dateTakenMs },
            dateOf = { it.item.localDate },
        )

        val entries = ArrayList<GalleryEntry>(visible.size)
        val slots = ArrayList<GridSlot>(visible.size + 32)
        val slotIndexOfEntry = HashMap<Long, Int>(visible.size * 2)
        for ((date, sectionEntries) in sections) {
            slots += GridSlot.Header(date, formatDateHeader(date))
            for (entry in sectionEntries) {
                slotIndexOfEntry[entry.id] = slots.size
                slots += GridSlot.Cell(entry, entries.size)
                entries += entry
            }
        }

        return GalleryUiState(
            loading = false,
            slots = slots,
            entries = entries,
            slotIndexOfEntry = slotIndexOfEntry,
            filter = filter,
            columns = columns,
            loadError = loadError,
        )
    }

    /** 権限付与後・エラー後の手動再読み込み。 */
    fun retryLoad() {
        mediaRepository.refresh()
    }

    fun setColumns(columns: Int) {
        viewModelScope.launch { settingsRepository.setGridColumns(columns) }
    }

    fun setFilter(mode: RawFilterMode) {
        viewModelScope.launch { settingsRepository.setRawFilter(mode) }
    }

    fun setTypeFilter(filter: MediaTypeFilter) {
        viewModelScope.launch { settingsRepository.setMediaTypeFilter(filter) }
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
