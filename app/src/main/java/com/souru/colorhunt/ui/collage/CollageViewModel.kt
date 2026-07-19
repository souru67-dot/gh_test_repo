package com.souru.colorhunt.ui.collage

import android.app.Application
import android.content.Context
import android.graphics.Bitmap
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.ViewModelProvider.AndroidViewModelFactory.Companion.APPLICATION_KEY
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.souru.colorhunt.ColorHuntApplication
import com.souru.colorhunt.data.BitmapLoader
import com.souru.colorhunt.data.PhotoRepository
import com.souru.colorhunt.data.export.CollageRenderer
import com.souru.colorhunt.data.export.ImageExporter
import com.souru.colorhunt.data.export.ShareHelper
import com.souru.colorhunt.domain.color.Hsv
import com.souru.colorhunt.domain.config.CellCountPreset
import com.souru.colorhunt.domain.config.CollageStyle
import com.souru.colorhunt.domain.config.FeatureFlags
import com.souru.colorhunt.domain.config.SnsSize
import com.souru.colorhunt.domain.model.HuntPhoto
import com.souru.colorhunt.domain.pro.ProState
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** One-shot events the screen reacts to (snackbars, launching the share sheet). */
sealed interface CollageEvent {
    data object Saved : CollageEvent
    data object SaveFailed : CollageEvent
    data object SaveNeedsPermission : CollageEvent
    data class ShareReady(val uri: android.net.Uri) : CollageEvent
    data object ShareFailed : CollageEvent
}

data class CollageUiState(
    val loading: Boolean = true,
    val photoCount: Int = 0,
    val size: SnsSize = SnsSize.DEFAULT,
    val countPreset: CellCountPreset = CellCountPreset.CUSTOM,
    val cellCount: Int = 0,
    val style: CollageStyle = CollageStyle(),
    val preview: Bitmap? = null,
    val rendering: Boolean = false,
    val isPro: Boolean = FeatureFlags.isPro,
) {
    val isEmpty: Boolean get() = !loading && photoCount == 0
}

@OptIn(FlowPreview::class)
class CollageViewModel(
    application: Application,
    private val repository: PhotoRepository,
) : AndroidViewModel(application) {

    private val appContext: Context get() = getApplication()

    // Source bitmaps, decoded once, keyed by photo id.
    private val bitmaps = LinkedHashMap<String, Bitmap>()
    private var photos: List<HuntPhoto> = emptyList()

    private val size = MutableStateFlow(SnsSize.DEFAULT)
    private val countPreset = MutableStateFlow(CellCountPreset.CUSTOM)
    private val style = MutableStateFlow(CollageStyle())
    private val order = MutableStateFlow<List<String>>(emptyList())
    /** Bumped to force a re-render when nothing observable changed (e.g. bitmaps finished decoding). */
    private val renderTick = MutableStateFlow(0)

    private val preview = MutableStateFlow<Bitmap?>(null)
    private val rendering = MutableStateFlow(false)
    private val loading = MutableStateFlow(true)

    private val _events = MutableSharedFlow<CollageEvent>(
        extraBufferCapacity = 4,
        onBufferOverflow = BufferOverflow.DROP_OLDEST,
    )
    val events: SharedFlow<CollageEvent> = _events

    val uiState: StateFlow<CollageUiState> = combine(
        combine(size, countPreset, style, order) { s, c, st, o -> Spec(s, c, st, o) },
        preview, rendering, loading, ProState.isPro,
    ) { spec, prev, isRendering, isLoading, isPro ->
        CollageUiState(
            loading = isLoading,
            photoCount = spec.order.size,
            size = spec.size,
            countPreset = spec.countPreset,
            cellCount = resolveCellCount(spec.countPreset, spec.order.size),
            style = spec.style,
            preview = prev,
            rendering = isRendering,
            isPro = isPro,
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), CollageUiState())

    init {
        observeSelection()
        observeAndRender()
        // Re-render (e.g. drop the watermark) the moment Pro status changes.
        viewModelScope.launch { ProState.isPro.collect { renderTick.value += 1 } }
    }

    /**
     * Keep the collage in sync with the live selection: still-selected photos keep
     * their manual order, newly selected ones append, deselected ones drop out.
     * This fixes the "nothing selected" flicker where a one-shot snapshot went stale.
     */
    private fun observeSelection() {
        viewModelScope.launch {
            combine(repository.selectedIds, repository.photos) { ids, ph -> ids to ph }
                .collect { (ids, ph) ->
                    photos = ph
                    val known = order.value
                    val knownSet = known.toHashSet()
                    val stillSelected = known.filter { it in ids }
                    val newlySelected = ids.filter { it !in knownSet }
                    val next = stillSelected + newlySelected
                    if (next != known) order.value = next
                    ensureBitmaps(next, ph)
                    loading.value = false
                    renderTick.value += 1
                }
        }
    }

    private suspend fun ensureBitmaps(ids: List<String>, photos: List<HuntPhoto>) {
        withContext(Dispatchers.IO) {
            ids.forEach { id ->
                if (!bitmaps.containsKey(id)) {
                    val uri = photos.firstOrNull { it.id == id }?.uri ?: return@forEach
                    BitmapLoader.load(appContext, uri, SOURCE_MAX_DIM)?.let { bitmaps[id] = it }
                }
            }
        }
    }

    /** Re-render the preview whenever any input changes (debounced so sliders stay smooth). */
    private fun observeAndRender() {
        viewModelScope.launch {
            combine(size, countPreset, style, order, renderTick) { s, c, st, o, _ -> Spec(s, c, st, o) }
                .debounce(90)
                .collectLatest { spec ->
                    rendering.value = true
                    preview.value = renderCollage(
                        spec.size,
                        resolveCellCount(spec.countPreset, spec.order.size),
                        spec.style,
                        spec.order,
                    )
                    rendering.value = false
                }
        }
    }

    private suspend fun renderCollage(
        size: SnsSize,
        cellCount: Int,
        style: CollageStyle,
        order: List<String>,
    ): Bitmap = withContext(Dispatchers.Default) {
        val cells: List<Bitmap?> = (0 until cellCount).map { order.getOrNull(it)?.let { id -> bitmaps[id] } }
        val bg = if (style.backgroundFollowsTheme) themeColor() else style.backgroundColor
        val density = size.exportWidth / REFERENCE_WIDTH_DP
        val paletteColors = if (style.paletteStrip && FeatureFlags.isPro) {
            (0 until cellCount).mapNotNull { i -> order.getOrNull(i)?.let { id -> colorFor(id) } }
        } else {
            emptyList()
        }
        CollageRenderer.render(
            cells = cells,
            cellCount = cellCount,
            style = style.copy(backgroundColor = bg),
            widthPx = size.exportWidth,
            heightPx = size.exportHeight,
            density = density,
            addWatermark = !FeatureFlags.isPro,
            paletteColors = paletteColors,
        )
    }

    // --- User actions -------------------------------------------------------

    fun setSize(newSize: SnsSize) {
        if (newSize.proOnly && !FeatureFlags.isPro) return
        size.value = newSize
    }

    fun setCountPreset(preset: CellCountPreset) {
        countPreset.value = preset
    }

    fun updateStyle(transform: (CollageStyle) -> CollageStyle) {
        style.value = transform(style.value)
    }

    /** Reorder cells so photos run around the colour wheel (spec: 色相グラデーション自動整列). */
    fun sortByHue() {
        val byId = photos.associateBy { it.id }
        order.value = order.value.sortedBy { id ->
            byId[id]?.dominantColor?.let { Hsv.fromColorInt(it).hue } ?: Float.MAX_VALUE
        }
    }

    /** Drag-rearrange a cell from one position to another. */
    fun move(from: Int, to: Int) {
        val list = order.value
        if (from !in list.indices || to !in list.indices || from == to) return
        val mutable = list.toMutableList()
        mutable.add(to, mutable.removeAt(from))
        order.value = mutable
    }

    fun save() {
        viewModelScope.launch {
            if (needsLegacyStoragePermission()) {
                _events.emit(CollageEvent.SaveNeedsPermission)
                return@launch
            }
            performSave()
        }
    }

    /** Called by the screen after the user grants the legacy storage permission. */
    fun onStoragePermissionGranted() {
        viewModelScope.launch { performSave() }
    }

    private suspend fun performSave() {
        val bmp = currentExportBitmap()
        val result = ImageExporter.saveToGallery(appContext, bmp, defaultFileName())
        _events.emit(if (result.isSuccess) CollageEvent.Saved else CollageEvent.SaveFailed)
    }

    fun share() {
        viewModelScope.launch {
            val bmp = currentExportBitmap()
            val uri = ShareHelper.cacheForShare(appContext, bmp, defaultFileName())
            _events.emit(if (uri != null) CollageEvent.ShareReady(uri) else CollageEvent.ShareFailed)
        }
    }

    private suspend fun currentExportBitmap(): Bitmap =
        renderCollage(size.value, resolveCellCount(countPreset.value, order.value.size), style.value, order.value)

    private fun colorFor(id: String): Int? = photos.firstOrNull { it.id == id }?.dominantColor

    private fun resolveCellCount(preset: CellCountPreset, photoCount: Int): Int {
        val raw = preset.count ?: photoCount
        val capped = if (FeatureFlags.isPro) raw else raw.coerceAtMost(FeatureFlags.FREE_MAX_COLLAGE_CELLS)
        return capped.coerceAtLeast(1)
    }

    private fun themeColor(): Int = try {
        val ta = appContext.theme.obtainStyledAttributes(intArrayOf(android.R.attr.colorPrimary))
        val color = ta.getColor(0, 0xFFFFFFFF.toInt())
        ta.recycle()
        color
    } catch (t: Throwable) {
        0xFFFFFFFF.toInt()
    }

    private fun needsLegacyStoragePermission(): Boolean =
        android.os.Build.VERSION.SDK_INT < android.os.Build.VERSION_CODES.Q &&
            appContext.checkSelfPermission(android.Manifest.permission.WRITE_EXTERNAL_STORAGE) !=
            android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun defaultFileName(): String = "ColorHunt_" + System.currentTimeMillis()

    override fun onCleared() {
        super.onCleared()
        bitmaps.values.forEach { if (!it.isRecycled) it.recycle() }
        bitmaps.clear()
    }

    private data class Spec(
        val size: SnsSize,
        val countPreset: CellCountPreset,
        val style: CollageStyle,
        val order: List<String>,
    )

    companion object {
        private const val SOURCE_MAX_DIM = 1080
        private const val REFERENCE_WIDTH_DP = 360f

        val Factory = viewModelFactory {
            initializer {
                val app = this[APPLICATION_KEY] as ColorHuntApplication
                CollageViewModel(app, app.container.photoRepository)
            }
        }
    }
}
