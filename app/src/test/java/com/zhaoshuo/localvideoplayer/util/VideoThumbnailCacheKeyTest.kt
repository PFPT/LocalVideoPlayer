package com.zhaoshuo.localvideoplayer.util

import android.net.Uri
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class VideoThumbnailCacheKeyTest {
    @Test
    fun keysFollowContentUri() {
        val media = Uri.parse("content://media/external/video/media/4")
        val document = Uri.parse("content://com.android.externalstorage.documents/document/1")
        assertEquals(media.toString(), thumbnailCacheKey(media))
        assertNotEquals(thumbnailCacheKey(media), thumbnailCacheKey(document))
    }

    @Test
    fun modifiedTimeChangesTheKey() {
        val media = Uri.parse("content://media/external/video/media/4")
        assertEquals(media.toString(), thumbnailCacheKey(media, dateModifiedSeconds = 0L))
        assertEquals("$media|12", thumbnailCacheKey(media, dateModifiedSeconds = 12L))
        assertNotEquals(
            thumbnailCacheKey(media, dateModifiedSeconds = 12L),
            thumbnailCacheKey(media, dateModifiedSeconds = 13L),
        )
    }
}
