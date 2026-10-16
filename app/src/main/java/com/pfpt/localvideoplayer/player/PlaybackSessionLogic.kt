package com.pfpt.localvideoplayer.player

import androidx.media3.common.Player

enum class PlaybackOpenAction {
    Reuse,
    Replace,
}

sealed interface SleepChoice {
    data object Off : SleepChoice
    data class Minutes(val minutes: Int) : SleepChoice
    data object EndOfItem : SleepChoice

    companion object {
        val minutePresets = listOf(15, 30, 45, 60)
    }
}

fun playbackPlaylistKey(uris: List<String>): String = uris.joinToString("\u0000")

fun playbackOpenAction(
    activeKey: String?,
    requestedKey: String,
    activeSoftDecode: Boolean,
    requestedSoftDecode: Boolean,
    hasPlayer: Boolean,
): PlaybackOpenAction {
    val reuse = hasPlayer &&
        activeKey != null &&
        activeKey == requestedKey &&
        activeSoftDecode == requestedSoftDecode
    return if (reuse) PlaybackOpenAction.Reuse else PlaybackOpenAction.Replace
}

fun shouldSeekToRequestedIndex(
    action: PlaybackOpenAction,
    returnToExisting: Boolean,
    playerIndex: Int,
    requestedIndex: Int,
): Boolean {
    return action == PlaybackOpenAction.Reuse &&
        !returnToExisting &&
        playerIndex != requestedIndex
}

fun indicesToPreload(current: Int, count: Int): List<Int> {
    if (count <= 0 || current !in 0 until count) return emptyList()
    return listOf(current - 1, current, current + 1).filter { it in 0 until count }
}

fun subtitleForVideo(
    videoKey: String,
    manualSubtitles: Map<String, String>,
    sidecarKey: String?,
): String? = manualSubtitles[videoKey] ?: sidecarKey

fun shouldPreloadSubtitle(
    index: Int,
    currentIndex: Int,
    existingSubtitle: String?,
    desiredSubtitle: String?,
): Boolean {
    if (index == currentIndex) return false
    if (desiredSubtitle == null) return false
    return existingSubtitle != desiredSubtitle
}

fun shouldAttachOnArrival(
    existingSubtitle: String?,
    desiredSubtitle: String?,
): Boolean {
    if (desiredSubtitle == null) return false
    return existingSubtitle != desiredSubtitle
}

fun sleepDeadlineElapsed(choice: SleepChoice, nowElapsedMs: Long): Long? {
    return when (choice) {
        SleepChoice.Off,
        SleepChoice.EndOfItem,
        -> null
        is SleepChoice.Minutes -> nowElapsedMs + choice.minutes.coerceAtLeast(1) * 60_000L
    }
}

fun sleepRemaining(deadlineElapsedMs: Long?, nowElapsedMs: Long): Long? {
    deadlineElapsedMs ?: return null
    return (deadlineElapsedMs - nowElapsedMs).coerceAtLeast(0L)
}

fun pausesAtEndOfItem(choice: SleepChoice): Boolean = choice is SleepChoice.EndOfItem

fun formatSleepRemaining(remainingMs: Long): String {
    val totalSeconds = (remainingMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

fun showsPauseIcon(playWhenReady: Boolean, playbackState: Int): Boolean {
    return playWhenReady && playbackState != Player.STATE_ENDED
}

fun showSkipIntro(positionMs: Long, introEndMs: Long, isSeeking: Boolean): Boolean {
    return introEndMs > 0L && !isSeeking && positionMs >= 0L && positionMs < introEndMs
}

fun showSkipOutro(
    positionMs: Long,
    outroStartMs: Long,
    durationMs: Long,
    isSeeking: Boolean,
): Boolean {
    return outroStartMs >= 0L &&
        durationMs > 0L &&
        outroStartMs < durationMs &&
        !isSeeking &&
        positionMs >= outroStartMs &&
        positionMs < durationMs
}
