package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Test

class DownloadFormatTest {
    @Test
    fun formatBytes() {
        assertEquals("512 B", DownloadFormat.formatBytes(512))
        assertEquals("1.0 KB", DownloadFormat.formatBytes(1024))
        assertEquals("1.5 MB", DownloadFormat.formatBytes(1_572_864))
        assertEquals("1.00 GB", DownloadFormat.formatBytes(1_073_741_824))
    }

    @Test
    fun formatSpeed() {
        assertEquals("0 B/s", DownloadFormat.formatSpeed(0))
        assertEquals("2.0 MB/s", DownloadFormat.formatSpeed(2_097_152))
    }

    @Test
    fun formatEta() {
        assertEquals("0:00", DownloadFormat.formatEta(0))
        assertEquals("6:22", DownloadFormat.formatEta(382))
        assertEquals("1:05:09", DownloadFormat.formatEta(3909))
    }

    @Test
    fun formatProgressPercent() {
        assertEquals(50, DownloadFormat.formatProgressPercent(500, 1000, 0, 0))
        assertEquals(37, DownloadFormat.formatProgressPercent(-1, -1, 45, 120))
        assertEquals(0, DownloadFormat.formatProgressPercent(-1, -1, 0, 0))
    }
}
