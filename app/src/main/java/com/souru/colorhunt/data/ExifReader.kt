package com.souru.colorhunt.data

import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads a photo's Exif GPS coordinates (Phase 3 color map).
 *
 * On API 29+ the location is redacted from MediaStore images unless we ask for
 * the original via [MediaStore.setRequireOriginal] AND hold ACCESS_MEDIA_LOCATION;
 * we try that first and fall back to the plain stream. Photo Picker URIs are
 * always redacted by the system, so those legitimately return null and land in
 * the "no location" list. Never throws.
 */
object ExifReader {

    /** @return latitude to longitude, or null when unavailable. */
    suspend fun readLatLng(context: Context, uri: Uri): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            readFrom(context, requireOriginal(context, uri)) ?: readFrom(context, uri)
        }

    private fun requireOriginal(context: Context, uri: Uri): Uri = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.setRequireOriginal(uri)
        } else {
            uri
        }
    } catch (t: Throwable) {
        uri
    }

    private fun readFrom(context: Context, uri: Uri): Pair<Double, Double>? = try {
        context.contentResolver.openInputStream(uri)?.use { stream ->
            val latLong = ExifInterface(stream).latLong
            if (latLong != null && latLong.size == 2) latLong[0] to latLong[1] else null
        }
    } catch (t: Throwable) {
        null
    }
}
