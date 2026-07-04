package com.souru.lumina.data.coil

import android.content.Context
import android.net.Uri
import android.util.Size
import coil3.ImageLoader
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import coil3.size.pxOrElse

/**
 * グリッドサムネイル用のフェッチ対象。MediaStore のサムネイルキャッシュ
 * (contentResolver.loadThumbnail)を使うことで、DNG(埋め込みプレビュー)も
 * 動画も高速に取得できる。
 */
data class MediaThumb(val uri: Uri, val id: Long)

class MediaThumbKeyer : Keyer<MediaThumb> {
    override fun key(data: MediaThumb, options: Options): String {
        val w = options.size.width.pxOrElse { 512 }
        return "media-thumb:${data.id}:$w"
    }
}

class MediaThumbFetcher(
    private val context: Context,
    private val data: MediaThumb,
    private val options: Options,
) : Fetcher {

    override suspend fun fetch(): FetchResult {
        val width = options.size.width.pxOrElse { 512 }
        val height = options.size.height.pxOrElse { width }
        val bitmap = context.contentResolver.loadThumbnail(data.uri, Size(width, height), null)
        return ImageFetchResult(
            image = bitmap.asImage(),
            isSampled = true,
            dataSource = DataSource.DISK,
        )
    }

    class Factory(private val context: Context) : Fetcher.Factory<MediaThumb> {
        override fun create(data: MediaThumb, options: Options, imageLoader: ImageLoader): Fetcher =
            MediaThumbFetcher(context, data, options)
    }
}
