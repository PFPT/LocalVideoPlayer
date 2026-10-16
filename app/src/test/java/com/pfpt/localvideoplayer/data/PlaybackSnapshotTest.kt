package com.zhaoshuo.localvideoplayer.data

import android.net.Uri
import com.zhaoshuo.localvideoplayer.model.PlaybackRecord
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class PlaybackSnapshotTest {
    @Test
    fun snapshotKeepsRecordsBeyondRecentLimit() {
        val records = (1..20).associate { index ->
            val uri = "uri-$index"
            uri to PlaybackRecord(
                uri = Uri.parse(uri),
                title = "title-$index",
                positionMs = 10_000L,
                durationMs = 100_000L,
                lastPlayedAt = index.toLong(),
            )
        }
        val order = (1..20).fold(emptyList<String>()) { acc, index ->
            updateRecentOrder(acc, "uri-$index")
        }
        val view = playbackSnapshotView(records, order)
        assertEquals(15, view.recent.size)
        assertEquals(20, view.records.size)
        assertEquals("uri-20", view.recent.first().uri.toString())
        assertTrue(view.records.containsKey("uri-1"))
        assertTrue(view.recent.none { it.uri.toString() == "uri-1" })
    }
}
