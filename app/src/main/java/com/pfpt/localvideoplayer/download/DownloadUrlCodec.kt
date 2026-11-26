package com.pfpt.localvideoplayer.download

import java.net.URLDecoder

internal object DownloadUrlCodec {
    fun extractUrlParam(url: String): String? {
        val query = url.substringAfter('?', "").substringBefore('#')
        if (query.isEmpty()) return null
        return query.split('&')
            .firstOrNull { it.startsWith("url=", ignoreCase = true) }
            ?.substringAfter('=')
            ?.takeIf { it.isNotBlank() }
    }

    fun fullyDecode(value: String): String {
        var decoded = value
        repeat(4) {
            val next = decodeOnce(decoded)
            if (next == decoded) return decoded
            decoded = next
        }
        return decoded
    }

    fun decodeOnce(value: String): String = runCatching {
        URLDecoder.decode(value, Charsets.UTF_8.name())
    }.getOrDefault(value)
}
