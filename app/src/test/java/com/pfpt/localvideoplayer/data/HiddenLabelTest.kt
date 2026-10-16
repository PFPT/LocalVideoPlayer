package com.pfpt.localvideoplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class HiddenLabelTest {
    @Test
    fun usesStoredTitle() {
        val entry = decodeHiddenEntry(encodeHiddenEntry("content://media/1", "Episode"))
        assertEquals("Episode", hiddenDisplayLabel(entry))
        assertEquals("content://media/1", entry.uri)
    }

    @Test
    fun fallsBackToUriTail() {
        assertEquals(
            "1",
            hiddenDisplayLabel(HiddenVideoEntry("content://media/external/video/media/1", "")),
        )
        assertEquals(
            "clip.mp4",
            hiddenDisplayLabel(HiddenVideoEntry("content://tree/doc/clip.mp4", "  ")),
        )
        assertEquals(
            "legacy",
            hiddenDisplayLabel(decodeHiddenEntry("content://files/legacy")),
        )
    }
}
