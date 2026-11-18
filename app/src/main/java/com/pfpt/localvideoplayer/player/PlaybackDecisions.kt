package com.pfpt.localvideoplayer.player

import kotlin.math.max

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

const val MIN_SPEED_BOOST = 2.0f

data class SpeedSnapshot(
    val playbackSpeed: Float,
    val savedPlaybackSpeed: Float,
    val boosted: Boolean,
)

sealed interface SpeedChange {
    data class Set(
        val playbackSpeed: Float,
        val savedPlaybackSpeed: Float,
        val boosted: Boolean,
        val playIfPaused: Boolean,
    ) : SpeedChange
    data object Unchanged : SpeedChange
}

fun speedAfterDefault(snapshot: SpeedSnapshot, defaultSpeed: Float): SpeedSnapshot {
    if (snapshot.boosted) return snapshot
    return snapshot.copy(
        playbackSpeed = defaultSpeed,
        savedPlaybackSpeed = defaultSpeed,
    )
}

fun speedBoostStart(snapshot: SpeedSnapshot, isPlaying: Boolean): SpeedChange {
    if (snapshot.boosted) return SpeedChange.Unchanged
    val saved = snapshot.playbackSpeed
    return SpeedChange.Set(
        playbackSpeed = max(MIN_SPEED_BOOST, saved),
        savedPlaybackSpeed = saved,
        boosted = true,
        playIfPaused = !isPlaying,
    )
}

fun speedBoostEnd(snapshot: SpeedSnapshot): SpeedSnapshot {
    if (!snapshot.boosted) return snapshot
    return snapshot.copy(
        playbackSpeed = snapshot.savedPlaybackSpeed,
        boosted = false,
    )
}

fun speedToRestoreOnLeave(snapshot: SpeedSnapshot): Float? {
    if (!snapshot.boosted) return null
    return snapshot.savedPlaybackSpeed
}

enum class DecoderRecovery {
    RetrySoft,
    Ignore,
    FailRead,
    FailDecode,
}

fun decoderRecovery(
    retryable: Boolean,
    sourceRead: Boolean,
    appliedSoftDecode: Boolean,
    alreadyRetried: Boolean,
    retryPending: Boolean,
): DecoderRecovery {
    if (retryable && !appliedSoftDecode && !alreadyRetried) {
        return DecoderRecovery.RetrySoft
    }
    if (retryPending) return DecoderRecovery.Ignore
    return if (sourceRead) DecoderRecovery.FailRead else DecoderRecovery.FailDecode
}