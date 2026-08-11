package com.zhaoshuo.localvideoplayer.util

import android.net.Uri
import com.zhaoshuo.localvideoplayer.model.SeriesGroupKind
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SeriesGrouperTest {
    @Test
    fun groupsEpisodesBySeriesName() {
        val videos = listOf(
            video(1, "Cool Show S01E01.mp4"),
            video(2, "Cool Show S01E02.mp4"),
            video(3, "Other Movie.mp4"),
        )
        val entries = SeriesGrouper.groupVideos(videos)
        val series = entries.filterIsInstance<VideoLibraryEntry.Series>()
            .filter { it.series.groupKind == SeriesGroupKind.EPISODE }
        assertTrue(series.any { it.series.episodes.size >= 2 })
        assertTrue(entries.any { it is VideoLibraryEntry.Single })
    }

    @Test
    fun playlistBySeriesNameFindsSiblings() {
        val target = video(2, "Drama S01E02.mkv")
        val candidates = listOf(
            video(1, "Drama S01E01.mkv"),
            target,
            video(3, "Drama S01E03.mkv"),
            video(4, "Unrelated.mkv"),
        )
        val playlist = SeriesGrouper.playlistBySeriesName(target, candidates)
        requireNotNull(playlist)
        assertEquals(3, playlist.items.size)
        assertEquals(1, playlist.currentIndex)
    }

    private fun video(id: Long, title: String): VideoItem {
        return VideoItem(
            id = id,
            uri = Uri.parse("content://media/external/video/media/$id"),
            title = title,
            durationMs = 60_000L,
            dateAdded = id,
            thumbnailUri = null,
        )
    }
}
