package com.souru.lumina.data.video

import android.content.Context
import android.media.MediaCodecInfo
import android.media.MediaExtractor
import android.media.MediaFormat
import android.net.Uri
import android.util.Log

/**
 * 入力動画の色特性。MediaExtractorのタグから取得し、欠落分は
 * 解像度・プロファイルから推定する([inferred] = true)。
 */
data class VideoColorInfo(
    val standard: Int,   // MediaFormat.COLOR_STANDARD_*
    val transfer: Int,   // MediaFormat.COLOR_TRANSFER_*
    val range: Int,      // MediaFormat.COLOR_RANGE_*
    val bitDepth: Int,   // 8 or 10
    val inferred: Boolean,
) {
    val isHdr: Boolean
        get() = transfer == MediaFormat.COLOR_TRANSFER_HLG ||
            transfer == MediaFormat.COLOR_TRANSFER_ST2084

    /** 編集画面のバッジ表示用(例: "HLG 10bit")。 */
    val badgeLabel: String
        get() {
            val base = when (transfer) {
                MediaFormat.COLOR_TRANSFER_HLG -> "HLG"
                MediaFormat.COLOR_TRANSFER_ST2084 -> "PQ"
                else -> "SDR"
            }
            val depth = if (bitDepth == 10) " 10bit" else ""
            val mark = if (inferred) "?" else ""
            return "$base$depth$mark"
        }
}

/** 出力ファイルの色タグ自己検証の結果。 */
data class ColorTagCheck(
    val ok: Boolean,
    val detail: String,
)

object VideoColorAnalyzer {

    private const val TAG = "VideoColor"

    /** 入力動画の色特性を検出する(IOで呼ぶこと)。 */
    fun detect(context: Context, uri: Uri): VideoColorInfo? = runCatching {
        withVideoTrackFormat(context, uri) { format -> analyze(format) }
    }.getOrNull()

    private fun analyze(format: MediaFormat): VideoColorInfo {
        var inferred = false
        val width = format.intOrNull(MediaFormat.KEY_WIDTH) ?: 0
        val height = format.intOrNull(MediaFormat.KEY_HEIGHT) ?: 0
        val profile = format.intOrNull(MediaFormat.KEY_PROFILE)
        val mime = format.getString(MediaFormat.KEY_MIME).orEmpty()

        // ビット深度: 10bitプロファイルから判定
        val bitDepth = when {
            mime == MediaFormat.MIMETYPE_VIDEO_HEVC &&
                (profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10 ||
                    profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10 ||
                    profile == MediaCodecInfo.CodecProfileLevel.HEVCProfileMain10HDR10Plus) -> 10

            mime == MediaFormat.MIMETYPE_VIDEO_AVC &&
                profile == MediaCodecInfo.CodecProfileLevel.AVCProfileHigh10 -> 10

            else -> 8
        }

        var transfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
        var standard = format.intOrNull(MediaFormat.KEY_COLOR_STANDARD)
        var range = format.intOrNull(MediaFormat.KEY_COLOR_RANGE)

        if (standard == null) {
            // タグ欠落時の推定: 10bitの高解像度素材はBT.2020である可能性が高い
            standard = if (bitDepth == 10 && maxOf(width, height) >= 3000) {
                MediaFormat.COLOR_STANDARD_BT2020
            } else {
                MediaFormat.COLOR_STANDARD_BT709
            }
            inferred = true
        }
        if (transfer == null) {
            // 10bit + BT.2020ならHLG(Xperiaのカメラログ系はHLG収録)、それ以外はSDR
            transfer = if (bitDepth == 10 && standard == MediaFormat.COLOR_STANDARD_BT2020) {
                MediaFormat.COLOR_TRANSFER_HLG
            } else {
                MediaFormat.COLOR_TRANSFER_SDR_VIDEO
            }
            inferred = true
        }
        if (range == null) {
            range = MediaFormat.COLOR_RANGE_LIMITED
            inferred = true
        }
        return VideoColorInfo(standard, transfer, range, bitDepth, inferred)
    }

    /**
     * 書き出したファイルを開き直し、SNS向け正規化の期待値
     * (BT.709 / SDR / limited)どおりに色タグが書かれているか検証する。
     * MediaExtractorはMP4のcolrボックス由来のタグを返すため、
     * コンテナレベルの検証を兼ねる。
     */
    fun verifySdrBt709(path: String): ColorTagCheck = runCatching {
        val extractor = MediaExtractor()
        try {
            extractor.setDataSource(path)
            val format = findVideoTrack(extractor)
                ?: return ColorTagCheck(false, "映像トラックが見つかりません")
            val standard = format.intOrNull(MediaFormat.KEY_COLOR_STANDARD)
            val transfer = format.intOrNull(MediaFormat.KEY_COLOR_TRANSFER)
            val range = format.intOrNull(MediaFormat.KEY_COLOR_RANGE)
            val ok = standard == MediaFormat.COLOR_STANDARD_BT709 &&
                transfer == MediaFormat.COLOR_TRANSFER_SDR_VIDEO &&
                range == MediaFormat.COLOR_RANGE_LIMITED
            val detail = "standard=$standard transfer=$transfer range=$range " +
                "(期待: standard=${MediaFormat.COLOR_STANDARD_BT709} " +
                "transfer=${MediaFormat.COLOR_TRANSFER_SDR_VIDEO} " +
                "range=${MediaFormat.COLOR_RANGE_LIMITED})"
            if (!ok) Log.w(TAG, "出力色タグの検証に失敗: $detail")
            ColorTagCheck(ok, detail)
        } finally {
            extractor.release()
        }
    }.getOrElse { t ->
        Log.w(TAG, "出力色タグの検証でエラー", t)
        ColorTagCheck(false, "検証エラー: ${t.message}")
    }

    private inline fun <T> withVideoTrackFormat(
        context: Context,
        uri: Uri,
        block: (MediaFormat) -> T,
    ): T? {
        val extractor = MediaExtractor()
        return try {
            extractor.setDataSource(context, uri, null)
            findVideoTrack(extractor)?.let(block)
        } finally {
            extractor.release()
        }
    }

    private fun findVideoTrack(extractor: MediaExtractor): MediaFormat? {
        for (i in 0 until extractor.trackCount) {
            val format = extractor.getTrackFormat(i)
            if (format.getString(MediaFormat.KEY_MIME).orEmpty().startsWith("video/")) {
                return format
            }
        }
        return null
    }

    private fun MediaFormat.intOrNull(key: String): Int? =
        if (containsKey(key)) getInteger(key) else null
}
