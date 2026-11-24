package com.pfpt.localvideoplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.player.PlaybackSession
import com.pfpt.localvideoplayer.ui.PlayerUiState

@Composable
internal fun rememberOpenedPlayer(
    playlist: List<VideoItem>,
    ui: PlayerUiState,
    playbackSession: PlaybackSession,
): ExoPlayer? {
    val exoPlayer by produceState<ExoPlayer?>(
        initialValue = null,
        playlist,
        ui.softDecodeGeneration,
    ) {
        if (playlist.isEmpty()) {
            value = null
            return@produceState
        }
        val playIndex = ui.currentIndex.coerceIn(0, playlist.lastIndex)
        val replay = ui.replayPositionMs
        ui.replayPositionMs = null
        value = playbackSession.open(
            items = playlist,
            startIndex = playIndex,
            requestedSoftDecode = ui.appliedSoftDecode,
            startPositionMs = replay,
        )
    }
    return exoPlayer
}
