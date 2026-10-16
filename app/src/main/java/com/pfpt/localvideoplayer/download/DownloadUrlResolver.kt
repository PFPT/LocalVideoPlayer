package com.pfpt.localvideoplayer.download

import java.net.URI
import java.net.URLDecoder

object DownloadUrlResolver {
    const val DEFAULT_USER_AGENT =
        "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/128.0.0.0 Safari/537.36"

    fun unwrapProxyUrl(url: String): String {
        var current = url.trim()
        if (current.isEmpty()) return current
        while (DownloadUrlClassifier.isLocalProxyUrl(current)) {
            val inner = extractUrlParam(current)?.let(::fullyDecode) ?: break
            if (inner == current) break
            current = inner
        }
        return fullyDecode(current)
    }

    fun resolveDownloadUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        if (DownloadUrlClassifier.isMagnetUrl(trimmed)) return trimmed
        if (DownloadUrlClassifier.isHlsDownloadUrl(trimmed)) {
            return DownloadUrlClassifier.extractNestedM3u8Url(trimmed)
                ?.let(::unwrapProxyUrl)
                ?: unwrapProxyUrl(trimmed)
        }
        return unwrapProxyUrl(trimmed)
    }

    fun taskDownloadUrl(url: String): String {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return trimmed
        if (DownloadUrlClassifier.isLocalProxyUrl(trimmed)) return trimmed
        return resolveDownloadUrl(trimmed)
    }

    fun mediaFetchUrl(url: String): String {
        val trimmed = url.trim()
        if (DownloadUrlClassifier.isLocalProxyUrl(trimmed)) return trimmed
        return unwrapProxyUrl(trimmed)
    }

    fun inferHeaders(url: String, userHeaders: Map<String, String>): Map<String, String> {
        if (DownloadUrlClassifier.isMagnetUrl(url)) return userHeaders
        val merged = LinkedHashMap<String, String>()
        extractHeadersFromUrl(url).forEach { (key, value) -> merged.putIfAbsent(key, value) }
        userHeaders.forEach { (key, value) -> merged[key] = value }
        val directUrl = resolveDownloadUrl(url)
        if (!merged.keys.any { it.equals("Referer", ignoreCase = true) }) {
            originReferer(directUrl)?.let { merged["Referer"] = it }
        }
        if (!merged.keys.any { it.equals("User-Agent", ignoreCase = true) }) {
            merged["User-Agent"] = DEFAULT_USER_AGENT
        }
        return merged
    }

    fun ensureDownloadHeaders(headers: Map<String, String>): Map<String, String> {
        if (headers.keys.any { it.equals("User-Agent", ignoreCase = true) }) return headers
        return LinkedHashMap(headers).apply { put("User-Agent", DEFAULT_USER_AGENT) }
    }

    fun hlsChildHeaders(playlistUrl: String, headers: Map<String, String>): Map<String, String> {
        val merged = LinkedHashMap(ensureDownloadHeaders(headers))
        val refererKey = merged.keys.firstOrNull { it.equals("Referer", ignoreCase = true) }
        val current = refererKey?.let { merged[it] }
        val origin = originReferer(playlistUrl)
        if (current.isNullOrBlank() || current == origin) {
            if (refererKey != null) merged.remove(refererKey)
            merged["Referer"] = playlistUrl
        }
        return merged
    }

    private fun extractHeadersFromUrl(url: String): Map<String, String> {
        val result = LinkedHashMap<String, String>()
        var current: String? = url.trim()
        while (current != null) {
            appendQueryHeaders(current, result)
            current = extractUrlParam(current)?.let(::fullyDecode)
        }
        return result
    }

    private fun appendQueryHeaders(url: String, out: MutableMap<String, String>) {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return
        query.split('&').forEach { param ->
            val key = param.substringBefore('=')
            if (key.isBlank() || !param.contains('=')) return@forEach
            val normalized = key.lowercase()
            val value = decodeOnce(param.substringAfter('='))
            when (normalized) {
                "referer", "referrer" -> out.putIfAbsent("Referer", value)
                "ua", "user-agent", "useragent" -> out.putIfAbsent("User-Agent", value)
                "origin" -> out.putIfAbsent("Origin", value)
            }
        }
    }

    private fun originReferer(url: String): String? = runCatching {
        val uri = URI(url.trim())
        val scheme = uri.scheme ?: return null
        val host = uri.host ?: return null
        val port = uri.port
        if (port > 0 && port != defaultPort(scheme)) {
            "$scheme://$host:$port/"
        } else {
            "$scheme://$host/"
        }
    }.getOrNull()

    private fun defaultPort(scheme: String): Int = when (scheme.lowercase()) {
        "http" -> 80
        "https" -> 443
        else -> -1
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
