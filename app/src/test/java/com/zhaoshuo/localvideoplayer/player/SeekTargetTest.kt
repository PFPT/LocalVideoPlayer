package com.zhaoshuo.localvideoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Test

class SeekTargetTest {
    @Test
    fun clampsInsideKnownDuration() {
        assertEquals(90_000L, seekTargetMs(positionMs = 80_000L, deltaMs = 20_000L, durationMs = 90_000L))
        assertEquals(0L, seekTargetMs(positionMs = 5_000L, deltaMs = -10_000L, durationMs = 90_000L))
    }

    @Test
    fun relativeSeekWithoutDurationOnlyClampsAtZero() {
        assertEquals(70_000L, seekTargetMs(positionMs = 60_000L, deltaMs = 10_000L, durationMs = 0L))
        assertEquals(0L, seekTargetMs(positionMs = 4_000L, deltaMs = -10_000L, durationMs = 0L))
    }

    @Test
    fun unseekableUnknownDurationStaysWithinBuffer() {
        assertEquals(
            12_000L,
            seekTargetMs(
                positionMs = 10_000L,
                deltaMs = 30_000L,
                durationMs = 0L,
                bufferedPositionMs = 12_000L,
                seekable = false,
            ),
        )
        assertEquals(
            10_000L,
            seekTargetMs(
                positionMs = 10_000L,
                deltaMs = 30_000L,
                durationMs = 0L,
                bufferedPositionMs = 8_000L,
                seekable = false,
            ),
        )
    }

    @Test
    fun swipeRangeUsesOneMinuteWhenDurationIsUnknown() {
        assertEquals(SEEK_UNKNOWN_DURATION_SWIPE_MS, swipeSeekRangeMs(durationMs = 0L))
    }

    @Test
    fun swipeRangeUsesShorterKnownDuration() {
        assertEquals(30_000L, swipeSeekRangeMs(durationMs = 30_000L))
        assertEquals(SEEK_FULL_SWIPE_MS, swipeSeekRangeMs(durationMs = SEEK_FULL_SWIPE_MS + 1L))
    }
}
