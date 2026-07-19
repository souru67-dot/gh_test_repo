package com.souru.colorhunt.domain.model

import android.net.Uri
import com.souru.colorhunt.domain.color.ColorBucket

/** Where a photo is in the background analysis pipeline. */
enum class AnalysisState { Pending, Done, Failed }

/**
 * A single hunted photo and everything we derive from it.
 *
 * Created immediately (as [AnalysisState.Pending]) when the user picks images so
 * the grid can show them right away; the dominant colour, [bucket] and — from
 * Phase 3 — GPS are filled in by background workers. Any of those may stay null
 * if extraction fails, and the UI must tolerate that rather than crash.
 */
data class HuntPhoto(
    val uri: Uri,
    val dominantColor: Int? = null,
    val bucket: ColorBucket? = null,
    val latitude: Double? = null,
    val longitude: Double? = null,
    val analysis: AnalysisState = AnalysisState.Pending,
) {
    /** Stable identity for list keys and selection sets. */
    val id: String get() = uri.toString()

    val hasLocation: Boolean get() = latitude != null && longitude != null
}
