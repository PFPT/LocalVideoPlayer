package com.pfpt.localvideoplayer.download

data class ByteRange(val start: Long, val endInclusive: Long) {
    val length: Long get() = endInclusive - start + 1
}

object ProgressiveRangePlanner {
    const val MIN_MULTI_RANGE_BYTES = 2L * 1024 * 1024
    const val MIN_CONNECTIONS = 2
    const val MAX_CONNECTIONS = 32
    const val DEFAULT_CONNECTIONS = 24

    fun clampConnections(value: Int): Int = value.coerceIn(MIN_CONNECTIONS, MAX_CONNECTIONS)

    fun shouldUseMultiRange(totalBytes: Long, connections: Int): Boolean =
        totalBytes >= MIN_MULTI_RANGE_BYTES && clampConnections(connections) >= 2

    fun plan(totalBytes: Long, connections: Int): List<ByteRange> {
        require(totalBytes > 0) { "totalBytes must be > 0" }
        val parts = clampConnections(connections).toLong().coerceAtMost(totalBytes).toInt().coerceAtLeast(1)
        if (parts <= 1 || !shouldUseMultiRange(totalBytes, parts)) {
            return listOf(ByteRange(0, totalBytes - 1))
        }
        val base = totalBytes / parts
        val remainder = totalBytes % parts
        var start = 0L
        return buildList(parts) {
            for (index in 0 until parts) {
                val extra = if (index < remainder) 1L else 0L
                val length = base + extra
                val end = start + length - 1
                add(ByteRange(start, end))
                start = end + 1
            }
        }
    }
}
