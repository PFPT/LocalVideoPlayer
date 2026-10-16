package com.pfpt.localvideoplayer.data

import android.net.Uri
import com.pfpt.localvideoplayer.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class DurationBackfillTest {
    @Test
    fun resolvedDurationFillsOnlyMissingVideos() {
        val known = video(1, "a.mp4")
        val missing = video(2, "b.mp4").copy(durationMs = 0L)
        val updated = withResolvedDurations(
            listOf(known, missing),
            mapOf(missing.uri.toString() to 42_000L),
        )
        assertEquals(1_000L, updated[0].durationMs)
        assertEquals(42_000L, updated[1].durationMs)
        assertEquals(listOf(known, missing), withResolvedDurations(listOf(known, missing), emptyMap()))
    }

    private fun video(id: Long, title: String): VideoItem {
        return VideoItem(
            id = id,
            uri = Uri.parse("content://media/external/video/media/$id"),
            title = title,
            durationMs = 1_000L,
            dateAdded = id,
            thumbnailUri = null,
        )
    }
}
