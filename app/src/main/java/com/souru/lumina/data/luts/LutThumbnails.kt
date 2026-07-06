package com.souru.lumina.data.luts

import android.graphics.Bitmap
import android.graphics.Color

/**
 * LUT一覧用のサムネイル生成。合成のサンプル画像(Log風の低コントラスト・
 * 低彩度グラデーション)にLUTを適用した結果を小さなビットマップで返す。
 */
object LutThumbnails {

    private const val WIDTH = 120
    private const val HEIGHT = 72

    /** サンプル画像(Log風)のピクセルを遅延生成してキャッシュする。 */
    private val basePixels: IntArray by lazy {
        val pixels = IntArray(WIDTH * HEIGHT)
        val hsv = FloatArray(3)
        for (y in 0 until HEIGHT) {
            // 縦: 明るさ(上が明るい)
            val v = 1f - y / (HEIGHT - 1f)
            for (x in 0 until WIDTH) {
                // 横: 色相スイープ(空色〜肌色〜緑を含む)
                hsv[0] = 360f * x / WIDTH
                hsv[1] = 0.55f
                hsv[2] = v
                val c = Color.HSVToColor(hsv)
                // Log風に変換: コントラストと彩度を圧縮
                val r = logify(Color.red(c) / 255f)
                val g = logify(Color.green(c) / 255f)
                val b = logify(Color.blue(c) / 255f)
                pixels[y * WIDTH + x] = Color.rgb(
                    (r * 255f).toInt(),
                    (g * 255f).toInt(),
                    (b * 255f).toInt(),
                )
            }
        }
        pixels
    }

    private fun logify(v: Float): Float {
        // 輝度域を0.15..0.75へ圧縮し、グレーに寄せて彩度も落とす
        val compressed = 0.15f + v * 0.60f
        return 0.5f + (compressed - 0.5f) * 0.85f
    }

    /** サンプル画像にLUTを適用したサムネイルを生成する(CPU、呼び出しはIOで)。 */
    fun render(lut: CubeLut): Bitmap {
        val src = basePixels
        val out = IntArray(src.size)
        val sampled = FloatArray(3)
        for (i in src.indices) {
            val c = src[i]
            lut.sample(
                Color.red(c) / 255f,
                Color.green(c) / 255f,
                Color.blue(c) / 255f,
                sampled,
            )
            out[i] = Color.rgb(
                (sampled[0].coerceIn(0f, 1f) * 255f).toInt(),
                (sampled[1].coerceIn(0f, 1f) * 255f).toInt(),
                (sampled[2].coerceIn(0f, 1f) * 255f).toInt(),
            )
        }
        return Bitmap.createBitmap(out, WIDTH, HEIGHT, Bitmap.Config.ARGB_8888)
    }
}
