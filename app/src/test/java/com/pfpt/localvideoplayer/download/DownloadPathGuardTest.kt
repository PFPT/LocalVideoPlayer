package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class DownloadPathGuardTest {
    @Test
    fun relativePathStaysInsideRoot() {
        val root = File("downloads/task")
        val resolved = DownloadPathGuard.resolveInside(root, "video/episode.mkv")
        assertNotNull(resolved)
        assertTrue(DownloadPathGuard.isInside(root, resolved!!))
    }

    @Test
    fun parentSegmentsAreRejected() {
        val root = File("downloads/task")
        assertNull(DownloadPathGuard.resolveInside(root, "../other/secret.mkv"))
        assertFalse(DownloadPathGuard.isInside(root, File(root, "../other/secret.mkv")))
    }

    @Test
    fun nestedParentSegmentsAreRejected() {
        val root = File("downloads/task")
        assertNull(DownloadPathGuard.resolveInside(root, "video/../../outside.mkv"))
    }
}
