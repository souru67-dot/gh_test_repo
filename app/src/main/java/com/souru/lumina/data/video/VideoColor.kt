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

    /**
     * 入力変換([InputTransform])の自動推定。コンテナ/コーデックのタグから
     * 確実に判別できるものだけを返し、判別できない各社Logは推測で当てない
     * (誤った色になるより「なし(709)」の方が安全。ユーザーが手動選択できる)。
     *
     * - HDR(HLG/PQ): デコーダのSDRトーンマップが709化を担うため [InputTransform.HLG]
     *   (=トーンマップ委譲。decodeは行わない)。バッジには「入力: HLG(自動)」と出せる。
     * - それ以外(SDR): Log収録はメタデータに現れないことが多く、確実な判別が
     *   できないため [InputTransform.NONE](Rec.709入力とみなす)。
     *
     * 端末モデル名やベンダー独自Logは公開判別材料が乏しいため、ここでは推測せず、
     * ヒストグラムが中央に寄っている場合のUI側ヒント + 手動選択に委ねる。
     */
    fun detectInputTransform(info: VideoColorInfo?): InputTransform =
        if (info?.isHdr == true) InputTransform.HLG else InputTransform.NONE

    /**
     * 「Log素材の可能性」ヒント判定(IOで呼ぶこと)。代表フレームを1枚だけ
     * 縮小取得し、輝度ヒストグラムが中央に強く寄っている(=フラットで彩度・
     * コントラストが低いLog特有の分布)場合に true。確実な判別ではなく、
     * ユーザーに手動選択を促すためのヒントに留める(誤検出は無害)。
     */
    fun detectLogLikely(context: Context, uri: Uri): Boolean = runCatching {
        val retriever = android.media.MediaMetadataRetriever()
        try {
            retriever.setDataSource(context, uri)
            val durationMs = retriever
                .extractMetadata(android.media.MediaMetadataRetriever.METADATA_KEY_DURATION)
                ?.toLongOrNull() ?: 0L
            val atUs = (durationMs / 2).coerceAtLeast(0L) * 1000L
            val frame = retriever.getScaledFrameAtTime(
                atUs,
                android.media.MediaMetadataRetriever.OPTION_CLOSEST_SYNC,
                64,
                64,
            ) ?: return false
            var inMid = 0
            var total = 0
            val w = frame.width
            val h = frame.height
            val row = IntArray(w)
            var y = 0
            while (y < h) {
                frame.getPixels(row, 0, w, 0, y, w, 1)
                for (px in row) {
                    val r = (px shr 16) and 0xFF
                    val g = (px shr 8) and 0xFF
                    val b = px and 0xFF
                    val luma = (0.2126f * r + 0.7152f * g + 0.0722f * b) / 255f
                    if (luma in 0.22f..0.62f) inMid++
                    total++
                }
                y += 2 // 1行おきで十分
            }
            frame.recycle()
            total > 0 && inMid.toFloat() / total >= 0.88f
        } finally {
            retriever.release()
        }
    }.getOrDefault(false)

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
