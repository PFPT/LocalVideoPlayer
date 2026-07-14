package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ProgressiveRangePlannerTest {
    @Test
    fun clampConnections_boundsValue() {
        assertEquals(2, ProgressiveRangePlanner.clampConnections(0))
        assertEquals(2, ProgressiveRangePlanner.clampConnections(1))
        assertEquals(8, ProgressiveRangePlanner.clampConnections(8))
        assertEquals(24, ProgressiveRangePlanner.clampConnections(24))
        assertEquals(32, ProgressiveRangePlanner.clampConnections(32))
        assertEquals(32, ProgressiveRangePlanner.clampConnections(100))
        assertEquals(24, ProgressiveRangePlanner.DEFAULT_CONNECTIONS)
        assertEquals(32, ProgressiveRangePlanner.MAX_CONNECTIONS)
    }

    @Test
    fun shouldUseMultiRange_requiresMinimumSize() {
        assertFalse(ProgressiveRangePlanner.shouldUseMultiRange(1024, 8))
        assertFalse(
            ProgressiveRangePlanner.shouldUseMultiRange(
                ProgressiveRangePlanner.MIN_MULTI_RANGE_BYTES - 1,
                8,
            ),
        )
        assertTrue(
            ProgressiveRangePlanner.shouldUseMultiRange(
                ProgressiveRangePlanner.MIN_MULTI_RANGE_BYTES,
                8,
            ),
        )
    }

    @Test
    fun plan_smallFile_returnsSingleRange() {
        val ranges = ProgressiveRangePlanner.plan(1024, 8)
        assertEquals(1, ranges.size)
        assertEquals(0, ranges[0].start)
        assertEquals(1023, ranges[0].endInclusive)
        assertEquals(1024, ranges[0].length)
    }

    @Test
    fun plan_coversEntireFileWithoutGapsOrOverlap() {
        val total = 10L * 1024 * 1024 + 7
        val ranges = ProgressiveRangePlanner.plan(total, 8)
        assertEquals(8, ranges.size)
        assertEquals(0, ranges.first().start)
        assertEquals(total - 1, ranges.last().endInclusive)
        assertEquals(total, ranges.sumOf { it.length })
        for (index in 1 until ranges.size) {
            assertEquals(ranges[index - 1].endInclusive + 1, ranges[index].start)
        }
    }

    @Test
    fun plan_lastSegmentAlignedWhenUneven() {
        val total = ProgressiveRangePlanner.MIN_MULTI_RANGE_BYTES + 3
        val ranges = ProgressiveRangePlanner.plan(total, 4)
        assertEquals(4, ranges.size)
        assertEquals(total, ranges.sumOf { it.length })
        assertEquals(total - 1, ranges.last().endInclusive)
        val lengths = ranges.map { it.length }
        assertTrue(lengths.maxOrNull()!! - lengths.minOrNull()!! <= 1)
    }

    @Test
    fun plan_clampsConnectionCount() {
        val total = 8L * 1024 * 1024
        val ranges = ProgressiveRangePlanner.plan(total, 100)
        assertEquals(32, ranges.size)
        assertEquals(total, ranges.sumOf { it.length })
    }
}
