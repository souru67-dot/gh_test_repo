package com.souru.lumina.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * 外部デバイス(SAF)から端末の Pictures/Lumina/Import/ へ写真・動画をコピーする。
 *
 * - 進捗はフォアグラウンド通知に表示
 * - 重複(同一表示名かつ同一サイズが取り込み先に既存)はスキップ
 * - RAW+JPEGペアを揃えるための展開は呼び出し側(ViewModel)で行い、
 *   Workerは渡されたURI群を順にコピーする
 */
class ImportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uris = inputData.getStringArray(KEY_URIS) ?: return Result.failure()
        val names = inputData.getStringArray(KEY_NAMES) ?: return Result.failure()
        val mimes = inputData.getStringArray(KEY_MIMES) ?: return Result.failure()
        val sizes = inputData.getLongArray(KEY_SIZES) ?: LongArray(uris.size)
        if (uris.size != names.size || uris.size != mimes.size) return Result.failure()

        setForeground(createForegroundInfo(0, uris.size))

        var copied = 0
        var skipped = 0
        var failed = 0
        withContext(Dispatchers.IO) {
            for (i in uris.indices) {
                val sourceUri = runCatching { Uri.parse(uris[i]) }.getOrNull()
                if (sourceUri == null) {
                    failed++
                    continue
                }
                val name = names[i]
                val mime = mimes[i]
                val size = sizes.getOrElse(i) { 0L }
                try {
                    if (alreadyImported(name, size)) {
                        skipped++
                    } else if (copyOne(sourceUri, name, mime)) {
                        copied++
                    } else {
                        failed++
                    }
                } catch (c: kotlinx.coroutines.CancellationException) {
                    throw c
                } catch (t: Throwable) {
                    failed++
                }
                runCatching { setForeground(createForegroundInfo(i + 1, uris.size)) }
            }
        }

        notifyFinished(
            "取り込みが完了しました",
            "コピー $copied 件 / スキップ $skipped 件" + if (failed > 0) " / 失敗 $failed 件" else "",
        )
        return Result.success(
            workDataOf(KEY_COPIED to copied, KEY_SKIPPED to skipped, KEY_FAILED to failed),
        )
    }

    /** 表示名+サイズが一致するファイルが取り込み先に既にあるか。 */
    private fun alreadyImported(displayName: String, size: Long): Boolean {
        val resolver = applicationContext.contentResolver
        val collection = MediaStore.Files.getContentUri(MediaStore.VOLUME_EXTERNAL)
        val projection = arrayOf(MediaStore.MediaColumns.SIZE)
        val selection = "${MediaStore.MediaColumns.RELATIVE_PATH} LIKE ? AND " +
            "${MediaStore.MediaColumns.DISPLAY_NAME} = ?"
        val args = arrayOf("$IMPORT_DIR%", displayName)
        return runCatching {
            resolver.query(collection, projection, selection, args, null)?.use { cursor ->
                while (cursor.moveToNext()) {
                    // サイズ未知(0)の場合は名前一致のみで重複とみなす
                    if (size <= 0L) return true
                    val existing = if (!cursor.isNull(0)) cursor.getLong(0) else -1L
                    if (existing == size) return true
                }
                false
            } ?: false
        }.getOrDefault(false)
    }

    private fun copyOne(sourceUri: Uri, displayName: String, mime: String): Boolean {
        val resolver = applicationContext.contentResolver
        val isVideo = mime.startsWith("video/")
        val values = ContentValues().apply {
            put(MediaStore.MediaColumns.DISPLAY_NAME, displayName)
            put(MediaStore.MediaColumns.MIME_TYPE, mime)
            put(MediaStore.MediaColumns.RELATIVE_PATH, IMPORT_DIR)
            put(MediaStore.MediaColumns.IS_PENDING, 1)
        }
        val collection = if (isVideo) {
            MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        } else {
            MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        }
        val dest = resolver.insert(collection, values) ?: return false
        return try {
            resolver.openInputStream(sourceUri)?.use { input ->
                resolver.openOutputStream(dest)?.use { output ->
                    input.copyTo(output)
                } ?: throw IllegalStateException("出力ストリームを開けませんでした")
            } ?: throw IllegalStateException("入力ストリームを開けませんでした")
            values.clear()
            values.put(MediaStore.MediaColumns.IS_PENDING, 0)
            resolver.update(dest, values, null, null)
            true
        } catch (t: Throwable) {
            // 失敗時はpending行を残さない
            runCatching { resolver.delete(dest, null, null) }
            throw t
        }
    }

    private fun createForegroundInfo(done: Int, total: Int): ForegroundInfo {
        ensureChannel()
        val notification: Notification =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_download)
                .setContentTitle("外部デバイスから取り込み中")
                .setContentText("$done / $total")
                .setProgress(total.coerceAtLeast(1), done, total == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
        )
    }

    private fun notifyFinished(title: String, text: String) {
        ensureChannel()
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle(title)
            .setContentText(text)
            .setAutoCancel(true)
            .build()
        runCatching { manager.notify(NOTIFICATION_DONE_ID, notification) }
    }

    private fun ensureChannel() {
        val manager =
            applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        manager.createNotificationChannel(
            NotificationChannel(CHANNEL_ID, "外部デバイスの取り込み", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "external_import"
        private const val NOTIFICATION_ID = 2001
        private const val NOTIFICATION_DONE_ID = 2002
        const val IMPORT_DIR = "Pictures/Lumina/Import"

        const val KEY_URIS = "uris"
        const val KEY_NAMES = "names"
        const val KEY_MIMES = "mimes"
        const val KEY_SIZES = "sizes"
        const val KEY_COPIED = "copied"
        const val KEY_SKIPPED = "skipped"
        const val KEY_FAILED = "failed"

        fun buildRequest(
            uris: List<String>,
            names: List<String>,
            mimes: List<String>,
            sizes: List<Long>,
        ): OneTimeWorkRequest {
            val data = Data.Builder()
                .putStringArray(KEY_URIS, uris.toTypedArray())
                .putStringArray(KEY_NAMES, names.toTypedArray())
                .putStringArray(KEY_MIMES, mimes.toTypedArray())
                .putLongArray(KEY_SIZES, sizes.toLongArray())
                .build()
            return OneTimeWorkRequestBuilder<ImportWorker>()
                .setInputData(data)
                .build()
        }
    }
}
