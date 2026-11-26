package com.pfpt.localvideoplayer.download

import java.net.URI

internal object DownloadFetchPolicy {
    fun allowsChildFetch(taskUrl: String, childUrl: String): Boolean {
        if (DownloadUrlClassifier.isLocalProxyUrl(taskUrl)) return true
        return !isSensitiveTarget(childUrl)
    }

    fun isSensitiveTarget(url: String): Boolean {
        val host = hostOf(url) ?: return false
        return isLoopback(host) || isLinkLocal(host)
    }

    private fun hostOf(url: String): String? = runCatching {
        val uri = URI(url.trim())
        uri.host?.trim()?.trim('[', ']')?.lowercase()
    }.getOrNull()

    private fun isLoopback(host: String): Boolean {
        if (host == "localhost" || host == "::1" || host == "0.0.0.0") return true
        val v4 = ipv4(host) ?: return false
        return v4[0] == 127
    }

    private fun isLinkLocal(host: String): Boolean {
        if (host.startsWith("fe80:")) return true
        val v4 = ipv4(host) ?: return false
        return v4[0] == 169 && v4[1] == 254
    }

    private fun ipv4(host: String): IntArray? {
        val parts = host.split('.')
        if (parts.size != 4) return null
        val numbers = parts.map { part -> part.toIntOrNull()?.takeIf { it in 0..255 } ?: return null }
        return numbers.toIntArray()
    }
}
