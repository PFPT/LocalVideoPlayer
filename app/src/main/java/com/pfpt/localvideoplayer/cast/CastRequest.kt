package com.pfpt.localvideoplayer.cast

internal object CastRequest {
    fun mediaPath(token: String): String = "/v/$token"

    fun accepts(path: String, token: String): Boolean {
        if (token.isBlank()) return false
        return path == mediaPath(token)
    }

    fun parseRange(header: String?, total: Long): Pair<Long, Long>? {
        if (header.isNullOrBlank() || total <= 0) return null
        if (!header.startsWith("bytes=")) return null
        val spec = header.removePrefix("bytes=").substringBefore(',').trim()
        val parts = spec.split('-', limit = 2)
        if (parts.size != 2) return null
        return try {
            when {
                parts[0].isEmpty() && parts[1].isNotEmpty() -> {
                    val suffix = parts[1].toLong()
                    val start = (total - suffix).coerceAtLeast(0L)
                    start to (total - 1)
                }
                parts[0].isNotEmpty() && parts[1].isEmpty() -> {
                    val start = parts[0].toLong().coerceIn(0L, total - 1)
                    start to (total - 1)
                }
                else -> {
                    val start = parts[0].toLong().coerceIn(0L, total - 1)
                    val end = parts[1].toLong().coerceIn(start, total - 1)
                    start to end
                }
            }
        } catch (_: NumberFormatException) {
            null
        }
    }
}
