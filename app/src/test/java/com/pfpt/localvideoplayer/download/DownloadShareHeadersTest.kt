package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadShareHeadersTest {
    @Test
    fun headersFromAnotherAppAreDropped() {
        val headers = mapOf("Cookie" to "session=1", "Referer" to "https://evil.example")
        assertTrue(DownloadShareHeaders.accepted("com.other.app", "com.pfpt.localvideoplayer", headers).isEmpty())
        assertTrue(DownloadShareHeaders.accepted(null, "com.pfpt.localvideoplayer", headers).isEmpty())
    }

    @Test
    fun headersFromTheSameAppAreKept() {
        val headers = mapOf("Referer" to "https://example.com")
        assertEquals(
            headers,
            DownloadShareHeaders.accepted("com.pfpt.localvideoplayer", "com.pfpt.localvideoplayer", headers),
        )
    }
}
