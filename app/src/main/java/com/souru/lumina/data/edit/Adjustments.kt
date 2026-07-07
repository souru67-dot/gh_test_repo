package com.souru.lumina.data.edit

import android.graphics.Bitmap
import android.graphics.BitmapShader
import android.graphics.RuntimeShader
import android.graphics.Shader

/**
 * 写真の簡易調整パラメータ。すべて -1..1(露出のみEVで-3..3、シャープは0..1)。
 * 0がニュートラル。
 */
data class Adjustments(
    val exposure: Float = 0f,
    val contrast: Float = 0f,
    val highlights: Float = 0f,
    val shadows: Float = 0f,
    val whites: Float = 0f,
    val blacks: Float = 0f,
    val temperature: Float = 0f,
    val tint: Float = 0f,
    val saturation: Float = 0f,
    val vibrance: Float = 0f,
    val sharpen: Float = 0f,
) {
    val isIdentity: Boolean
        get() = this == Adjustments()
}

/**
 * AGSL(RuntimeShader)による調整。プレビュー(graphicsLayerのRenderEffect)と
 * 書き出し(HardwareRendererオフスクリーン描画)で同じシェーダーを共有する。
 */
object AdjustmentShader {

    const val CONTENT_SHADER_NAME = "content"

    // language=AGSL
    val SOURCE = """
        uniform shader content;
        uniform shader lut;
        uniform float uLutEnabled;
        uniform float uLutSize;
        uniform float uExposure;
        uniform float uContrast;
        uniform float uHighlights;
        uniform float uShadows;
        uniform float uWhites;
        uniform float uBlacks;
        uniform float uTemperature;
        uniform float uTint;
        uniform float uSaturation;
        uniform float uVibrance;
        uniform float uSharpen;

        float lumaOf(float3 c) {
            return dot(c, float3(0.2126, 0.7152, 0.0722));
        }

        // 3D LUT(2Dストリップ: x = r + size*bスライス, y = g)のサンプリング。
        // スライス内のr/gはテクスチャのバイリニア補間、bは2スライスのmixで
        // トライリニア相当にする
        float3 applyLut(float3 c) {
            if (uLutEnabled < 0.5) {
                return c;
            }
            float maxIndex = uLutSize - 1.0;
            float b = clamp(c.b, 0.0, 1.0) * maxIndex;
            float b0 = floor(b);
            float b1 = min(b0 + 1.0, maxIndex);
            float f = b - b0;
            float x = clamp(c.r, 0.0, 1.0) * maxIndex + 0.5;
            float y = clamp(c.g, 0.0, 1.0) * maxIndex + 0.5;
            float3 s0 = float3(lut.eval(float2(x + b0 * uLutSize, y)).rgb);
            float3 s1 = float3(lut.eval(float2(x + b1 * uLutSize, y)).rgb);
            return mix(s0, s1, f);
        }

        float3 adjust(float3 c) {
            // 露出(EV)
            c = c * pow(2.0, uExposure);
            // 色温度・色かぶり(簡易ホワイトバランス)
            c.r = c.r + uTemperature * 0.10;
            c.b = c.b - uTemperature * 0.10;
            c.g = c.g - uTint * 0.10;
            // ハイライト/シャドウ(輝度マスク)
            float luma = lumaOf(c);
            float shadowMask = 1.0 - smoothstep(0.0, 0.5, luma);
            float highlightMask = smoothstep(0.5, 1.0, luma);
            c = c + uShadows * 0.25 * shadowMask;
            c = c + uHighlights * 0.25 * highlightMask;
            // 白レベル・黒レベル
            c = c * (1.0 + uWhites * 0.25) + uBlacks * 0.15;
            // コントラスト(中間グレー基準)
            c = (c - 0.5) * (1.0 + uContrast * 0.75) + 0.5;
            // 彩度・自然な彩度(低彩度ほど強く効く)
            float maxc = max(c.r, max(c.g, c.b));
            float minc = min(c.r, min(c.g, c.b));
            float satAmount = clamp(maxc - minc, 0.0, 1.0);
            float factor = 1.0 + uSaturation + uVibrance * (1.0 - satAmount);
            c = mix(float3(lumaOf(c)), c, factor);
            return clamp(c, 0.0, 1.0);
        }

        half4 main(float2 coord) {
            half4 src = content.eval(coord);
            float3 c = float3(src.rgb);
            if (uSharpen > 0.0) {
                // アンシャープマスク
                float3 blur = float3(content.eval(coord + float2(1.5, 0.0)).rgb);
                blur = blur + float3(content.eval(coord - float2(1.5, 0.0)).rgb);
                blur = blur + float3(content.eval(coord + float2(0.0, 1.5)).rgb);
                blur = blur + float3(content.eval(coord - float2(0.0, 1.5)).rgb);
                blur = blur * 0.25;
                c = c + (c - blur) * (uSharpen * 1.2);
            }
            // 適用順は「フィルタ(LUT)→調整」に固定
            c = applyLut(c);
            c = adjust(c);
            // 画像ピクセル外(透明: a=0)にはシャドウ持ち上げ等の効果を
            // かけない。premultiplied alpha前提のためRGBにαを乗算し、
            // 範囲外は純黒透明のまま維持する(書き出しはα=1なので不変)
            return half4(half3(c) * src.a, src.a);
        }
    """.trimIndent()

    fun create(): RuntimeShader = RuntimeShader(SOURCE)
}

/**
 * 3D LUT(cube[R][G][B]、ARGB)をAGSL用の2Dストリップに変換する。
 * レイアウト: 幅 size*size / 高さ size、x = r + size*b、y = g。
 */
object LutStrip {

    fun fromCube(cube: Array<Array<IntArray>>): Bitmap {
        val size = cube.size
        val width = size * size
        val pixels = IntArray(width * size)
        for (bi in 0 until size) {
            for (gi in 0 until size) {
                for (ri in 0 until size) {
                    pixels[gi * width + bi * size + ri] = cube[ri][gi][bi]
                }
            }
        }
        return Bitmap.createBitmap(pixels, width, size, Bitmap.Config.ARGB_8888)
    }

    /** uniform shaderは未バインドだと描画できないため、無効時に使うダミー。 */
    val dummy: Bitmap by lazy {
        Bitmap.createBitmap(intArrayOf(0xFF000000.toInt()), 1, 1, Bitmap.Config.ARGB_8888)
    }
}

/** フィルタLUT(ストリップ)をシェーダーへ適用する。nullでフィルタ無効。 */
fun RuntimeShader.applyFilterLut(strip: Bitmap?) {
    if (strip != null) {
        setInputShader(
            "lut",
            BitmapShader(strip, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP).apply {
                filterMode = BitmapShader.FILTER_MODE_LINEAR
            },
        )
        setFloatUniform("uLutEnabled", 1f)
        setFloatUniform("uLutSize", strip.height.toFloat())
    } else {
        setInputShader(
            "lut",
            BitmapShader(LutStrip.dummy, Shader.TileMode.CLAMP, Shader.TileMode.CLAMP),
        )
        setFloatUniform("uLutEnabled", 0f)
        setFloatUniform("uLutSize", 2f)
    }
}

fun RuntimeShader.applyAdjustments(a: Adjustments) {
    setFloatUniform("uExposure", a.exposure)
    setFloatUniform("uContrast", a.contrast)
    setFloatUniform("uHighlights", a.highlights)
    setFloatUniform("uShadows", a.shadows)
    setFloatUniform("uWhites", a.whites)
    setFloatUniform("uBlacks", a.blacks)
    setFloatUniform("uTemperature", a.temperature)
    setFloatUniform("uTint", a.tint)
    setFloatUniform("uSaturation", a.saturation)
    setFloatUniform("uVibrance", a.vibrance)
    setFloatUniform("uSharpen", a.sharpen)
}
