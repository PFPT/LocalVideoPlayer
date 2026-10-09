package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Assert.assertTrue
import org.junit.Test

class HlsVodPlaylistParserTest {
    @Test
    fun selectsHighestBandwidthVariantAndResolvesRelativeUrl() {
        val selected = HlsVodPlaylistParser.selectVariant(
            "https://example.com/hls/master.m3u8",
            """
                #EXTM3U
                #EXT-X-STREAM-INF:BANDWIDTH=400000
                low/index.m3u8
                #EXT-X-STREAM-INF:BANDWIDTH=1200000
                high/index.m3u8
            """.trimIndent(),
        )

        assertEquals("https://example.com/hls/high/index.m3u8", selected)
    }

    @Test
    fun parsesExplicitAndSequenceDerivedAesIvs() {
        val explicit = HlsVodPlaylistParser.parseIv("0x000102030405060708090a0b0c0d0e0f")
        assertArrayEquals(ByteArray(16) { it.toByte() }, explicit)

        val playlist = HlsVodPlaylistParser.parseMediaPlaylist(
            "https://example.com/media.m3u8",
            """
                #EXTM3U
                #EXT-X-MEDIA-SEQUENCE:5
                #EXT-X-KEY:METHOD=AES-128,URI="key.bin"
                #EXTINF:6,
                segment.ts
                #EXT-X-ENDLIST
            """.trimIndent(),
        )

        assertArrayEquals(ByteArray(16).also { it[15] = 5 }, playlist.segments.single().encryption!!.iv)
        assertEquals(6.0, playlist.segments.single().durationSec, 0.001)
    }

    @Test
    fun parsesFmp4PlaylistWithInitMap() {
        val playlist = HlsVodPlaylistParser.parseMediaPlaylist(
            "https://example.com/media.m3u8",
            """
                #EXTM3U
                #EXT-X-VERSION:6
                #EXT-X-INDEPENDENT-SEGMENTS
                #EXT-X-MAP:URI="init.mp4"
                #EXTINF:6,
                segment-00001.m4s
                #EXT-X-DISCONTINUITY
                #EXTINF:6,
                segment-00002.m4s
                #EXT-X-ENDLIST
            """.trimIndent(),
        )

        assertEquals("https://example.com/init.mp4", playlist.initSegmentUrl)
        assertEquals(2, playlist.segments.size)
        assertTrue(playlist.segments.first().url.endsWith(".m4s"))
    }

    @Test
    fun rejectsByteRangeMediaPlaylist() {
        val error = assertThrows(HlsVodException::class.java) {
            HlsVodPlaylistParser.parseMediaPlaylist(
                "https://example.com/media.m3u8",
                """
                    #EXTM3U
                    #EXTINF:6,
                    #EXT-X-BYTERANGE:1024@0
                    segment.ts
                    #EXT-X-ENDLIST
                """.trimIndent(),
            )
        }

        assertEquals(HlsVodFailure.UNSUPPORTED_FRAGMENTED, error.failure)
    }

    @Test
    fun parsesProxySegmentUrls() {
        val playlist = HlsVodPlaylistParser.parseMediaPlaylist(
            "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fcdn.example.com%252Findex.m3u8",
            """
                #EXTM3U
                #EXTINF:6,
                http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fcdn.example.com%2Fseg00001.ts
                #EXT-X-ENDLIST
            """.trimIndent(),
        )

        assertEquals(1, playlist.segments.size)
        assertTrue(playlist.segments.single().url.contains("/proxy?"))
    }

    @Test
    fun parsesM4sSegments() {
        val playlist = HlsVodPlaylistParser.parseMediaPlaylist(
            "https://example.com/media.m3u8",
            """
                #EXTM3U
                #EXTINF:4,
                segment-00001.m4s
                #EXT-X-ENDLIST
            """.trimIndent(),
        )

        assertEquals(1, playlist.segments.size)
        assertTrue(playlist.segments.single().url.endsWith(".m4s"))
    }

    @Test
    fun parsesExtensionlessRelativeSegment() {
        val playlist = HlsVodPlaylistParser.parseMediaPlaylist(
            "https://example.com/hls/media.m3u8",
            """
                #EXTM3U
                #EXTINF:6,
                seg0001
                #EXT-X-ENDLIST
            """.trimIndent(),
        )

        assertEquals("https://example.com/hls/seg0001", playlist.segments.single().url)
        assertEquals(HlsSegmentFormat.TS, playlist.segmentFormat())
    }

    @Test
    fun parsesMp4SegmentUrlAsFmp4() {
        val playlist = HlsVodPlaylistParser.parseMediaPlaylist(
            "https://example.com/media.m3u8",
            """
                #EXTM3U
                #EXTINF:4,
                https://cdn.example.com/video/segment.mp4?token=1
                #EXT-X-ENDLIST
            """.trimIndent(),
        )

        assertEquals("https://cdn.example.com/video/segment.mp4?token=1", playlist.segments.single().url)
        assertEquals(HlsSegmentFormat.FMP4, playlist.segmentFormat())
    }

    @Test
    fun parsesDisguisedJpgSegmentUrl() {
        val playlist = HlsVodPlaylistParser.parseMediaPlaylist(
            "https://example.com/media.m3u8",
            """
                #EXTM3U
                #EXTINF:3,
                https://cdn.example.com/camo/0.jpg
                #EXT-X-ENDLIST
            """.trimIndent(),
        )

        assertEquals("https://cdn.example.com/camo/0.jpg", playlist.segments.single().url)
    }

    @Test
    fun rejectsSegmentLineWithoutExtinf() {
        val error = assertThrows(HlsVodException::class.java) {
            HlsVodPlaylistParser.parseMediaPlaylist(
                "https://example.com/media.m3u8",
                """
                    #EXTM3U
                    https://cdn.example.com/camo/0.jpg
                    #EXT-X-ENDLIST
                """.trimIndent(),
            )
        }

        assertEquals(HlsVodFailure.UNSUPPORTED_SEGMENT, error.failure)
    }
}
