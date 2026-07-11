package com.souru.lumina.data.model

/**
 * ギャラリーの表示フィルタ状態。
 *
 * - [type]: メディア種別(すべて/写真のみ/動画のみ)
 * - [format]: 写真の形式(すべて/JPEG/RAW)
 *
 * 動画の表示条件を明確化する: 動画は「種別が写真以外(すべて/動画のみ)」かつ
 * 「写真形式がすべて」のときだけ表示する。写真形式にJPEG/RAWを指定した時点で
 * 対象は写真に限定され、動画は必ず除外される。
 *
 * 「動画のみ+RAW」のような矛盾した組み合わせは [normalized] で正規化してから
 * 保存・使用する。判定ロジックはAndroid非依存でユニットテスト可能。
 */
data class GalleryFilter(
    val type: MediaTypeFilter = MediaTypeFilter.ALL,
    // 既定は「すべて」。JPEGを既定にするとPNG(スクリーンショット等)や
    // 動画が初期表示で除外され「一覧に出てこない」と見えてしまうため
    val format: RawFilterMode = RawFilterMode.ALL,
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
     * 動画: 種別が「写真のみ」でない かつ 形式が「すべて」のときのみ表示
     *       (形式にJPEG/RAWを選んだ時点で対象は写真限定になり動画は除外)
     * 写真: 種別が「動画のみ」でないこと + 形式フィルタに一致すること
     */
    fun matches(isVideo: Boolean, isRaw: Boolean, isJpeg: Boolean): Boolean = when {
        // 動画のみ選択時は常に動画を表示。すべて選択時は写真形式が
        // 「すべて」のときだけ(JPEG/RAW指定は写真限定なので動画は除外)
        isVideo -> type == MediaTypeFilter.VIDEO ||
            (type == MediaTypeFilter.ALL && format == RawFilterMode.ALL)
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
                    ?: RawFilterMode.ALL,
            ).normalized()
    }
}
