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
 * we try that first and fall back to the plain stream.
 *
 * Photo Picker URIs are redacted by the system too, so manually picked photos
 * would never map. When the app also holds the photo-library permission we can
 * resolve the picker URI back to its MediaStore original via
 * [MediaStore.getMediaUri] and read GPS from there — so "写真を選ぶ" photos get
 * locations as long as photo access was granted. Without that permission they
 * legitimately return null and land in the "no location" list. Never throws.
 */
object ExifReader {

    /** @return latitude to longitude, or null when unavailable. */
    suspend fun readLatLng(context: Context, uri: Uri): Pair<Double, Double>? =
        withContext(Dispatchers.IO) {
            val original = mediaStoreOriginal(context, uri)
            (original?.let { readFrom(context, requireOriginal(it)) })
                ?: readFrom(context, requireOriginal(uri))
                ?: readFrom(context, uri)
        }

    /** Resolve a Photo Picker URI back to its (un-redacted-able) MediaStore URI, if possible. */
    private fun mediaStoreOriginal(context: Context, uri: Uri): Uri? = try {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q && uri.authority == MediaStore.AUTHORITY) {
            null // already a MediaStore uri
        } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.getMediaUri(context, uri)
        } else {
            null
        }
    } catch (t: Throwable) {
        null
    }

    private fun requireOriginal(uri: Uri): Uri = try {
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
