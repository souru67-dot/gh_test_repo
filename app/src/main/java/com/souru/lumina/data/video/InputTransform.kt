package com.souru.lumina.data.video

import kotlin.math.exp
import kotlin.math.pow
import kotlin.math.sqrt

/** 入力変換の精度区分。UIに「(近似)」等を出し分けるのに使う。 */
enum class InputAccuracy { OFFICIAL, APPROX, NONE }

/**
 * 入力変換の微調整。各社Logの個体差・撮影露出のばらつき吸収用。
 * exposureEv: ±2EV(scene-linearで 2^EV 倍)、contrast: ピボット周りの微調整。
 */
data class InputTransformParams(
    val exposureEv: Float = 0f,
    val contrast: Float = 0f,
) {
    val isIdentity: Boolean get() = exposureEv == 0f && contrast == 0f
}

/**
 * 入力変換(各社Log → Rec.709)。クリエイティブLUTの前段でLog素材を709へ
 * 正規化し、どの機材のLogでも同じプリセットが同じ色の方向性で使えるようにする。
 *
 * 精度区分:
 *  - OFFICIAL: 公開仕様の数式を実装(S-Log3 / Apple Log)。HLGは標準規格だが
 *    実運用ではデコーダのSDRトーンマップが709化を担うため恒等に委譲する。
 *  - APPROX: 公開カーブが無い/非公開のため近似(汎用Log・D-Log-M・S-Cinetone・
 *    各スマホベンダーLog)。UIに「(近似)」を明示し、厳密対応を偽らない。
 *  - NONE: 変換なし(Rec.709入力とみなす)。既定。
 *
 * 数式・定数は tools/lut_analysis/input_transforms.py で検証済み(単調・レンジ・
 * S-Log3 18%グレー→709≈0.44)。
 */
enum class InputTransform(
    val id: String,
    val displayName: String,
    val accuracy: InputAccuracy,
) {
    NONE("none", "なし(Rec.709)", InputAccuracy.NONE),
    SLOG3("slog3", "Sony S-Log3 / S-Gamut3.Cine", InputAccuracy.OFFICIAL),
    APPLE_LOG("apple_log", "Apple Log", InputAccuracy.OFFICIAL),
    HLG("hlg", "HLG (BT.2100)", InputAccuracy.OFFICIAL),
    GENERAL_LOG("general", "汎用Log", InputAccuracy.APPROX),
    DLOG_M("dlog_m", "DJI D-Log-M (近似)", InputAccuracy.APPROX),
    SCINETONE("scinetone", "S-Cinetone for Mobile", InputAccuracy.APPROX),
    OPPO_LOG("oppo_log", "OPPO Log (近似)", InputAccuracy.APPROX),
    XIAOMI_LOG("xiaomi_log", "Xiaomi Log (近似)", InputAccuracy.APPROX),
    VIVO_LOG("vivo_log", "vivo Log (近似)", InputAccuracy.APPROX),
    GALAXY_LOG("galaxy_log", "Galaxy Log (近似)", InputAccuracy.APPROX),
    ;

    /**
     * 1画素(0..1)を Rec.709 へ変換して [out] に書き込む。[hdrToneMapped] が true の
     * ときは、HDR素材がデコーダで既にSDR(709)へトーンマップ済みであることを意味し、
     * Log系decodeは二重適用を避けるため恒等にする(EV/コントラストのみ適用)。
     */
    fun apply(r: Float, g: Float, b: Float, params: InputTransformParams, hdrToneMapped: Boolean, out: FloatArray) {
        // NONE / HLG(トーンマップ委譲)/ HDRトーンマップ済み は decode を行わない
        val decodeSkipped = this == NONE || this == HLG || hdrToneMapped
        if (decodeSkipped) {
            out[0] = r; out[1] = g; out[2] = b
            if (!params.isIdentity) applyTrim(out, params)
            return
        }

        // 1) Log → scene-linear(チャンネル毎)
        var lr: Float
        var lg: Float
        var lb: Float
        when (this) {
            SLOG3 -> { lr = slog3(r); lg = slog3(g); lb = slog3(b) }
            APPLE_LOG -> { lr = appleLog(r); lg = appleLog(g); lb = appleLog(b) }
            DLOG_M -> { lr = generalLog(r, 0.095f, 2.1f); lg = generalLog(g, 0.095f, 2.1f); lb = generalLog(b, 0.095f, 2.1f) }
            OPPO_LOG -> { lr = generalLog(r, 0.08f, 1.95f); lg = generalLog(g, 0.08f, 1.95f); lb = generalLog(b, 0.08f, 1.95f) }
            XIAOMI_LOG -> { lr = generalLog(r, 0.10f, 2.0f); lg = generalLog(g, 0.10f, 2.0f); lb = generalLog(b, 0.10f, 2.0f) }
            VIVO_LOG -> { lr = generalLog(r, 0.09f, 1.9f); lg = generalLog(g, 0.09f, 1.9f); lb = generalLog(b, 0.09f, 1.9f) }
            GALAXY_LOG -> { lr = generalLog(r, 0.085f, 2.0f); lg = generalLog(g, 0.085f, 2.0f); lb = generalLog(b, 0.085f, 2.0f) }
            SCINETONE -> {
                // S-CinetoneはLogではなくピクチャープロファイル。軽い復元のみ行い
                // 既存ユーザーの見た目が大きく変わらないようにする(彩度は後段の
                // クリエイティブLUT/調整に委ねる)。
                sCinetone(r, g, b, out)
                if (!params.isIdentity) applyTrim(out, params)
                return
            }
            else -> { lr = generalLog(r, 0.09f, 1.9f); lg = generalLog(g, 0.09f, 1.9f); lb = generalLog(b, 0.09f, 1.9f) }
        }

        // 2) 露出オフセット(scene-linearで)
        if (params.exposureEv != 0f) {
            val ev = 2f.pow(params.exposureEv)
            lr *= ev; lg *= ev; lb *= ev
        }

        // 3) 色域マトリクス(scene-linear空間、709外は0クリップ)
        val (mr, mg, mb) = when (this) {
            SLOG3 -> matMul(lr, lg, lb, M_SCINE_TO_709)
            APPLE_LOG -> matMul(lr, lg, lb, M_2020_TO_709)
            else -> Triple(lr, lg, lb) // 近似系は色域変換を省略(709primaries想定)
        }

        // 4) scene-linear → Rec.709 表示エンコード
        out[0] = encode709(mr)
        out[1] = encode709(mg)
        out[2] = encode709(mb)

        // 5) コントラスト微調整(709空間、ピボット0.44)
        if (params.contrast != 0f) applyContrast(out, params.contrast)
    }

    private fun applyTrim(out: FloatArray, params: InputTransformParams) {
        if (params.exposureEv != 0f) {
            val ev = 2f.pow(params.exposureEv)
            // 709空間の露出は近似的にゲイン
            out[0] = (out[0] * ev).coerceIn(0f, 1f)
            out[1] = (out[1] * ev).coerceIn(0f, 1f)
            out[2] = (out[2] * ev).coerceIn(0f, 1f)
        }
        if (params.contrast != 0f) applyContrast(out, params.contrast)
    }

    private fun applyContrast(out: FloatArray, contrast: Float) {
        val gain = 1f + contrast
        for (i in 0..2) {
            out[i] = ((out[i] - PIVOT) * gain + PIVOT).coerceIn(0f, 1f)
        }
    }

    companion object {
        private const val PIVOT = 0.44f

        fun fromId(id: String?): InputTransform =
            entries.firstOrNull { it.id == id } ?: NONE

        // --- Log → scene-linear(公開仕様/近似) ---
        /** Sony S-Log3 公式 decode。 */
        private fun slog3(x: Float): Float {
            val bp = 171.2102946929f / 1023f
            return if (x >= bp) {
                (10f.pow(((x * 1023f) - 420f) / 261.5f)) * 0.19f - 0.01f
            } else {
                ((x * 1023f) - 95f) * 0.01125000f / (171.2102946929f - 95f)
            }
        }

        /** Apple Log Profile 公式 decode。 */
        private fun appleLog(p: Float): Float {
            val r0 = -0.05641088f
            val rt = 0.01f
            val c = 47.28711236f
            val beta = 0.00964052f
            val gamma = 0.08550479f
            val delta = 0.69336945f
            val pt = c * (rt - r0) * (rt - r0)
            return when {
                p < 0f -> r0
                p < pt -> sqrt(p / c) + r0
                else -> 2f.pow((p - delta) / gamma) - beta
            }
        }

        /** 未知Log向けの汎用decode(近似)。持ち上がった黒を戻し指数で伸ばす。 */
        private fun generalLog(p: Float, black: Float, gain: Float): Float {
            val num = 10f.pow((p - black) * gain) - 1f
            val den = 10f.pow((1f - black) * gain) - 1f
            return num / den
        }

        /** S-Cinetone for Mobile の軽い復元(近似、Logではない)。 */
        private fun sCinetone(r: Float, g: Float, b: Float, out: FloatArray) {
            // 軽いコントラスト(ピボット周り)のみ。彩度は後段に委ねる。
            val con = 1.08f
            out[0] = ((r - PIVOT) * con + PIVOT).coerceIn(0f, 1f)
            out[1] = ((g - PIVOT) * con + PIVOT).coerceIn(0f, 1f)
            out[2] = ((b - PIVOT) * con + PIVOT).coerceIn(0f, 1f)
        }

        // --- scene-linear → Rec.709 表示エンコード ---
        // 18%グレー(linear)を 0.44 に合わせる露出スケール + 軽いハイライトロールオフ。
        private const val ENC_GAMMA = 2.2f
        private const val ENC_GREY_IN = 0.18f
        private const val ENC_GREY_OUT = 0.44f
        private const val ENC_KNEE = 0.90f
        private val ENC_SCALE = (ENC_GREY_OUT.toDouble().pow(ENC_GAMMA.toDouble()) / ENC_GREY_IN).toFloat()

        private fun encode709(lin: Float): Float {
            val l = if (lin < 0f) 0f else lin
            var y = (l * ENC_SCALE).pow(1f / ENC_GAMMA)
            if (y > ENC_KNEE) {
                y = ENC_KNEE + (1f - exp(-(y - ENC_KNEE) / (1f - ENC_KNEE))) * (1f - ENC_KNEE)
            }
            return y.coerceIn(0f, 1f)
        }

        // 色域マトリクス(RGB→RGB、scene-linear)。行優先。
        // tools/lut_analysis/input_transforms.py で算出。
        private val M_SCINE_TO_709 = floatArrayOf(
            1.6269f, -0.5401f, -0.0868f,
            -0.1785f, 1.4179f, -0.2394f,
            -0.0444f, -0.1959f, 1.2402f,
        )
        private val M_2020_TO_709 = floatArrayOf(
            1.6605f, -0.5876f, -0.0728f,
            -0.1246f, 1.1329f, -0.0083f,
            -0.0182f, -0.1006f, 1.1187f,
        )

        private fun matMul(r: Float, g: Float, b: Float, m: FloatArray): Triple<Float, Float, Float> {
            val rr = (m[0] * r + m[1] * g + m[2] * b).coerceAtLeast(0f)
            val gg = (m[3] * r + m[4] * g + m[5] * b).coerceAtLeast(0f)
            val bb = (m[6] * r + m[7] * g + m[8] * b).coerceAtLeast(0f)
            return Triple(rr, gg, bb)
        }
    }
}
