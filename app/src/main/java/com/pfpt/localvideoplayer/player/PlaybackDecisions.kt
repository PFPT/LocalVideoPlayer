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

sealed interface SeekCommit {
    data class Apply(val positionMs: Long) : SeekCommit
    data object Reject : SeekCommit
}

fun seekCommit(
    targetMs: Long,
    playerDurationMs: Long,
    resolvedDurationMs: Long,
    currentPositionMs: Long,
    seekable: Boolean,
): SeekCommit {
    val duration = if (playerDurationMs > 0L) playerDurationMs else resolvedDurationMs
    if (duration <= 0L && !seekable && targetMs <= currentPositionMs) {
        return SeekCommit.Reject
    }
    return SeekCommit.Apply(targetMs)
}