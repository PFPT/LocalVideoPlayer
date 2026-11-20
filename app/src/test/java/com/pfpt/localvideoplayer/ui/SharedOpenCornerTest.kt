package com.pfpt.localvideoplayer.ui

import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.ui.theme.LumenThumbCorner
import org.junit.Assert.assertEquals
import org.junit.Test

class SharedOpenCornerTest {
    @Test
    fun thumbnailAtRestKeepsThumbCorner() {
        assertEquals(
            LumenThumbCorner,
            sharedOpenCorner(restingCorner = LumenThumbCorner, atRest = true),
        )
    }

    @Test
    fun thumbnailInFlightBecomesSquare() {
        assertEquals(
            0.dp,
            sharedOpenCorner(restingCorner = LumenThumbCorner, atRest = false),
        )
    }

    @Test
    fun playerAtRestStaysSquare() {
        assertEquals(
            0.dp,
            sharedOpenCorner(restingCorner = 0.dp, atRest = true),
        )
    }

    @Test
    fun playerInFlightUsesThumbCorner() {
        assertEquals(
            LumenThumbCorner,
            sharedOpenCorner(restingCorner = 0.dp, atRest = false),
        )
    }
}