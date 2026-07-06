package com.souru.lumina.data.model

import android.net.Uri
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

enum class MediaKind { IMAGE, VIDEO }

/** MIME判定の純ロジック(null・未知のMIMEでも落ちない)。 */
object MediaMime {
    fun isRaw(mimeType: String?, displayName: String?): Boolean =
        mimeType.equals("image/x-adobe-dng", ignoreCase = true) ||
            displayName?.endsWith(".dng", ignoreCase = true) == true

    fun isJpeg(mimeType: String?): Boolean =
        mimeType.equals("image/jpeg", ignoreCase = true) ||
            mimeType.equals("image/jpg", ignoreCase = true)
}

/** MediaStore 上の1ファイル。 */
data class MediaItem(
    val id: Long,
    val uri: Uri,
    val displayName: String,
    val mimeType: String,
    val dateTakenMs: Long,
    val sizeBytes: Long,
    val width: Int,
    val height: Int,
    val durationMs: Long,
    val kind: MediaKind,
    /** ゴミ箱アイテムの自動削除予定時刻(エポック秒)。通常アイテムは0。 */
    val dateExpiresSec: Long = 0,
    /** MediaStoreのORIENTATION(0/90/180/270)。DNGの向き補正に使う。 */
    val orientationDeg: Int = 0,
    /** MediaStore標準のIS_FAVORITE(他アプリとお気に入り状態が共通)。 */
    val isFavorite: Boolean = false,
    /** 端末フォルダ(アルバム)のBUCKET_ID / BUCKET_DISPLAY_NAME。 */
    val bucketId: Long = 0,
    val bucketName: String? = null,
) {
    val isRaw: Boolean
        get() = MediaMime.isRaw(mimeType, displayName)

    /** MIMEタイプでの厳密なJPEG判定。 */
    val isJpeg: Boolean
        get() = MediaMime.isJpeg(mimeType)

    val baseName: String
        get() = displayName.substringBeforeLast('.')

    val localDate: LocalDate
        get() = Instant.ofEpochMilli(dateTakenMs).atZone(ZoneId.systemDefault()).toLocalDate()
}

/** メディア種別の表示フィルタ(すべて / 写真のみ / 動画のみ)。 */
enum class MediaTypeFilter { ALL, PHOTO, VIDEO }

/** 写真の形式フィルタ(すべて / JPEG / RAW)。動画には適用しない。 */
enum class RawFilterMode { JPEG, RAW, ALL }

/**
 * グリッドに表示する1エントリ。ペアリング済みの場合は [counterpart] に
 * もう一方(JPEG表示中ならRAW、RAW表示中ならJPEG)が入る。
 */
data class GalleryEntry(
    val item: MediaItem,
    val counterpart: MediaItem? = null,
) {
    val id: Long get() = item.id
    val isPaired: Boolean get() = counterpart != null
}

/** グリッドのスロット(日付ヘッダー or セル)。 */
sealed interface GridSlot {
    val key: Any

    data class Header(val date: LocalDate, val label: String) : GridSlot {
        override val key: Any get() = "header-$date"
    }

    data class Cell(val entry: GalleryEntry, val entryIndex: Int) : GridSlot {
        override val key: Any get() = entry.id
    }
}
