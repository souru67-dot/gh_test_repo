package com.souru.colorhunt.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.palette.graphics.Palette
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a dominant colour from an image [Uri] using AndroidX Palette.
 *
 * All heavy work (decode + palette) runs off the main thread. Results are cached
 * by uri so re-visiting the grid or rebuilding a collage is instant. Every
 * failure path (missing file, undecodable image, empty palette) returns null
 * instead of throwing, so a bad photo never takes the pipeline down.
 */
class PaletteExtractor(private val context: Context) {

    private val cache = LruCache<String, Int>(CACHE_ENTRIES)

    /** @return packed 0xFFRRGGBB dominant colour, or null if it could not be determined. */
    suspend fun extractDominantColor(uri: Uri): Int? {
        cache.get(uri.toString())?.let { return it }

        val bitmap = decodeDownsampled(uri) ?: return null
        return try {
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).clearFilters().generate()
            }
            pickColor(palette)?.also { cache.put(uri.toString(), it) }
        } catch (t: Throwable) {
            null
        } finally {
            bitmap.recycle()
        }
    }

    private fun pickColor(palette: Palette): Int? {
        // Prefer the visually dominant swatch, then fall back through the
        // themed swatches so we still get a colour for low-contrast photos.
        val swatch = palette.dominantSwatch
            ?: palette.vibrantSwatch
            ?: palette.mutedSwatch
            ?: palette.swatches.maxByOrNull { it.population }
        return swatch?.rgb
    }

    private suspend fun decodeDownsampled(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
            // First pass: bounds only, to compute a sample size.
            val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, bounds)
            } ?: return@withContext null

            if (bounds.outWidth <= 0 || bounds.outHeight <= 0) return@withContext null

            val opts = BitmapFactory.Options().apply {
                inSampleSize = computeSampleSize(bounds.outWidth, bounds.outHeight, TARGET_MAX_DIM)
                inPreferredConfig = Bitmap.Config.ARGB_8888
            }
            context.contentResolver.openInputStream(uri)?.use {
                BitmapFactory.decodeStream(it, null, opts)
            }
        } catch (t: Throwable) {
            null
        }
    }

    private fun computeSampleSize(width: Int, height: Int, targetMax: Int): Int {
        var sample = 1
        var w = width
        var h = height
        while (w / 2 >= targetMax && h / 2 >= targetMax) {
            w /= 2
            h /= 2
            sample *= 2
        }
        return sample
    }

    private companion object {
        const val CACHE_ENTRIES = 512
        // Palette does not need full resolution; a small image is faster and enough.
        const val TARGET_MAX_DIM = 160
    }
}
