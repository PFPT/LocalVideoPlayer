package com.zhaoshuo.localvideoplayer.util

import android.net.Uri
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [33])
class MediaDeleteRequestTest {
    @Test
    fun createDeleteRequestAcceptsMediaCollectionsOnly() {
        assertTrue(acceptsCreateDeleteRequest(Uri.parse("content://media/external/video/media/42")))
        assertTrue(acceptsCreateDeleteRequest(Uri.parse("content://media/external_primary/video/media/42")))
        assertTrue(acceptsCreateDeleteRequest(Uri.parse("content://media/external/downloads/7")))
        assertTrue(acceptsCreateDeleteRequest(Uri.parse("content://media/external/images/media/3")))
        assertTrue(acceptsCreateDeleteRequest(Uri.parse("content://media/external/audio/media/8")))
        assertFalse(acceptsCreateDeleteRequest(Uri.parse("content://media/external/file/9")))
        assertFalse(acceptsCreateDeleteRequest(Uri.parse("file:///storage/emulated/0/Download/a.mp4")))
        assertFalse(
            acceptsCreateDeleteRequest(
                Uri.parse("content://com.android.externalstorage.documents/document/primary%3ADownload%2Fa.mp4"),
            ),
        )
    }
}
