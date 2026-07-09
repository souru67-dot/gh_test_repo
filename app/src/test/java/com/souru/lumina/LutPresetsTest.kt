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
     * Faded Filmの「グレードの性格」を数値で担保する回帰テスト。色の性格は
     * 参考画像の計測(tools/lut_analysis)に基づき、強度はLog→709復元量。
     * 絶対値ではなく方向性(黒の浮き・シャドウ緑・ハイライト暖色・十分な効き)を
     * 検証する(強度は素材前提で調整され得るため)。
     */
    @Test
    fun `Faded Filmのグレード性格`() {
        val black = LutPresets.transform(LutPreset.FADED_FILM, 0f, 0f, 0f)
        val sh = LutPresets.transform(LutPreset.FADED_FILM, 0.2f, 0.2f, 0.2f)
        val hi = LutPresets.transform(LutPreset.FADED_FILM, 0.85f, 0.85f, 0.85f)

        // 黒はわずかに浮き、緑シアン寄り(純黒でない/G,B >= R)
        val blackLuma = 0.2126f * black[0] + 0.7152f * black[1] + 0.0722f * black[2]
        assertTrue("黒がわずかに浮く: $blackLuma", blackLuma in 0.0f..0.10f)
        assertTrue("黒は緑シアン寄り(G>=R)", black[1] >= black[0])
        // シャドウは緑かぶり(G>R)
        assertTrue("シャドウは緑寄り(G>R)", sh[1] > sh[0])
        // ハイライトは暖色(青が沈む B<R かつ B<G)
        assertTrue("ハイライトは暖色(B<R)", hi[2] < hi[0])
        assertTrue("ハイライトは暖色(B<G)", hi[2] < hi[1])

        // 他プリセット同等の効き: 恒等からの最大逸脱が十分大きい(薄すぎない)
        val steps = 9
        var maxDev = 0f
        for (ri in 0 until steps) for (gi in 0 until steps) for (bi in 0 until steps) {
            val r = ri / (steps - 1f)
            val g = gi / (steps - 1f)
            val b = bi / (steps - 1f)
            val out = LutPresets.transform(LutPreset.FADED_FILM, r, g, b)
            maxDev = maxOf(maxDev, abs(out[0] - r), abs(out[1] - g), abs(out[2] - b))
        }
        assertTrue("効きが十分(薄すぎない): maxDev=$maxDev", maxDev > 0.15f)
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
