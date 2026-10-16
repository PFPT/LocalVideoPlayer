package com.pfpt.localvideoplayer.player

import androidx.media3.common.MimeTypes
import org.junit.Assert.assertEquals
import org.junit.Test

class SubtitleMimeTest {

    @Test
    fun mapsVtt() {
        assertEquals(MimeTypes.TEXT_VTT, subtitleMimeType("file:///subs/show.vtt"))
    }

    @Test
    fun mapsAssAndSsa() {
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("content://media/subs/show.ass"))
        assertEquals(MimeTypes.TEXT_SSA, subtitleMimeType("file:///tmp/show.SSA"))
    }

    @Test
    fun defaultsToSubRip() {
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("file:///subs/show.srt"))
        assertEquals(MimeTypes.APPLICATION_SUBRIP, subtitleMimeType("content://media/123"))
    }

    @Test
    fun sidecarCandidatesUseBaseName() {
        assertEquals(
            listOf("Show.srt", "Show.vtt", "Show.ass", "Show.ssa"),
            sidecarSubtitleCandidates("Show.mp4"),
        )
    }
}
