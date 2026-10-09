package com.zhaoshuo.localvideoplayer.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.io.File

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class FolderDurationCacheTest {
    @Test
    fun skipsUnchangedFileAndMissesWhenModified() {
        val file = File.createTempFile("durations", ".json")
        val cache = FolderDurationCache(file)
        cache.put("content://doc/1", 10L, 5_000L)
        assertEquals(5_000L, cache.get("content://doc/1", 10L))
        assertNull(cache.get("content://doc/1", 11L))
        cache.save()

        val reloaded = FolderDurationCache(file)
        reloaded.load()
        assertEquals(5_000L, reloaded.get("content://doc/1", 10L))
        file.delete()
    }
}
