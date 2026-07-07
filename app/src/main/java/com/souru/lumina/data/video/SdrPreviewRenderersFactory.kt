package com.souru.lumina.data.video

import android.content.Context
import android.media.MediaFormat
import android.os.Handler
import androidx.media3.common.ColorInfo
import androidx.media3.common.Format
import androidx.media3.common.util.UnstableApi
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.Renderer
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.video.MediaCodecVideoRenderer
import androidx.media3.exoplayer.video.VideoRendererEventListener

/**
 * 編集プレビュー用のRenderersFactory。HDR(HLG/PQ)入力のとき、デコーダーに
 * SDRへのトーンマップを要求する(KEY_COLOR_TRANSFER_REQUEST)。
 *
 * これによりLUT・調整のGLエフェクトは常にSDR(BT.709)信号に対して適用され、
 * 書き出し(TransformerのHDR_MODE_TONE_MAP_HDR_TO_SDR)と同じ
 * 「トーンマップ→LUT」の順序がプレビューでも保証される。
 */
@UnstableApi
class SdrPreviewRenderersFactory(context: Context) : DefaultRenderersFactory(context) {

    override fun buildVideoRenderers(
        context: Context,
        extensionRendererMode: Int,
        mediaCodecSelector: MediaCodecSelector,
        enableDecoderFallback: Boolean,
        eventHandler: Handler,
        eventListener: VideoRendererEventListener,
        allowedVideoJoiningTimeMs: Long,
        out: ArrayList<Renderer>,
    ) {
        out.add(
            object : MediaCodecVideoRenderer(
                context,
                codecAdapterFactory,
                mediaCodecSelector,
                allowedVideoJoiningTimeMs,
                enableDecoderFallback,
                eventHandler,
                eventListener,
                MAX_DROPPED_VIDEO_FRAME_COUNT_TO_NOTIFY,
            ) {
                override fun getMediaFormat(
                    format: Format,
                    codecMimeType: String,
                    codecMaxValues: MediaCodecVideoRenderer.CodecMaxValues,
                    codecOperatingRate: Float,
                    deviceNeedsNoPostProcessWorkaround: Boolean,
                    tunnelingAudioSessionId: Int,
                ): MediaFormat {
                    val mediaFormat = super.getMediaFormat(
                        format,
                        codecMimeType,
                        codecMaxValues,
                        codecOperatingRate,
                        deviceNeedsNoPostProcessWorkaround,
                        tunnelingAudioSessionId,
                    )
                    if (ColorInfo.isTransferHdr(format.colorInfo)) {
                        mediaFormat.setInteger(
                            MediaFormat.KEY_COLOR_TRANSFER_REQUEST,
                            MediaFormat.COLOR_TRANSFER_SDR_VIDEO,
                        )
                    }
                    return mediaFormat
                }
            },
        )
    }
}
