package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNotificationOrderTest {
    @Test
    fun newestCreatedTaskStaysFirstRegardlessOfInputOrder() {
        val tasks = listOf(
            task(id = "episode-9", createdAt = 1_000, downloadedBytes = 500, speed = 9_000),
            task(id = "episode-10", createdAt = 2_000, downloadedBytes = 10, speed = 100),
            task(id = "episode-8", createdAt = 500, downloadedBytes = 800, speed = 50_000),
        )
        val expected = listOf("episode-10", "episode-9", "episode-8")
        assertEquals(expected, notificationTasksInStableOrder(tasks).map { it.id })
        assertEquals(expected, notificationTasksInStableOrder(tasks.asReversed()).map { it.id })
    }

    @Test
    fun progressAndSpeedDoNotChangeOrder() {
        val slow = task(id = "episode-9", createdAt = 1_000, downloadedBytes = 10, speed = 100)
        val fast = task(id = "episode-10", createdAt = 2_000, downloadedBytes = 900, speed = 50_000)
        val before = notificationTasksInStableOrder(listOf(slow, fast)).map { it.id }
        val after = notificationTasksInStableOrder(
            listOf(
                slow.copy(downloadedBytes = 950, downloadSpeedBps = 80_000),
                fast.copy(downloadedBytes = 20, downloadSpeedBps = 50),
            ),
        ).map { it.id }
        assertEquals(listOf("episode-10", "episode-9"), before)
        assertEquals(before, after)
    }

    @Test
    fun sameCreatedAtBreaksTiesById() {
        val tasks = listOf(
            task(id = "b", createdAt = 5),
            task(id = "a", createdAt = 5),
            task(id = "c", createdAt = 5),
        )
        assertEquals(listOf("a", "b", "c"), notificationTasksInStableOrder(tasks).map { it.id })
    }

    @Test
    fun sortKeyFollowsTheSameOrder() {
        val tasks = notificationTasksInStableOrder(
            listOf(
                task(id = "episode-9", createdAt = 999),
                task(id = "episode-10", createdAt = 2_000),
                task(id = "episode-8", createdAt = 100),
                task(id = "episode-10b", createdAt = 2_000),
            ),
        )
        val keys = tasks.map { notificationSortKey(it.createdAt, it.id) }
        assertEquals(keys.sorted(), keys)
        assertEquals(notificationSortKey(2_000, "episode-10"), keys.first())
    }
}

private fun task(
    id: String,
    createdAt: Long,
    downloadedBytes: Long = 0,
    speed: Long = 0,
) = DownloadTask(
    id = id,
    url = "https://example.com/$id.m3u8",
    fileName = "$id.mp4",
    status = DownloadStatus.RUNNING,
    downloadedBytes = downloadedBytes,
    downloadSpeedBps = speed,
    createdAt = createdAt,
)
