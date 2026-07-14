package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Test

class HlsSegmentFormatTest {
    @Test
    fun detectsFmp4FromInitMap() {
        val playlist = HlsVodPlaylist(
            url = "https://example.com/media.m3u8",
            signature = "sig",
            initSegmentUrl = "https://example.com/init.mp4",
            segments = listOf(
                HlsSegment("https://example.com/seg.ts", 6.0, null),
            ),
        )
        assertEquals(HlsSegmentFormat.FMP4, playlist.segmentFormat())
    }

    @Test
    fun detectsFmp4FromM4sUrls() {
        val playlist = HlsVodPlaylist(
            url = "https://example.com/media.m3u8",
            signature = "sig",
            segments = listOf(
                HlsSegment("https://example.com/seg.m4s", 6.0, null),
            ),
        )
        assertEquals(HlsSegmentFormat.FMP4, playlist.segmentFormat())
    }

    @Test
    fun defaultsToTs() {
        val playlist = HlsVodPlaylist(
            url = "https://example.com/media.m3u8",
            signature = "sig",
            segments = listOf(
                HlsSegment("https://example.com/seg.ts", 6.0, null),
            ),
        )
        assertEquals(HlsSegmentFormat.TS, playlist.segmentFormat())
        assertEquals("ts", playlist.segmentFormat().segmentExtension())
        assertEquals("m4s", HlsSegmentFormat.FMP4.segmentExtension())
    }
}
