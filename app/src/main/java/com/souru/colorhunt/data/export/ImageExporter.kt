package com.souru.colorhunt.data.export

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

/**
 * Saves a bitmap to the device gallery via MediaStore, under Pictures/ColorHunt.
 *
 * On API 29+ this needs no permission (scoped storage, RELATIVE_PATH + pending
 * flag). On API 26–28 it writes through the legacy external path, which requires
 * WRITE_EXTERNAL_STORAGE (declared with maxSdkVersion=28 and requested at runtime
 * by the caller before invoking this).
 */
object ImageExporter {

    private const val ALBUM = "ColorHunt"

    suspend fun saveToGallery(
        context: Context,
        bitmap: Bitmap,
        displayName: String,
    ): Result<Uri> = withContext(Dispatchers.IO) {
        try {
            val uri = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                saveScoped(context, bitmap, displayName)
            } else {
                saveLegacy(context, bitmap, displayName)
            }
            if (uri != null) Result.success(uri) else Result.failure(IllegalStateException("insert failed"))
        } catch (t: Throwable) {
            Result.failure(t)
        }
    }

    private fun saveScoped(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val resolver = context.contentResolver
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/" + ALBUM)
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values) ?: return null
        resolver.openOutputStream(uri)?.use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }
            ?: run { resolver.delete(uri, null, null); return null }
        values.clear()
        values.put(MediaStore.Images.Media.IS_PENDING, 0)
        resolver.update(uri, values, null, null)
        return uri
    }

    @Suppress("DEPRECATION")
    private fun saveLegacy(context: Context, bitmap: Bitmap, displayName: String): Uri? {
        val dir = File(
            Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES),
            ALBUM,
        )
        if (!dir.exists()) dir.mkdirs()
        val file = File(dir, "$displayName.png")
        FileOutputStream(file).use { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }

        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/png")
            put(MediaStore.Images.Media.DATA, file.absolutePath)
        }
        return context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values)
    }
}
