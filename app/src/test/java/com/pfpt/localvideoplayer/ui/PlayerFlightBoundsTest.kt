package com.pfpt.localvideoplayer.ui

import androidx.compose.animation.core.Spring
import androidx.compose.ui.geometry.Rect
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class PlayerFlightBoundsTest {
    @Test
    fun toPlayerHidesOnceSourceIsKnown() {
        assertTrue(
            flightPageHidden(
                phase = FlightPhase.ToPlayer,
                sourceKnown = true,
                targetKnown = false,
            ),
        )
    }

    @Test
    fun toPlayerStaysVisibleWithoutSource() {
        assertFalse(
            flightPageHidden(
                phase = FlightPhase.ToPlayer,
                sourceKnown = false,
                targetKnown = true,
            ),
        )
    }

    @Test
    fun toListHidesOnlyWhenBothEndsAreKnown() {
        assertFalse(
            flightPageHidden(
                phase = FlightPhase.ToList,
                sourceKnown = false,
                targetKnown = true,
            ),
        )
        assertTrue(
            flightPageHidden(
                phase = FlightPhase.ToList,
                sourceKnown = true,
                targetKnown = true,
            ),
        )
    }

    @Test
    fun idleFlightKeepsThePageVisible() {
        assertFalse(
            flightPageHidden(
                phase = FlightPhase.None,
                sourceKnown = true,
                targetKnown = true,
            ),
        )
    }

    @Test
    fun boundsFollowTheFraction() {
        val start = Rect(0f, 0f, 10f, 20f)
        val end = Rect(10f, 30f, 40f, 70f)
        assertEquals(start, flightBounds(start, end, 0f))
        assertEquals(end, flightBounds(start, end, 1f))
        assertEquals(Rect(5f, 15f, 25f, 45f), flightBounds(start, end, 0.5f))
    }

    @Test
    fun boundsSpringUsesMediumLowStiffness() {
        assertEquals(Spring.StiffnessMediumLow, flightBoundsSpring().stiffness)
    }
}
