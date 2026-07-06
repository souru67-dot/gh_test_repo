package com.souru.lumina.data.edit

import android.graphics.RuntimeShader

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
            c = adjust(c);
            // 画像ピクセル外(透明: a=0)にはシャドウ持ち上げ等の効果を
            // かけない。premultiplied alpha前提のためRGBにαを乗算し、
            // 範囲外は純黒透明のまま維持する(書き出しはα=1なので不変)
            return half4(half3(c) * src.a, src.a);
        }
    """.trimIndent()

    fun create(): RuntimeShader = RuntimeShader(SOURCE)
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
