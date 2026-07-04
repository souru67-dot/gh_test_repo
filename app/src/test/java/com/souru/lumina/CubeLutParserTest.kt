package com.souru.lumina

import com.souru.lumina.data.luts.CubeLutParser
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class CubeLutParserTest {

    private val identity2 = """
        # コメント行
        TITLE "Identity"
        LUT_3D_SIZE 2

        0.0 0.0 0.0
        1.0 0.0 0.0
        0.0 1.0 0.0
        1.0 1.0 0.0
        0.0 0.0 1.0
        1.0 0.0 1.0
        0.0 1.0 1.0
        1.0 1.0 1.0
    """.trimIndent()

    @Test
    fun `恒等LUTをパースできる`() {
        val lut = CubeLutParser.parse(identity2)
        assertEquals(2, lut.size)
        assertEquals("Identity", lut.title)
        val out = FloatArray(3)
        lut.sample(1f, 0f, 0f, out)
        assertEquals(1f, out[0], 1e-4f)
        assertEquals(0f, out[1], 1e-4f)
        assertEquals(0f, out[2], 1e-4f)
    }

    @Test
    fun `トリリニア補間で中間値が返る`() {
        val lut = CubeLutParser.parse(identity2)
        val out = FloatArray(3)
        lut.sample(0.5f, 0.25f, 0.75f, out)
        assertEquals(0.5f, out[0], 1e-4f)
        assertEquals(0.25f, out[1], 1e-4f)
        assertEquals(0.75f, out[2], 1e-4f)
    }

    @Test
    fun `サイズ宣言がないとエラー`() {
        assertThrows(IllegalArgumentException::class.java) {
            CubeLutParser.parse("0.0 0.0 0.0")
        }
    }

    @Test
    fun `データ数が合わないとエラー`() {
        assertThrows(IllegalArgumentException::class.java) {
            CubeLutParser.parse("LUT_3D_SIZE 2\n0.0 0.0 0.0")
        }
    }

    @Test
    fun `DOMAINを正規化する`() {
        val text = """
            LUT_3D_SIZE 2
            DOMAIN_MIN 0.0 0.0 0.0
            DOMAIN_MAX 2.0 2.0 2.0
            0.0 0.0 0.0
            2.0 0.0 0.0
            0.0 2.0 0.0
            2.0 2.0 0.0
            0.0 0.0 2.0
            2.0 0.0 2.0
            0.0 2.0 2.0
            2.0 2.0 2.0
        """.trimIndent()
        val lut = CubeLutParser.parse(text)
        val out = FloatArray(3)
        lut.sample(1f, 1f, 1f, out)
        assertEquals(1f, out[0], 1e-4f)
    }
}
