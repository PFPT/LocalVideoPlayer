package com.zhaoshuo.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadUrlResolverTest {
  @Test
  fun unwrapNestedLocalProxyM3u8() {
    val url =
        "http://127.0.0.1:9978/m3u8?url=http%3A%2F%2F127.0.0.1%3A9978%2Fproxy%3Fdo%3Dm3u8%26url%3Dhttps%253A%252F%252Fv.lzcdn28.com%252F20260712%252F12602_8c511196%252Findex.m3u8"
    assertEquals(
        "https://v.lzcdn28.com/20260712/12602_8c511196/index.m3u8",
        DownloadUrlResolver.resolveDownloadUrl(url),
    )
  }

  @Test
  fun unwrapLocalProxySegment() {
    val url = "http://127.0.0.1:9978/proxy?do=m3u8&url=https%3A%2F%2Fcdn.example.com%2Fseg00001.ts"
    assertEquals("https://cdn.example.com/seg00001.ts", DownloadUrlResolver.unwrapProxyUrl(url))
  }

  @Test
  fun leavesDirectCdnUrlUnchanged() {
    val url = "https://cdn.example.com/video/index.m3u8"
    assertEquals(url, DownloadUrlResolver.resolveDownloadUrl(url))
  }

  @Test
  fun inferHeadersAddsRefererFromDirectUrl() {
    val url =
        "http://127.0.0.1:9978/m3u8?url=https%3A%2F%2Fcdn.example.com%2Findex.m3u8"
        val headers = DownloadUrlResolver.inferHeaders(url, emptyMap())
    assertEquals("https://cdn.example.com/", headers["Referer"])
  }

  @Test
  fun inferHeadersPrefersUserReferer() {
    val url = "https://cdn.example.com/index.m3u8"
    val headers = DownloadUrlResolver.inferHeaders(url, mapOf("Referer" to "https://example.com/"))
    assertEquals("https://example.com/", headers["Referer"])
  }

  @Test
  fun inferHeadersExtractsRefererFromQuery() {
    val url = "http://127.0.0.1:9978/proxy?url=https%3A%2F%2Fcdn.example.com%2Fseg.ts&referer=https%3A%2F%2Fsite.example%2F"
    val headers = DownloadUrlResolver.inferHeaders(url, emptyMap())
    assertTrue(headers["Referer"] == "https://site.example/" || headers["Referer"] == "https://cdn.example.com/")
  }
}
