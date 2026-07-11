package com.souru.lumina.data.video

/**
 * 書き出しプリセット。どちらも出力はSDR 8bit / BT.709 / limitedに正規化される
 * (SNSのアップロード再エンコードがSDR・BT.709前提のため)。
 */
enum class VideoExportPreset(val label: String, val description: String) {
    SNS_STANDARD(
        label = "SNS標準",
        description = "SDR / BT.709 / H.264・解像度は元のまま(上限4K)。" +
            "Instagram等のSNSでも色が変わりにくい設定です",
    ),
    HIGH_QUALITY_ARCHIVE(
        label = "高品質アーカイブ",
        description = "同じくSDR / BT.709のまま高ビットレートで保存します",
    ),
}
