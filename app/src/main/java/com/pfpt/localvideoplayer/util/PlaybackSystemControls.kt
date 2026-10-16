package com.pfpt.localvideoplayer.util

import android.app.Activity
import android.media.AudioManager
import android.view.WindowManager

class PlaybackSystemControls(
    private val activity: Activity,
    private val audioManager: AudioManager,
) {
    private val maxVolume = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
    private var savedBrightness: Float? = null

    fun currentBrightness(): Float {
        val value = activity.window.attributes.screenBrightness
        return if (value in 0f..1f) value else 0.5f
    }

    fun setBrightness(fraction: Float) {
        if (savedBrightness == null) {
            savedBrightness = activity.window.attributes.screenBrightness
        }
        val clamped = fraction.coerceIn(0.01f, 1f)
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = clamped
        activity.window.attributes = layoutParams
    }

    fun restoreBrightness() {
        val layoutParams = activity.window.attributes
        layoutParams.screenBrightness = savedBrightness ?: WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE
        activity.window.attributes = layoutParams
        savedBrightness = null
    }

    fun currentVolumeFraction(): Float {
        val current = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
        return current.toFloat() / maxVolume.toFloat()
    }

    fun setVolumeFraction(fraction: Float) {
        val target = (fraction.coerceIn(0f, 1f) * maxVolume).toInt().coerceIn(0, maxVolume)
        audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, target, 0)
    }
}
