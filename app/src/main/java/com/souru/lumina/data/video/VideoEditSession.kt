package com.souru.lumina.data.video

import androidx.media3.common.Effect

/**
 * 動画編集セッションの現在エフェクトを画面間で共有する軽量ホルダー。
 * 投稿プレビュー(リール)で「LUT適用後のプレビューがそのまま再生される」
 * ことを保証するために、編集画面がエフェクト再計算のたびに書き込む。
 */
class VideoEditSession {
    @Volatile
    var mediaId: Long? = null

    @Volatile
    var effects: List<Effect> = emptyList()

    fun update(mediaId: Long, effects: List<Effect>) {
        this.mediaId = mediaId
        this.effects = effects
    }

    fun effectsFor(mediaId: Long): List<Effect> =
        if (this.mediaId == mediaId) effects else emptyList()
}
