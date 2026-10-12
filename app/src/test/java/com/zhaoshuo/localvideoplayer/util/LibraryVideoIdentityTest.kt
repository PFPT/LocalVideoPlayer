package com.zhaoshuo.localvideoplayer.util

import android.net.Uri
import com.zhaoshuo.localvideoplayer.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class LibraryVideoIdentityTest {
    @Test
    fun samePathKeepsTheVideoCollectionCopy() {
        val path = "Download/Cool Show/"
        val videoCopy = item(
            id = 1,
            title = "Cool Show S01E01.mp4",
            uri = "content://media/external/video/media/1",
            relativePath = path,
            durationMs = 1_000L,
        )
        val downloadCopy = item(
            id = 9,
            title = "Cool Show S01E01.mp4",
            uri = "content://media/external/downloads/9",
            relativePath = path,
            durationMs = 90_000L,
        )
        val kept = dedupeLibraryVideos(listOf(downloadCopy, videoCopy))
        assertEquals(1, kept.size)
        assertEquals(videoCopy.uri, kept.single().uri)
    }

    @Test
    fun sameRankKeepsTheLongerDuration() {
        val path = "Download/Cool Show/"
        val shorter = item(
            id = 1,
            title = "Cool Show S01E01.mp4",
            uri = "content://media/external/downloads/1",
            relativePath = path,
            durationMs = 1_000L,
        )
        val longer = item(
            id = 2,
            title = "Cool Show S01E01.mp4",
            uri = "content://media/external/downloads/2",
            relativePath = path,
            durationMs = 50_000L,
        )
        val kept = dedupeLibraryVideos(listOf(shorter, longer))
        assertEquals(longer.uri, kept.single().uri)
    }

    @Test
    fun differentPathsAndMissingPathsStaySeparate() {
        val first = item(
            id = 1,
            title = "clip.mp4",
            uri = "content://media/external/video/media/1",
            relativePath = "Download/A/",
        )
        val second = item(
            id = 2,
            title = "clip.mp4",
            uri = "content://media/external/downloads/2",
            relativePath = "Movies/B/",
        )
        val unnamed = item(
            id = 3,
            title = "clip.mp4",
            uri = "content://media/external/file/3",
            relativePath = null,
        )
        val otherUnnamed = item(
            id = 4,
            title = "clip.mp4",
            uri = "file:///storage/emulated/0/clip.mp4",
            relativePath = null,
        )
        val kept = dedupeLibraryVideos(listOf(first, second, unnamed, otherUnnamed))
        assertEquals(4, kept.size)
        assertTrue(kept.map { it.uri }.containsAll(listOf(first.uri, second.uri, unnamed.uri, otherUnnamed.uri)))
    }

    private fun item(
        id: Long,
        title: String,
        uri: String,
        relativePath: String?,
        durationMs: Long = 1_000L,
    ): VideoItem {
        return VideoItem(
            id = id,
            uri = Uri.parse(uri),
            title = title,
            durationMs = durationMs,
            dateAdded = id,
            thumbnailUri = null,
            relativePath = relativePath,
        )
    }
}
