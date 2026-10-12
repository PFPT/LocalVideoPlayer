package com.zhaoshuo.localvideoplayer.ui

import android.net.Uri
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import com.zhaoshuo.localvideoplayer.model.AspectRatioMode
import kotlinx.coroutines.Job

enum class PlaybackFailure {
    Decode,
    Read,
}

@Stable
class PlayerUiState(
    initialIndex: Int,
    initialSoftDecode: Boolean,
    initialSpeed: Float = 1.0f,
    initialAspect: AspectRatioMode = AspectRatioMode.FIT,
    initialRepeatOne: Boolean = false,
) {
    var currentIndex by mutableIntStateOf(initialIndex)
    var isPlaying by mutableStateOf(true)
    var playbackSpeed by mutableFloatStateOf(initialSpeed)
    var savedPlaybackSpeed by mutableFloatStateOf(initialSpeed)
    var isSpeedBoosted by mutableStateOf(false)
    var controlsVisible by mutableStateOf(true)
    var controlsLocked by mutableStateOf(false)
    var isFullscreen by mutableStateOf(false)
    var forcedLandscapeForFullscreen by mutableStateOf(false)
    var showSpeedSheet by mutableStateOf(false)
    var showEpisodeSheet by mutableStateOf(false)
    var showMoreSheet by mutableStateOf(false)
    var showTrackSheet by mutableStateOf<Int?>(null)
    var showInfoDialog by mutableStateOf(false)
    var subtitleUri by mutableStateOf<Uri?>(null)
    var isLandscape by mutableStateOf(false)
    var isInPipMode by mutableStateOf(false)
    var videoScale by mutableFloatStateOf(1f)
    var offsetX by mutableFloatStateOf(0f)
    var offsetY by mutableFloatStateOf(0f)
    var isSeeking by mutableStateOf(false)
    var previewPositionMs by mutableLongStateOf(0L)
    var seekStartPositionMs by mutableLongStateOf(0L)
    var hiddenPlaybackPositionMs by mutableLongStateOf(0L)
    var skipPositionReady by mutableStateOf(false)
    var repeatOne by mutableStateOf(initialRepeatOne)
    var aspectRatioMode by mutableStateOf(initialAspect)
    var showAspectSheet by mutableStateOf(false)
    var doubleTapSeekDeltaMs by mutableLongStateOf(0L)
    var showDoubleTapSeek by mutableStateOf(false)
    var videoWidth by mutableIntStateOf(0)
    var videoHeight by mutableIntStateOf(0)
    var isAdjustingBrightness by mutableStateOf(false)
    var isAdjustingVolume by mutableStateOf(false)
    var brightnessFraction by mutableFloatStateOf(0.5f)
    var volumeFraction by mutableFloatStateOf(0.5f)
    var brightnessHideJob by mutableStateOf<Job?>(null)
    var volumeHideJob by mutableStateOf<Job?>(null)
    var softDecodeGeneration by mutableIntStateOf(0)
    var appliedSoftDecode by mutableStateOf(initialSoftDecode)
    var softDecodeRetried by mutableStateOf(false)
    var softDecodeRetryPending by mutableStateOf(false)
    var replayPositionMs by mutableStateOf<Long?>(null)
    var playbackFailure by mutableStateOf<PlaybackFailure?>(null)
    var abPointA by mutableStateOf<Long?>(null)
    var abPointB by mutableStateOf<Long?>(null)

    fun resetSpeedBoost() {
        if (isSpeedBoosted) {
            playbackSpeed = savedPlaybackSpeed
            isSpeedBoosted = false
        }
    }

    fun resetControlsTimer() {
        controlsVisible = true
    }

    fun resetZoom() {
        videoScale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    fun toggleControlsVisibility() {
        controlsVisible = !controlsVisible
        if (controlsVisible) {
            resetControlsTimer()
        }
    }
}
