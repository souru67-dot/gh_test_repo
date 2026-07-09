package com.souru.lumina

import com.souru.lumina.data.luts.CubeLutParser
import com.souru.lumina.data.luts.LutPreset
import com.souru.lumina.data.luts.LutPresets
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * 標準LUTプリセットの数学的な妥当性検証:
 * - 生成した.cubeが自前パーサで正しく読めること(フォーマット妥当性)
 * - 全出力値がDomain 0..1に収まること
 * - グレー軸(r=g=b)に沿って各チャンネルが単調非減少であること
 * - 恒等変換から意図的に逸脱していること(効果が存在すること)
 */
class LutPresetsTest {

    @Test
    fun `生成したcubeはパース可能でサイズが正しい`() {
        for (preset in LutPreset.entries) {
            val lut = CubeLutParser.parse(LutPresets.generateCubeText(preset))
            assertEquals("$preset のサイズ", LutPresets.SIZE, lut.size)
        }
    }

    @Test
    fun `全出力が0から1の範囲に収まる`() {
        val steps = 17
        for (preset in LutPreset.entries) {
            for (ri in 0 until steps) {
                for (gi in 0 until steps) {
                    for (bi in 0 until steps) {
                        val out = LutPresets.transform(
                            preset,
                            ri / (steps - 1f),
                            gi / (steps - 1f),
                            bi / (steps - 1f),
                        )
                        for (ch in 0..2) {
                            assertTrue(
                                "$preset out[$ch]=${out[ch]} が範囲外",
                                out[ch] in 0f..1f,
                            )
                        }
                    }
                }
            }
        }
    }

    @Test
    fun `グレー軸に沿って各チャンネルが単調非減少`() {
        val steps = 257
        val epsilon = 1e-4f
        for (preset in LutPreset.entries) {
            var prev = LutPresets.transform(preset, 0f, 0f, 0f)
            for (i in 1 until steps) {
                val x = i / (steps - 1f)
                val cur = LutPresets.transform(preset, x, x, x)
                for (ch in 0..2) {
                    assertTrue(
                        "$preset ch=$ch x=$x で減少: ${prev[ch]} -> ${cur[ch]}",
                        cur[ch] >= prev[ch] - epsilon,
                    )
                }
                prev = cur
            }
        }
    }

    @Test
    fun `恒等変換から意図的に逸脱している`() {
        val steps = 9
        for (preset in LutPreset.entries) {
            var maxDeviation = 0f
            for (ri in 0 until steps) {
                for (gi in 0 until steps) {
                    for (bi in 0 until steps) {
                        val r = ri / (steps - 1f)
                        val g = gi / (steps - 1f)
                        val b = bi / (steps - 1f)
                        val out = LutPresets.transform(preset, r, g, b)
                        maxDeviation = maxOf(
                            maxDeviation,
                            abs(out[0] - r),
                            abs(out[1] - g),
                            abs(out[2] - b),
                        )
                    }
                }
            }
            assertTrue("$preset が恒等変換に近すぎる: max=$maxDeviation", maxDeviation > 0.05f)
        }
    }

    @Test
    fun `Mono Cinemaは完全なモノクロ`() {
        val steps = 9
        for (ri in 0 until steps) {
            for (gi in 0 until steps) {
                for (bi in 0 until steps) {
                    val out = LutPresets.transform(
                        LutPreset.MONO_CINEMA,
                        ri / (steps - 1f),
                        gi / (steps - 1f),
                        bi / (steps - 1f),
                    )
                    assertEquals(out[0], out[1], 1e-6f)
                    assertEquals(out[1], out[2], 1e-6f)
                }
            }
        }
    }

    /**
     * Faded Filmは参考画像の計測値(tools/lut_analysis)をターゲットに収束させた
     * プリセット。ニュートラル軸(グレー入力)の各輝度帯の出力が、計測ターゲットに
     * 一定許容内で一致すること=「グレードの芯」を数値で担保する回帰テスト。
     */
    @Test
    fun `Faded Filmのニュートラル軸が計測ターゲットに一致`() {
        // (入力x, 目標RGB) 目標は参考2枚の共通特性(analyze_reference.pyの計測)
        val targets = listOf(
            Triple(0.00f, floatArrayOf(0.040f, 0.051f, 0.053f), "黒: 緑シアンに軽く浮く"),
            Triple(0.20f, floatArrayOf(0.158f, 0.190f, 0.178f), "シャドウ: 緑かぶり"),
            Triple(0.50f, floatArrayOf(0.498f, 0.512f, 0.476f), "ミッド: ほぼ中立・低彩度"),
            Triple(0.85f, floatArrayOf(0.888f, 0.872f, 0.820f), "ハイライト: 暖色"),
            Triple(1.00f, floatArrayOf(0.962f, 0.950f, 0.922f), "白: 軽い抑え+暖色"),
        )
        val tol = 0.03f
        for ((x, tgt, label) in targets) {
            val out = LutPresets.transform(LutPreset.FADED_FILM, x, x, x)
            for (ch in 0..2) {
                assertTrue(
                    "$label ch=$ch 出力=${out[ch]} 目標=${tgt[ch]} (許容$tol)",
                    abs(out[ch] - tgt[ch]) <= tol,
                )
            }
        }
        // 特性の符号: シャドウは緑(G>R)、ハイライトは暖色(B<R)
        val sh = LutPresets.transform(LutPreset.FADED_FILM, 0.2f, 0.2f, 0.2f)
        assertTrue("シャドウは緑寄り(G>R)", sh[1] > sh[0])
        val hi = LutPresets.transform(LutPreset.FADED_FILM, 0.85f, 0.85f, 0.85f)
        assertTrue("ハイライトは暖色(B<R)", hi[2] < hi[0])
    }

    @Test
    fun `Clean Contrastはグレーを無彩色のまま保つ`() {
        val steps = 33
        for (i in 0 until steps) {
            val x = i / (steps - 1f)
            val out = LutPresets.transform(LutPreset.CLEAN_CONTRAST, x, x, x)
            assertEquals("x=$x でR/G不一致", out[0], out[1], 1e-4f)
            assertEquals("x=$x でG/B不一致", out[1], out[2], 1e-4f)
        }
    }
}
