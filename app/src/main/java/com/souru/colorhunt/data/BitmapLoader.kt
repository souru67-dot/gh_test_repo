package com.souru.colorhunt.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.core.graphics.drawable.toBitmap
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Decodes image [Uri]s into downsampled, correctly-oriented software bitmaps for
 * collage rendering.
 *
 * Uses Coil — the same decoder that renders the thumbnails — so every format the
 * app can display is handled (HEIC/AVIF included) and Exif orientation is applied
 * automatically. Returns a bitmap the caller exclusively owns (memory cache
 * disabled), safe to recycle. Never throws.
 */
object BitmapLoader {

    suspend fun load(context: Context, uri: Uri, maxDim: Int): Bitmap? =
        withContext(Dispatchers.IO) {
            try {
                val request = ImageRequest.Builder(context)
                    .data(uri)
                    .size(maxDim)
                    .allowHardware(false) // drawn onto a Canvas, must be software
                    .memoryCachePolicy(CachePolicy.DISABLED)
                    .build()
                val result = context.imageLoader.execute(request)
                (result as? SuccessResult)?.drawable?.toBitmap()
            } catch (t: Throwable) {
                null
            }
        }
}
