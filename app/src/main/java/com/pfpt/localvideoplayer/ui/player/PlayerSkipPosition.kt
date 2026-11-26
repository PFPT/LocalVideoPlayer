package com.pfpt.localvideoplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.ui.PlayerUiState
import kotlinx.coroutines.delay

@Composable
internal fun PlayerSkipPositionEffect(
    exoPlayer: ExoPlayer,
    ui: PlayerUiState,
    introSeconds: Int,
    outroSeconds: Int,
) {
    LaunchedEffect(exoPlayer, introSeconds, outroSeconds, ui.controlsVisible) {
        if (introSeconds <= 0 && outroSeconds <= 0) {
            ui.skipPositionReady = false
            return@LaunchedEffect
        }
        if (ui.controlsVisible) return@LaunchedEffect
        while (true) {
            ui.hiddenPlaybackPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            ui.skipPositionReady = true
            delay(250)
        }
    }
}
