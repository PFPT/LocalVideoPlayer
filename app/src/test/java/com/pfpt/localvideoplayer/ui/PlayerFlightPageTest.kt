package com.pfpt.localvideoplayer.ui

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFlightPageTest {
    @Test
    fun pageHidesWhileTransitionRuns() {
        assertTrue(hidePlayerPageForTransition(transitionActive = true))
    }

    @Test
    fun pageShowsWhenTransitionIsIdle() {
        assertFalse(hidePlayerPageForTransition(transitionActive = false))
    }
}