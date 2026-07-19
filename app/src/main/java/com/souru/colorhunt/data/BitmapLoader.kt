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
                val decoded = (result as? SuccessResult)?.drawable?.toBitmap() ?: return@withContext null
                // Own an independent software copy: Coil's bitmap pool may recycle/reuse
                // the returned bitmap once the request ends, which would otherwise turn
                // cells we hold and draw later into black (recycled) images.
                if (decoded.isRecycled) null
                else decoded.copy(Bitmap.Config.ARGB_8888, false)
            } catch (t: Throwable) {
                null
            }
        }
}
