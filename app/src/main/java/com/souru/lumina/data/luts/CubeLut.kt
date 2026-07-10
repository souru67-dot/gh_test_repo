package com.souru.lumina.data.luts

import com.souru.lumina.data.edit.Adjustments
import kotlin.math.floor
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.roundToInt

/**
 * 3D LUT(.cube)。データはファイル順(R が最も速く変化)で
 * RGBトリプレットのフラット配列として保持する。
 */
class CubeLut(
    val size: Int,
    val title: String?,
    val data: FloatArray,
) {
    init {
        require(size >= 2) { "LUT_3D_SIZE は2以上が必要です" }
        require(data.size == size * size * size * 3) {
            "LUTデータ数が不正です: expected=${size * size * size * 3}, actual=${data.size}"
        }
    }

    private fun valueAt(ri: Int, gi: Int, bi: Int, channel: Int): Float =
        data[3 * (ri + size * (gi + size * bi)) + channel]

    /** トリリニア補間でサンプリングする。入出力とも0..1。 */
    fun sample(r: Float, g: Float, b: Float, out: FloatArray) {
        val maxIndex = size - 1
        val x = r.coerceIn(0f, 1f) * maxIndex
        val y = g.coerceIn(0f, 1f) * maxIndex
        val z = b.coerceIn(0f, 1f) * maxIndex
        val x0 = floor(x).toInt().coerceAtMost(maxIndex)
        val y0 = floor(y).toInt().coerceAtMost(maxIndex)
        val z0 = floor(z).toInt().coerceAtMost(maxIndex)
        val x1 = min(x0 + 1, maxIndex)
        val y1 = min(y0 + 1, maxIndex)
        val z1 = min(z0 + 1, maxIndex)
        val fx = x - x0
        val fy = y - y0
        val fz = z - z0

        for (ch in 0..2) {
            val c000 = valueAt(x0, y0, z0, ch)
            val c100 = valueAt(x1, y0, z0, ch)
            val c010 = valueAt(x0, y1, z0, ch)
            val c110 = valueAt(x1, y1, z0, ch)
            val c001 = valueAt(x0, y0, z1, ch)
            val c101 = valueAt(x1, y0, z1, ch)
            val c011 = valueAt(x0, y1, z1, ch)
            val c111 = valueAt(x1, y1, z1, ch)
            val c00 = c000 + (c100 - c000) * fx
            val c10 = c010 + (c110 - c010) * fx
            val c01 = c001 + (c101 - c001) * fx
            val c11 = c011 + (c111 - c011) * fx
            val c0 = c00 + (c10 - c00) * fy
            val c1 = c01 + (c11 - c01) * fy
            out[ch] = c0 + (c1 - c0) * fz
        }
    }
}

/** Adobe Cube LUT Specification 1.0 のサブセットをパースする。 */
object CubeLutParser {

    fun parse(text: String): CubeLut {
        var size = -1
        var title: String? = null
        var domainMin = floatArrayOf(0f, 0f, 0f)
        var domainMax = floatArrayOf(1f, 1f, 1f)
        val values = ArrayList<Float>(33 * 33 * 33 * 3)

        for (rawLine in text.lineSequence()) {
            val line = rawLine.trim()
            if (line.isEmpty() || line.startsWith("#")) continue
            when {
                line.startsWith("TITLE", ignoreCase = true) ->
                    title = line.substringAfter(' ', "").trim().trim('"')

                line.startsWith("LUT_3D_SIZE", ignoreCase = true) ->
                    size = line.substringAfter(' ').trim().toIntOrNull()
                        ?: throw IllegalArgumentException("LUT_3D_SIZE が不正です: $line")

                line.startsWith("LUT_1D_SIZE", ignoreCase = true) ->
                    throw IllegalArgumentException("1D LUTには対応していません")

                line.startsWith("DOMAIN_MIN", ignoreCase = true) ->
                    domainMin = parseTriple(line.substringAfter(' '))

                line.startsWith("DOMAIN_MAX", ignoreCase = true) ->
                    domainMax = parseTriple(line.substringAfter(' '))

                line.first().isDigit() || line.first() == '-' || line.first() == '.' -> {
                    val parts = line.split(Regex("\\s+"))
                    if (parts.size < 3) throw IllegalArgumentException("データ行が不正です: $line")
                    for (i in 0..2) {
                        values += parts[i].toFloatOrNull()
                            ?: throw IllegalArgumentException("数値が不正です: $line")
                    }
                }

                else -> {
                    // 未知のキーワードは無視する
                }
            }
        }

        if (size <= 0) throw IllegalArgumentException("LUT_3D_SIZE が見つかりません")
        val expected = size * size * size * 3
        if (values.size != expected) {
            throw IllegalArgumentException(
                "LUTデータ数が不正です: expected=$expected, actual=${values.size}",
            )
        }

        // DOMAIN_MIN/MAX を 0..1 に正規化
        val data = FloatArray(expected)
        for (i in 0 until expected) {
            val ch = i % 3
            val range = domainMax[ch] - domainMin[ch]
            data[i] = if (range != 0f) (values[i] - domainMin[ch]) / range else values[i]
        }
        return CubeLut(size, title, data)
    }

    private fun parseTriple(text: String): FloatArray {
        val parts = text.trim().split(Regex("\\s+"))
        require(parts.size >= 3) { "3成分が必要です: $text" }
        return floatArrayOf(parts[0].toFloat(), parts[1].toFloat(), parts[2].toFloat())
    }
}

/**
 * LUT・強度・簡易調整を1つの3D LUT(cube[R][G][B]、ARGB_8888)に焼き込む。
 * Media3 の SingleColorLut.createFromCube に渡す形式で、プレビュー
 * (ExoPlayer.setVideoEffects)と書き出し(Transformer)が完全に一致する。
 */
object LutBaker {

    const val BAKE_SIZE = 33

    /** 入力変換なし(Rec.709入力前提)の従来経路。既存挙動を完全維持する。 */
    fun bake(
        lut: CubeLut?,
        strength: Float,
        adjustments: Adjustments,
        size: Int = BAKE_SIZE,
    ): Array<Array<IntArray>> = bake(
        input = com.souru.lumina.data.video.InputTransform.NONE,
        inputParams = com.souru.lumina.data.video.InputTransformParams(),
        hdrToneMapped = false,
        lut = lut,
        strength = strength,
        adjustments = adjustments,
        size = size,
    )

    /**
     * 入力変換(Log→709)→ クリエイティブLUT → 簡易調整 を単一の3D LUTへ合成する。
     * 入力変換で709へ正規化してからLUTが当たるため、どのLog素材でも同じプリセットが
     * 同じ色の方向性で効く。[input]=NONE のときは従来と完全一致(非回帰)。
     * クリエイティブLUTの強度は「正規化後の709」と「709+LUT」の間で補間する。
     */
    fun bake(
        input: com.souru.lumina.data.video.InputTransform,
        inputParams: com.souru.lumina.data.video.InputTransformParams,
        hdrToneMapped: Boolean,
        lut: CubeLut?,
        strength: Float,
        adjustments: Adjustments,
        size: Int = BAKE_SIZE,
    ): Array<Array<IntArray>> {
        val cube = Array(size) { Array(size) { IntArray(size) } }
        val sampled = FloatArray(3)
        val tin = FloatArray(3)
        val maxIndex = (size - 1).toFloat()
        val s = strength.coerceIn(0f, 1f)
        val hasInput = input != com.souru.lumina.data.video.InputTransform.NONE || !inputParams.isIdentity
        for (ri in 0 until size) {
            val r = ri / maxIndex
            for (gi in 0 until size) {
                val g = gi / maxIndex
                for (bi in 0 until size) {
                    val b = bi / maxIndex
                    // 入力変換で 709 へ正規化(NONEなら恒等)
                    var rr: Float
                    var gg: Float
                    var bb: Float
                    if (hasInput) {
                        input.apply(r, g, b, inputParams, hdrToneMapped, tin)
                        rr = tin[0]; gg = tin[1]; bb = tin[2]
                    } else {
                        rr = r; gg = g; bb = b
                    }
                    // クリエイティブLUT(正規化後の値を入力に)
                    if (lut != null && s > 0f) {
                        lut.sample(rr, gg, bb, sampled)
                        rr += (sampled[0] - rr) * s
                        gg += (sampled[1] - gg) * s
                        bb += (sampled[2] - bb) * s
                    }
                    cube[ri][gi][bi] = applyColorAdjustments(rr, gg, bb, adjustments)
                }
            }
        }
        return cube
    }

    /** AGSLシェーダー(AdjustmentShader)と同じ式のKotlin実装。ARGBを返す。 */
    private fun applyColorAdjustments(r0: Float, g0: Float, b0: Float, a: Adjustments): Int {
        var r = r0
        var g = g0
        var b = b0
        if (!a.isIdentity) {
            val ev = 2f.pow(a.exposure)
            r *= ev; g *= ev; b *= ev
            r += a.temperature * 0.10f
            b -= a.temperature * 0.10f
            g -= a.tint * 0.10f
            val luma = luma(r, g, b)
            val shadowMask = 1f - smoothstep(0f, 0.5f, luma)
            val highlightMask = smoothstep(0.5f, 1f, luma)
            r += a.shadows * 0.25f * shadowMask + a.highlights * 0.25f * highlightMask
            g += a.shadows * 0.25f * shadowMask + a.highlights * 0.25f * highlightMask
            b += a.shadows * 0.25f * shadowMask + a.highlights * 0.25f * highlightMask
            val whiteGain = 1f + a.whites * 0.25f
            val blackLift = a.blacks * 0.15f
            r = r * whiteGain + blackLift
            g = g * whiteGain + blackLift
            b = b * whiteGain + blackLift
            val contrastGain = 1f + a.contrast * 0.75f
            r = (r - 0.5f) * contrastGain + 0.5f
            g = (g - 0.5f) * contrastGain + 0.5f
            b = (b - 0.5f) * contrastGain + 0.5f
            val maxc = max(r, max(g, b))
            val minc = min(r, min(g, b))
            val satAmount = (maxc - minc).coerceIn(0f, 1f)
            val factor = 1f + a.saturation + a.vibrance * (1f - satAmount)
            val l = luma(r, g, b)
            r = l + (r - l) * factor
            g = l + (g - l) * factor
            b = l + (b - l) * factor
        }
        val ri = (r.coerceIn(0f, 1f) * 255f).roundToInt()
        val gi = (g.coerceIn(0f, 1f) * 255f).roundToInt()
        val bi = (b.coerceIn(0f, 1f) * 255f).roundToInt()
        return (0xFF shl 24) or (ri shl 16) or (gi shl 8) or bi
    }

    private fun luma(r: Float, g: Float, b: Float): Float =
        0.2126f * r + 0.7152f * g + 0.0722f * b

    private fun smoothstep(edge0: Float, edge1: Float, x: Float): Float {
        val t = ((x - edge0) / (edge1 - edge0)).coerceIn(0f, 1f)
        return t * t * (3f - 2f * t)
    }
}
