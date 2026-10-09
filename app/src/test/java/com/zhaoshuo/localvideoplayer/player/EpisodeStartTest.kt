package com.zhaoshuo.localvideoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class EpisodeStartTest {
    @Test
    fun usesStoredResumeWhenSwitchingEpisode() {
        assertEquals(12_000L, episodeStartPositionMs(resumePositionMs = 12_000L))
    }

    @Test
    fun startsAtZeroWhenNothingIsResumable() {
        assertEquals(0L, episodeStartPositionMs(resumePositionMs = 0L))
    }

    @Test
    fun replayPositionOverridesStoredResume() {
        assertEquals(
            4_500L,
            episodeStartPositionMs(resumePositionMs = 12_000L, replayPositionMs = 4_500L),
        )
    }
}
