package com.pfpt.localvideoplayer.player

fun resolvedDurationMs(liveDurationMs: Long, catalogDurationMs: Long): Long {
    if (liveDurationMs > 0L) return liveDurationMs
    return catalogDurationMs.coerceAtLeast(0L)
}

fun displayDurationMs(isCasting: Boolean, castDurationMs: Long, localDurationMs: Long): Long {
    if (isCasting && castDurationMs > 0L) return castDurationMs
    return localDurationMs
}

fun shouldBackfillDuration(catalogDurationMs: Long, liveDurationMs: Long): Boolean {
    return catalogDurationMs <= 0L && liveDurationMs <= 0L
}

fun shouldPublishKnownDuration(displayDurationMs: Long, catalogDurationMs: Long): Boolean {
    return displayDurationMs > 0L && catalogDurationMs <= 0L
}