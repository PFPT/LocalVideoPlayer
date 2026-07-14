package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadNameResolverTest {
    @Test
    fun parsesTitleAndUrlFromPipeFormat() {
        val parsed = DownloadNameResolver.parseSharedText("说唱巅峰对决 2026-20260712 下|http://127.0.0.1:9978/m3u8?url=abc")
        assertEquals("说唱巅峰对决 2026-20260712 下", parsed.title)
        assertEquals("http://127.0.0.1:9978/m3u8?url=abc", parsed.url)
    }

    @Test
    fun parsesTitleAndUrlFromMultilineShare() {
        val parsed = DownloadNameResolver.parseSharedText(
            """
            说唱巅峰对决 2026-20260712 下
            http://127.0.0.1:9978/m3u8?url=abc
            """.trimIndent(),
        )
        assertEquals("说唱巅峰对决 2026-20260712 下", parsed.title)
        assertEquals("http://127.0.0.1:9978/m3u8?url=abc", parsed.url)
    }

    @Test
    fun parsesUrlOnlyShare() {
        val parsed = DownloadNameResolver.parseSharedText("https://cdn.example.com/movie.mp4")
        assertNull(parsed.title)
        assertEquals("https://cdn.example.com/movie.mp4", parsed.url)
    }

    @Test
    fun addsMp4ExtensionForHlsTitle() {
        assertEquals(
            "说唱巅峰对决.mp4",
            DownloadNameResolver.toOutputFileName("说唱巅峰对决", isHls = true, fallback = "download.mp4"),
        )
    }

    @Test
    fun sanitizesInvalidFileNameCharacters() {
        assertEquals("abc_def", DownloadNameResolver.sanitizeTitle("abc/def"))
    }

    @Test
    fun infersTitleFromCdnPath() {
        val title = DownloadNameResolver.inferTitleFromCdnPath(
            "https://v.lzcdn28.com/20260712/12602_8c511196/index.m3u8",
        )
        assertEquals("20260712-12602_8c511196", title)
    }

    @Test
    fun composesSeriesAndEpisodeFromFongMiMedia() {
        val title = DownloadNameResolver.composeFongMiTitle(
            DownloadNameResolver.FongMiMedia(title = "种地吧4", artist = "20260514上"),
            "http://127.0.0.1:9978/m3u8?url=https%3A%2F%2Fv.lzcdn28.com%2F20260514%2Findex.m3u8",
        )
        assertEquals("种地吧4-20260514上", title)
    }

    @Test
    fun enrichesTitleWithDateFromStreamUrlWhenArtistMissing() {
        val title = DownloadNameResolver.enrichTitleFromStreamUrl(
            "种地吧4",
            "http://127.0.0.1:9978/m3u8?url=https%3A%2F%2Fv.lzcdn28.com%2F20260514%2Findex.m3u8",
        )
        assertEquals("种地吧4-20260514", title)
    }

    @Test
    fun prefersExplicitNameQueryParam() {
        val title = DownloadNameResolver.extractTitleFromUrl(
            "http://127.0.0.1:9978/m3u8?name=%E7%A7%8D%E5%9C%B0%E5%90%A74-20260514%E4%B8%8A&url=https%3A%2F%2Fexample.com%2Findex.m3u8",
        )
        assertEquals("种地吧4-20260514上", title)
    }
}
