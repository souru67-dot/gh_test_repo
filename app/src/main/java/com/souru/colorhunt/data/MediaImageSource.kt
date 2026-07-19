package com.souru.colorhunt.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Reads image URIs straight from the device gallery via MediaStore, for the
 * "grant photo access → auto-sort" flow. Requires READ_MEDIA_IMAGES (API 33+)
 * or READ_EXTERNAL_STORAGE (API <= 32); the caller requests the permission.
 *
 * Returns the most-recent images first so the newest hunt shows up immediately.
 * Never throws — returns an empty list on any error.
 */
object MediaImageSource {

    suspend fun recentImages(context: Context, limit: Int): List<Uri> =
        withContext(Dispatchers.IO) {
            val result = ArrayList<Uri>(limit)
            try {
                val collection = MediaStore.Images.Media.EXTERNAL_CONTENT_URI
                val projection = arrayOf(MediaStore.Images.Media._ID)
                val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
                context.contentResolver.query(collection, projection, null, null, sortOrder)?.use { cursor ->
                    val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID)
                    while (cursor.moveToNext() && result.size < limit) {
                        val id = cursor.getLong(idColumn)
                        result.add(ContentUris.withAppendedId(collection, id))
                    }
                }
            } catch (t: Throwable) {
                // Permission revoked mid-query, provider error, etc. — return what we have.
            }
            result
        }
}
