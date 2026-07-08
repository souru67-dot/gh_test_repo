package com.souru.lumina

import com.souru.lumina.data.model.GalleryFilter
import com.souru.lumina.data.model.MediaTypeFilter
import com.souru.lumina.data.model.RawFilterMode
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class GalleryFilterTest {

    // 代表アイテム: 動画 / JPEG写真 / RAW写真 / その他写真(HEICなど)
    private fun GalleryFilter.video() = matches(isVideo = true, isRaw = false, isJpeg = false)
    private fun GalleryFilter.jpeg() = matches(isVideo = false, isRaw = false, isJpeg = true)
    private fun GalleryFilter.raw() = matches(isVideo = false, isRaw = true, isJpeg = false)
    private fun GalleryFilter.other() = matches(isVideo = false, isRaw = false, isJpeg = false)

    /**
     * メディア種別 × 写真形式(3×3)の期待表。値は (video, jpeg, raw, other) の可視。
     * 動画は「種別が写真以外」かつ「形式がすべて」のときだけ表示される。
     * (VIDEO×JPEG / VIDEO×RAW は normalized() で VIDEO×ALL に正規化される)
     *
     *              | ALL              | JPEG      | RAW
     * ------------ | ---------------- | --------- | ---------
     * ALL(すべて)  | v, j, r, o       | j のみ    | r のみ
     * PHOTO(写真)  | j, r, o(動画無)  | j のみ    | r のみ
     * VIDEO(動画)  | v のみ           | v のみ*   | v のみ*   (*正規化でALL扱い)
     */
    @Test
    fun `メディア種別×写真形式の3×3組み合わせが期待どおり`() {
        data class Expect(val video: Boolean, val jpeg: Boolean, val raw: Boolean, val other: Boolean)

        val table = mapOf(
            (MediaTypeFilter.ALL to RawFilterMode.ALL) to Expect(true, true, true, true),
            (MediaTypeFilter.ALL to RawFilterMode.JPEG) to Expect(false, true, false, false),
            (MediaTypeFilter.ALL to RawFilterMode.RAW) to Expect(false, false, true, false),
            (MediaTypeFilter.PHOTO to RawFilterMode.ALL) to Expect(false, true, true, true),
            (MediaTypeFilter.PHOTO to RawFilterMode.JPEG) to Expect(false, true, false, false),
            (MediaTypeFilter.PHOTO to RawFilterMode.RAW) to Expect(false, false, true, false),
            (MediaTypeFilter.VIDEO to RawFilterMode.ALL) to Expect(true, false, false, false),
            (MediaTypeFilter.VIDEO to RawFilterMode.JPEG) to Expect(true, false, false, false),
            (MediaTypeFilter.VIDEO to RawFilterMode.RAW) to Expect(true, false, false, false),
        )

        for ((combo, expect) in table) {
            // 実際の利用と同じく正規化してから判定する
            val f = GalleryFilter(combo.first, combo.second).normalized()
            val label = "${combo.first}×${combo.second}"
            assertEquals("$label video", expect.video, f.video())
            assertEquals("$label jpeg", expect.jpeg, f.jpeg())
            assertEquals("$label raw", expect.raw, f.raw())
            assertEquals("$label other", expect.other, f.other())
        }
    }

    @Test
    fun `すべて×JPEG・すべて×RAWでは動画が除外される(バグ回帰)`() {
        assertFalse(GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.JPEG).video())
        assertFalse(GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.RAW).video())
    }

    @Test
    fun `写真のみ では動画が常に非表示`() {
        for (format in RawFilterMode.entries) {
            assertFalse(GalleryFilter(MediaTypeFilter.PHOTO, format).video())
        }
    }

    @Test
    fun `写真のみ×RAW はRAW写真だけ表示`() {
        val f = GalleryFilter(MediaTypeFilter.PHOTO, RawFilterMode.RAW)
        assertTrue(f.raw()); assertFalse(f.jpeg()); assertFalse(f.video())
    }

    @Test
    fun `動画のみ では写真が常に非表示・動画は表示`() {
        for (format in RawFilterMode.entries) {
            val f = GalleryFilter(MediaTypeFilter.VIDEO, format)
            assertTrue(f.video()); assertFalse(f.jpeg()); assertFalse(f.raw())
        }
    }

    @Test
    fun `正規化 動画のみ+RAWは形式がすべてに戻る`() {
        val normalized = GalleryFilter(MediaTypeFilter.VIDEO, RawFilterMode.RAW).normalized()
        assertEquals(RawFilterMode.ALL, normalized.format)
        assertEquals(MediaTypeFilter.VIDEO, normalized.type)
    }

    @Test
    fun `正規化 矛盾のない組み合わせは変更されない`() {
        val f = GalleryFilter(MediaTypeFilter.PHOTO, RawFilterMode.RAW)
        assertEquals(f, f.normalized())
    }

    @Test
    fun `不正な保存値はデフォルトにフォールバックする`() {
        val f = GalleryFilter.fromStored("BROKEN_VALUE", "!!!corrupt!!!")
        assertEquals(MediaTypeFilter.ALL, f.type)
        assertEquals(RawFilterMode.ALL, f.format)
    }

    @Test
    fun `既定フィルタはPNGスクショと動画を表示する(すべて×すべて)`() {
        val f = GalleryFilter.DEFAULT
        assertTrue(f.video()); assertTrue(f.jpeg()); assertTrue(f.raw()); assertTrue(f.other())
    }

    @Test
    fun `null保存値(初回起動)はデフォルトになる`() {
        val f = GalleryFilter.fromStored(null, null)
        assertEquals(GalleryFilter.DEFAULT, f)
    }

    @Test
    fun `保存値からの復元でも矛盾組み合わせは正規化される`() {
        val f = GalleryFilter.fromStored("VIDEO", "RAW")
        assertEquals(MediaTypeFilter.VIDEO, f.type)
        assertEquals(RawFilterMode.ALL, f.format)
    }
}
