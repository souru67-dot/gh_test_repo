package com.souru.colorhunt.data

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import android.util.LruCache
import androidx.core.graphics.drawable.toBitmap
import androidx.palette.graphics.Palette
import coil.imageLoader
import coil.request.CachePolicy
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.souru.colorhunt.domain.color.Hsv
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * Extracts a representative "theme colour" from an image [Uri] using AndroidX Palette.
 *
 * Decoding goes through **Coil** (the same decoder that renders the thumbnails),
 * so any format the app can display — JPEG/PNG/WEBP/HEIC/AVIF/… — is handled
 * consistently. Earlier a raw `BitmapFactory` path returned null for some device
 * formats (e.g. HEIC), which pushed perfectly good photos into "uncategorized".
 *
 * For a colour-hunting app the useful colour is the **colourful subject**, not the
 * largest region (a blue flower on dark foliage is mostly dark by pixel count),
 * so we pick the most prominent *chromatic* swatch and only fall back to the
 * overall dominant (grey/white/black) when the photo genuinely has no colour.
 *
 * All heavy work runs off the main thread; results are cached by uri. Every
 * failure path returns null instead of throwing.
 */
class PaletteExtractor(private val context: Context) {

    private val cache = LruCache<String, Int>(CACHE_ENTRIES)

    /** @return packed 0xFFRRGGBB representative colour, or null if it could not be determined. */
    suspend fun extractDominantColor(uri: Uri): Int? {
        cache.get(uri.toString())?.let { return it }

        val bitmap = decode(uri, TARGET_MAX_DIM) ?: return null
        return try {
            val palette = withContext(Dispatchers.Default) {
                Palette.from(bitmap).clearFilters().maximumColorCount(MAX_PALETTE_COLORS).generate()
            }
            val color = pickThemeColor(palette) ?: averageColor(bitmap)
            color?.also { cache.put(uri.toString(), it) }
        } catch (t: Throwable) {
            averageColor(bitmap)?.also { cache.put(uri.toString(), it) }
        } finally {
            if (!bitmap.isRecycled) bitmap.recycle()
        }
    }

    /**
     * Score each swatch by area **and** how "theme-worthy" its colour is, then take
     * the best. This beats picking the single most-saturated swatch (which wrongly
     * grabs a tiny vivid sign in an otherwise grey city shot) and picking the most
     * populous swatch (which grabs the dark background of a lit subject). A large
     * muted region wins for a filmic city scene → GRAY; a mid-tone colourful subject
     * wins over a dark background → its true colour.
     */
    private fun pickThemeColor(palette: Palette): Int? {
        val swatches = palette.swatches
        if (swatches.isEmpty()) return null
        return swatches.maxByOrNull { scoreOf(it) }?.rgb
    }

    /**
     * Pastels used to be penalised twice — once for being bright and again for
     * being low-saturation — so a small dark region beat a photo that was
     * mostly pale yellow or pink. The bright penalty now only applies to
     * near-white (a blown highlight, which is what it was for), and the
     * saturation term has a higher floor and a gentler slope so a soft colour
     * still competes with a vivid one.
     */
    private fun scoreOf(swatch: Palette.Swatch): Double {
        val hsv = Hsv.fromColorInt(swatch.rgb)
        val satWeight = SAT_BASE + SAT_GAIN * hsv.saturation
        val valWeight = when {
            hsv.value < DARK_CUTOFF -> DARK_WEIGHT
            hsv.value < MID_START ->
                DARK_WEIGHT + (hsv.value - DARK_CUTOFF) / (MID_START - DARK_CUTOFF) * (1f - DARK_WEIGHT)
            hsv.value > BRIGHT_CUTOFF ->
                if (hsv.saturation < NEAR_WHITE_MAX_SAT) BRIGHT_WEIGHT else PASTEL_WEIGHT
            else -> 1f
        }
        return swatch.population.toDouble() * satWeight * valWeight
    }

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

    /** Decode via Coil to a software bitmap we exclusively own (safe to recycle). */
    private suspend fun decode(uri: Uri, maxDim: Int): Bitmap? = withContext(Dispatchers.IO) {
        try {
            val request = ImageRequest.Builder(context)
                .data(uri)
                .size(maxDim)
                .allowHardware(false) // Palette must read pixels
                .memoryCachePolicy(CachePolicy.DISABLED) // own the bitmap, don't share cache
                .build()
            val result = context.imageLoader.execute(request)
            (result as? SuccessResult)?.drawable?.toBitmap()
        } catch (t: Throwable) {
            null
        }
    }

    private companion object {
        const val CACHE_ENTRIES = 512
        const val TARGET_MAX_DIM = 160
        const val MAX_PALETTE_COLORS = 24

        // Swatch scoring weights (see scoreOf).
        const val SAT_BASE = 0.45f       // floor so a big muted region still competes
        const val SAT_GAIN = 0.75f       // gentle slope: a pastel still beats a tiny vivid patch
        const val DARK_CUTOFF = 0.12f    // below this value → heavily down-weighted
        const val DARK_WEIGHT = 0.2f
        const val MID_START = 0.28f      // value at which weight reaches 1.0
        const val BRIGHT_CUTOFF = 0.92f  // above here: blown highlight, or a pastel
        const val NEAR_WHITE_MAX_SAT = 0.10f // below this a bright colour is just white
        const val BRIGHT_WEIGHT = 0.45f  // near-white (blown highlight) → suppressed
        const val PASTEL_WEIGHT = 0.85f  // bright but coloured → kept in the running
    }
}
