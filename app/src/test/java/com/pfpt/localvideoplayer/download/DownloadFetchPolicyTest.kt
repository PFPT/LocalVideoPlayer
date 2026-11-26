package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadFetchPolicyTest {
    @Test
    fun localProxyTaskMayFetchLoopback() {
        assertTrue(
            DownloadFetchPolicy.allowsChildFetch(
                "http://127.0.0.1:9977/proxy?url=https%3A%2F%2Fcdn.example%2Fa.m3u8",
                "http://127.0.0.1:9977/seg0.ts",
            ),
        )
    }

    @Test
    fun publicPlaylistCannotFetchLoopbackOrLinkLocal() {
        val task = "https://cdn.example/live/index.m3u8"
        assertFalse(DownloadFetchPolicy.allowsChildFetch(task, "http://127.0.0.1/secret"))
        assertFalse(DownloadFetchPolicy.allowsChildFetch(task, "http://localhost/secret"))
        assertFalse(DownloadFetchPolicy.allowsChildFetch(task, "http://169.254.169.254/latest/meta-data"))
        assertFalse(DownloadFetchPolicy.allowsChildFetch(task, "http://[fe80::1]/seg.ts"))
        assertTrue(DownloadFetchPolicy.allowsChildFetch(task, "https://cdn.example/seg0.ts"))
    }
}
