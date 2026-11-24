package com.pfpt.localvideoplayer.ui.player

import org.junit.Assert.assertEquals
import org.junit.Test

class PlayerGestureTest {
    @Test
    fun brightnessStaysInsideTheGestureRange() {
        assertEquals(0.01f, clampedBrightnessFraction(0f), 0f)
        assertEquals(0.4f, clampedBrightnessFraction(0.4f), 0f)
        assertEquals(1f, clampedBrightnessFraction(2f), 0f)
    }

    @Test
    fun volumeStaysInsideTheGestureRange() {
        assertEquals(0f, clampedVolumeFraction(-1f), 0f)
        assertEquals(0.2f, clampedVolumeFraction(0.2f), 0f)
        assertEquals(1f, clampedVolumeFraction(3f), 0f)
    }
}
