package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HlsMergerTest {
    @Test
    fun writesConcatListWithAbsolutePaths() {
        val dir = createTempDir()
        try {
            val segments = listOf(
                File(dir, "seg00000.ts").apply { writeText("ts") },
                File(dir, "seg00001.ts").apply { writeText("ts") },
            )
            val listFile = File(dir, "segments.txt")
            HlsMerger.writeConcatList(
                listFile,
                HlsMerger.MergeInput(
                    segmentFiles = segments,
                    durationsSec = listOf(6.0, 4.5),
                    format = HlsSegmentFormat.TS,
                ),
            )
            val text = listFile.readText()
            assertTrue(text.contains(segments[0].absolutePath))
            assertTrue(text.contains(segments[1].absolutePath))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writesTsPlaylist() {
        val dir = createTempDir()
        try {
            val segments = listOf(
                File(dir, "seg00000.ts").apply { writeText("ts") },
                File(dir, "seg00001.ts").apply { writeText("ts") },
            )
            val playlist = File(dir, "index.m3u8")
            HlsMerger.writeLocalPlaylist(
                playlist,
                HlsMerger.MergeInput(
                    segmentFiles = segments,
                    durationsSec = listOf(6.0, 4.5),
                    format = HlsSegmentFormat.TS,
                ),
                HlsSegmentFormat.TS,
            )
            val text = playlist.readText()
            assertTrue(text.contains("#EXT-X-VERSION:3"))
            assertTrue(text.contains("#EXT-X-TARGETDURATION:"))
            assertTrue(text.contains("#EXTINF:6,"))
            assertTrue(text.contains("#EXTINF:4.5,"))
            assertTrue(text.contains("seg00000.ts"))
            assertTrue(text.contains("#EXT-X-ENDLIST"))
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun writesFmp4PlaylistWithInitMap() {
        val dir = createTempDir()
        try {
            val init = File(dir, "init.mp4").apply { writeText("init") }
            val segments = listOf(File(dir, "seg00000.m4s").apply { writeText("m4s") })
            val playlist = File(dir, "index.m3u8")
            HlsMerger.writeLocalPlaylist(
                playlist,
                HlsMerger.MergeInput(
                    segmentFiles = segments,
                    durationsSec = listOf(6.0),
                    format = HlsSegmentFormat.FMP4,
                    initFile = init,
                ),
                HlsSegmentFormat.FMP4,
            )
            val text = playlist.readText()
            assertTrue(text.contains("#EXT-X-VERSION:6"))
            assertTrue(text.contains("""#EXT-X-MAP:URI="init.mp4""""))
            assertTrue(text.contains("seg00000.m4s"))
        } finally {
            dir.deleteRecursively()
        }
    }
}
