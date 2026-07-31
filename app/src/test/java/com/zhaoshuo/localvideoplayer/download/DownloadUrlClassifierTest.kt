package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUrlClassifierTest {
    @Test
    fun recognizesDirectM3u8Url() {
        val url = "https://cdn.example.com/video/index.m3u8"
        assertTrue(DownloadUrlClassifier.isHlsDownloadUrl(url))
        assertEquals("video.mp4", DownloadUrlClassifier.defaultFileName(url, "download.mp4"))
    }

    @Test
    fun recognizesLocalProxyM3u8Endpoint() {
        val url = "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260712%252F12602_8c511196%252Findex.m3u8"
        assertTrue(DownloadUrlClassifier.isHlsDownloadUrl(url))
        assertTrue(DownloadUrlClassifier.isLocalProxyUrl(url))
        assertEquals(
            "https://v.lzcdn28.com/20260712/12602_8c511196/index.m3u8",
            DownloadUrlClassifier.extractNestedM3u8Url(url),
        )
        assertEquals("12602_8c511196.mp4", DownloadUrlClassifier.defaultFileName(url, "download.mp4"))
    }

    @Test
    fun recognizesProxyDoM3u8Query() {
        val url = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fcdn.example.com%2Fmaster.m3u8"
        assertTrue(DownloadUrlClassifier.isHlsDownloadUrl(url))
    }

    @Test
    fun rejectsRegularMp4Url() {
        val url = "https://cdn.example.com/video/movie.mp4"
        assertFalse(DownloadUrlClassifier.isHlsDownloadUrl(url))
    }

    @Test
    fun recognizesMagnetUrl() {
        val url = "magnet:?xt=urn:btih:aaabb331189c1bb1f198797dc208d14afa824429&dn=Sample"
        assertTrue(DownloadUrlClassifier.isMagnetUrl(url))
        assertFalse(DownloadUrlClassifier.isHlsDownloadUrl(url))
        assertEquals("Sample", DownloadUrlClassifier.magnetDisplayName(url))
    }

    @Test
    fun extractsFirstMagnetFromGluedClipboard() {
        val glued =
            "magnet:?xt=urn:btih:aaabb331189c1bb1f198797dc208d14afa824429" +
                "magnet:?xt=urn:btih:bbbbb331189c1bb1f198797dc208d14afa824429"
        assertEquals(
            "magnet:?xt=urn:btih:aaabb331189c1bb1f198797dc208d14afa824429",
            DownloadUrlClassifier.extractFirstMagnet(glued),
        )
    }
}
