package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadSpeedTrackerTest {
    @Test
    fun markProgressComputesSpeed() {
        var now = 0L
        val tracker = DownloadSpeedTracker(windowMs = 3000) { now }
        assertEquals(0L, tracker.markProgress(0))
        now = 1000
        assertEquals(1000L, tracker.markProgress(1000))
        now = 2000
        assertEquals(1000L, tracker.markProgress(2000))
    }

    @Test
    fun estimateRemainingSeconds() {
        var now = 0L
        val tracker = DownloadSpeedTracker(windowMs = 3000) { now }
        tracker.markProgress(0)
        now = 1000
        tracker.markProgress(1000)
        assertEquals(9L, tracker.estimateRemainingSeconds(1000, 10_000))
        assertNull(tracker.estimateRemainingSeconds(1000, -1))
        assertNull(tracker.estimateRemainingSeconds(1000, 1000))
    }
}
