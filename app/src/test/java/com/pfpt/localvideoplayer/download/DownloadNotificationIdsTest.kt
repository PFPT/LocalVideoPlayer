package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadNotificationIdsTest {
    @Test
    fun taskNotificationIdIsStableForSameTask() {
        val taskId = "f19021ff-ebc2-48bd-a316-8f9c3af6833b"
        assertEquals(
            DownloadService.taskNotificationId(taskId),
            DownloadService.taskNotificationId(taskId),
        )
    }

    @Test
    fun taskNotificationIdDiffersFromSummaryId() {
        val taskId = "task-1"
        assertEquals(3001 + (taskId.hashCode() and 0xFFFF), DownloadService.taskNotificationId(taskId))
    }
}
