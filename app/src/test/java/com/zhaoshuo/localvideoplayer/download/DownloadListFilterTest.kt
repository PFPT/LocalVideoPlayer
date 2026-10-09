package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadListFilterTest {
    private val tasks = listOf(
        task("Episode One.mp4", DownloadStatus.RUNNING),
        task("Episode Two.mp4", DownloadStatus.QUEUED),
        task("Paused Show.mp4", DownloadStatus.PAUSED),
        task("Merging.mp4", DownloadStatus.RUNNING, merging = true),
        task("Done.mp4", DownloadStatus.COMPLETED),
        task("Broken.mp4", DownloadStatus.FAILED),
        task("Dropped.mp4", DownloadStatus.CANCELED),
    )

    @Test
    fun filtersByFileNameIgnoringCase() {
        val filtered = filterDownloadTasks(tasks, "episode", DownloadListStatusFilter.All)
        assertEquals(listOf("Episode One.mp4", "Episode Two.mp4"), filtered.map { it.fileName })
    }

    @Test
    fun fileNameSearchDoesNotMatchUrl() {
        val filtered = filterDownloadTasks(tasks, "example.com", DownloadListStatusFilter.All)
        assertEquals(emptyList<String>(), filtered.map { it.fileName })
    }

    @Test
    fun activeIncludesQueuedRunningPausedAndMerging() {
        val filtered = filterDownloadTasks(tasks, "", DownloadListStatusFilter.Active)
        assertEquals(
            listOf("Episode One.mp4", "Episode Two.mp4", "Paused Show.mp4", "Merging.mp4"),
            filtered.map { it.fileName },
        )
    }

    @Test
    fun completedFilterKeepsFinishedOnly() {
        val filtered = filterDownloadTasks(tasks, "", DownloadListStatusFilter.Completed)
        assertEquals(listOf("Done.mp4"), filtered.map { it.fileName })
    }

    @Test
    fun failedFilterIncludesCanceled() {
        val filtered = filterDownloadTasks(tasks, "", DownloadListStatusFilter.Failed)
        assertEquals(listOf("Broken.mp4", "Dropped.mp4"), filtered.map { it.fileName })
    }

    @Test
    fun searchAndStatusCombine() {
        val filtered = filterDownloadTasks(tasks, "episode", DownloadListStatusFilter.Active)
        assertEquals(listOf("Episode One.mp4", "Episode Two.mp4"), filtered.map { it.fileName })
    }

    private fun task(
        name: String,
        status: DownloadStatus,
        merging: Boolean = false,
    ) = DownloadTask(
        url = "https://example.com/$name",
        fileName = name,
        status = status,
        isMerging = merging,
    )
}
