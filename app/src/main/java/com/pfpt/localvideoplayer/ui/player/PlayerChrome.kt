package com.pfpt.localvideoplayer.ui.player

import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.cast.CastManager
import com.pfpt.localvideoplayer.data.PlayerPreferencesStore
import com.pfpt.localvideoplayer.player.PlayerProgressState
import com.pfpt.localvideoplayer.player.SpeedChange
import com.pfpt.localvideoplayer.player.SpeedSnapshot
import com.pfpt.localvideoplayer.player.speedBoostEnd
import com.pfpt.localvideoplayer.player.speedBoostStart
import com.pfpt.localvideoplayer.ui.PlayerUiState
import com.pfpt.localvideoplayer.util.PlaybackSystemControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class PlayerChromeActions(
    val onSpeedBoostStart: () -> Unit,
    val onSpeedBoostEnd: () -> Unit,
    val onPlayPauseClick: () -> Unit,
    val onPreviousClick: () -> Unit,
    val onNextClick: () -> Unit,
    val onScrubStart: () -> Unit,
    val onScrubEnd: () -> Unit,
    val seekToPosition: (Long) -> Unit,
    val toggleRepeatOne: () -> Unit,
    val getCurrentBrightness: () -> Float,
    val getCurrentVolume: () -> Float,
)

internal fun playerChromeActions(
    ui: PlayerUiState,
    exoPlayer: ExoPlayer,
    progress: PlayerProgressState,
    isCasting: Boolean,
    castManager: CastManager?,
    hasPrevious: Boolean,
    hasNext: Boolean,
    systemControls: PlaybackSystemControls?,
    togglePlayback: () -> Unit,
    playAdjacentEpisode: (Int) -> Unit,
    scope: CoroutineScope,
    playerPreferencesStore: PlayerPreferencesStore,
): PlayerChromeActions {
    return PlayerChromeActions(
        onSpeedBoostStart = {
            val change = speedBoostStart(
                snapshot = SpeedSnapshot(
                    playbackSpeed = ui.playbackSpeed,
                    savedPlaybackSpeed = ui.savedPlaybackSpeed,
                    boosted = ui.isSpeedBoosted,
                ),
                isPlaying = exoPlayer.isPlaying,
            )
            when (change) {
                SpeedChange.Unchanged -> Unit
                is SpeedChange.Set -> {
                    ui.savedPlaybackSpeed = change.savedPlaybackSpeed
                    ui.playbackSpeed = change.playbackSpeed
                    ui.isSpeedBoosted = change.boosted
                    if (change.playIfPaused) {
                        exoPlayer.play()
                    }
                }
            }
        },
        onSpeedBoostEnd = {
            val next = speedBoostEnd(
                SpeedSnapshot(
                    playbackSpeed = ui.playbackSpeed,
                    savedPlaybackSpeed = ui.savedPlaybackSpeed,
                    boosted = ui.isSpeedBoosted,
                ),
            )
            ui.playbackSpeed = next.playbackSpeed
            ui.savedPlaybackSpeed = next.savedPlaybackSpeed
            ui.isSpeedBoosted = next.boosted
        },
        onPlayPauseClick = {
            ui.resetControlsTimer()
            togglePlayback()
        },
        onPreviousClick = {
            ui.resetControlsTimer()
            if (!isCasting && hasPrevious) {
                playAdjacentEpisode(-1)
            }
        },
        onNextClick = {
            ui.resetControlsTimer()
            if (!isCasting && hasNext) {
                playAdjacentEpisode(1)
            }
        },
        onScrubStart = {
            if (isCasting) castManager?.playback?.beginScrub()
        },
        onScrubEnd = {
            if (isCasting) castManager?.playback?.endScrub()
        },
        seekToPosition = { position ->
            if (isCasting) {
                castManager?.playback?.seekTo(position)
            } else {
                exoPlayer.seekTo(position)
            }
            progress.currentPositionMs = position
        },
        toggleRepeatOne = {
            ui.repeatOne = !ui.repeatOne
            scope.launch { playerPreferencesStore.setRepeatOne(ui.repeatOne) }
        },
        getCurrentBrightness = { systemControls?.currentBrightness() ?: ui.brightnessFraction },
        getCurrentVolume = { systemControls?.currentVolumeFraction() ?: ui.volumeFraction },
    )
}
