package com.souru.colorhunt.data

import android.content.Context
import android.net.Uri
import com.souru.colorhunt.domain.color.ColorClassifier
import com.souru.colorhunt.domain.model.AnalysisState
import com.souru.colorhunt.domain.model.HuntPhoto
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit

/**
 * In-memory store of hunted photos, shared across the Sort and Collage screens.
 *
 * Newly picked photos are appended immediately as [AnalysisState.Pending] so the
 * grid updates without waiting; dominant-colour extraction then streams in from
 * background coroutines with bounded parallelism, so even a large selection
 * keeps the UI responsive. Collage selection is kept here too so it survives
 * navigation between screens.
 */
class PhotoRepository(
    private val extractor: PaletteExtractor,
    private val context: Context,
    private val scope: CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default),
) {
    private val analysisGate = Semaphore(MAX_PARALLEL_ANALYSIS)

    private val _photos = MutableStateFlow<List<HuntPhoto>>(emptyList())
    val photos: StateFlow<List<HuntPhoto>> = _photos.asStateFlow()

    private val _selectedIds = MutableStateFlow<Set<String>>(emptySet())
    val selectedIds: StateFlow<Set<String>> = _selectedIds.asStateFlow()

    /** Append newly picked photos (deduped) and kick off background analysis. */
    fun addPhotos(uris: List<Uri>) {
        val existing = _photos.value.mapTo(HashSet()) { it.uri }
        val fresh = uris.filter { it !in existing }.map { HuntPhoto(uri = it) }
        if (fresh.isEmpty()) return

        _photos.update { it + fresh }
        fresh.forEach { photo -> scope.launch { analyze(photo) } }
    }

    private suspend fun analyze(photo: HuntPhoto) {
        val (color, latLng) = analysisGate.withPermit {
            val c = runCatching { extractor.extractDominantColor(photo.uri) }.getOrNull()
            val ll = runCatching { ExifReader.readLatLng(context, photo.uri) }.getOrNull()
            c to ll
        }
        val updated = if (color != null) {
            photo.copy(
                dominantColor = color,
                bucket = ColorClassifier.classify(color),
                latitude = latLng?.first,
                longitude = latLng?.second,
                analysis = AnalysisState.Done,
            )
        } else {
            photo.copy(
                latitude = latLng?.first,
                longitude = latLng?.second,
                analysis = AnalysisState.Failed,
            )
        }
        _photos.update { list -> list.map { if (it.id == updated.id) updated else it } }
    }

    fun toggleSelection(id: String) {
        _selectedIds.update { if (id in it) it - id else it + id }
    }

    fun clearSelection() {
        _selectedIds.value = emptySet()
    }

    /** Photos currently selected for the collage, in stable grid order. */
    fun selectedPhotos(): List<HuntPhoto> {
        val ids = _selectedIds.value
        return _photos.value.filter { it.id in ids }
    }

    fun clearAll() {
        _photos.value = emptyList()
        _selectedIds.value = emptySet()
    }

    private companion object {
        // Cap concurrent decodes so a huge multi-select can't exhaust memory/CPU.
        const val MAX_PARALLEL_ANALYSIS = 4
    }
}
