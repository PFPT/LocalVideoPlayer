package com.pfpt.localvideoplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFlightSurfaceTest {
    @Test
    fun surfaceViewHidesWhileTransitionRuns() {
        assertTrue(
            hidePlayerSurfaceForTransition(
                useTextureView = false,
                isCasting = false,
                transitionActive = true,
            ),
        )
    }

    @Test
    fun surfaceViewShowsWhenTransitionIsIdle() {
        assertFalse(
            hidePlayerSurfaceForTransition(
                useTextureView = false,
                isCasting = false,
                transitionActive = false,
            ),
        )
    }

    @Test
    fun textureViewStaysVisibleDuringTransition() {
        assertFalse(
            hidePlayerSurfaceForTransition(
                useTextureView = true,
                isCasting = false,
                transitionActive = true,
            ),
        )
    }

    @Test
    fun castSurfaceStaysVisibleDuringTransition() {
        assertFalse(
            hidePlayerSurfaceForTransition(
                useTextureView = false,
                isCasting = true,
                transitionActive = true,
            ),
        )
    }
}