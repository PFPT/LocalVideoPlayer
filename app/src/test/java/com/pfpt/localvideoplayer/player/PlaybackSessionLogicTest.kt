package com.pfpt.localvideoplayer.player

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
        assertTrue(subtitleTrackStaysOff(textOff = true, alreadyDisabled = false, isCurrent = true))
        assertFalse(subtitleTrackStaysOff(textOff = true, alreadyDisabled = false, isCurrent = false))
        assertTrue(subtitleTrackStaysOff(textOff = false, alreadyDisabled = true, isCurrent = false))
        assertFalse(subtitleTrackStaysOff(textOff = false, alreadyDisabled = false, isCurrent = true))
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
    fun floatingWindowKeepsVideoAndStaysInsideTheScreen() {
        assertFalse(
            shouldSuppressVideo(onPlayerScreen = false, hostStarted = false, floating = true),
        )
        assertTrue(
            shouldSuppressVideo(onPlayerScreen = true, hostStarted = false, floating = false),
        )
        assertFalse(
            shouldSuppressVideo(onPlayerScreen = true, hostStarted = true, floating = false),
        )
        assertEquals(450 to 253, floatingWindowSize(1000, 2000, 1920, 1080))
        assertEquals(0, clampWindowOffset(-20, 0, 100))
        assertEquals(40, clampWindowOffset(80, 0, 40))
        assertEquals(0, clampWindowOffset(12, 0, -1))
        val grown = resizeFloatingFrame(
            origin = FloatingFrame(10, 20, 160, 90),
            corner = FloatingCorner.BottomRight,
            fingerX = 330,
            fingerY = 200,
            ratio = 16f / 9f,
            minWidth = 160,
            screenWidth = 1000,
            screenHeight = 1000,
        )
        assertEquals(10, grown.x)
        assertEquals(20, grown.y)
        assertEquals(320, grown.width)
        assertEquals(180, grown.height)
        val shrunk = resizeFloatingFrame(
            origin = FloatingFrame(200, 200, 320, 180),
            corner = FloatingCorner.TopLeft,
            fingerX = 40,
            fingerY = 110,
            ratio = 16f / 9f,
            minWidth = 160,
            screenWidth = 1000,
            screenHeight = 1000,
        )
        assertEquals(520, shrunk.x + shrunk.width)
        assertEquals(380, shrunk.y + shrunk.height)
        assertTrue(kotlin.math.abs(shrunk.width.toFloat() / shrunk.height - 16f / 9f) < 0.05f)
        val fitted = fitFloatingFrame(
            FloatingFrame(900, 40, 400, 200),
            screenWidth = 1000,
            screenHeight = 800,
        )
        assertTrue(fitted.x + fitted.width <= 1000)
        assertEquals(400, fitted.width)
        assertFalse(
            shouldSuppressVideo(onPlayerScreen = false, hostStarted = false, floating = true),
        )
    }

    @Test
    fun floatingTapOpensThePlayerOnlyOnTheSecondTap() {
        assertEquals(
            FloatingPointerAction.ToggleControls,
            floatingPointerAction(movedPastSlop = false, secondTapWithinTimeout = false),
        )
        assertEquals(
            FloatingPointerAction.OpenPlayer,
            floatingPointerAction(movedPastSlop = false, secondTapWithinTimeout = true),
        )
        assertEquals(
            FloatingPointerAction.Move,
            floatingPointerAction(movedPastSlop = true, secondTapWithinTimeout = true),
        )
    }

    @Test
    fun floatingCornerHandlesSitOnTheirOwnCorners() {
        val width = 200
        val height = 100
        val handle = 28
        val topLeft = floatingCornerHandle(width, height, handle, FloatingCorner.TopLeft)
        val topRight = floatingCornerHandle(width, height, handle, FloatingCorner.TopRight)
        val bottomLeft = floatingCornerHandle(width, height, handle, FloatingCorner.BottomLeft)
        val bottomRight = floatingCornerHandle(width, height, handle, FloatingCorner.BottomRight)
        assertEquals(FloatingFrame(0, 0, handle, handle), topLeft)
        assertEquals(FloatingFrame(width - handle, 0, handle, handle), topRight)
        assertEquals(FloatingFrame(0, height - handle, handle, handle), bottomLeft)
        assertEquals(FloatingFrame(width - handle, height - handle, handle, handle), bottomRight)
        assertTrue(topRight.x > width / 2)
        assertTrue(bottomLeft.y > height / 2)
        assertEquals(112, floatingMinWidthPx(screenShortSidePx = 400, density = 3f))
        assertEquals(120, floatingMinWidthPx(screenShortSidePx = 1000, density = 1f))
        assertEquals(302, floatingMinWidthPx(screenShortSidePx = 1080, density = 3f))
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
