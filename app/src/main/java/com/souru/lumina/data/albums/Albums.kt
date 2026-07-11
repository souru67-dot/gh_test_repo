package com.souru.lumina.data.albums

import com.souru.lumina.data.MediaRepository
import com.souru.lumina.data.model.MediaItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map

/** アルバム(端末フォルダまたは将来の仮想コレクション)。 */
data class Album(
    val id: Long,
    val name: String,
    val itemCount: Int,
    val cover: MediaItem,
    val updatedAtMs: Long,
)

enum class AlbumSortOrder { UPDATED, NAME }

/**
 * アルバム一覧のデータソース抽象。現在は端末フォルダ(BUCKET)ベースのみだが、
 * 将来ユーザーが手動で作る仮想アルバム(コレクション)を追加できるよう
 * インターフェースで分離しておく。
 */
interface AlbumsSource {
    fun observeAlbums(): Flow<List<Album>>
}

/** MediaStoreのBUCKET_ID / BUCKET_DISPLAY_NAMEをアルバムとして扱うソース。 */
class BucketAlbumsSource(
    private val mediaRepository: MediaRepository,
) : AlbumsSource {

    override fun observeAlbums(): Flow<List<Album>> =
        mediaRepository.observeMedia()
            .map { media ->
                media
                    .filter { it.bucketId != 0L }
                    .groupBy { it.bucketId }
                    .map { (bucketId, items) ->
                        val latest = items.maxBy { it.dateTakenMs }
                        Album(
                            id = bucketId,
                            name = items.firstNotNullOfOrNull { it.bucketName } ?: "その他",
                            itemCount = items.size,
                            cover = latest,
                            updatedAtMs = latest.dateTakenMs,
                        )
                    }
            }
            .flowOn(Dispatchers.Default)
}
