package com.souru.lumina.util

import android.content.Context
import android.net.Uri
import androidx.exifinterface.media.ExifInterface

/**
 * メディアの表示向き(0/90/180/270)の解決を一元化する。
 * 優先順: MediaStoreのORIENTATIONカラム → ファイル自体のEXIF。
 * サムネイル・ビューアなど全読み込み経路がこれを使い、重複実装を作らない。
 */
object MediaOrientation {

    fun resolve(context: Context, uri: Uri, mediaStoreOrientation: Int): Int {
        val normalized = normalize(mediaStoreOrientation)
        if (normalized != 0) return normalized
        return runCatching {
            context.contentResolver.openInputStream(uri)?.use { input ->
                ExifInterface(input).rotationDegrees
            } ?: 0
        }.getOrElse { 0 }.let(::normalize)
    }

    fun normalize(degrees: Int): Int = ((degrees % 360) + 360) % 360
}
