package com.souru.lumina.data.luts

import kotlin.math.max

/** LUTライブラリでのカテゴリ分け。 */
enum class LutCategory(val label: String) {
    BASIC("ベーシック"),
    CINEMATIC("シネマティック"),
    EMO("エモ・ノスタルジー"),
}

/**
 * 標準搭載のLUTプリセット。すべて数学的に自前生成した.cubeデータで、
 * 実在ブランドのLUTファイルや名称は使用しない(Playストア公開時の
 * 商標・権利リスク回避)。
 *
 * いずれもLog系(低コントラスト・低彩度、S-Cinetone for Mobile等)の
 * 入力を前提にトーンカーブを設計している。Rec.709素材に当てると
 * 濃すぎる場合がある。
 */
enum class LutPreset(
    val displayName: String,
    val fileName: String,
    val category: LutCategory,
) {
    CLEAN_CONTRAST("Clean Contrast", "Clean Contrast.cube", LutCategory.BASIC),
    TEAL_ORANGE("Teal & Orange", "Teal & Orange.cube", LutCategory.CINEMATIC),
    CINEMATIC_WARM("Cinematic Warm", "Cinematic Warm.cube", LutCategory.CINEMATIC),
    CHROME_FILM("Chrome Film", "Chrome Film.cube", LutCategory.CINEMATIC),
    MONO_CINEMA("Mono Cinema", "Mono Cinema.cube", LutCategory.CINEMATIC),
    NOSTALGIC_FILM("Nostalgic Film", "Nostalgic Film.cube", LutCategory.EMO),
    EMO_DUSK("Emo Dusk", "Emo Dusk.cube", LutCategory.EMO),
    HALATION_GLOW("Halation Glow", "Halation Glow.cube", LutCategory.EMO),
    TOKYO_NIGHT("Tokyo Night", "Tokyo Night.cube", LutCategory.EMO),
    RETRO_VHS("Retro VHS", "Retro VHS.cube", LutCategory.EMO),
    AIRY_FILM("Airy Film", "Airy Film.cube", LutCategory.EMO),
    GOLDEN_MEMORIES("Golden Memories", "Golden Memories.cube", LutCategory.EMO),
    FADED_FILM("Faded Film", "Faded Film.cube", LutCategory.EMO),
}

/**
 * 作例(くすみ・エモ・ノスタルジックフィルム)の色を再現するための調整量。
 * 後から「もう少しシャドウを浮かせて」「緑をもっとくすませて」等の指示に
 * 即応できるよう、各特徴を名前付き定数として分離している。値はすべて
 * 「強度100%」時の効き量(強度スライダーでこれを線形に薄められる)。
 */
object FadedFilmParams {
    /** 黒点。わずかに浮かせて暗部にグレー/色みを残す(小さいほど黒が締まる)。 */
    const val BLACK_POINT = 0.008f
    /** 白のロールオフ開始点と、その上での圧縮スロープ(白飛び手前を柔らかく)。 */
    const val TONE_HI = 0.83f
    const val HIGHLIGHT_ROLLOFF = 0.38f
    /** コントラスト(>1で高め・黒を締める)とその中心ピボット(低めで暗部を締める)。
     *  効きを他プリセット(Teal & Orange / Cinematic Warm)並みに引き上げるため
     *  コントラストを強めに設定している。 */
    const val CONTRAST = 1.40f
    const val CONTRAST_PIVOT = 0.42f
    /** ベース彩度。マット感は保ちつつ、動画でも色が乗って見える程度に上げる。 */
    const val BASE_SATURATION = 0.85f

    /** 暗部に残す色み(わずかな緑・青)。純黒に沈む手前にグレー〜寒色を残す。 */
    const val SHADOW_TINT_GREEN = 0.018f
    const val SHADOW_TINT_BLUE = 0.034f

    /** ハイライトの暖色量(赤>緑で黄〜オレンジ寄り)。 */
    const val HIGHLIGHT_WARM_RED = 0.050f
    const val HIGHLIGHT_WARM_GREEN = 0.026f

    /** 緑域をさらにくすませる量(1に近いほど彩度を残す)。 */
    const val GREEN_DESAT = 0.50f
    /** 緑域をイエロー寄りへシフトする量(赤を上げ青を下げる)。 */
    const val GREEN_HUE_SHIFT = 0.050f
    /** 緑域判定の鋭さ。 */
    const val GREEN_WEIGHT_GAIN = 3.0f

    /** 暖色(オレンジ・肌)域で戻す彩度(1超で維持〜強調)。被写体を沈ませない。
     *  高コントラストで既に暖色が持ち上がるため、白飛び防止にやや控えめ。 */
    const val WARM_SAT_KEEP = 1.22f
    /** 暖色域判定の鋭さ。 */
    const val WARM_WEIGHT_GAIN = 3.0f
}

object LutPresets {

    const val SIZE = 33

    /**
     * プリセット生成アルゴリズムの版。変えると既存生成物が再生成される。
     * v2: 「強度100%で仕上がった濃度」を基準にコントラスト・彩度の
     * 振り幅を全体的に引き上げ(従来は約70%相当の薄さだった)。
     * エモ・ノスタルジー系6種を追加。
     * v3: 作例(くすみフィルム)再現の Faded Film を追加
     * v4: Faded Filmを作例に忠実化(黒締め・高コントラスト)。
     * v5: 生成物のパース検証+破損時の個別再生成を導入したため、全プリセットを
     *     一度検証し直すよう版を更新
     * v6: Faded Filmを作例により忠実化。黒点を締め(0.015→0.008)コントラストを
     *     上げて(1.16→1.22)、動画でも効果がはっきり分かるメリハリを付けた
     * v7: 動画で効きが地味との指摘を受け、効き量を他プリセット並みに増強。
     *     コントラスト1.22→1.40、ベース彩度0.74→0.85、暗部/ハイライトの
     *     色分離とハイライト暖色を強化(平均逸脱量をCinematic Warm相当へ)
     */
    const val VERSION = 7

    /**
     * 1色を変換する。入出力とも0..1。
     *
     * 設計メモ: トーンカーブは「線形成分0.15 + smoothstep0.85」の合成で、
     * グレー軸上の傾きが常に0.15以上になる。色シフトは輝度の滑らかな関数で、
     * ハイライト側の負方向シフトの傾きを合計-0.6より緩く抑えているため
     * グレー軸に沿った単調性が構造的に保たれる(ユニットテストで検証)。
     * シャドウ重み(1-l)²に係る負方向シフトは微分が正になるため常に安全
     */
    fun transform(preset: LutPreset, r0: Float, g0: Float, b0: Float): FloatArray {
        var r: Float
        var g: Float
        var b: Float
        when (preset) {
            LutPreset.CLEAN_CONTRAST -> {
                // Log→709の基本変換: コントラストと彩度の復元のみ
                r = tone(r0, 0.08f, 0.90f)
                g = tone(g0, 0.08f, 0.90f)
                b = tone(b0, 0.08f, 0.90f)
                val l = luma(r, g, b)
                r = sat(r, l, 1.40f)
                g = sat(g, l, 1.40f)
                b = sat(b, l, 1.40f)
            }

            LutPreset.TEAL_ORANGE -> {
                r = tone(r0, 0.08f, 0.90f)
                g = tone(g0, 0.08f, 0.90f)
                b = tone(b0, 0.08f, 0.90f)
                val l = luma(r, g, b)
                r = sat(r, l, 1.32f)
                g = sat(g, l, 1.32f)
                b = sat(b, l, 1.32f)
                // シャドウ→ティール、ハイライト→オレンジのスプリットトーン
                val sw = (1f - l) * (1f - l)
                val hw = l * l
                r += -0.080f * sw + 0.095f * hw
                g += 0.020f * sw + 0.030f * hw
                b += 0.095f * sw - 0.085f * hw
            }

            LutPreset.CINEMATIC_WARM -> {
                r = tone(r0, 0.06f, 0.90f)
                g = tone(g0, 0.06f, 0.90f)
                b = tone(b0, 0.06f, 0.90f)
                var l = luma(r, g, b)
                // 緑を落ち着かせる(緑チャンネルを輝度へ寄せる)
                g += (l - g) * 0.16f
                l = luma(r, g, b)
                r = sat(r, l, 1.18f)
                g = sat(g, l, 1.18f)
                b = sat(b, l, 1.18f)
                // 暖色寄りのハイライト
                val hw = l * l
                r += 0.020f + 0.055f * hw
                b -= 0.045f
                // わずかなフェード
                r = 0.022f + r * 0.965f
                g = 0.022f + g * 0.965f
                b = 0.022f + b * 0.965f
            }

            LutPreset.CHROME_FILM -> {
                // シャドウ締め気味のドキュメンタリー調
                r = tone(r0, 0.12f, 0.90f)
                g = tone(g0, 0.12f, 0.90f)
                b = tone(b0, 0.12f, 0.90f)
                var l = luma(r, g, b)
                // 青をくすませる
                b += (l - b) * 0.26f
                l = luma(r, g, b)
                r = sat(r, l, 0.72f)
                g = sat(g, l, 0.72f)
                b = sat(b, l, 0.72f)
                r -= 0.012f
            }

            LutPreset.NOSTALGIC_FILM -> {
                // 低コントラスト+フェードした黒+暖色シフト
                r = tone(r0, 0.03f, 0.97f)
                g = tone(g0, 0.03f, 0.97f)
                b = tone(b0, 0.03f, 0.97f)
                val l = luma(r, g, b)
                r = sat(r, l, 0.85f)
                g = sat(g, l, 0.85f)
                b = sat(b, l, 0.85f)
                r += 0.045f
                b -= 0.055f
                r = 0.08f + r * 0.88f
                g = 0.08f + g * 0.88f
                b = 0.08f + b * 0.88f
            }

            LutPreset.MONO_CINEMA -> {
                // 赤フィルター気味のフィルム調モノクロ
                val gray = 0.50f * r0 + 0.36f * g0 + 0.14f * b0
                val v = tone(gray, 0.08f, 0.90f)
                r = v
                g = v
                b = v
            }

            LutPreset.EMO_DUSK -> {
                // 夕暮れ・ブルーアワー。シャドウ青紫+ほんのり暖色ハイライト、
                // 低コントラスト+フェード黒
                r = tone(r0, 0.03f, 0.98f)
                g = tone(g0, 0.03f, 0.98f)
                b = tone(b0, 0.03f, 0.98f)
                val l = luma(r, g, b)
                r = sat(r, l, 1.08f)
                g = sat(g, l, 1.08f)
                b = sat(b, l, 1.08f)
                val sw = (1f - l) * (1f - l)
                val hw = l * l
                r += 0.025f * sw + 0.045f * hw
                g += -0.030f * sw + 0.015f * hw
                b += 0.095f * sw - 0.030f * hw
                r = 0.05f + r * 0.93f
                g = 0.05f + g * 0.93f
                b = 0.05f + b * 0.93f
            }

            LutPreset.HALATION_GLOW -> {
                // フィルムのハレーション風: ハイライトの赤み+ソフトな
                // ロールオフ(上端をsmoothstep域外にして白を柔らかく残す)。
                // 光学的なにじみ自体はLUTでは再現不可のため色再現のみ。
                // にじみ(ブルーム)効果は将来のGLエフェクト候補
                r = tone(r0, 0.05f, 1.08f)
                g = tone(g0, 0.05f, 1.08f)
                b = tone(b0, 0.05f, 1.08f)
                val l = luma(r, g, b)
                r = sat(r, l, 1.18f)
                g = sat(g, l, 1.18f)
                b = sat(b, l, 1.18f)
                val hw = l * l
                r += 0.075f * hw
                g += 0.022f * hw
                b += 0.008f * hw
                r = 0.02f + r * 0.97f
                g = 0.02f + g * 0.97f
                b = 0.02f + b * 0.97f
            }

            LutPreset.TOKYO_NIGHT -> {
                // ネオン・夜景。黒を締めつつ暗部に青を残し、
                // シャドウ=シアン/ハイライト=マゼンタに分離、高彩度
                r = tone(r0, 0.13f, 0.92f)
                g = tone(g0, 0.13f, 0.92f)
                b = tone(b0, 0.13f, 0.92f)
                val l = luma(r, g, b)
                r = sat(r, l, 1.42f)
                g = sat(g, l, 1.42f)
                b = sat(b, l, 1.42f)
                val sw = (1f - l) * (1f - l)
                val hw = l * l
                r += -0.045f * sw + 0.050f * hw
                g += 0.025f * sw - 0.035f * hw
                b += 0.090f * sw + 0.045f * hw
            }

            LutPreset.RETRO_VHS -> {
                // レトロビデオ調: 彩度低め・緑かぶり・持ち上がった黒
                r = tone(r0, 0.06f, 0.92f)
                g = tone(g0, 0.06f, 0.92f)
                b = tone(b0, 0.06f, 0.92f)
                val l = luma(r, g, b)
                r = sat(r, l, 0.68f)
                g = sat(g, l, 0.68f)
                b = sat(b, l, 0.68f)
                g += 0.030f
                b -= 0.015f
                r = 0.09f + r * 0.86f
                g = 0.09f + g * 0.86f
                b = 0.09f + b * 0.86f
            }

            LutPreset.AIRY_FILM -> {
                // 明るく淡い(Vlog/カフェ系)。白に近いハイライトと柔らかい肌
                r = tone(r0, 0.0f, 0.82f)
                g = tone(g0, 0.0f, 0.82f)
                b = tone(b0, 0.0f, 0.82f)
                val l = luma(r, g, b)
                r = sat(r, l, 0.92f)
                g = sat(g, l, 0.92f)
                b = sat(b, l, 0.92f)
                r += 0.018f
                b += 0.010f
                r = 0.04f + r * 0.96f
                g = 0.04f + g * 0.96f
                b = 0.04f + b * 0.96f
            }

            LutPreset.FADED_FILM -> return fadedFilm(r0, g0, b0)

            LutPreset.GOLDEN_MEMORIES -> {
                // 全体を金色に寄せる強い暖色ノスタルジー
                r = tone(r0, 0.05f, 0.90f)
                g = tone(g0, 0.05f, 0.90f)
                b = tone(b0, 0.05f, 0.90f)
                val l = luma(r, g, b)
                r = sat(r, l, 1.15f)
                g = sat(g, l, 1.15f)
                b = sat(b, l, 1.15f)
                val hw = l * l
                r += 0.055f + 0.045f * hw
                g += 0.022f + 0.012f * hw
                b -= 0.075f
                r = 0.03f + r * 0.96f
                g = 0.03f + g * 0.96f
                b = 0.03f + b * 0.96f
            }
        }
        return floatArrayOf(clamp01(r), clamp01(g), clamp01(b))
    }

    /** .cubeテキスト(Adobe Cube LUT Specification 1.0 サブセット)を生成する。 */
    fun generateCubeText(preset: LutPreset, size: Int = SIZE): String {
        val sb = StringBuilder(size * size * size * 24)
        sb.append("TITLE \"Lumina ").append(preset.displayName).append("\"\n")
        sb.append("# Generated by Lumina (v").append(VERSION).append("). Log素材向け\n")
        sb.append("LUT_3D_SIZE ").append(size).append('\n')
        sb.append("DOMAIN_MIN 0.0 0.0 0.0\n")
        sb.append("DOMAIN_MAX 1.0 1.0 1.0\n")
        val maxIndex = (size - 1).toFloat()
        // .cubeはRが最も速く変化する順
        for (bi in 0 until size) {
            for (gi in 0 until size) {
                for (ri in 0 until size) {
                    val out = transform(preset, ri / maxIndex, gi / maxIndex, bi / maxIndex)
                    sb.append(fmt(out[0])).append(' ')
                        .append(fmt(out[1])).append(' ')
                        .append(fmt(out[2])).append('\n')
                }
            }
        }
        return sb.toString()
    }

    private fun fmt(v: Float): String = "%.6f".format(java.util.Locale.US, v)

    /**
     * 作例(オレンジの猫/くすみフィルム)の色を再現する変換。
     * 引き締めた黒・高めのコントラスト・マットな低彩度・くすんだ黄緑・
     * 暖色のソフトなハイライトを、緑域と暖色域を色相選択的に扱って作る。
     * 調整量は [FadedFilmParams]。
     *
     * グレー軸(r=g=b)では緑/暖色の重みが0になり、黒点+ロールオフ+
     * コントラスト+暗部/ハイライトの色みのみが効くため単調性が保たれる(テスト済)。
     */
    private fun fadedFilm(r0: Float, g0: Float, b0: Float): FloatArray {
        val p = FadedFilmParams
        // 1) トーン: 黒点(締まり)→白ロールオフ(柔らかい飛び)→ピボット周りの
        //    高コントラスト
        var r = fadedTone(r0)
        var g = fadedTone(g0)
        var b = fadedTone(b0)

        // 2) 全体をマットな低彩度へ
        var l = luma(r, g, b)
        r = sat(r, l, p.BASE_SATURATION)
        g = sat(g, l, p.BASE_SATURATION)
        b = sat(b, l, p.BASE_SATURATION)

        // 3) 緑域: さらにくすませ、イエロー寄りへシフト(植物の緑を枯れ気味に)
        val greenW = (((g - max(r, b)) * p.GREEN_WEIGHT_GAIN)).coerceIn(0f, 1f)
        if (greenW > 0f) {
            val gl = luma(r, g, b)
            r = lerp(r, sat(r, gl, p.GREEN_DESAT), greenW)
            g = lerp(g, sat(g, gl, p.GREEN_DESAT), greenW)
            b = lerp(b, sat(b, gl, p.GREEN_DESAT), greenW)
            r += p.GREEN_HUE_SHIFT * greenW
            b -= p.GREEN_HUE_SHIFT * 0.5f * greenW
        }

        // 4) 暖色(オレンジ・肌)域: 彩度を戻して被写体を沈ませない
        val warmW = (((r - b) * p.WARM_WEIGHT_GAIN)).coerceIn(0f, 1f) *
            smoothstep(-0.02f, 0.06f, r - g)
        if (warmW > 0f) {
            val wl = luma(r, g, b)
            val keep = lerp(1f, p.WARM_SAT_KEEP, warmW)
            r = sat(r, wl, keep)
            g = sat(g, wl, keep)
            b = sat(b, wl, keep)
        }

        // 5) 暗部に緑/青の色みを残し、ハイライトを暖色へ寄せる
        l = luma(r, g, b)
        val sw = (1f - l) * (1f - l)
        val hw = l * l
        r += p.HIGHLIGHT_WARM_RED * hw
        g += p.SHADOW_TINT_GREEN * sw + p.HIGHLIGHT_WARM_GREEN * hw
        b += p.SHADOW_TINT_BLUE * sw

        return floatArrayOf(clamp01(r), clamp01(g), clamp01(b))
    }

    /**
     * Faded Film専用トーン。黒点で締め、[TONE_HI] 以上をロールオフして
     * 白飛びを柔らかくし、ピボット周りで高コントラスト化する。
     */
    private fun fadedTone(x: Float): Float {
        val p = FadedFilmParams
        var y = p.BLACK_POINT + (1f - p.BLACK_POINT) * x
        if (y > p.TONE_HI) y = p.TONE_HI + (y - p.TONE_HI) * p.HIGHLIGHT_ROLLOFF
        return (y - p.CONTRAST_PIVOT) * p.CONTRAST + p.CONTRAST_PIVOT
    }

    private fun lerp(a: Float, b: Float, t: Float): Float = a + (b - a) * t

    /**
     * Log素材を持ち上げるS字トーン。線形成分を混ぜて端でも傾きが
     * 0.15を下回らないようにする(単調性の保証)。
     */
    private fun tone(x: Float, lo: Float, hi: Float): Float =
        0.15f * x + 0.85f * smoothstep(lo, hi, x)

    private fun sat(c: Float, l: Float, factor: Float): Float = l + (c - l) * factor

    private fun luma(r: Float, g: Float, b: Float): Float =
        0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }

    private fun clamp01(v: Float): Float = v.coerceIn(0f, 1f)
}
