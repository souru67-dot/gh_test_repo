package com.souru.lumina.data.model

/**
 * ギャラリーの表示フィルタ状態。
 *
 * - [type]: メディア種別(すべて/写真のみ/動画のみ)。動画の表示可否はこの軸だけで決まる
 * - [format]: 写真の形式(すべて/JPEG/RAW)。写真にのみ作用し、動画には影響しない
 *
 * 「動画のみ+RAW」のような矛盾した組み合わせは [normalized] で正規化してから
 * 保存・使用する。判定ロジックはAndroid非依存でユニットテスト可能。
 */
data class GalleryFilter(
    val type: MediaTypeFilter = MediaTypeFilter.ALL,
    val format: RawFilterMode = RawFilterMode.JPEG,
) {
    /** 矛盾した組み合わせを正規化する(動画のみ選択時は形式をすべてに戻す)。 */
    fun normalized(): GalleryFilter =
        if (type == MediaTypeFilter.VIDEO && format != RawFilterMode.ALL) {
            copy(format = RawFilterMode.ALL)
        } else {
            this
        }

    /**
     * 1アイテムの可視判定。
     * 動画: 種別フィルタのみで決まる(形式フィルタは作用しない)
     * 写真: 種別が「動画のみ」でないこと + 形式フィルタに一致すること
     */
    fun matches(isVideo: Boolean, isRaw: Boolean, isJpeg: Boolean): Boolean = when {
        isVideo -> type != MediaTypeFilter.PHOTO
        else -> type != MediaTypeFilter.VIDEO && when (format) {
            RawFilterMode.ALL -> true
            RawFilterMode.JPEG -> isJpeg
            RawFilterMode.RAW -> isRaw
        }
    }

    companion object {
        val DEFAULT = GalleryFilter()

        /** 保存値からの復元。未知/不正な値はデフォルトにフォールバックする。 */
        fun fromStored(typeValue: String?, formatValue: String?): GalleryFilter =
            GalleryFilter(
                type = typeValue?.let { v -> MediaTypeFilter.entries.firstOrNull { it.name == v } }
                    ?: MediaTypeFilter.ALL,
                format = formatValue?.let { v -> RawFilterMode.entries.firstOrNull { it.name == v } }
                    ?: RawFilterMode.JPEG,
            ).normalized()
    }
}
