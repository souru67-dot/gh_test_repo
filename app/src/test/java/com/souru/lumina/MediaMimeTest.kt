package com.souru.lumina

import com.souru.lumina.data.model.GalleryFilter
import com.souru.lumina.data.model.MediaMime
import com.souru.lumina.data.model.MediaTypeFilter
import com.souru.lumina.data.model.RawFilterMode
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** 想定外のMIMEタイプが流れてもフィルター/判定が落ちないことの検証。 */
class MediaMimeTest {

    @Test
    fun `nullのMIMEでも落ちずにfalseを返す`() {
        assertFalse(MediaMime.isJpeg(null))
        assertFalse(MediaMime.isRaw(null, null))
    }

    @Test
    fun `大文字小文字は無視される`() {
        assertTrue(MediaMime.isJpeg("IMAGE/JPEG"))
        assertTrue(MediaMime.isRaw("IMAGE/X-ADOBE-DNG", "a.dng"))
    }

    @Test
    fun `拡張子dngはMIMEが不明でもRAW扱い`() {
        assertTrue(MediaMime.isRaw("application/octet-stream", "DSC_0001.DNG"))
    }

    @Test
    fun `heicはJPEGでもRAWでもない`() {
        assertFalse(MediaMime.isJpeg("image/heic"))
        assertFalse(MediaMime.isRaw("image/heic", "IMG_0001.heic"))
    }

    @Test
    fun `heic写真はJPEGフィルタで非表示・すべてで表示`() {
        val isRaw = MediaMime.isRaw("image/heic", "a.heic")
        val isJpeg = MediaMime.isJpeg("image/heic")
        assertFalse(
            GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.JPEG)
                .matches(isVideo = false, isRaw = isRaw, isJpeg = isJpeg),
        )
        assertTrue(
            GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.ALL)
                .matches(isVideo = false, isRaw = isRaw, isJpeg = isJpeg),
        )
    }

    @Test
    fun `mp4以外の動画MIME(quicktime等)でも動画として通常どおり扱える`() {
        // 動画判定はMEDIA_TYPEカラムで行うため、MIMEが何であれ
        // 種別軸で扱える。すべて×すべて・動画のみでは表示、
        // 写真のみ・写真形式指定(JPEG/RAW)では非表示
        assertTrue(
            GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.ALL)
                .matches(isVideo = true, isRaw = false, isJpeg = false),
        )
        assertTrue(
            GalleryFilter(MediaTypeFilter.VIDEO, RawFilterMode.ALL)
                .matches(isVideo = true, isRaw = false, isJpeg = false),
        )
        assertFalse(
            GalleryFilter(MediaTypeFilter.PHOTO, RawFilterMode.ALL)
                .matches(isVideo = true, isRaw = false, isJpeg = false),
        )
        // 写真形式にRAW/JPEGを指定した時点で動画は除外される(バグ修正)
        assertFalse(
            GalleryFilter(MediaTypeFilter.ALL, RawFilterMode.RAW)
                .matches(isVideo = true, isRaw = false, isJpeg = false),
        )
    }
}
