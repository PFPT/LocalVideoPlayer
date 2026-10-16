package com.pfpt.localvideoplayer.player

const val SEEK_FULL_SWIPE_MS = 10 * 60_000L
const val SEEK_UNKNOWN_DURATION_SWIPE_MS = 60_000L

fun swipeSeekRangeMs(durationMs: Long): Long {
    return if (durationMs > 0L) {
        minOf(durationMs, SEEK_FULL_SWIPE_MS)
    } else {
        SEEK_UNKNOWN_DURATION_SWIPE_MS
    }
}

fun seekTargetMs(
    positionMs: Long,
    deltaMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long = Long.MAX_VALUE,
    seekable: Boolean = true,
): Long {
    val target = positionMs + deltaMs
    val upper = when {
        durationMs > 0L -> durationMs
        !seekable -> bufferedPositionMs.coerceAtLeast(positionMs)
        else -> Long.MAX_VALUE
    }
    return target.coerceIn(0L, upper)
}
