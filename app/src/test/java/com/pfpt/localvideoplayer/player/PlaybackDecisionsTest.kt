package com.pfpt.localvideoplayer.player

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
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

    @Test
    fun defaultSpeedReplacesBothValuesWhenNotBoosted() {
        val next = speedAfterDefault(
            snapshot = SpeedSnapshot(playbackSpeed = 1.5f, savedPlaybackSpeed = 1.5f, boosted = false),
            defaultSpeed = 1.25f,
        )
        assertEquals(1.25f, next.playbackSpeed, 0f)
        assertEquals(1.25f, next.savedPlaybackSpeed, 0f)
        assertFalse(next.boosted)
    }

    @Test
    fun defaultSpeedLeavesBoostedSnapshotUntouched() {
        val current = SpeedSnapshot(playbackSpeed = 2f, savedPlaybackSpeed = 1.5f, boosted = true)
        assertEquals(current, speedAfterDefault(current, defaultSpeed = 1.25f))
    }

    @Test
    fun boostFromNormalSpeedUsesFloorAndPlaysWhenPaused() {
        val change = speedBoostStart(
            snapshot = SpeedSnapshot(playbackSpeed = 1f, savedPlaybackSpeed = 1f, boosted = false),
            isPlaying = false,
        )
        assertEquals(
            SpeedChange.Set(
                playbackSpeed = 2f,
                savedPlaybackSpeed = 1f,
                boosted = true,
                playIfPaused = true,
            ),
            change,
        )
    }

    @Test
    fun boostWhileAlreadyPlayingDoesNotRequestPlay() {
        val change = speedBoostStart(
            snapshot = SpeedSnapshot(playbackSpeed = 1f, savedPlaybackSpeed = 1f, boosted = false),
            isPlaying = true,
        )
        assertEquals(
            SpeedChange.Set(
                playbackSpeed = 2f,
                savedPlaybackSpeed = 1f,
                boosted = true,
                playIfPaused = false,
            ),
            change,
        )
    }

    @Test
    fun boostKeepsSpeedAboveFloor() {
        val change = speedBoostStart(
            snapshot = SpeedSnapshot(playbackSpeed = 3f, savedPlaybackSpeed = 1f, boosted = false),
            isPlaying = true,
        )
        assertEquals(
            SpeedChange.Set(
                playbackSpeed = 3f,
                savedPlaybackSpeed = 3f,
                boosted = true,
                playIfPaused = false,
            ),
            change,
        )
    }

    @Test
    fun repeatedBoostIsUnchanged() {
        assertEquals(
            SpeedChange.Unchanged,
            speedBoostStart(
                snapshot = SpeedSnapshot(playbackSpeed = 2f, savedPlaybackSpeed = 1f, boosted = true),
                isPlaying = false,
            ),
        )
    }

    @Test
    fun endingBoostRestoresSavedSpeed() {
        assertEquals(
            SpeedSnapshot(playbackSpeed = 1.5f, savedPlaybackSpeed = 1.5f, boosted = false),
            speedBoostEnd(SpeedSnapshot(playbackSpeed = 2f, savedPlaybackSpeed = 1.5f, boosted = true)),
        )
    }

    @Test
    fun endingBoostWithoutActiveBoostReturnsSnapshot() {
        val current = SpeedSnapshot(playbackSpeed = 1.25f, savedPlaybackSpeed = 1.25f, boosted = false)
        assertEquals(current, speedBoostEnd(current))
    }

    @Test
    fun leaveRestoresSavedSpeedOnlyWhileBoosted() {
        assertEquals(
            1.5f,
            speedToRestoreOnLeave(SpeedSnapshot(playbackSpeed = 2f, savedPlaybackSpeed = 1.5f, boosted = true))!!,
            0f,
        )
        assertNull(
            speedToRestoreOnLeave(SpeedSnapshot(playbackSpeed = 1.5f, savedPlaybackSpeed = 1.5f, boosted = false)),
        )
    }

    @Test
    fun firstRetryableFailureRecoversWithSoftwareDecoder() {
        assertEquals(
            DecoderRecovery.RetrySoft,
            decoderRecovery(
                retryable = true,
                sourceRead = false,
                appliedSoftDecode = false,
                alreadyRetried = false,
                retryPending = true,
            ),
        )
    }

    @Test
    fun retryableFailureAfterSoftwareDecodeWaitsOrFails() {
        assertEquals(
            DecoderRecovery.Ignore,
            decoderRecovery(
                retryable = true,
                sourceRead = true,
                appliedSoftDecode = true,
                alreadyRetried = false,
                retryPending = true,
            ),
        )
        assertEquals(
            DecoderRecovery.FailRead,
            decoderRecovery(
                retryable = true,
                sourceRead = true,
                appliedSoftDecode = false,
                alreadyRetried = true,
                retryPending = false,
            ),
        )
        assertEquals(
            DecoderRecovery.FailDecode,
            decoderRecovery(
                retryable = true,
                sourceRead = false,
                appliedSoftDecode = true,
                alreadyRetried = true,
                retryPending = false,
            ),
        )
    }

    @Test
    fun pendingRetryIgnoresFurtherErrors() {
        assertEquals(
            DecoderRecovery.Ignore,
            decoderRecovery(
                retryable = false,
                sourceRead = true,
                appliedSoftDecode = true,
                alreadyRetried = true,
                retryPending = true,
            ),
        )
    }

    @Test
    fun terminalFailureFollowsSourceReadFlag() {
        assertEquals(
            DecoderRecovery.FailRead,
            decoderRecovery(
                retryable = false,
                sourceRead = true,
                appliedSoftDecode = false,
                alreadyRetried = false,
                retryPending = false,
            ),
        )
        assertEquals(
            DecoderRecovery.FailDecode,
            decoderRecovery(
                retryable = false,
                sourceRead = false,
                appliedSoftDecode = false,
                alreadyRetried = false,
                retryPending = false,
            ),
        )
    }
}