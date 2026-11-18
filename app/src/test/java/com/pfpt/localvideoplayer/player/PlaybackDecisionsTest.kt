package com.pfpt.localvideoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackDecisionsTest {
    @Test
    fun liveDurationWinsWhenPositive() {
        assertEquals(8_000L, resolvedDurationMs(liveDurationMs = 8_000L, catalogDurationMs = 1_000L))
    }

    @Test
    fun catalogDurationUsedWhenLiveIsMissing() {
        assertEquals(5_000L, resolvedDurationMs(liveDurationMs = 0L, catalogDurationMs = 5_000L))
        assertEquals(5_000L, resolvedDurationMs(liveDurationMs = -1L, catalogDurationMs = 5_000L))
    }

    @Test
    fun missingDurationsResolveToZero() {
        assertEquals(0L, resolvedDurationMs(liveDurationMs = 0L, catalogDurationMs = 0L))
        assertEquals(0L, resolvedDurationMs(liveDurationMs = -1L, catalogDurationMs = -5L))
    }

    @Test
    fun castDurationWinsWhileCasting() {
        assertEquals(
            9_000L,
            displayDurationMs(isCasting = true, castDurationMs = 9_000L, localDurationMs = 4_000L),
        )
    }

    @Test
    fun localDurationUsedWhenCastDurationIsMissing() {
        assertEquals(
            4_000L,
            displayDurationMs(isCasting = false, castDurationMs = 9_000L, localDurationMs = 4_000L),
        )
        assertEquals(
            4_000L,
            displayDurationMs(isCasting = true, castDurationMs = 0L, localDurationMs = 4_000L),
        )
    }

    @Test
    fun backfillOnlyWhenBothDurationsAreMissing() {
        assertTrue(shouldBackfillDuration(catalogDurationMs = 0L, liveDurationMs = 0L))
        assertFalse(shouldBackfillDuration(catalogDurationMs = 1L, liveDurationMs = 0L))
        assertFalse(shouldBackfillDuration(catalogDurationMs = 0L, liveDurationMs = 1L))
    }

    @Test
    fun publishOnlyWhenScreenKnowsDurationAndCatalogDoesNot() {
        assertTrue(shouldPublishKnownDuration(displayDurationMs = 3_000L, catalogDurationMs = 0L))
        assertFalse(shouldPublishKnownDuration(displayDurationMs = 0L, catalogDurationMs = 0L))
        assertFalse(shouldPublishKnownDuration(displayDurationMs = 3_000L, catalogDurationMs = 3_000L))
    }

    @Test
    fun knownPlayerDurationAlwaysApplies() {
        assertEquals(
            SeekCommit.Apply(1_000L),
            seekCommit(
                targetMs = 1_000L,
                playerDurationMs = 9_000L,
                resolvedDurationMs = 0L,
                currentPositionMs = 4_000L,
                seekable = false,
            ),
        )
    }

    @Test
    fun resolvedDurationAppliesWhenPlayerDurationIsMissing() {
        assertEquals(
            SeekCommit.Apply(1_000L),
            seekCommit(
                targetMs = 1_000L,
                playerDurationMs = 0L,
                resolvedDurationMs = 9_000L,
                currentPositionMs = 4_000L,
                seekable = false,
            ),
        )
    }

    @Test
    fun seekableUnknownDurationAlwaysApplies() {
        assertEquals(
            SeekCommit.Apply(500L),
            seekCommit(
                targetMs = 500L,
                playerDurationMs = 0L,
                resolvedDurationMs = 0L,
                currentPositionMs = 4_000L,
                seekable = true,
            ),
        )
    }

    @Test
    fun unseekableUnknownDurationAppliesOnlyForward() {
        assertEquals(
            SeekCommit.Apply(5_000L),
            seekCommit(
                targetMs = 5_000L,
                playerDurationMs = -1L,
                resolvedDurationMs = 0L,
                currentPositionMs = 4_000L,
                seekable = false,
            ),
        )
        assertEquals(
            SeekCommit.Reject,
            seekCommit(
                targetMs = 4_000L,
                playerDurationMs = 0L,
                resolvedDurationMs = 0L,
                currentPositionMs = 4_000L,
                seekable = false,
            ),
        )
        assertEquals(
            SeekCommit.Reject,
            seekCommit(
                targetMs = 3_000L,
                playerDurationMs = 0L,
                resolvedDurationMs = 0L,
                currentPositionMs = 4_000L,
                seekable = false,
            ),
        )
    }
}