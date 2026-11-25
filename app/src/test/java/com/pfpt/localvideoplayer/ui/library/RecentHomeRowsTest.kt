package com.pfpt.localvideoplayer.ui.library

import android.net.Uri
import com.pfpt.localvideoplayer.model.PlaybackRecord
import com.pfpt.localvideoplayer.model.SeriesGroupKind
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.model.VideoLibraryEntry
import com.pfpt.localvideoplayer.model.VideoSeries
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class RecentHomeRowsTest {
    @Test
    fun seriesKeepsTheNewestEpisode() {
        val older = record("content://video/2", "Show E1", playedAt = 1_000)
        val newer = record("content://video/3", "Show E2", playedAt = 3_000)
        val single = record("content://video/9", "Movie", playedAt = 2_000)
        val rows = recentHomeRows(
            records = listOf(newer, single, older),
            entries = listOf(
                VideoLibraryEntry.Series(
                    VideoSeries(
                        id = "show",
                        title = "Show",
                        episodes = listOf(
                            video("content://video/2", "Show E1"),
                            video("content://video/3", "Show E2"),
                        ),
                        groupKind = SeriesGroupKind.EPISODE,
                    ),
                ),
                VideoLibraryEntry.Single(video("content://video/9", "Movie")),
            ),
        )
        assertEquals(listOf("series:show", "content://video/9"), rows.map { it.key })
        assertEquals(listOf("Show", "Movie"), rows.map { it.title })
        assertEquals(newer.uri, rows.first().record.uri)
        assertEquals(3_000L, rows.first().record.lastPlayedAt)
    }

    @Test
    fun folderFilesStaySeparate() {
        val first = record("content://video/a", "A", playedAt = 2)
        val second = record("content://video/b", "B", playedAt = 1)
        val rows = recentHomeRows(
            records = listOf(first, second),
            entries = listOf(
                VideoLibraryEntry.Series(
                    VideoSeries(
                        id = "folder",
                        title = "Folder",
                        episodes = listOf(
                            video("content://video/a", "A"),
                            video("content://video/b", "B"),
                        ),
                        groupKind = SeriesGroupKind.DIRECTORY,
                    ),
                ),
            ),
        )
        assertEquals(listOf("content://video/a", "content://video/b"), rows.map { it.key })
    }

    private fun record(uri: String, title: String, playedAt: Long) = PlaybackRecord(
        uri = Uri.parse(uri),
        title = title,
        positionMs = 12_000,
        durationMs = 60_000,
        lastPlayedAt = playedAt,
    )

    private fun video(uri: String, title: String) = VideoItem(
        id = uri.hashCode().toLong(),
        uri = Uri.parse(uri),
        title = title,
        durationMs = 60_000,
        dateAdded = 0,
        thumbnailUri = null,
    )
}
