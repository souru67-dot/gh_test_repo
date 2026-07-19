package com.souru.colorhunt.data

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.util.LruCache
import androidx.palette.graphics.Palette
import com.souru.colorhunt.domain.color.Hsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a representative "theme colour" from an image [Uri] using AndroidX Palette.
 *
 * For a colour-hunting app the useful colour is the **colourful subject**, not the
 * largest region: a blue hydrangea shot against dark foliage is mostly dark by
 * pixel count, so Palette's population-based `dominantSwatch` would return the
 * near-black background. Instead we pick the most prominent *chromatic* swatch
 * (enough saturation, not too dark/blown-out) and only fall back to the overall
 * dominant (grey/white/black) when the photo genuinely has no colour.
 *
 * All heavy work runs off the main thread; results are cached by uri. Every
 * failure path returns null instead of throwing, and a last-resort average keeps
 * decodable-but-awkward photos out of the "uncategorized" bucket.
 */
class PaletteExtractor(private val context: Context) {

    private val cache = LruCache<String, Int>(CACHE_ENTRIES)

    /** @return packed 0xFFRRGGBB representative colour, or null if it could not be determined. */
    suspend fun extractDominantColor(uri: Uri): Int? {
        cache.get(uri.toString())?.let { return it }

        val bitmap = decodeDownsampled(uri) ?: return null
        return try {
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).clearFilters().maximumColorCount(MAX_PALETTE_COLORS).generate()
            }
            val color = pickThemeColor(palette) ?: averageColor(bitmap)
            color?.also { cache.put(uri.toString(), it) }
        } catch (t: Throwable) {
            // Palette can throw on odd inputs; still try a plain average before giving up.
            averageColor(bitmap)?.also { cache.put(uri.toString(), it) }
        } finally {
            bitmap.recycle()
        }
    }

    /**
     * Prefer the most prominent chromatic swatch; only use an achromatic one when
     * the whole image is essentially grey/white/black.
     */
    private fun pickThemeColor(palette: Palette): Int? {
        val swatches = palette.swatches
        if (swatches.isEmpty()) return null

        val colorful = swatches.filter { swatch ->
            val hsv = Hsv.fromColorInt(swatch.rgb)
            hsv.saturation >= SUBJECT_MIN_SATURATION &&
                hsv.value in SUBJECT_MIN_VALUE..SUBJECT_MAX_VALUE
        }
        val pick = colorful.maxByOrNull { it.population }
            ?: swatches.maxByOrNull { it.population }
        return pick?.rgb
    }

    /** Last-resort average colour over the (already small) bitmap. */
    private fun averageColor(bitmap: Bitmap): Int? {
        if (bitmap.isRecycled || bitmap.width == 0 || bitmap.height == 0) return null
        val step = 4
        var r = 0L; var g = 0L; var b = 0L; var n = 0L
        var y = 0
        while (y < bitmap.height) {
            var x = 0
            while (x < bitmap.width) {
                val c = bitmap.getPixel(x, y)
                r += (c shr 16) and 0xFF
                g += (c shr 8) and 0xFF
                b += c and 0xFF
                n++
                x += step
            }
            y += step
        }
        if (n == 0L) return null
        return (0xFF shl 24) or ((r / n).toInt() shl 16) or ((g / n).toInt() shl 8) or (b / n).toInt()
    }

    private suspend fun decodeDownsampled(uri: Uri): Bitmap? = withContext(Dispatchers.IO) {
        try {
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
        const val TARGET_MAX_DIM = 160
        const val MAX_PALETTE_COLORS = 24

        // What counts as a "colourful subject" swatch rather than background.
        const val SUBJECT_MIN_SATURATION = 0.18f
        const val SUBJECT_MIN_VALUE = 0.20f
        const val SUBJECT_MAX_VALUE = 0.96f
    }
}
