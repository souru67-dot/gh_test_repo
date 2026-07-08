package com.souru.lumina.util

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.exifinterface.media.ExifInterface
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import java.io.File

/**
 * OSの共有シート(Intent.ACTION_SEND / SEND_MULTIPLE + createChooser)で共有する。
 *
 * 共有先が少なくならないよう、MIMEは常に有効な値へ正規化する
 * (MediaStoreのMIMEがnull/octet-streamだと対応アプリがほぼ出なくなるため)。
 */
object Sharing {

    private const val AUTHORITY_SUFFIX = ".fileprovider"

    /** 共有に使うMIME。無効・非対応値はkindから image/* ・ video/* に落とす。 */
    fun shareMime(item: MediaItem): String {
        val m = item.mimeType.lowercase()
        return when {
            item.kind == MediaKind.VIDEO ->
                if (m.startsWith("video/")) m else "video/mp4"
            // DNGは対応アプリが少ないが仕様として許容(単体共有可能に)
            item.isRaw -> "image/x-adobe-dng"
            m.startsWith("image/") -> m
            else -> "image/*"
        }
    }

    /** 複数共有のMIME。種別が混在すれば */*、揃っていれば image/* か video/*。 */
    private fun shareMimeMultiple(items: List<MediaItem>): String {
        val allImage = items.all { it.kind == MediaKind.IMAGE }
        val allVideo = items.all { it.kind == MediaKind.VIDEO }
        return when {
            allImage -> "image/*"
            allVideo -> "video/*"
            else -> "*/*"
        }
    }

    /**
     * 1件を共有する。[stripLocation] がtrueかつJPEG画像のときは、GPS等の
     * 位置情報を除去したコピーをFileProvider経由で共有する。
     */
    fun share(context: Context, item: MediaItem, stripLocation: Boolean = false) {
        val uri: Uri
        val mime: String
        if (stripLocation && canStripLocation(item)) {
            val stripped = writeLocationStrippedCopy(context, item)
            if (stripped != null) {
                uri = stripped
                mime = "image/jpeg"
            } else {
                uri = item.uri
                mime = shareMime(item)
            }
        } else {
            uri = item.uri
            mime = shareMime(item)
        }
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = mime
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    /** 複数件を共有する。stripLocationは対象JPEGのみ除去コピーに差し替える。 */
    fun shareMultiple(context: Context, items: List<MediaItem>, stripLocation: Boolean = false) {
        if (items.isEmpty()) return
        if (items.size == 1) {
            share(context, items.first(), stripLocation)
            return
        }
        val uris = ArrayList<Uri>(items.size)
        for (item in items) {
            val u = if (stripLocation && canStripLocation(item)) {
                writeLocationStrippedCopy(context, item) ?: item.uri
            } else {
                item.uri
            }
            uris += u
        }
        val intent = Intent(Intent.ACTION_SEND_MULTIPLE).apply {
            type = shareMimeMultiple(items)
            putParcelableArrayListExtra(Intent.EXTRA_STREAM, uris)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, null))
    }

    /** 位置情報除去は、EXIFを持つJPEG画像でのみ可能。 */
    fun canStripLocation(item: MediaItem): Boolean =
        item.kind == MediaKind.IMAGE && item.isJpeg

    /** 選択にGPS除去可能なJPEGが1件でも含まれるか(UI表示判定用)。 */
    fun anyStrippable(items: List<MediaItem>): Boolean = items.any { canStripLocation(it) }

    /**
     * 元ファイルをcacheDir/share/にコピーし、GPS系EXIFタグを削除して
     * FileProviderのcontent URIを返す。失敗時はnull(呼び出し側で原本にフォールバック)。
     */
    private fun writeLocationStrippedCopy(context: Context, item: MediaItem): Uri? = runCatching {
        val dir = File(context.cacheDir, "share").apply { mkdirs() }
        val outFile = File(dir, "noexif_${item.id}_${item.displayName}")
        context.contentResolver.openInputStream(item.uri)?.use { input ->
            outFile.outputStream().use { output -> input.copyTo(output) }
        } ?: return null

        val exif = ExifInterface(outFile.absolutePath)
        // 位置に関わるタグをすべて削除する
        LOCATION_TAGS.forEach { exif.setAttribute(it, null) }
        runCatching { exif.setAttribute(ExifInterface.TAG_GPS_LATITUDE, null) }
        exif.saveAttributes()

        FileProvider.getUriForFile(context, context.packageName + AUTHORITY_SUFFIX, outFile)
    }.getOrNull()

    private val LOCATION_TAGS = arrayOf(
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
        ExifInterface.TAG_GPS_PROCESSING_METHOD,
        ExifInterface.TAG_GPS_AREA_INFORMATION,
        ExifInterface.TAG_GPS_DOP,
        ExifInterface.TAG_GPS_SPEED,
        ExifInterface.TAG_GPS_SPEED_REF,
        ExifInterface.TAG_GPS_TRACK,
        ExifInterface.TAG_GPS_TRACK_REF,
        ExifInterface.TAG_GPS_IMG_DIRECTION,
        ExifInterface.TAG_GPS_IMG_DIRECTION_REF,
    )
}
