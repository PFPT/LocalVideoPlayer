package com.pfpt.localvideoplayer.model

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibrarySearchTest {

    @Test
    fun emptyQueryReturnsAll() {
        val entries = listOf(single("Alpha"), series("Show", "E1", "E2"))
        assertEquals(entries, searchLibrary(entries, "  "))
    }

    @Test
    fun matchesSingleTitle() {
        val entries = listOf(single("Alpha"), single("Beta"))
        val result = searchLibrary(entries, "alp")
        assertEquals(1, result.size)
        assertEquals("Alpha", (result[0] as VideoLibraryEntry.Single).video.title)
    }

    @Test
    fun matchesSeriesTitleKeepsAllEpisodes() {
        val entries = listOf(series("Cool Show", "E1", "E2"))
        val result = searchLibrary(entries, "cool")
        val series = result.single() as VideoLibraryEntry.Series
        assertEquals(2, series.series.episodes.size)
    }

    @Test
    fun matchesEpisodeTitleFiltersEpisodes() {
        val entries = listOf(series("Show", "Pilot", "Finale"))
        val result = searchLibrary(entries, "fin")
        val series = result.single() as VideoLibraryEntry.Series
        assertEquals(listOf("Finale"), series.series.episodes.map { it.title })
    }

    @Test
    fun noMatchReturnsEmpty() {
        val entries = listOf(single("Alpha"))
        assertTrue(searchLibrary(entries, "zzz").isEmpty())
    }

    private fun single(title: String): VideoLibraryEntry.Single {
        return VideoLibraryEntry.Single(
            VideoItem(
                id = title.hashCode().toLong(),
                uri = Uri.parse("content://video/$title"),
                title = title,
                durationMs = 1_000L,
                dateAdded = 1L,
                thumbnailUri = null,
            ),
        )
    }

    private fun series(title: String, vararg episodes: String): VideoLibraryEntry.Series {
        return VideoLibraryEntry.Series(
            VideoSeries(
                id = title,
                title = title,
                episodes = episodes.map { name ->
                    VideoItem(
                        id = name.hashCode().toLong(),
                        uri = Uri.parse("content://video/$title/$name"),
                        title = name,
                        durationMs = 1_000L,
                        dateAdded = 1L,
                        thumbnailUri = null,
                    )
                },
            ),
        )
    }
}
