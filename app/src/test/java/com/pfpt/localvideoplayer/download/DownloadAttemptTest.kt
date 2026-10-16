package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadAttemptTest {
    @Test
    fun pauseAndCancelDoNotRetry() {
        assertNull(nextDownloadAttempt(stopped = true, canceled = false, attempt = 0, maxAttempts = 3))
        assertNull(nextDownloadAttempt(stopped = false, canceled = true, attempt = 0, maxAttempts = 3))
        assertEquals(1, nextDownloadAttempt(stopped = false, canceled = false, attempt = 0, maxAttempts = 3))
        assertNull(nextDownloadAttempt(stopped = false, canceled = false, attempt = 2, maxAttempts = 3))
    }
}
