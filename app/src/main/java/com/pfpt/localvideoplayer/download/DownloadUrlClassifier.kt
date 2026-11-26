package com.pfpt.localvideoplayer.download

object DownloadUrlClassifier {
    fun isMagnetUrl(url: String): Boolean =
        url.trim().startsWith("magnet:?", ignoreCase = true)

    fun extractFirstMagnet(text: String): String? {
        val start = text.indexOf("magnet:?", ignoreCase = true)
        if (start < 0) return null
        val next = text.indexOf("magnet:?", start + 8, ignoreCase = true)
        val end = if (next >= 0) next else text.length
        return text.substring(start, end)
            .trim()
            .trimEnd(',', '.', ';', ')', ']', '}', '"', '\'')
            .takeIf { isMagnetUrl(it) }
    }

    fun magnetDisplayName(url: String): String? {
        val query = url.trim().substringAfter('?', "")
        if (query.isEmpty()) return null
        val dn = query.split('&')
            .firstOrNull { it.startsWith("dn=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.let { DownloadUrlCodec.decodeOnce(it).trim() }
            ?.takeIf { it.isNotBlank() }
        if (dn != null) return dn
        val xt = query.split('&')
            .firstOrNull { it.startsWith("xt=", ignoreCase = true) }
            ?.substringAfter('=')
            .orEmpty()
        val hash = xt.substringAfter("urn:btih:", "").substringBefore('&').trim()
        return hash.takeIf { it.isNotBlank() }
    }

    fun isHlsDownloadUrl(url: String): Boolean {
        val trimmed = url.trim()
        if (trimmed.isEmpty()) return false
        if (isMagnetUrl(trimmed)) return false
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
            val decoded = DownloadUrlCodec.fullyDecode(current)
            if (decoded.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)) {
                deepest = decoded
            }
            current = DownloadUrlCodec.extractUrlParam(current)?.let(DownloadUrlCodec::fullyDecode)
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

}
