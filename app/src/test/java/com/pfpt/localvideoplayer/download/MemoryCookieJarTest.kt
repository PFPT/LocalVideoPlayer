package com.pfpt.localvideoplayer.download

import okhttp3.Cookie
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Test

class MemoryCookieJarTest {
    @Test
    fun replaysCookieForSameHost() {
        val jar = MemoryCookieJar()
        val playlist = "https://cdn.example.com/video/index.m3u8".toHttpUrl()
        val cookie = Cookie.parse(playlist, "sid=abc; Path=/")!!
        jar.saveFromResponse(playlist, listOf(cookie))
        val loaded = jar.loadForRequest("https://cdn.example.com/video/0.jpg".toHttpUrl())
        assertEquals("abc", loaded.single().value)
    }
}
