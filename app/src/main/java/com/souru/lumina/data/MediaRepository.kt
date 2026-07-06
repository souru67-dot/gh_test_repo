package com.souru.lumina.data

import android.content.ContentResolver
import android.content.ContentUris
import android.content.Context
import android.database.ContentObserver
import android.database.Cursor
import android.net.Uri
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.MediaStore
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.channels.awaitClose
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.callbackFlow
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.mapLatest
import kotlinx.coroutines.flow.merge
import kotlinx.coroutines.flow.onStart

class MediaRepository(private val context: Context) {

    private val changes: Flow<Unit> = callbackFlow {
        val observer = object : ContentObserver(Handler(Looper.getMainLooper())) {
            override fun onChange(selfChange: Boolean) {
                trySend(Unit)
            }
        }
        context.contentResolver.registerContentObserver(
            MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL),
            true,
            observer,
        )
        awaitClose { context.contentResolver.unregisterContentObserver(observer) }
    }

    private val manualRefresh = MutableSharedFlow<Unit>(extraBufferCapacity = 1)

    private val _loadError = MutableStateFlow(false)

    /** 直近の読み込みが失敗したか(UIのエラー表示+再試行に使う)。 */
    val loadError: StateFlow<Boolean> = _loadError.asStateFlow()

    /** 権限付与後・エラー後などの手動再読み込み。 */
    fun refresh() {
        manualRefresh.tryEmit(Unit)
    }

    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun observeMedia(): Flow<List<MediaItem>> =
        merge(changes, manualRefresh)
            .debounce(300)
            .onStart { emit(Unit) }
            // クエリ失敗(権限の遷移タイミング等)でアプリを落とさず空リストへ
            .mapLatest {
                runCatching { queryAll() }.fold(
                    onSuccess = { items ->
                        _loadError.value = false
                        items
                    },
                    onFailure = {
                        _loadError.value = true
                        emptyList()
                    },
                )
            }
            .flowOn(Dispatchers.IO)

    /** ゴミ箱(IS_TRASHED=1)のアイテムを監視する。 */
    @OptIn(FlowPreview::class, ExperimentalCoroutinesApi::class)
    fun observeTrashed(): Flow<List<MediaItem>> =
        changes
            .debounce(300)
            .onStart { emit(Unit) }
            .mapLatest { runCatching { queryTrashed() }.getOrElse { emptyList() } }
            .flowOn(Dispatchers.IO)

    /** MediaStoreのゴミ箱アイテムを取得する(QUERY_ARG_MATCH_TRASHED)。 */
    fun queryTrashed(): List<MediaItem> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
            MediaStore.MediaColumns.DATE_EXPIRES,
        )
        val queryArgs = Bundle().apply {
            putString(
                ContentResolver.QUERY_ARG_SQL_SELECTION,
                "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)",
            )
            putStringArray(
                ContentResolver.QUERY_ARG_SQL_SELECTION_ARGS,
                arrayOf(
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
                ),
            )
            putString(
                ContentResolver.QUERY_ARG_SQL_SORT_ORDER,
                "${MediaStore.MediaColumns.DATE_EXPIRES} ASC",
            )
            putInt(MediaStore.QUERY_ARG_MATCH_TRASHED, MediaStore.MATCH_ONLY)
        }

        val items = ArrayList<MediaItem>(64)
        context.contentResolver.query(collection, projection, queryArgs, null)
            ?.use { cursor -> readItems(cursor, items) }
        return items
    }

    suspend fun getItem(id: Long): MediaItem? = kotlinx.coroutines.withContext(Dispatchers.IO) {
        queryAll(selectionExtra = "${MediaStore.Files.FileColumns._ID} = $id").firstOrNull()
    }

    fun queryAll(selectionExtra: String? = null): List<MediaItem> {
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(
            MediaStore.Files.FileColumns._ID,
            MediaStore.Files.FileColumns.DISPLAY_NAME,
            MediaStore.Files.FileColumns.MIME_TYPE,
            MediaStore.Files.FileColumns.DATE_TAKEN,
            MediaStore.Files.FileColumns.DATE_ADDED,
            MediaStore.Files.FileColumns.SIZE,
            MediaStore.Files.FileColumns.WIDTH,
            MediaStore.Files.FileColumns.HEIGHT,
            MediaStore.Files.FileColumns.DURATION,
            MediaStore.Files.FileColumns.MEDIA_TYPE,
        )
        val selection = "${MediaStore.Files.FileColumns.MEDIA_TYPE} IN (?, ?)" +
            (selectionExtra?.let { " AND ($it)" } ?: "")
        val selectionArgs = arrayOf(
            MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE.toString(),
            MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO.toString(),
        )
        val sortOrder =
            "${MediaStore.Files.FileColumns.DATE_TAKEN} DESC, ${MediaStore.Files.FileColumns.DATE_ADDED} DESC"

        val items = ArrayList<MediaItem>(1024)
        context.contentResolver.query(collection, projection, selection, selectionArgs, sortOrder)
            ?.use { cursor -> readItems(cursor, items) }
        return items
    }

    /**
     * カーソル読み取りの防御層。カラム欠落(index=-1)やnull値は既定値へ
     * フォールバックし、1アイテムの読み取り失敗はそのアイテムのスキップに
     * 留めて全体へ波及させない。
     */
    private fun readItems(cursor: Cursor, out: MutableList<MediaItem>) {
        val idCol = cursor.getColumnIndex(MediaStore.Files.FileColumns._ID)
        val nameCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DISPLAY_NAME)
        val mimeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MIME_TYPE)
        val takenCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_TAKEN)
        val addedCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DATE_ADDED)
        val sizeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.SIZE)
        val widthCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.WIDTH)
        val heightCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.HEIGHT)
        val durationCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
        val typeCol = cursor.getColumnIndex(MediaStore.Files.FileColumns.MEDIA_TYPE)
        val expiresCol = cursor.getColumnIndex(MediaStore.MediaColumns.DATE_EXPIRES)
        if (idCol < 0) return

        while (cursor.moveToNext()) {
            runCatching {
                val kind = when (cursor.safeInt(typeCol)) {
                    MediaStore.Files.FileColumns.MEDIA_TYPE_IMAGE -> MediaKind.IMAGE
                    MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO -> MediaKind.VIDEO
                    else -> return@runCatching
                }
                val id = cursor.safeLong(idCol)
                if (id <= 0) return@runCatching
                val taken = cursor.safeLong(takenCol)
                val added = cursor.safeLong(addedCol)
                out += MediaItem(
                    id = id,
                    uri = contentUriFor(kind, id),
                    displayName = cursor.safeString(nameCol) ?: "unknown_$id",
                    mimeType = cursor.safeString(mimeCol) ?: "application/octet-stream",
                    dateTakenMs = if (taken > 0) taken else added * 1000L,
                    sizeBytes = cursor.safeLong(sizeCol),
                    width = cursor.safeInt(widthCol),
                    height = cursor.safeInt(heightCol),
                    durationMs = cursor.safeLong(durationCol),
                    kind = kind,
                    dateExpiresSec = cursor.safeLong(expiresCol),
                )
            }
            // 失敗したアイテムはスキップするだけ(全体をクラッシュさせない)
        }
    }

    private fun Cursor.safeLong(index: Int): Long =
        if (index >= 0 && !isNull(index)) getLong(index) else 0L

    private fun Cursor.safeInt(index: Int): Int =
        if (index >= 0 && !isNull(index)) getInt(index) else 0

    private fun Cursor.safeString(index: Int): String? =
        if (index >= 0 && !isNull(index)) getString(index) else null

    private fun contentUriFor(kind: MediaKind, id: Long): Uri = when (kind) {
        MediaKind.IMAGE -> ContentUris.withAppendedId(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, id)
        MediaKind.VIDEO -> ContentUris.withAppendedId(MediaStore.Video.Media.EXTERNAL_CONTENT_URI, id)
    }
}
