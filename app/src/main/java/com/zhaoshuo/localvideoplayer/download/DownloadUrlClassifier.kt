package com.zhaoshuo.localvideoplayer.download

import java.net.URLDecoder

object DownloadUrlClassifier {
    fun isHlsDownloadUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        val path = trimmed.substringBefore('?').substringBefore('#')
        if (path.endsWith(".m3u8", ignoreCase = true)) return true
        if (path.endsWith("/m3u8", ignoreCase = true)) return true
        if (trimmed.contains("do=m3u8", ignoreCase = true)) return true
        return extractNestedM3u8Url(trimmed) != null
    }

    fun extractNestedM3u8Url(url: String): String? {
        var current: String? = url.trim()
        var deepest: String? = null
        while (current != null) {
            val decoded = fullyDecode(current)
            if (decoded.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)) {
                deepest = decoded
            }
            current = extractUrlParam(current)?.let(::fullyDecode)
        }
        return deepest
    }

    fun defaultFileName(url: String, fallback: String): String {
        val nested = extractNestedM3u8Url(url)
        val source = nested ?: url
        val path = source.substringBefore('?').substringBefore('#')
        val fileStem = path.substringAfterLast('/').substringBeforeLast('.')
        val stem = when {
            fileStem.isBlank() -> null
            fileStem.lowercase() in GENERIC_M3U8_NAMES -> {
                path.substringBeforeLast('/').substringAfterLast('/').takeIf { it.isNotBlank() }
            }
            else -> fileStem
        }
        return if (stem != null) "$stem.mp4" else fallback
    }

    private val GENERIC_M3U8_NAMES = setOf("index", "playlist", "master", "stream", "media", "chunklist")

    fun isLocalProxyUrl(url: String): Boolean {
        val normalized = url.trim().lowercase()
        return normalized.startsWith("http://127.0.0.1") ||
            normalized.startsWith("http://localhost") ||
            normalized.startsWith("http://[::1]")
    }

    private fun extractUrlParam(url: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.startsWith("url=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
    }

    private fun fullyDecode(value: String): String {
        var decoded = value
        repeat(4) {
            val next = decodeOnce(decoded)
            if (next == decoded) return decoded
            decoded = next
        }
        return decoded
    }

    private fun decodeOnce(value: String): String = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrDefault(value)
}
