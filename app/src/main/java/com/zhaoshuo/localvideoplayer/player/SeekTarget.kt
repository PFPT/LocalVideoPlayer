package com.zhaoshuo.localvideoplayer.player

const val SEEK_FULL_SWIPE_MS = 10 * 60_000L

fun swipeSeekRangeMs(durationMs: Long): Long {
    return if (durationMs > 0L) minOf(durationMs, SEEK_FULL_SWIPE_MS) else SEEK_FULL_SWIPE_MS
}

fun seekTargetMs(positionMs: Long, deltaMs: Long, durationMs: Long): Long {
    val target = positionMs + deltaMs
    return if (durationMs > 0L) target.coerceIn(0L, durationMs) else target.coerceAtLeast(0L)
}
