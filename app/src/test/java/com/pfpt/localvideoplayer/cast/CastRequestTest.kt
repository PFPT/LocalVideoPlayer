package com.pfpt.localvideoplayer.cast

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CastRequestTest {
    @Test
    fun onlyTheTokenPathIsAccepted() {
        val token = "abc123"
        assertTrue(CastRequest.accepts("/v/abc123", token))
        assertFalse(CastRequest.accepts("/video", token))
        assertFalse(CastRequest.accepts("/", token))
        assertFalse(CastRequest.accepts("/v/other", token))
        assertFalse(CastRequest.accepts("/v/abc123/../other", token))
    }

    @Test
    fun rangeParsesOpenEndAndSuffix() {
        assertEquals(0L to 99L, CastRequest.parseRange("bytes=0-", 100))
        assertEquals(90L to 99L, CastRequest.parseRange("bytes=-10", 100))
        assertEquals(10L to 20L, CastRequest.parseRange("bytes=10-20", 100))
        assertNull(CastRequest.parseRange("bytes=nope", 100))
        assertNull(CastRequest.parseRange(null, 100))
    }
}
