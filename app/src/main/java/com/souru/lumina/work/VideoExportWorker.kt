package com.souru.lumina.work

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.ContentValues
import android.content.Context
import android.content.pm.ServiceInfo
import android.net.Uri
import android.provider.MediaStore
import androidx.core.app.NotificationCompat
import androidx.media3.common.MediaItem
import androidx.media3.common.util.UnstableApi
import androidx.media3.effect.Presentation
import androidx.media3.effect.SingleColorLut
import androidx.media3.transformer.Composition
import androidx.media3.transformer.DefaultEncoderFactory
import androidx.media3.transformer.EditedMediaItem
import androidx.media3.transformer.Effects
import androidx.media3.transformer.ExportException
import androidx.media3.transformer.ExportResult
import androidx.media3.transformer.ProgressHolder
import androidx.media3.transformer.Transformer
import androidx.media3.transformer.VideoEncoderSettings
import androidx.work.CoroutineWorker
import androidx.work.Data
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequest
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.souru.lumina.data.edit.Adjustments
import com.souru.lumina.data.luts.CubeLutParser
import com.souru.lumina.data.luts.LutBaker
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Media3 Transformer による動画書き出し(バックグラウンド+進捗通知)。
 * LUT・強度・簡易調整は LutBaker で単一の3D LUTに焼き込むため、
 * プレビューと書き出しの色が一致する。元ファイルは非破壊で、
 * Movies/Lumina に別名保存する。
 */
@UnstableApi
class VideoExportWorker(
    context: Context,
    params: WorkerParameters,
) : CoroutineWorker(context, params) {

    override suspend fun doWork(): Result {
        val uriString = inputData.getString(KEY_URI) ?: return Result.failure()
        val baseName = inputData.getString(KEY_BASE_NAME) ?: "video"
        val lutPath = inputData.getString(KEY_LUT_PATH)
        val strength = inputData.getFloat(KEY_STRENGTH, 1f)
        val adjustments = Adjustments(
            exposure = inputData.getFloat(KEY_EXPOSURE, 0f),
            contrast = inputData.getFloat(KEY_CONTRAST, 0f),
            highlights = inputData.getFloat(KEY_HIGHLIGHTS, 0f),
            shadows = inputData.getFloat(KEY_SHADOWS, 0f),
            temperature = inputData.getFloat(KEY_TEMPERATURE, 0f),
            saturation = inputData.getFloat(KEY_SATURATION, 0f),
        )
        val trimStartMs = inputData.getLong(KEY_TRIM_START_MS, 0L)
        val trimEndMs = inputData.getLong(KEY_TRIM_END_MS, 0L)
        val targetHeight = inputData.getInt(KEY_TARGET_HEIGHT, 0)
        val bitrate = inputData.getInt(KEY_BITRATE, 0)

        setForeground(createForegroundInfo(0))

        val outputFile = File(
            applicationContext.cacheDir,
            "export_${System.currentTimeMillis()}.mp4",
        )
        return try {
            transform(
                uri = Uri.parse(uriString),
                outputFile = outputFile,
                lutPath = lutPath,
                strength = strength,
                adjustments = adjustments,
                trimStartMs = trimStartMs,
                trimEndMs = trimEndMs,
                targetHeight = targetHeight,
                bitrate = bitrate,
            )
            val savedUri = saveToMediaStore(outputFile, baseName)
            notifyFinished("書き出しが完了しました", "Movies/Lumina に保存しました")
            Result.success(workDataOf(KEY_RESULT_URI to savedUri.toString()))
        } catch (c: kotlinx.coroutines.CancellationException) {
            // キャンセルはWorkManagerに委ねる(pending行はsaveToMediaStore内で削除済み)
            throw c
        } catch (t: Throwable) {
            notifyFinished("書き出しに失敗しました", t.message ?: "不明なエラー")
            Result.failure(workDataOf(KEY_ERROR to (t.message ?: "unknown")))
        } finally {
            outputFile.delete()
        }
    }

    private suspend fun transform(
        uri: Uri,
        outputFile: File,
        lutPath: String?,
        strength: Float,
        adjustments: Adjustments,
        trimStartMs: Long,
        trimEndMs: Long,
        targetHeight: Int,
        bitrate: Int,
    ) {
        // LUTベイクはCPU負荷が小さいので先に実行しておく
        val lut = lutPath?.let { CubeLutParser.parse(File(it).readText()) }
        val hasColorEffect = lut != null && strength > 0f || !adjustments.isIdentity
        val videoEffects = buildList {
            if (hasColorEffect) {
                add(SingleColorLut.createFromCube(LutBaker.bake(lut, strength, adjustments)))
            }
            if (targetHeight > 0) {
                add(Presentation.createForHeight(targetHeight))
            }
        }

        val clipping = MediaItem.ClippingConfiguration.Builder().apply {
            if (trimStartMs > 0) setStartPositionMs(trimStartMs)
            if (trimEndMs > 0) setEndPositionMs(trimEndMs)
        }.build()
        val mediaItem = MediaItem.Builder()
            .setUri(uri)
            .setClippingConfiguration(clipping)
            .build()
        val editedItem = EditedMediaItem.Builder(mediaItem)
            .setEffects(Effects(emptyList(), videoEffects))
            .build()

        // Transformer は Looper スレッドで動かす必要がある
        withContext(Dispatchers.Main) {
            val done = CompletableDeferred<Unit>()
            val transformer = Transformer.Builder(applicationContext)
                .apply {
                    if (bitrate > 0) {
                        setEncoderFactory(
                            DefaultEncoderFactory.Builder(applicationContext)
                                .setRequestedVideoEncoderSettings(
                                    VideoEncoderSettings.Builder().setBitrate(bitrate).build(),
                                )
                                .build(),
                        )
                    }
                }
                .addListener(object : Transformer.Listener {
                    override fun onCompleted(composition: Composition, exportResult: ExportResult) {
                        done.complete(Unit)
                    }

                    override fun onError(
                        composition: Composition,
                        exportResult: ExportResult,
                        exportException: ExportException,
                    ) {
                        done.completeExceptionally(exportException)
                    }
                })
                .build()

            transformer.start(editedItem, outputFile.absolutePath)

            val holder = ProgressHolder()
            try {
                while (!done.isCompleted) {
                    val state = transformer.getProgress(holder)
                    if (state == Transformer.PROGRESS_STATE_AVAILABLE) {
                        runCatching { setForeground(createForegroundInfo(holder.progress)) }
                    }
                    delay(500)
                }
                done.await()
            } catch (t: Throwable) {
                transformer.cancel()
                throw t
            }
        }
    }

    private fun saveToMediaStore(file: File, baseName: String): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val values = ContentValues().apply {
            put(MediaStore.Video.Media.DISPLAY_NAME, "${baseName}_LUMINA_$timestamp.mp4")
            put(MediaStore.Video.Media.MIME_TYPE, "video/mp4")
            put(MediaStore.Video.Media.RELATIVE_PATH, "Movies/Lumina")
            // 他ギャラリーの日付順で正しい位置に並ぶよう撮影日時を明示する
            put(MediaStore.Video.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Video.Media.IS_PENDING, 1)
        }
        val resolver = applicationContext.contentResolver
        val collection = MediaStore.Video.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStoreへの登録に失敗しました")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                file.inputStream().use { input -> input.copyTo(out) }
            } ?: throw IllegalStateException("出力ストリームを開けませんでした")
            values.clear()
            values.put(MediaStore.Video.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            resolver.delete(uri, null, null)
            throw t
        }
        return uri
    }

    private fun createForegroundInfo(progress: Int): ForegroundInfo {
        ensureChannel()
        val notification: Notification =
            NotificationCompat.Builder(applicationContext, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.stat_sys_upload)
                .setContentTitle("動画を書き出し中")
                .setContentText("$progress%")
                .setProgress(100, progress, progress == 0)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        return ForegroundInfo(
            NOTIFICATION_ID,
            notification,
            ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC,
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
            NotificationChannel(CHANNEL_ID, "動画の書き出し", NotificationManager.IMPORTANCE_LOW),
        )
    }

    companion object {
        private const val CHANNEL_ID = "video_export"
        private const val NOTIFICATION_ID = 1001
        private const val NOTIFICATION_DONE_ID = 1002

        const val KEY_URI = "uri"
        const val KEY_BASE_NAME = "baseName"
        const val KEY_LUT_PATH = "lutPath"
        const val KEY_STRENGTH = "strength"
        const val KEY_EXPOSURE = "exposure"
        const val KEY_CONTRAST = "contrast"
        const val KEY_HIGHLIGHTS = "highlights"
        const val KEY_SHADOWS = "shadows"
        const val KEY_TEMPERATURE = "temperature"
        const val KEY_SATURATION = "saturation"
        const val KEY_TRIM_START_MS = "trimStartMs"
        const val KEY_TRIM_END_MS = "trimEndMs"
        const val KEY_TARGET_HEIGHT = "targetHeight"
        const val KEY_BITRATE = "bitrate"
        const val KEY_RESULT_URI = "resultUri"
        const val KEY_ERROR = "error"

        fun buildRequest(
            uri: Uri,
            baseName: String,
            lutPath: String?,
            strength: Float,
            adjustments: Adjustments,
            trimStartMs: Long,
            trimEndMs: Long,
            targetHeight: Int,
            bitrate: Int,
        ): OneTimeWorkRequest {
            val data = Data.Builder()
                .putString(KEY_URI, uri.toString())
                .putString(KEY_BASE_NAME, baseName)
                .putFloat(KEY_STRENGTH, strength)
                .putFloat(KEY_EXPOSURE, adjustments.exposure)
                .putFloat(KEY_CONTRAST, adjustments.contrast)
                .putFloat(KEY_HIGHLIGHTS, adjustments.highlights)
                .putFloat(KEY_SHADOWS, adjustments.shadows)
                .putFloat(KEY_TEMPERATURE, adjustments.temperature)
                .putFloat(KEY_SATURATION, adjustments.saturation)
                .putLong(KEY_TRIM_START_MS, trimStartMs)
                .putLong(KEY_TRIM_END_MS, trimEndMs)
                .putInt(KEY_TARGET_HEIGHT, targetHeight)
                .putInt(KEY_BITRATE, bitrate)
                .apply { lutPath?.let { putString(KEY_LUT_PATH, it) } }
                .build()
            return OneTimeWorkRequestBuilder<VideoExportWorker>()
                .setInputData(data)
                .build()
        }
    }
}
