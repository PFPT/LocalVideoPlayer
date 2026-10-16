package com.pfpt.localvideoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class AbLoopTest {
    @Test
    fun returnsAOncePositionReachesB() {
        assertNull(abLoopTarget(1_000L, 5_000L, 4_999L))
        assertEquals(1_000L, abLoopTarget(1_000L, 5_000L, 5_000L))
        assertEquals(1_000L, abLoopTarget(1_000L, 5_000L, 6_000L))
    }

    @Test
    fun ignoresMissingOrReversedPoints() {
        assertNull(abLoopTarget(null, 5_000L, 5_000L))
        assertNull(abLoopTarget(1_000L, null, 5_000L))
        assertNull(abLoopTarget(5_000L, 1_000L, 5_000L))
        assertNull(abLoopTarget(1_000L, 1_000L, 1_000L))
    }
}
