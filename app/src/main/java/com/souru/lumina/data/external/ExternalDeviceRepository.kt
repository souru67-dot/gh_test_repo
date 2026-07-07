package com.souru.lumina.data.external

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.provider.DocumentsContract
import android.util.Log
import com.souru.lumina.data.SettingsRepository
import com.souru.lumina.data.model.MediaItem
import com.souru.lumina.data.model.MediaKind
import com.souru.lumina.data.model.MediaMime
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** 外部デバイス走査の結果状態。 */
sealed interface ExternalDeviceState {
    data object NotSelected : ExternalDeviceState
    data object Loading : ExternalDeviceState
    data class Ready(val treeUri: Uri, val items: List<MediaItem>) : ExternalDeviceState

    /** 権限失効(取り外し)や読み取り不能。再選択を促す。 */
    data class Disconnected(val treeUri: Uri?) : ExternalDeviceState
}

/**
 * USB/SDカード等、SAFのDocumentツリーから写真・動画を読むリポジトリ。
 *
 * - 権限は [takePersistableUriPermission] で永続化し、再接続時に再選択を省く
 * - 走査は DocumentsContract の子問い合わせ(DocumentFileより高速)で再帰
 * - ツリーが読めない(取り外し・権限失効)場合は [ExternalDeviceState.Disconnected]
 */
class ExternalDeviceRepository(
    private val context: Context,
    private val settingsRepository: SettingsRepository,
) {

    /** SAFのツリー選択インテント(呼び出し側でActivityResultに渡す)。 */
    fun openTreeIntent(): Intent =
        Intent(Intent.ACTION_OPEN_DOCUMENT_TREE).apply {
            addFlags(
                Intent.FLAG_GRANT_READ_URI_PERMISSION or
                    Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
            )
        }

    /** 選択されたツリーURIの読み取り権限を永続化して保存する。 */
    suspend fun persistTree(treeUri: Uri) {
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                treeUri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
        }.onFailure { Log.w(TAG, "権限の永続化に失敗", it) }
        settingsRepository.setExternalTreeUri(treeUri.toString())
    }

    suspend fun clearTree() {
        currentTreeUri()?.let { uri ->
            runCatching {
                context.contentResolver.releasePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            }
        }
        settingsRepository.setExternalTreeUri(null)
    }

    private suspend fun currentTreeUri(): Uri? =
        settingsRepository.externalTreeUriOnce()?.let(Uri::parse)

    /** 保存済みツリーがあり、かつ現在も権限が有効かを確認する。 */
    suspend fun hasValidPersistedTree(): Boolean = withContext(Dispatchers.IO) {
        val uri = currentTreeUri() ?: return@withContext false
        context.contentResolver.persistedUriPermissions.any {
            it.uri == uri && it.isReadPermission
        }
    }

    /** 保存済みツリーを走査する。未選択・失効時は対応する状態を返す。 */
    suspend fun scan(): ExternalDeviceState = withContext(Dispatchers.IO) {
        val treeUri = currentTreeUri() ?: return@withContext ExternalDeviceState.NotSelected
        if (!hasValidPersistedTree()) {
            return@withContext ExternalDeviceState.Disconnected(treeUri)
        }
        val items = runCatching { walkTree(treeUri) }.getOrElse { t ->
            // 走査中の失効(取り外し)はDisconnected扱い
            Log.w(TAG, "外部ツリーの走査に失敗(取り外し?)", t)
            return@withContext ExternalDeviceState.Disconnected(treeUri)
        }
        ExternalDeviceState.Ready(treeUri, items)
    }

    private fun walkTree(treeUri: Uri): List<MediaItem> {
        val rootDocId = DocumentsContract.getTreeDocumentId(treeUri)
        val out = ArrayList<MediaItem>(128)
        val stack = ArrayDeque<String>()
        stack.addLast(rootDocId)
        var guard = 0
        while (stack.isNotEmpty()) {
            // 異常なほど深い/巨大なツリーでも暴走しないよう上限を設ける
            if (guard++ > MAX_NODES) break
            val parentDocId = stack.removeLast()
            val childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(treeUri, parentDocId)
            context.contentResolver.query(
                childrenUri,
                arrayOf(
                    DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                    DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                    DocumentsContract.Document.COLUMN_MIME_TYPE,
                    DocumentsContract.Document.COLUMN_LAST_MODIFIED,
                    DocumentsContract.Document.COLUMN_SIZE,
                ),
                null,
                null,
                null,
            )?.use { cursor ->
                val idCol = 0
                val nameCol = 1
                val mimeCol = 2
                val modifiedCol = 3
                val sizeCol = 4
                while (cursor.moveToNext()) {
                    val docId = cursor.getString(idCol) ?: continue
                    val name = cursor.getString(nameCol) ?: "unknown"
                    val mime = cursor.getString(mimeCol) ?: ""
                    if (mime == DocumentsContract.Document.MIME_TYPE_DIR) {
                        stack.addLast(docId)
                        continue
                    }
                    val kind = kindOf(mime, name) ?: continue
                    val docUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
                    out += MediaItem(
                        id = stableId(docId),
                        uri = docUri,
                        displayName = name,
                        mimeType = mime.ifEmpty { guessMime(name, kind) },
                        dateTakenMs = if (!cursor.isNull(modifiedCol)) cursor.getLong(modifiedCol) else 0L,
                        sizeBytes = if (!cursor.isNull(sizeCol)) cursor.getLong(sizeCol) else 0L,
                        width = 0,
                        height = 0,
                        durationMs = 0L,
                        kind = kind,
                        isExternal = true,
                    )
                }
            }
        }
        return out
    }

    private fun kindOf(mime: String, name: String): MediaKind? = when {
        mime.startsWith("image/") -> MediaKind.IMAGE
        mime.startsWith("video/") -> MediaKind.VIDEO
        // MIMEが空/octet-streamの場合は拡張子で判定(一部のリーダーで発生)
        name.matchesExt(IMAGE_EXTS) -> MediaKind.IMAGE
        name.matchesExt(VIDEO_EXTS) -> MediaKind.VIDEO
        else -> null
    }

    private fun guessMime(name: String, kind: MediaKind): String = when {
        MediaMime.isRaw(null, name) -> "image/x-adobe-dng"
        kind == MediaKind.VIDEO -> "video/mp4"
        else -> "image/jpeg"
    }

    private fun String.matchesExt(exts: Set<String>): Boolean {
        val dot = lastIndexOf('.')
        if (dot < 0) return false
        return substring(dot + 1).lowercase() in exts
    }

    /** Document IDから安定した正のLongを合成する(グリッドキー用)。 */
    private fun stableId(docId: String): Long =
        (docId.hashCode().toLong() and 0xFFFFFFFFL) or EXTERNAL_ID_FLAG

    companion object {
        private const val TAG = "ExternalDevice"
        private const val MAX_NODES = 5000
        // 端末MediaStoreのIDと衝突しないよう上位ビットを立てる
        private const val EXTERNAL_ID_FLAG = 0x1_0000_0000L
        private val IMAGE_EXTS = setOf("jpg", "jpeg", "png", "dng", "heic", "heif", "webp")
        private val VIDEO_EXTS = setOf("mp4", "mov", "m4v", "3gp", "mkv", "webm")
    }
}
