package com.pfpt.localvideoplayer.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FolderVideoScannerTest {

    @Test
    fun acceptsVideoMimeAndExtensions() {
        assertTrue(FolderVideoScanner.isVideoFile("clip.mp4", "video/mp4"))
        assertTrue(FolderVideoScanner.isVideoFile("Show.mkv", "application/octet-stream"))
        assertTrue(FolderVideoScanner.isVideoFile("live.m3u8", "application/vnd.apple.mpegurl"))
        assertFalse(FolderVideoScanner.isVideoFile("notes.txt", "text/plain"))
        assertFalse(FolderVideoScanner.isVideoFile("cover.png", "image/png"))
    }
}
