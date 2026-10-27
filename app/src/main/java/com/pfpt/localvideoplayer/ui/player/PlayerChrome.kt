package com.pfpt.localvideoplayer.ui.player

import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.cast.CastManager
import com.pfpt.localvideoplayer.data.PlayerPreferencesStore
import com.pfpt.localvideoplayer.player.PlayerProgressState
import com.pfpt.localvideoplayer.ui.PlayerUiState
import com.pfpt.localvideoplayer.util.PlaybackSystemControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch
import kotlin.math.max

private const val MIN_SPEED_BOOST = 2.0f

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
            if (!ui.isSpeedBoosted) {
                ui.savedPlaybackSpeed = ui.playbackSpeed
                ui.playbackSpeed = max(MIN_SPEED_BOOST, ui.savedPlaybackSpeed)
                ui.isSpeedBoosted = true
                if (!exoPlayer.isPlaying) {
                    exoPlayer.play()
                }
            }
        },
        onSpeedBoostEnd = { ui.resetSpeedBoost() },
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
