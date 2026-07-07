package com.souru.lumina.data.coil

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.MediaMetadataRetriever
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import com.souru.lumina.data.model.MediaKind
import kotlin.math.max

/**
 * 外部デバイス(SAFのDocument URI)用サムネイル。MediaStoreの
 * loadThumbnailが使えないため、USB越しでも速いよう以下の優先順で取得する:
 *
 * 1. 画像: ExifInterfaceの埋め込みサムネイル(あれば全画素デコード不要)
 * 2. 画像: BitmapFactoryでinSampleSizeを効かせた縮小デコード
 * 3. 動画: MediaMetadataRetrieverのフレーム
 *
 * 結果はCoilのディスクキャッシュに載る(キーにdocId+サイズを含める)。
 */
data class ExternalThumb(
    val uri: Uri,
    val docId: String,
    val kind: MediaKind,
    val rotationDeg: Int = 0,
)

class ExternalThumbKeyer : Keyer<ExternalThumb> {
    override fun key(data: ExternalThumb, options: Options): String {
        val w = options.size.width.pxOrElse { 512 }
        return "ext-thumb:${data.docId}:$w:${data.rotationDeg}"
    }
}

class ExternalThumbFetcher(
    private val context: Context,
    private val data: ExternalThumb,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val target = options.size.width.pxOrElse { 512 }
        val bitmap = when (data.kind) {
            MediaKind.IMAGE -> decodeImage(target)
            MediaKind.VIDEO -> decodeVideoFrame()
        } ?: throw IllegalStateException("サムネイルを生成できませんでした")
        return ImageFetchResult(
            image = bitmap.rotatedBy(data.rotationDeg).asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    private fun decodeImage(target: Int): Bitmap? {
        val resolver = context.contentResolver
        // 1. 埋め込みサムネイル(JPEGは通常あり、USB越しでも高速)
        runCatching {
            resolver.openInputStream(data.uri)?.use { input ->
                val exif = ExifInterface(input)
                if (exif.hasThumbnail()) exif.thumbnailBitmap else null
            }
        }.getOrNull()?.let { return it }

        // 2. 縮小デコード(まず寸法だけ読み、inSampleSizeを決める)
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        runCatching {
            resolver.openInputStream(data.uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        }
        val sample = if (bounds.outWidth > 0) {
            max(1, bounds.outWidth / max(1, target))
        } else {
            1
        }
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        return runCatching {
            resolver.openInputStream(data.uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
        }.getOrNull()
    }

    private fun decodeVideoFrame(): Bitmap? = runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, data.uri)
            retriever.getFrameAtTime(0)
        }
    }.getOrNull()

    class Factory(private val context: Context) : Fetcher.Factory<ExternalThumb> {
        override fun create(data: ExternalThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            ExternalThumbFetcher(context, data, options)
    }
}
