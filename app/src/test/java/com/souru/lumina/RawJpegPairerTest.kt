package com.souru.lumina

import com.souru.lumina.data.pairing.PairCandidate
import com.souru.lumina.data.pairing.RawJpegPairer
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RawJpegPairerTest {

    private fun raw(id: Long, base: String, time: Long) = PairCandidate(id, base, time, isRaw = true)
    private fun jpg(id: Long, base: String, time: Long) = PairCandidate(id, base, time, isRaw = false)

    @Test
    fun `同名かつ近接時刻のRAWとJPEGがペアになる`() {
        val pairs = RawJpegPairer.pair(
            listOf(
                raw(1, "DSC_0001", 1000),
                jpg(2, "DSC_0001", 1200),
            ),
        )
        assertEquals(2L, pairs[1L])
        assertEquals(1L, pairs[2L])
    }

    @Test
    fun `大文字小文字が違ってもペアになる`() {
        val pairs = RawJpegPairer.pair(
            listOf(
                raw(1, "dsc_0001", 1000),
                jpg(2, "DSC_0001", 1000),
            ),
        )
        assertEquals(2L, pairs[1L])
    }

    @Test
    fun `ベース名が違うとペアにならない`() {
        val pairs = RawJpegPairer.pair(
            listOf(
                raw(1, "DSC_0001", 1000),
                jpg(2, "DSC_0002", 1000),
            ),
        )
        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `撮影時刻が離れすぎているとペアにならない`() {
        val pairs = RawJpegPairer.pair(
            listOf(
                raw(1, "DSC_0001", 0),
                jpg(2, "DSC_0001", RawJpegPairer.MAX_TIME_DIFF_MS + 1),
            ),
        )
        assertTrue(pairs.isEmpty())
    }

    @Test
    fun `複数候補からは撮影時刻が最も近いものを選ぶ`() {
        val pairs = RawJpegPairer.pair(
            listOf(
                raw(1, "DSC_0001", 1000),
                jpg(2, "DSC_0001", 4000),
                jpg(3, "DSC_0001", 1100),
            ),
        )
        assertEquals(3L, pairs[1L])
        assertEquals(1L, pairs[3L])
        assertEquals(null, pairs[2L])
    }

    @Test
    fun `RAW単独やJPEG単独はペアに含まれない`() {
        val pairs = RawJpegPairer.pair(
            listOf(
                raw(1, "DSC_0001", 1000),
                jpg(2, "DSC_0002", 1000),
                raw(3, "DSC_0003", 2000),
                jpg(4, "DSC_0003", 2100),
            ),
        )
        assertEquals(2, pairs.size / 1)
        assertEquals(4L, pairs[3L])
        assertEquals(3L, pairs[4L])
        assertEquals(null, pairs[1L])
        assertEquals(null, pairs[2L])
    }

    @Test
    fun `連写で同名グループに複数ペアがあっても1対1で対応する`() {
        val pairs = RawJpegPairer.pair(
            listOf(
                raw(1, "DSC_0001", 1000),
                raw(2, "DSC_0001", 3000),
                jpg(3, "DSC_0001", 1050),
                jpg(4, "DSC_0001", 3050),
            ),
        )
        assertEquals(3L, pairs[1L])
        assertEquals(4L, pairs[2L])
    }
}
