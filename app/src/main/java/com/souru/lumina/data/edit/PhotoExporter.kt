package com.souru.lumina.data.edit

import android.content.ContentValues
import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.Canvas
import android.graphics.ColorSpace
import android.graphics.HardwareRenderer
import android.graphics.ImageDecoder
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.RecordingCanvas
import android.graphics.RectF
import android.graphics.RenderNode
import android.graphics.RuntimeShader
import android.graphics.Shader
import android.hardware.HardwareBuffer
import android.media.ImageReader
import android.net.Uri
import android.provider.MediaStore
import androidx.exifinterface.media.ExifInterface
import com.souru.lumina.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt

/**
 * 写真の非破壊書き出し。元ファイルは変更せず、調整・トリミング・回転を
 * 焼き込んだJPEGを Pictures/Lumina に別名保存する。Exifは可能な範囲で
 * 元ファイルから引き継ぐ。
 */
object PhotoExporter {

    private const val MAX_DIMENSION = 8192
    private const val JPEG_QUALITY = 95
    const val ALBUM_DIR = "Pictures/Lumina"

    /**
     * @param cropRect 回転適用後の画像に対する正規化(0..1)クロップ領域。nullで全体
     * @param rotationDeg 0/90/180/270
     * @param filterStrip フィルタLUT(強度ベイク済みストリップ)。nullでフィルタなし
     */
    suspend fun export(
        context: Context,
        source: MediaItem,
        adjustments: Adjustments,
        cropRect: RectF?,
        rotationDeg: Int,
        filterStrip: Bitmap? = null,
    ): Uri = withContext(Dispatchers.Default) {
        val decoded = decodeFull(context, source)
        val transformed = rotateAndCrop(decoded, rotationDeg, cropRect)
        if (transformed != decoded) decoded.recycle()
        val rendered = if (adjustments.isIdentity && filterStrip == null) {
            transformed
        } else {
            renderWithShader(transformed, adjustments, filterStrip)
                .also { transformed.recycle() }
        }
        val uri = saveToMediaStore(context, rendered, source)
        rendered.recycle()
        uri
    }

    private fun decodeFull(context: Context, source: MediaItem): Bitmap {
        val decoderSource = ImageDecoder.createSource(context.contentResolver, source.uri)
        return ImageDecoder.decodeBitmap(decoderSource) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val maxDim = max(info.size.width, info.size.height)
            if (maxDim > MAX_DIMENSION) {
                val sample = (maxDim + MAX_DIMENSION - 1) / MAX_DIMENSION
                decoder.setTargetSampleSize(sample)
            }
        }
    }

    private fun rotateAndCrop(src: Bitmap, rotationDeg: Int, cropRect: RectF?): Bitmap {
        val rotated = if (rotationDeg % 360 != 0) {
            val matrix = Matrix().apply { postRotate(rotationDeg.toFloat()) }
            Bitmap.createBitmap(src, 0, 0, src.width, src.height, matrix, true)
        } else {
            src
        }
        if (cropRect == null) return rotated
        val left = (cropRect.left * rotated.width).roundToInt().coerceIn(0, rotated.width - 1)
        val top = (cropRect.top * rotated.height).roundToInt().coerceIn(0, rotated.height - 1)
        val width = (cropRect.width() * rotated.width).roundToInt()
            .coerceIn(1, rotated.width - left)
        val height = (cropRect.height() * rotated.height).roundToInt()
            .coerceIn(1, rotated.height - top)
        if (left == 0 && top == 0 && width == rotated.width && height == rotated.height) {
            return rotated
        }
        val cropped = Bitmap.createBitmap(rotated, left, top, width, height)
        if (rotated != src && cropped != rotated) rotated.recycle()
        return cropped
    }

    /**
     * プレビューと同じ RuntimeShader をオフスクリーンのGPU描画
     * (HardwareRenderer + ImageReader)で適用する。
     */
    private fun renderWithShader(
        src: Bitmap,
        adjustments: Adjustments,
        filterStrip: Bitmap?,
    ): Bitmap {
        val width = src.width
        val height = src.height
        val shader = AdjustmentShader.create().apply {
            applyAdjustments(adjustments)
            // プレビューと同じ順序(フィルタ→調整)がシェーダー内で保証される
            applyFilterLut(filterStrip)
            setInputShader(
                AdjustmentShader.CONTENT_SHADER_NAME,
                BitmapShader(src, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
            )
        }

        val reader = ImageReader.newInstance(
            width,
            height,
            PixelFormat.RGBA_8888,
            1,
            HardwareBuffer.USAGE_GPU_SAMPLED_IMAGE or HardwareBuffer.USAGE_GPU_COLOR_OUTPUT,
        )
        val renderer = HardwareRenderer()
        val node = RenderNode("photoExport")
        try {
            node.setPosition(0, 0, width, height)
            val canvas: RecordingCanvas = node.beginRecording(width, height)
            val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply { this.shader = shader }
            canvas.drawRect(0f, 0f, width.toFloat(), height.toFloat(), paint)
            node.endRecording()

            renderer.setContentRoot(node)
            renderer.setSurface(reader.surface)
            renderer.createRenderRequest().setWaitForPresent(true).syncAndDraw()

            val image = reader.acquireNextImage()
                ?: throw IllegalStateException("書き出し用フレームを取得できませんでした")
            image.use {
                val buffer = it.hardwareBuffer
                    ?: throw IllegalStateException("HardwareBufferを取得できませんでした")
                buffer.use { hb ->
                    val hardwareBitmap =
                        Bitmap.wrapHardwareBuffer(hb, ColorSpace.get(ColorSpace.Named.SRGB))
                            ?: throw IllegalStateException("Bitmap変換に失敗しました")
                    return hardwareBitmap.copy(Bitmap.Config.ARGB_8888, false)
                        .also { _ -> hardwareBitmap.recycle() }
                }
            }
        } finally {
            renderer.destroy()
            node.discardDisplayList()
            reader.close()
        }
    }

    private fun saveToMediaStore(context: Context, bitmap: Bitmap, source: MediaItem): Uri {
        val timestamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val displayName = "${source.baseName}_LUMINA_$timestamp.jpg"
        val values = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, displayName)
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            put(MediaStore.Images.Media.RELATIVE_PATH, ALBUM_DIR)
            // 他ギャラリーの日付順で正しい位置に並ぶよう撮影日時を明示する
            put(MediaStore.Images.Media.DATE_TAKEN, System.currentTimeMillis())
            put(MediaStore.Images.Media.IS_PENDING, 1)
        }
        val resolver = context.contentResolver
        val collection = MediaStore.Images.Media.getContentUri(MediaStore.VOLUME_EXTERNAL_PRIMARY)
        val uri = resolver.insert(collection, values)
            ?: throw IllegalStateException("MediaStoreへの登録に失敗しました")
        try {
            resolver.openOutputStream(uri)?.use { out ->
                if (!bitmap.compress(Bitmap.CompressFormat.JPEG, JPEG_QUALITY, out)) {
                    throw IllegalStateException("JPEGエンコードに失敗しました")
                }
            } ?: throw IllegalStateException("出力ストリームを開けませんでした")
            // Exifの書き換え(ファイル全体の書き直し)はIS_PENDING解除前に行う。
            // 解除後に書き換えるとMediaProviderのスキャン結果(サイズ等)と
            // 実ファイルが食い違い、他アプリで開けない・見えない原因になる
            copyExif(context, source, uri)
            values.clear()
            values.put(MediaStore.Images.Media.IS_PENDING, 0)
            resolver.update(uri, values, null, null)
        } catch (t: Throwable) {
            // 失敗・キャンセル時はpending行を残さない(残すと永久に不可視のゴミになる)
            resolver.delete(uri, null, null)
            throw t
        }
        return uri
    }

    // 引き継ぐExifタグ(撮影情報・GPS)。画素に関わるタグは引き継がない。
    private val EXIF_TAGS_TO_COPY = arrayOf(
        ExifInterface.TAG_DATETIME_ORIGINAL,
        ExifInterface.TAG_DATETIME_DIGITIZED,
        ExifInterface.TAG_OFFSET_TIME_ORIGINAL,
        ExifInterface.TAG_SUBSEC_TIME_ORIGINAL,
        ExifInterface.TAG_MAKE,
        ExifInterface.TAG_MODEL,
        ExifInterface.TAG_F_NUMBER,
        ExifInterface.TAG_EXPOSURE_TIME,
        ExifInterface.TAG_PHOTOGRAPHIC_SENSITIVITY,
        ExifInterface.TAG_ISO_SPEED,
        ExifInterface.TAG_FOCAL_LENGTH,
        ExifInterface.TAG_FOCAL_LENGTH_IN_35MM_FILM,
        ExifInterface.TAG_LENS_MAKE,
        ExifInterface.TAG_LENS_MODEL,
        ExifInterface.TAG_APERTURE_VALUE,
        ExifInterface.TAG_SHUTTER_SPEED_VALUE,
        ExifInterface.TAG_EXPOSURE_BIAS_VALUE,
        ExifInterface.TAG_METERING_MODE,
        ExifInterface.TAG_WHITE_BALANCE,
        ExifInterface.TAG_FLASH,
        ExifInterface.TAG_GPS_LATITUDE,
        ExifInterface.TAG_GPS_LATITUDE_REF,
        ExifInterface.TAG_GPS_LONGITUDE,
        ExifInterface.TAG_GPS_LONGITUDE_REF,
        ExifInterface.TAG_GPS_ALTITUDE,
        ExifInterface.TAG_GPS_ALTITUDE_REF,
        ExifInterface.TAG_GPS_TIMESTAMP,
        ExifInterface.TAG_GPS_DATESTAMP,
    )

    private fun copyExif(context: Context, source: MediaItem, dest: Uri) {
        runCatching {
            val attrs = context.contentResolver.openInputStream(source.uri)?.use { input ->
                val exif = ExifInterface(input)
                EXIF_TAGS_TO_COPY.mapNotNull { tag ->
                    exif.getAttribute(tag)?.let { tag to it }
                }
            } ?: return
            context.contentResolver.openFileDescriptor(dest, "rw")?.use { pfd ->
                val destExif = ExifInterface(pfd.fileDescriptor)
                attrs.forEach { (tag, value) -> destExif.setAttribute(tag, value) }
                // 画素は回転焼き込み済みなので向きは常にNORMAL
                destExif.setAttribute(
                    ExifInterface.TAG_ORIENTATION,
                    ExifInterface.ORIENTATION_NORMAL.toString(),
                )
                destExif.saveAttributes()
            }
        }
    }

    /** プレビュー用の縮小ビットマップを読み込む。 */
    suspend fun decodePreview(context: Context, source: MediaItem, targetMax: Int = 2048): Bitmap =
        withContext(Dispatchers.IO) {
            val decoderSource = ImageDecoder.createSource(context.contentResolver, source.uri)
            ImageDecoder.decodeBitmap(decoderSource) { decoder, info, _ ->
                decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
                val maxDim = max(info.size.width, info.size.height)
                if (maxDim > targetMax) {
                    decoder.setTargetSampleSize(max(1, maxDim / targetMax))
                }
            }
        }
}
