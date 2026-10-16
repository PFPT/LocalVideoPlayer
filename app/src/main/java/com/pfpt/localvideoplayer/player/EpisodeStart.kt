package com.pfpt.localvideoplayer.player

fun episodeStartPositionMs(resumePositionMs: Long, replayPositionMs: Long? = null): Long {
    return (replayPositionMs ?: resumePositionMs).coerceAtLeast(0L)
}
