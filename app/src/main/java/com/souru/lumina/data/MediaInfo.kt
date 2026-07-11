package com.souru.lumina.data

import android.content.Context
import android.media.MediaMetadataRetriever
import androidx.exifinterface.media.ExifInterface
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.util.formatDuration
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale
import kotlin.math.abs
import kotlin.math.roundToInt
import kotlin.math.roundToLong

/**
 * ビューアの情報シートに表示するメタデータ。取得できなかった項目はnull
 * (行ごと非表示にする)。
 */
data class MediaInfo(
    val fileName: String,
    val format: String,
    val resolution: String?,
    val fileSize: String,
    val takenAt: String?,
    val camera: String?,
    val lens: String?,
    /** 撮影パラメータ1行表示: f/1.9 ・ 1/250s ・ ISO200 ・ 24mm(35mm判) */
    val paramsLine: String?,
    val exposureBias: String?,
    val videoLine: String?,
    /** 緯度・経度(GPS情報がある場合のみ)。 */
    val gps: Pair<Double, Double>?,
    /** RAW+JPEGペアの場合の(ファイル名, サイズ)一覧。 */
    val pairFiles: List<Pair<String, String>>,
)

object MediaInfoLoader {

    private val dateFormatter =
        DateTimeFormatter.ofPattern("yyyy年M月d日 HH:mm", Locale.JAPAN)

    suspend fun load(context: Context, entry: GalleryEntry): MediaInfo =
        withContext(Dispatchers.IO) {
            val item = entry.item
            when (item.kind) {
                MediaKind.IMAGE -> loadImageInfo(context, entry)
                MediaKind.VIDEO -> loadVideoInfo(context, item)
            }
        }

    private fun loadImageInfo(context: Context, entry: GalleryEntry): MediaInfo {
        val item = entry.item
        val exif = runCatching {
            context.contentResolver.openInputStream(item.uri)?.use { ExifInterface(it) }
        }.getOrNull()

        val fNumber = exif?.getAttributeDouble(ExifInterface.TAG_F_NUMBER, 0.0) ?: 0.0
        val exposureTime = exif?.getAttributeDouble(ExifInterface.TAG_EXPOSURE_TIME, 0.0) ?: 0.0
        val iso = exif?.getAttributeInt(ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY, 0) ?: 0
        val focal = exif?.getAttributeDouble(ExifInterface.TAG_FOCAL_LENGTH, 0.0) ?: 0.0
        val focal35 = exif?.getAttributeInt(ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM, 0) ?: 0
        val bias = exif?.getAttributeDouble(ExifInterface.TAG_EXPOSURE_BIAS_VALUE, Double.NaN)
            ?: Double.NaN

        val params = buildList {
            if (fNumber > 0) add("f/" + trimZero(fNumber))
            if (exposureTime > 0) add(formatShutter(exposureTime))
            if (iso > 0) add("ISO$iso")
            if (focal > 0) {
                add(
                    "${trimZero(focal)}mm" +
                        if (focal35 > 0 && focal35 != focal.roundToInt()) "(${focal35}mm判)" else "",
                )
            }
        }.takeIf { it.isNotEmpty() }?.joinToString(" ・ ")

        val make = exif?.getAttribute(ExifInterface.TAG_MAKE)?.trim()
        val model = exif?.getAttribute(ExifInterface.TAG_MODEL)?.trim()
        val camera = when {
            model == null -> make
            make == null || model.contains(make, ignoreCase = true) -> model
            else -> "$make $model"
        }

        return MediaInfo(
            fileName = item.displayName,
            format = formatOf(item),
            resolution = resolutionOf(item),
            fileSize = formatBytes(item.sizeBytes),
            takenAt = formatDate(item.dateTakenMs),
            camera = camera,
            lens = exif?.getAttribute(ExifInterface.TAG_LENS_MODEL)?.trim(),
            paramsLine = params,
            exposureBias = bias.takeIf { !it.isNaN() && abs(it) > 0.01 }
                ?.let { "%+.1f EV".format(it) },
            videoLine = null,
            gps = exif?.latLong?.let { it[0] to it[1] },
            pairFiles = pairFilesOf(entry),
        )
    }

    private fun loadVideoInfo(context: Context, item: MediaItem): MediaInfo {
        var frameRate: Float? = null
        var bitrate: Long? = null
        runCatching {
            MediaMetadataRetriever().use { retriever ->
                retriever.setDataSource(context, item.uri)
                frameRate = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_CAPTURE_FRAMERATE)
                    ?.toFloatOrNull()
                bitrate = retriever
                    .extractMetadata(MediaMetadataRetriever.METADATA_KEY_BITRATE)
                    ?.toLongOrNull()
            }
        }
        val videoLine = buildList {
            if (item.durationMs > 0) add(formatDuration(item.durationMs))
            frameRate?.takeIf { it > 0 }?.let { add("${trimZero(it.toDouble())}fps") }
            bitrate?.takeIf { it > 0 }?.let { add("%.1f Mbps".format(it / 1_000_000.0)) }
        }.takeIf { it.isNotEmpty() }?.joinToString(" ・ ")

        return MediaInfo(
            fileName = item.displayName,
            format = formatOf(item),
            resolution = resolutionOf(item),
            fileSize = formatBytes(item.sizeBytes),
            takenAt = formatDate(item.dateTakenMs),
            camera = null,
            lens = null,
            paramsLine = null,
            exposureBias = null,
            videoLine = videoLine,
            gps = null,
            pairFiles = emptyList(),
        )
    }

    private fun pairFilesOf(entry: GalleryEntry): List<Pair<String, String>> {
        val counterpart = entry.counterpart ?: return emptyList()
        val raw = if (entry.item.isRaw) entry.item else counterpart
        val jpeg = if (entry.item.isRaw) counterpart else entry.item
        return listOf(
            "RAW: ${raw.displayName}" to formatBytes(raw.sizeBytes),
            "JPEG: ${jpeg.displayName}" to formatBytes(jpeg.sizeBytes),
        )
    }

    private fun formatOf(item: MediaItem): String = when {
        item.isRaw -> "RAW (DNG)"
        item.isJpeg -> "JPEG"
        item.kind == MediaKind.VIDEO ->
            "動画 (${item.mimeType.substringAfter('/').uppercase(Locale.US)})"
        else -> item.mimeType.substringAfter('/').uppercase(Locale.US)
    }

    private fun resolutionOf(item: MediaItem): String? =
        if (item.width > 0 && item.height > 0) {
            val mp = item.width.toLong() * item.height / 1_000_000.0
            "${item.width} × ${item.height}" +
                if (item.kind == MediaKind.IMAGE) "(%.1f MP)".format(mp) else ""
        } else {
            null
        }

    private fun formatDate(epochMs: Long): String? =
        if (epochMs > 0) {
            Instant.ofEpochMilli(epochMs).atZone(ZoneId.systemDefault()).format(dateFormatter)
        } else {
            null
        }

    /** シャッタースピードをカメラ風表記(1/250s / 2.5s)にする。 */
    fun formatShutter(seconds: Double): String =
        if (seconds >= 1.0) {
            "${trimZero(seconds)}s"
        } else {
            "1/${(1.0 / seconds).roundToLong()}s"
        }

    fun formatBytes(bytes: Long): String = when {
        bytes >= 1_000_000_000 -> "%.2f GB".format(bytes / 1_000_000_000.0)
        bytes >= 1_000_000 -> "%.1f MB".format(bytes / 1_000_000.0)
        bytes >= 1_000 -> "%.0f KB".format(bytes / 1_000.0)
        else -> "$bytes B"
    }

    private fun trimZero(value: Double): String =
        if (value == value.roundToLong().toDouble()) {
            value.roundToLong().toString()
        } else {
            "%.1f".format(value)
        }
}
