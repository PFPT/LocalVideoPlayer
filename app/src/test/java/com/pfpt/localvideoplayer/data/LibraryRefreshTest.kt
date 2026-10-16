package com.zhaoshuo.localvideoplayer.data

import android.net.Uri
import com.zhaoshuo.localvideoplayer.model.SeriesGroupKind
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import com.zhaoshuo.localvideoplayer.model.VideoSeries
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class LibraryRefreshTest {
    @Test
    fun zeroScanKeepsKnownDuration() {
        val known = video(1, "a.mp4").copy(durationMs = 42_000L)
        val scanned = known.copy(durationMs = 0L)
        val unknown = video(2, "b.mp4").copy(durationMs = 0L)
        val kept = keepKnownDurations(listOf(known), listOf(scanned, unknown))
        assertEquals(42_000L, kept[0].durationMs)
        assertEquals(0L, kept[1].durationMs)
        val fresh = known.copy(durationMs = 9_000L)
        assertEquals(9_000L, keepKnownDurations(listOf(known), listOf(fresh)).single().durationMs)
    }

    @Test
    fun resolvedDurationFillsOnlyMissingVideos() {
        val known = video(1, "a.mp4")
        val missing = video(2, "b.mp4").copy(durationMs = 0L)
        val uri = missing.uri.toString()
        val updated = withResolvedDurations(listOf(known, missing), mapOf(uri to 42_000L))
        assertEquals(1_000L, updated[0].durationMs)
        assertEquals(42_000L, updated[1].durationMs)
        assertEquals(listOf(known, missing), withResolvedDurations(listOf(known, missing), emptyMap()))
    }

    @Test
    fun emptyDeltaSkipsRegroup() {
        val existing = listOf(video(1, "a.mp4"))
        assertNull(mergeIncrementalVideos(existing, emptyList()))
    }

    @Test
    fun deltaReplacesMatchingUriAndKeepsTheRest() {
        val existing = listOf(video(1, "a.mp4"), video(2, "b.mp4"))
        val updated = video(1, "a.mp4").copy(durationMs = 9_000L)
        val merged = mergeIncrementalVideos(existing, listOf(updated, video(3, "c.mp4")))
        assertEquals(setOf("a.mp4", "b.mp4", "c.mp4"), merged?.map { it.title }?.toSet())
        assertEquals(9_000L, merged?.first { it.title == "a.mp4" }?.durationMs)
    }

    @Test
    fun incrementalMergeDropsADownloadCopyOfTheSameFile() {
        val existing = video(1, "Cool Show S01E01.mp4").copy(relativePath = "Download/Cool Show/")
        val downloadCopy = video(9, "Cool Show S01E01.mp4").copy(
            uri = Uri.parse("content://media/external/downloads/9"),
            relativePath = "Download/Cool Show/",
            durationMs = 0L,
        )
        val merged = mergeIncrementalVideos(listOf(existing), listOf(downloadCopy))
        assertEquals(listOf(existing.uri), merged?.map { it.uri })
    }

    @Test
    fun missingMediaStoreIdIsRemoved() {
        val existing = listOf(
            video(1, "a.mp4"),
            video(2, "b.mp4"),
            video(3, "show.m3u8"),
            video(4, "folder.mp4").copy(relativePath = "content://tree/folder"),
        )
        val pruned = dropMissingMediaStoreVideos(existing, setOf(1L))
        assertEquals(listOf("a.mp4", "show.m3u8", "folder.mp4"), pruned?.map { it.title })
    }

    @Test
    fun incrementalMergeAlsoDropsDeletedIds() {
        val existing = listOf(
            video(1, "a.mp4"),
            video(2, "b.mp4"),
            video(3, "show.m3u8"),
            video(4, "folder.mp4").copy(relativePath = "content://tree/folder"),
        )
        val resolved = resolveIncrementalMedia(
            existing = existing,
            delta = listOf(video(5, "c.mp4")),
            liveIds = setOf(1L, 5L),
        )
        assertEquals(
            setOf("a.mp4", "show.m3u8", "folder.mp4", "c.mp4"),
            resolved?.map { it.title }?.toSet(),
        )
    }

    @Test
    fun unchangedIncrementalMediaReturnsNull() {
        val existing = listOf(video(1, "a.mp4"))
        assertNull(resolveIncrementalMedia(existing, emptyList(), setOf(1L)))
    }

    @Test
    fun completeOrEmptyIdSetSkipsPrune() {
        val existing = listOf(video(1, "a.mp4"), video(2, "b.mp4"))
        assertNull(dropMissingMediaStoreVideos(existing, setOf(1L, 2L)))
        assertNull(dropMissingMediaStoreVideos(existing, emptySet()))
        assertNull(dropMissingMediaStoreVideos(existing, null))
    }

    @Test
    fun documentTreeUriCountsAsFolderSource() {
        val folder = video(9, "ep.mp4").copy(
            uri = Uri.parse("content://com.android.externalstorage.documents/tree/primary%3AMovies/document/primary%3AMovies%2FShow%2Fep.mp4"),
            relativePath = "Show/",
        )
        assertEquals(true, isFolderSourceVideo(folder))
        assertEquals("Show/", FolderVideoScanner.folderDocumentRelativePath("Show"))
        assertEquals("", FolderVideoScanner.folderDocumentRelativePath(""))
    }

    @Test
    fun fileUriCountsAsFolderSource() {
        val fileVideo = video(10, "ep.mp4").copy(
            uri = Uri.parse("file:///storage/emulated/0/Download/ep.mp4"),
            relativePath = "Download/",
        )
        assertEquals(true, isFolderSourceVideo(fileVideo))
        assertEquals(false, isTrackedMediaStoreVideo(fileVideo))
    }

    @Test
    fun orphanBucketUsesLeafFolder() {
        assertEquals("Show", VideoRepository.leafBucketName("Download/Show/"))
        assertEquals("Download", VideoRepository.leafBucketName("Download/"))
        assertEquals(null, VideoRepository.leafBucketName(null))
        assertEquals(null, VideoRepository.leafBucketName(""))
    }

    @Test
    fun signatureIgnoresOrder() {
        val first = listOf(video(1, "a.mp4"), video(2, "b.mp4"))
        val second = listOf(video(2, "b.mp4"), video(1, "a.mp4"))
        assertEquals(videoSetSignature(first), videoSetSignature(second))
        assertEquals("", videoSetSignature(emptyList()))
        assertNotEquals(videoSetSignature(first), videoSetSignature(listOf(video(3, "c.mp4"))))
    }

    @Test
    fun smallChangesPatchAndPullOrLargeChangesRegroup() {
        assertEquals(false, shouldFullyRegroup(fromPull = false, existingEmpty = false, changeCount = 2))
        assertEquals(false, shouldFullyRegroup(fromPull = false, existingEmpty = false, changeCount = 24))
        assertEquals(true, shouldFullyRegroup(fromPull = true, existingEmpty = false, changeCount = 1))
        assertEquals(true, shouldFullyRegroup(fromPull = false, existingEmpty = true, changeCount = 1))
        assertEquals(true, shouldFullyRegroup(fromPull = false, existingEmpty = false, changeCount = 25))
    }

    @Test
    fun patchAttachesNewEpisodeAndDropsRemovedUri() {
        val first = video(1, "Cool Show S01E01.mp4")
        val second = video(2, "Cool Show S01E02.mp4")
        val series = VideoLibraryEntry.Series(
            VideoSeries(
                id = "show",
                title = "Cool Show",
                episodes = listOf(first, second),
                groupKind = SeriesGroupKind.EPISODE,
            ),
        )
        val third = video(3, "Cool Show S01E03.mp4")
        val added = patchLibraryEntries(listOf(series), listOf(first, second, third))
        val episodes = added.filterIsInstance<VideoLibraryEntry.Series>().single().series.episodes
        assertEquals(
            setOf("Cool Show S01E01.mp4", "Cool Show S01E02.mp4", "Cool Show S01E03.mp4"),
            episodes.map { it.title }.toSet(),
        )

        val removed = patchLibraryEntries(listOf(series), listOf(first))
        val remaining = removed.filterIsInstance<VideoLibraryEntry.Series>().single().series.episodes
        assertEquals(listOf("Cool Show S01E01.mp4"), remaining.map { it.title })

        val stranger = video(4, "Standalone Movie.mp4")
        val withStranger = patchLibraryEntries(listOf(series), listOf(first, second, stranger))
        assertEquals(1, withStranger.filterIsInstance<VideoLibraryEntry.Single>().size)
        assertEquals("Standalone Movie.mp4", withStranger.filterIsInstance<VideoLibraryEntry.Single>().single().video.title)
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
