package com.zhaoshuo.localvideoplayer.util

import android.net.Uri
import com.zhaoshuo.localvideoplayer.data.FolderVideoScanner
import com.zhaoshuo.localvideoplayer.model.VideoItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class SeriesGrouperFolderKeyTest {
    @Test
    fun mediaStoreDirectoryPathKeepsFullFolder() {
        val video = item(relativePath = "Movies/Show/", title = "ep.mkv")
        assertEquals("Movies/Show", SeriesGrouper.parentFolderKey(video))
    }

    @Test
    fun legacyPathWithFilenameStripsFileSegment() {
        val video = item(relativePath = "Movies/Show/ep.mkv", title = "ep.mkv")
        assertEquals("Movies/Show", SeriesGrouper.parentFolderKey(video))
    }

    @Test
    fun folderRelativePathHelperIsDirectoryOnly() {
        assertEquals("ThunderDownload/剧集/", FolderVideoScanner.folderDocumentRelativePath("ThunderDownload/剧集"))
        assertEquals("Download/", FolderVideoScanner.folderDocumentRelativePath("Download"))
        assertEquals("", FolderVideoScanner.folderDocumentRelativePath(""))
    }

    @Test
    fun relativeToPrimaryStorageUsesEmulatedRoot() {
        val root = File("/storage/emulated/0/Android/data/com.xunlei/files/ThunderDownload")
        assertEquals(
            "Android/data/com.xunlei/files/ThunderDownload",
            FolderVideoScanner.relativeToPrimaryStorage(root),
        )
        assertNull(FolderVideoScanner.relativeToPrimaryStorage(File("/mnt/not-primary/other")))
    }

    private fun item(relativePath: String?, title: String): VideoItem {
        return VideoItem(
            id = 1L,
            uri = Uri.parse("content://media/external/video/media/1"),
            title = title,
            durationMs = 1_000L,
            dateAdded = 1L,
            dateModified = 1L,
            thumbnailUri = null,
            relativePath = relativePath,
            bucketName = null,
        )
    }
}
