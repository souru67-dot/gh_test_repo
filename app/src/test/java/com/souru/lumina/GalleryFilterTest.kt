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

    @Test
    fun `すべて×すべて は全部表示`() {
        val f = GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.ALL)
        assertTrue(f.video()); assertTrue(f.jpeg()); assertTrue(f.raw()); assertTrue(f.other())
    }

    @Test
    fun `形式フィルタは動画に作用しない(すべて×JPEG・すべて×RAWでも動画は表示)`() {
        assertTrue(GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.JPEG).video())
        assertTrue(GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.RAW).video())
    }

    @Test
    fun `すべて×JPEG はJPEGのみ+動画`() {
        val f = GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.JPEG)
        assertTrue(f.jpeg()); assertFalse(f.raw()); assertFalse(f.other()); assertTrue(f.video())
    }

    @Test
    fun `すべて×RAW はRAWのみ+動画`() {
        val f = GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.RAW)
        assertFalse(f.jpeg()); assertTrue(f.raw()); assertFalse(f.other()); assertTrue(f.video())
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
        assertEquals(RawFilterMode.JPEG, f.format)
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
