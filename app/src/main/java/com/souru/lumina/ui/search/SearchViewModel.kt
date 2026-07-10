package com.souru.lumina.ui.search

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.lumina.LuminaApplication
import com.souru.lumina.data.MediaRepository
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.stateIn

data class SearchUiState(
    val query: String = "",
    val results: List<GalleryEntry> = emptyList(),
    /** 空クエリ(=検索前のプロンプト表示)か。 */
    val idle: Boolean = true,
)

/**
 * ローカル検索。ファイル名 / アルバム名 / 年月 / 形式 をスペース区切りの
 * トークンで AND 検索する。全件はすでにメモリ上のリストなので、
 * デバウンス後に Default ディスパッチャで線形フィルタするだけで十分速い。
 *
 * 年月の書式: "2024"(年)、"2024/5"・"2024-5"・"2024年5月"(年+月)、"5月"(月のみ)
 * 形式キーワード: raw/dng, jpeg/jpg, png, heic, 動画/video/mp4, 写真/photo
 */
class SearchViewModel(mediaRepository: MediaRepository) : ViewModel() {

    private val query = MutableStateFlow("")
    val queryText: StateFlow<String> = query.asStateFlow()

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    val uiState: StateFlow<SearchUiState> =
        combine(
            mediaRepository.observeMedia(),
            query.debounce(200),
        ) { items, q ->
            val trimmed = q.trim()
            if (trimmed.isEmpty()) {
                SearchUiState(query = q, idle = true)
            } else {
                val tokens = trimmed.split(Regex("\\s+"))
                val matched = items.filter { item -> tokens.all { matches(item, it) } }
                SearchUiState(
                    query = q,
                    results = matched.map { GalleryEntry(it) },
                    idle = false,
                )
            }
        }
            .flowOn(Dispatchers.Default)
            .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), SearchUiState())

    fun setQuery(value: String) {
        query.value = value
    }

    private fun matches(item: MediaItem, token: String): Boolean {
        val t = token.lowercase()

        // 形式キーワード
        when (t) {
            "raw", "dng" -> return item.isRaw
            "jpeg", "jpg" -> return item.isJpeg
            "png" -> return item.mimeType.equals("image/png", ignoreCase = true)
            "heic", "heif" -> return item.mimeType.lowercase().let {
                it == "image/heic" || it == "image/heif"
            }
            "動画", "video", "mp4" -> return item.kind == MediaKind.VIDEO
            "写真", "photo", "image" -> return item.kind == MediaKind.IMAGE
        }

        // 年月
        parseYearMonth(t)?.let { (year, month) ->
            val date = item.localDate
            val yearOk = year == null || date.year == year
            val monthOk = month == null || date.monthValue == month
            return yearOk && monthOk
        }

        // ファイル名 / アルバム名
        return item.displayName.contains(token, ignoreCase = true) ||
            (item.bucketName?.contains(token, ignoreCase = true) == true)
    }

    /**
     * 年月トークンの解釈。該当しない文字列は null(=名前検索へフォールバック)。
     *  "2024"→(2024,null) / "2024/5"・"2024-5"・"2024年5月"→(2024,5) / "5月"→(null,5)
     */
    private fun parseYearMonth(t: String): Pair<Int?, Int?>? {
        Regex("^(\\d{4})[/年-](\\d{1,2})月?$").find(t)?.let { m ->
            val month = m.groupValues[2].toInt()
            if (month in 1..12) return m.groupValues[1].toInt() to month
        }
        Regex("^(\\d{4})年?$").find(t)?.let { m ->
            val year = m.groupValues[1].toInt()
            if (year in 1990..2100) return year to null
        }
        Regex("^(\\d{1,2})月$").find(t)?.let { m ->
            val month = m.groupValues[1].toInt()
            if (month in 1..12) return null to month
        }
        return null
    }

    companion object {
        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as LuminaApplication
                SearchViewModel(mediaRepository = app.container.mediaRepository)
            }
        }
    }
}
