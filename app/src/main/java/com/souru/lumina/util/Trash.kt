package com.souru.lumina.util

import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.activity.result.IntentSenderRequest
import com.souru.lumina.data.model.GalleryEntry
import com.souru.lumina.data.model.MediaItem
import kotlin.math.ceil

/**
 * MediaStore標準のゴミ箱(IS_TRASHED)操作。システム確認ダイアログを伴うため、
 * 返した IntentSenderRequest を StartIntentSenderForResult で起動する。
 */
object Trash {

    /** ゴミ箱へ移動。 */
    fun trashRequest(context: Context, uris: List<Uri>): IntentSenderRequest =
        IntentSenderRequest.Builder(
            MediaStore.createTrashRequest(context.contentResolver, uris, true).intentSender,
        ).build()

    /** ゴミ箱から復元。 */
    fun restoreRequest(context: Context, uris: List<Uri>): IntentSenderRequest =
        IntentSenderRequest.Builder(
            MediaStore.createTrashRequest(context.contentResolver, uris, false).intentSender,
        ).build()

    /** 完全削除(取り消し不可)。 */
    fun deleteRequest(context: Context, uris: List<Uri>): IntentSenderRequest =
        IntentSenderRequest.Builder(
            MediaStore.createDeleteRequest(context.contentResolver, uris).intentSender,
        ).build()

    /** お気に入りの設定/解除(MediaStore標準のIS_FAVORITE)。 */
    fun favoriteRequest(context: Context, uris: List<Uri>, favorite: Boolean): IntentSenderRequest =
        IntentSenderRequest.Builder(
            MediaStore.createFavoriteRequest(context.contentResolver, uris, favorite).intentSender,
        ).build()

    /** DATE_EXPIRES(エポック秒)から自動削除までの残り日数を計算する。 */
    fun remainingDays(dateExpiresSec: Long, nowMs: Long = System.currentTimeMillis()): Int {
        if (dateExpiresSec <= 0) return 30 // 期限不明時はMediaStore既定の約30日
        return ceil((dateExpiresSec * 1000L - nowMs) / 86_400_000.0).toInt().coerceAtLeast(0)
    }
}

/** RAW+JPEGペアを削除するときの対象選択。 */
enum class PairDeleteChoice { JPEG_ONLY, RAW_ONLY, BOTH }

/**
 * 削除対象の解決。非ペアのエントリは表示中のアイテムそのもの、
 * ペアのエントリは [choice] に従ってJPEG側/RAW側/両方を返す。
 */
fun resolveDeletionItems(
    entries: List<GalleryEntry>,
    choice: PairDeleteChoice,
): List<MediaItem> =
    entries.flatMap { entry ->
        val counterpart = entry.counterpart
        if (counterpart == null) {
            listOf(entry.item)
        } else {
            val raw = if (entry.item.isRaw) entry.item else counterpart
            val jpeg = if (entry.item.isRaw) counterpart else entry.item
            when (choice) {
                PairDeleteChoice.JPEG_ONLY -> listOf(jpeg)
                PairDeleteChoice.RAW_ONLY -> listOf(raw)
                PairDeleteChoice.BOTH -> listOf(jpeg, raw)
            }
        }
    }.distinctBy { it.id }
