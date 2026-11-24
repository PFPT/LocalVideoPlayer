package com.pfpt.localvideoplayer.data

import org.junit.Assert.assertEquals
import org.junit.Test

class BookmarkListTest {
    @Test
    fun dedupesAndKeepsTheLimit() {
        val capped = (0 until 25).fold(emptyList<Long>()) { existing, index ->
            updateBookmarks(existing, index * 1_000L)
        }
        assertEquals(20, capped.size)
        assertEquals(5_000L, capped.first())
        assertEquals(24_000L, capped.last())
        val again = updateBookmarks(capped, 24_000L)
        assertEquals(20, again.size)
        assertEquals(1, again.count { it == 24_000L })
    }

    @Test
    fun seriesBookmarksFollowEpisodeOrderThenTime() {
        val rows = seriesBookmarks(
            episodes = listOf(
                "content://ep1" to "Episode 1",
                "content://ep2" to "Episode 2",
                "content://ep3" to "Episode 3",
            ),
            marksByUri = mapOf(
                "content://ep2" to listOf(30_000L, 5_000L),
                "content://ep1" to listOf(1_000L),
                "content://missing" to listOf(9_000L),
            ),
        )
        assertEquals(
            listOf(
                SeriesBookmark(0, "Episode 1", "content://ep1", 1_000L),
                SeriesBookmark(1, "Episode 2", "content://ep2", 5_000L),
                SeriesBookmark(1, "Episode 2", "content://ep2", 30_000L),
            ),
            rows,
        )
    }

    @Test
    fun seriesBookmarksOmitEpisodesWithoutMarks() {
        val rows = seriesBookmarks(
            episodes = listOf("content://ep1" to "Episode 1"),
            marksByUri = mapOf("content://ep1" to emptyList()),
        )
        assertEquals(emptyList<SeriesBookmark>(), rows)
    }
}
