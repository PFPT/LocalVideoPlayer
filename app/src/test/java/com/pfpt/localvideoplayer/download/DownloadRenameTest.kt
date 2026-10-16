package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class DownloadRenameTest {
    @Test
    fun composeRenamePreservesExtension() {
        assertEquals("新视频.mp4", DownloadNameResolver.composeRenameFileName("old.mp4", "新视频"))
    }

    @Test
    fun composeRenameSanitizesInvalidCharacters() {
        assertEquals("safe_name.mkv", DownloadNameResolver.composeRenameFileName("old.mkv", "safe/name"))
    }

    @Test
    fun composeRenameReturnsNullForBlankName() {
        assertNull(DownloadNameResolver.composeRenameFileName("old.mp4", "   "))
    }

    @Test
    fun composeRenameWithoutExtension() {
        assertEquals("newname", DownloadNameResolver.composeRenameFileName("oldname", "newname"))
    }
}
