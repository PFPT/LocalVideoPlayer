package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class HlsResumeTest {
    @Test
    fun resumeFromAllSegmentsOnDiskEvenWhenMetadataMismatch() {
        val dir = createTempDir()
        try {
            repeat(3) { index -> File(dir, "seg%05d.ts".format(index)).writeText("segment") }
            val task = DownloadTask(
                url = "https://example.com/stream.m3u8",
                fileName = "video.mp4",
                hlsCompletedSegments = 3,
                hlsTotalSegments = 3,
                hlsPlaylistUrl = "https://old.example.com/stream.m3u8",
                hlsPlaylistSignature = "old-signature",
            )
            val playlist = HlsVodPlaylist(
                url = "https://example.com/stream.m3u8",
                signature = "new-signature",
                segments = listOf(segment(), segment(), segment()),
            )
            val state = HlsResume.resolve(task, dir, playlist) { index -> File(dir, "seg%05d.ts".format(index)) }
            assertEquals(3, state.completedSegments)
            assertFalse(state.shouldWipe)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun resumeFromStoredProgressWhenMetadataMatches() {
        val dir = createTempDir()
        try {
            File(dir, "seg00000.ts").writeText("segment")
            File(dir, "seg00001.ts").writeText("segment")
            val task = DownloadTask(
                url = "https://example.com/stream.m3u8",
                fileName = "video.mp4",
                hlsCompletedSegments = 2,
                hlsTotalSegments = 4,
                hlsPlaylistUrl = "https://example.com/stream.m3u8",
                hlsPlaylistSignature = "same-signature",
            )
            val playlist = HlsVodPlaylist(
                url = "https://example.com/stream.m3u8",
                signature = "same-signature",
                segments = listOf(segment(), segment(), segment(), segment()),
            )
            val state = HlsResume.resolve(task, dir, playlist) { index -> File(dir, "seg%05d.ts".format(index)) }
            assertEquals(2, state.completedSegments)
            assertFalse(state.shouldWipe)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun wipeWhenPartialSegmentsDoNotMatchMetadata() {
        val dir = createTempDir()
        try {
            File(dir, "seg00000.ts").writeText("segment")
            val task = DownloadTask(
                url = "https://example.com/stream.m3u8",
                fileName = "video.mp4",
                hlsCompletedSegments = 1,
                hlsTotalSegments = 2,
                hlsPlaylistUrl = "https://old.example.com/stream.m3u8",
                hlsPlaylistSignature = "old-signature",
            )
            val playlist = HlsVodPlaylist(
                url = "https://example.com/stream.m3u8",
                signature = "new-signature",
                segments = listOf(segment(), segment()),
            )
            val state = HlsResume.resolve(task, dir, playlist) { index -> File(dir, "seg%05d.ts".format(index)) }
            assertEquals(0, state.completedSegments)
            assertTrue(state.shouldWipe)
        } finally {
            dir.deleteRecursively()
        }
    }

    @Test
    fun resumeFromLegacyTsFilesWhenPlaylistIsFmp4() {
        val dir = createTempDir()
        try {
            repeat(2) { index -> File(dir, "seg%05d.ts".format(index)).writeText("segment") }
            val task = DownloadTask(
                url = "https://example.com/stream.m3u8",
                fileName = "video.mp4",
                hlsCompletedSegments = 2,
                hlsTotalSegments = 2,
                hlsPlaylistUrl = "https://example.com/stream.m3u8",
                hlsPlaylistSignature = "same-signature",
            )
            val playlist = HlsVodPlaylist(
                url = "https://example.com/stream.m3u8",
                signature = "same-signature",
                initSegmentUrl = "https://example.com/init.mp4",
                segments = listOf(segment("seg.m4s"), segment("seg.m4s")),
            )
            val state = HlsResume.resolve(task, dir, playlist) { index ->
                File(dir, "seg%05d.m4s".format(index))
            }
            assertEquals(2, state.completedSegments)
            assertFalse(state.shouldWipe)
        } finally {
            dir.deleteRecursively()
        }
    }

    private fun segment(url: String = "https://example.com/segment.ts") = HlsSegment(
        url = url,
        durationSec = 4.0,
        encryption = null,
    )
}
