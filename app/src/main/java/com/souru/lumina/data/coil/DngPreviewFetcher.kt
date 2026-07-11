package com.souru.lumina.data.coil

import android.content.Context
import android.graphics.ImageDecoder
import android.net.Uri
import androidx.exifinterface.media.ExifInterface
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.decode.ImageSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.fetch.SourceFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse
import com.souru.lumina.util.MediaOrientation
import okio.Buffer
import kotlin.math.max

/**
 * DNGのビューア表示用フェッチ対象。フルデコードは重い(12bit RAWの
 * デモザイク)ため、DNGコンテナ内の埋め込みプレビューJPEGを
 * ExifInterface経由で優先的に取り出す。埋め込みが無い場合のみ
 * ImageDecoderでフォールバックデコードする。
 *
 * 抽出した生のプレビューJPEGにはEXIF Orientationが含まれないため、
 * DNG本体の向き(MediaStoreのORIENTATION → DNGのEXIFの順)を
 * ここで一律に適用する(グリッド・ビューア・切替すべてで向きが揃う)。
 *
 * @param orientationDeg MediaStoreのORIENTATION(0なら本体EXIFへフォールバック)
 */
data class DngPreview(val uri: Uri, val id: Long, val orientationDeg: Int = 0)

class DngPreviewKeyer : Keyer<DngPreview> {
    override fun key(data: DngPreview, options: Options): String =
        "dng-preview:${data.id}:${data.orientationDeg}"
}

class DngPreviewFetcher(
    private val context: Context,
    private val data: DngPreview,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        // 向き解決は共通ロジックに一元化(MediaStore ORIENTATION → EXIF)
        val rotation = MediaOrientation.resolve(context, data.uri, data.orientationDeg)

        val bytes = runCatching {
            context.contentResolver.openInputStream(data.uri)?.use { input ->
                val exif = ExifInterface(input)
                if (exif.hasThumbnail()) exif.thumbnailBytes else null
            }
        }.getOrNull()

        if (bytes != null) {
            if (rotation == 0) {
                // 高速パス: 回転不要ならデコードせずソースのまま渡す
                return SourceFetchResult(
                    source = ImageSource(
                        source = Buffer().apply { write(bytes) },
                        fileSystem = options.fileSystem,
                    ),
                    mimeType = "image/jpeg",
                    dataSource = DataSource.DISK,
                )
            }
            android.graphics.BitmapFactory.decodeByteArray(bytes, 0, bytes.size)?.let { bitmap ->
                return ImageFetchResult(
                    image = bitmap.rotatedBy(rotation).asImage(),
                    isSampled = true,
                    dataSource = DataSource.DISK,
                )
            }
        }
        return fullDecode(rotation)
    }

    /**
     * フォールバックのフルデコード。ImageDecoderはJPEGと違い
     * DNG(TIFF)のOrientationを適用しないため、ここでも回転を適用する。
     */
    private fun fullDecode(rotation: Int): FetchResult {
        val targetWidth = options.size.width.pxOrElse { 2048 }
        val source = ImageDecoder.createSource(context.contentResolver, data.uri)
        val bitmap = ImageDecoder.decodeBitmap(source) { decoder, info, _ ->
            decoder.allocator = ImageDecoder.ALLOCATOR_SOFTWARE
            val sample = max(1, info.size.width / max(1, targetWidth))
            if (sample > 1) decoder.setTargetSampleSize(sample)
        }
        return ImageFetchResult(
            image = bitmap.rotatedBy(rotation).asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<DngPreview> {
        override fun create(data: DngPreview, options: Options, imageLoader: ImageLoader): Fetcher =
            DngPreviewFetcher(context, data, options)
    }
}
