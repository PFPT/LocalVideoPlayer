package com.zhaoshuo.localvideoplayer.player

import androidx.media3.common.Player
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaybackSessionLogicTest {
    @Test
    fun reusesTheSamePlaylistAndReplacesADifferentOne() {
        val movie = playbackPlaylistKey(listOf("content://movie"))
        val series = playbackPlaylistKey(listOf("content://ep1", "content://ep2"))
        assertEquals(
            PlaybackOpenAction.Reuse,
            playbackOpenAction(
                activeKey = series,
                requestedKey = series,
                activeSoftDecode = false,
                requestedSoftDecode = false,
                hasPlayer = true,
            ),
        )
        assertEquals(
            PlaybackOpenAction.Replace,
            playbackOpenAction(
                activeKey = series,
                requestedKey = movie,
                activeSoftDecode = false,
                requestedSoftDecode = false,
                hasPlayer = true,
            ),
        )
        assertEquals(
            PlaybackOpenAction.Replace,
            playbackOpenAction(
                activeKey = series,
                requestedKey = series,
                activeSoftDecode = false,
                requestedSoftDecode = true,
                hasPlayer = true,
            ),
        )
        assertEquals(
            PlaybackOpenAction.Replace,
            playbackOpenAction(
                activeKey = null,
                requestedKey = movie,
                activeSoftDecode = false,
                requestedSoftDecode = false,
                hasPlayer = false,
            ),
        )
    }

    @Test
    fun returningToTheSessionDoesNotSeekAwayFromTheLiveIndex() {
        assertFalse(
            shouldSeekToRequestedIndex(
                action = PlaybackOpenAction.Reuse,
                returnToExisting = true,
                playerIndex = 2,
                requestedIndex = 0,
            ),
        )
        assertTrue(
            shouldSeekToRequestedIndex(
                action = PlaybackOpenAction.Reuse,
                returnToExisting = false,
                playerIndex = 2,
                requestedIndex = 0,
            ),
        )
        assertFalse(
            shouldSeekToRequestedIndex(
                action = PlaybackOpenAction.Replace,
                returnToExisting = false,
                playerIndex = 0,
                requestedIndex = 1,
            ),
        )
    }

    @Test
    fun manualSubtitleStaysOnThatVideo() {
        val manual = mapOf("content://ep1" to "content://ep1.srt")
        assertEquals(
            "content://ep1.srt",
            subtitleForVideo("content://ep1", manual, "content://ep1.ass"),
        )
        assertEquals(
            "content://ep2.srt",
            subtitleForVideo("content://ep2", manual, "content://ep2.srt"),
        )
        assertNull(subtitleForVideo("content://ep3", manual, null))
    }

    @Test
    fun preloadsNeighborsAndAttachesOnlyAMissingSubtitle() {
        assertEquals(listOf(0, 1), indicesToPreload(current = 0, count = 4))
        assertEquals(listOf(1, 2, 3), indicesToPreload(current = 2, count = 4))
        assertFalse(
            shouldPreloadSubtitle(
                index = 2,
                currentIndex = 2,
                existingSubtitle = null,
                desiredSubtitle = "content://ep.srt",
            ),
        )
        assertTrue(
            shouldPreloadSubtitle(
                index = 3,
                currentIndex = 2,
                existingSubtitle = null,
                desiredSubtitle = "content://ep.srt",
            ),
        )
        assertFalse(
            shouldPreloadSubtitle(
                index = 3,
                currentIndex = 2,
                existingSubtitle = "content://ep.srt",
                desiredSubtitle = "content://ep.srt",
            ),
        )
        assertTrue(shouldAttachOnArrival(existingSubtitle = null, desiredSubtitle = "content://ep.srt"))
        assertFalse(shouldAttachOnArrival(existingSubtitle = "content://ep.srt", desiredSubtitle = "content://ep.srt"))
        assertFalse(shouldAttachOnArrival(existingSubtitle = null, desiredSubtitle = null))
    }

    @Test
    fun sleepMinutesDeadlinePausesAndEndOfItemDoesNotUseAClock() {
        assertNull(sleepDeadlineElapsed(SleepChoice.Off, nowElapsedMs = 1_000L))
        assertNull(sleepDeadlineElapsed(SleepChoice.EndOfItem, nowElapsedMs = 1_000L))
        assertEquals(901_000L, sleepDeadlineElapsed(SleepChoice.Minutes(15), nowElapsedMs = 1_000L))
        assertEquals(0L, sleepRemaining(deadlineElapsedMs = 1_000L, nowElapsedMs = 1_500L))
        assertEquals(500L, sleepRemaining(deadlineElapsedMs = 1_500L, nowElapsedMs = 1_000L))
        assertTrue(pausesAtEndOfItem(SleepChoice.EndOfItem))
        assertFalse(pausesAtEndOfItem(SleepChoice.Minutes(30)))
        assertFalse(pausesAtEndOfItem(SleepChoice.Off))
        assertEquals("14:05", formatSleepRemaining(845_000L))
    }

    @Test
    fun pauseIconFollowsIntentAndClearsWhenPlaybackEnds() {
        assertTrue(showsPauseIcon(playWhenReady = true, playbackState = Player.STATE_BUFFERING))
        assertTrue(showsPauseIcon(playWhenReady = true, playbackState = Player.STATE_READY))
        assertFalse(showsPauseIcon(playWhenReady = true, playbackState = Player.STATE_ENDED))
        assertFalse(showsPauseIcon(playWhenReady = false, playbackState = Player.STATE_READY))
        assertFalse(showsPauseIcon(playWhenReady = false, playbackState = Player.STATE_BUFFERING))
    }

    @Test
    fun skipButtonsUseAnOpenEndAndNeedAKnownDuration() {
        assertTrue(showSkipIntro(positionMs = 0L, introEndMs = 90_000L, isSeeking = false))
        assertFalse(showSkipIntro(positionMs = 90_000L, introEndMs = 90_000L, isSeeking = false))
        assertFalse(showSkipIntro(positionMs = 1_000L, introEndMs = 90_000L, isSeeking = true))
        assertFalse(showSkipOutro(positionMs = 500_000L, outroStartMs = 480_000L, durationMs = 0L, isSeeking = false))
        assertTrue(showSkipOutro(positionMs = 500_000L, outroStartMs = 480_000L, durationMs = 600_000L, isSeeking = false))
        assertFalse(showSkipOutro(positionMs = 600_000L, outroStartMs = 480_000L, durationMs = 600_000L, isSeeking = false))
    }
}
