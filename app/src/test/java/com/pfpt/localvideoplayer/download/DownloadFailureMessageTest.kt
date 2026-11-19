package com.pfpt.localvideoplayer.download

import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.IOException
import java.net.SocketTimeoutException

class DownloadFailureMessageTest {
    @Test
    fun unavailableWinsOverBlankMessage() {
        assertEquals(
            "当前安装包不包含这项下载",
            downloadFailureMessage(
                error = AdvancedDownloadUnavailable(),
                unavailableMessage = "当前安装包不包含这项下载",
                timeoutMessage = "连接超时，点击继续重试",
                fallbackMessage = "无法访问视频直链，请检查分享链接是否有效",
            ),
        )
    }

    @Test
    fun timeoutUsesTimeoutMessage() {
        assertEquals(
            "连接超时，点击继续重试",
            downloadFailureMessage(
                error = SocketTimeoutException(),
                unavailableMessage = "当前安装包不包含这项下载",
                timeoutMessage = "连接超时，点击继续重试",
                fallbackMessage = "无法访问视频直链，请检查分享链接是否有效",
            ),
        )
    }

    @Test
    fun timeoutCauseUsesTimeoutMessage() {
        assertEquals(
            "连接超时，点击继续重试",
            downloadFailureMessage(
                error = IOException("wrapped", SocketTimeoutException()),
                unavailableMessage = "当前安装包不包含这项下载",
                timeoutMessage = "连接超时，点击继续重试",
                fallbackMessage = "无法访问视频直链，请检查分享链接是否有效",
            ),
        )
    }

    @Test
    fun blankMessageUsesFallback() {
        assertEquals(
            "无法访问视频直链，请检查分享链接是否有效",
            downloadFailureMessage(
                error = IOException(" "),
                unavailableMessage = "当前安装包不包含这项下载",
                timeoutMessage = "连接超时，点击继续重试",
                fallbackMessage = "无法访问视频直链，请检查分享链接是否有效",
            ),
        )
    }

    @Test
    fun otherMessageIsPreserved() {
        assertEquals(
            "HTTP 404",
            downloadFailureMessage(
                error = IOException("HTTP 404"),
                unavailableMessage = "当前安装包不包含这项下载",
                timeoutMessage = "连接超时，点击继续重试",
                fallbackMessage = "无法访问视频直链，请检查分享链接是否有效",
            ),
        )
    }
}