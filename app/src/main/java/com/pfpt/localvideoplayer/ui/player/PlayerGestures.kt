package com.pfpt.localvideoplayer.ui.player

import com.pfpt.localvideoplayer.ui.PlayerUiState
import com.pfpt.localvideoplayer.util.PlaybackSystemControls
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

internal const val GESTURE_HIDE_DELAY_MS = 1_500L

internal fun clampedBrightnessFraction(fraction: Float): Float = fraction.coerceIn(0.01f, 1f)

internal fun clampedVolumeFraction(fraction: Float): Float = fraction.coerceIn(0f, 1f)

internal class PlayerGestureController(
    private val controls: PlaybackSystemControls?,
    private val ui: PlayerUiState,
    private val scope: CoroutineScope,
) {
    fun onBrightnessStart() {
        val system = controls ?: return
        ui.brightnessHideJob?.cancel()
        ui.brightnessHideJob = null
        ui.brightnessFraction = system.currentBrightness()
        ui.isAdjustingBrightness = true
        ui.resetControlsTimer()
    }

    fun onBrightnessChange(fraction: Float) {
        val system = controls ?: return
        ui.brightnessFraction = clampedBrightnessFraction(fraction)
        system.setBrightness(ui.brightnessFraction)
    }

    fun onBrightnessEnd() {
        ui.brightnessHideJob?.cancel()
        ui.brightnessHideJob = scope.launch {
            delay(GESTURE_HIDE_DELAY_MS)
            ui.isAdjustingBrightness = false
            ui.brightnessHideJob = null
        }
    }

    fun onVolumeStart() {
        val system = controls ?: return
        ui.volumeHideJob?.cancel()
        ui.volumeHideJob = null
        ui.volumeFraction = system.currentVolumeFraction()
        ui.isAdjustingVolume = true
        ui.resetControlsTimer()
    }

    fun onVolumeChange(fraction: Float) {
        val system = controls ?: return
        system.setVolumeFraction(clampedVolumeFraction(fraction))
        ui.volumeFraction = system.currentVolumeFraction()
    }

    fun onVolumeEnd() {
        ui.volumeHideJob?.cancel()
        ui.volumeHideJob = scope.launch {
            delay(GESTURE_HIDE_DELAY_MS)
            ui.isAdjustingVolume = false
            ui.volumeHideJob = null
        }
    }
}
