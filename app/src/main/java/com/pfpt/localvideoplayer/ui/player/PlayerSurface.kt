package com.pfpt.localvideoplayer.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.cast.CastManager
import com.pfpt.localvideoplayer.cast.CastUiState
import com.pfpt.localvideoplayer.data.PlayerPreferences
import com.pfpt.localvideoplayer.data.SeriesSkipSettings
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.player.PlayerProgressState
import com.pfpt.localvideoplayer.ui.PlaybackFailure
import com.pfpt.localvideoplayer.ui.PlayerUiState
import com.pfpt.localvideoplayer.ui.components.CastButton
import com.pfpt.localvideoplayer.ui.components.CastMiniController
import com.pfpt.localvideoplayer.ui.components.CastSheetActions
import com.pfpt.localvideoplayer.ui.components.FittedPlayerSurface
import com.pfpt.localvideoplayer.ui.components.LandscapePlayerContent
import com.pfpt.localvideoplayer.ui.components.LiveSeekPreview
import com.pfpt.localvideoplayer.ui.components.PortraitPlayerContent

@Composable
internal fun PlayerSurface(
    modifier: Modifier,
    hidePage: Boolean,
    ui: PlayerUiState,
    exoPlayer: ExoPlayer,
    preferences: PlayerPreferences,
    progress: PlayerProgressState,
    displayDurationMs: Long,
    currentVideo: VideoItem?,
    playlist: List<VideoItem>,
    useLandscapeLayout: Boolean,
    isCasting: Boolean,
    currentTitle: String,
    seriesTitle: String,
    resolution: String?,
    showsPause: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    canEditSeriesSkip: Boolean,
    introEndMs: Long,
    outroStartMs: Long,
    seriesSkip: SeriesSkipSettings,
    chrome: PlayerChromeActions,
    gestures: PlayerGestureController,
    floating: Boolean,
    pipSupported: Boolean,
    castManager: CastManager?,
    castState: CastUiState,
    castPlaying: Boolean,
    castSheet: CastSheetActions,
    onMarkIntro: () -> Unit,
    onMarkOutro: () -> Unit,
    onClearIntro: () -> Unit,
    onClearOutro: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekPreviewFrame: (Long) -> Unit,
    onSeekCommitted: (Long) -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onFullscreenTap: (Offset, Float, Float) -> Unit,
    onDoubleTap: (Offset, Float) -> Unit,
    onBack: () -> Unit,
    onLockToggle: () -> Unit,
    onRotateClick: () -> Unit,
    onSkipIntro: () -> Unit,
    onSkipOutro: () -> Unit,
    onPipClick: () -> Unit,
    onFullscreenToggle: () -> Unit,
    onEnterLandscapeFullscreen: () -> Unit,
    onEpisodeSelected: (Int) -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .then(
                if (hidePage) {
                    Modifier
                } else {
                    Modifier.background(MaterialTheme.colorScheme.background)
                },
            ),
    ) {
        when {
            floating -> {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                )
            }
            ui.isInPipMode -> {
                FittedPlayerSurface(
                    player = exoPlayer,
                    modifier = Modifier.fillMaxSize(),
                    useTextureView = preferences.useTextureView,
                )
            }
            useLandscapeLayout -> {
                LandscapePlayerContent(
                    exoPlayer = exoPlayer,
                    progress = progress,
                    durationMs = displayDurationMs,
                    videoUri = currentVideo?.uri,
                    videoScale = ui.videoScale,
                    useTextureView = preferences.useTextureView,
                    offsetX = ui.offsetX,
                    offsetY = ui.offsetY,
                    ui = ui,
                    isCasting = isCasting,
                    isSeeking = ui.isSeeking,
                    controlsVisible = ui.controlsVisible,
                    controlsLocked = ui.controlsLocked,
                    isFullscreen = ui.isFullscreen,
                    title = currentTitle,
                    resolution = resolution,
                    isPlaying = showsPause,
                    playbackSpeed = ui.playbackSpeed,
                    repeatOne = ui.repeatOne,
                    isSpeedBoosted = ui.isSpeedBoosted,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    canEditSeriesSkip = canEditSeriesSkip && !isCasting,
                    introEndMs = introEndMs,
                    outroStartMs = outroStartMs,
                    introMarked = seriesSkip.introSeconds != null,
                    outroMarked = seriesSkip.outroSeconds != null,
                    onMarkIntro = onMarkIntro,
                    onMarkOutro = onMarkOutro,
                    onClearIntro = onClearIntro,
                    onClearOutro = onClearOutro,
                    onScaleChange = { ui.videoScale = it },
                    onOffsetChange = { x, y ->
                        ui.offsetX = x
                        ui.offsetY = y
                    },
                    onSeekPreview = onSeekPreview,
                    onSeekPreviewFrame = onSeekPreviewFrame,
                    onSeekCommitted = { position ->
                        onSeekCommitted(position)
                        ui.resetControlsTimer()
                    },
                    onTap = onFullscreenTap,
                    onDoubleTap = onDoubleTap,
                    onSeekingChange = onSeekingChange,
                    onSpeedBoostStart = chrome.onSpeedBoostStart,
                    onSpeedBoostEnd = chrome.onSpeedBoostEnd,
                    onBrightnessGestureStart = gestures::onBrightnessStart.takeIf { preferences.gestureBrightness },
                    onBrightnessChange = gestures::onBrightnessChange.takeIf { preferences.gestureBrightness },
                    onBrightnessGestureEnd = gestures::onBrightnessEnd.takeIf { preferences.gestureBrightness },
                    onVolumeGestureStart = gestures::onVolumeStart.takeIf { preferences.gestureVolume },
                    onVolumeChange = gestures::onVolumeChange.takeIf { preferences.gestureVolume },
                    onVolumeGestureEnd = gestures::onVolumeEnd.takeIf { preferences.gestureVolume },
                    getCurrentBrightness = chrome.getCurrentBrightness,
                    getCurrentVolume = chrome.getCurrentVolume,
                    isAdjustingBrightness = ui.isAdjustingBrightness,
                    isAdjustingVolume = ui.isAdjustingVolume,
                    brightnessFraction = ui.brightnessFraction,
                    volumeFraction = ui.volumeFraction,
                    onBack = onBack,
                    onPlayPauseClick = chrome.onPlayPauseClick,
                    onPreviousClick = chrome.onPreviousClick,
                    onNextClick = chrome.onNextClick,
                    onSeek = { position ->
                        ui.resetControlsTimer()
                        chrome.seekToPosition(position)
                    },
                    onScrubStart = chrome.onScrubStart,
                    onScrubEnd = chrome.onScrubEnd,
                    onLockToggle = onLockToggle,
                    onRotateClick = onRotateClick,
                    onSpeedClick = {
                        ui.resetControlsTimer()
                        ui.showSpeedSheet = true
                    },
                    onLoopClick = {
                        ui.resetControlsTimer()
                        chrome.toggleRepeatOne()
                    },
                    onEpisodePickerClick = {
                        ui.resetControlsTimer()
                        ui.showEpisodeSheet = true
                    },
                    onMoreClick = {
                        ui.resetControlsTimer()
                        ui.showMoreSheet = true
                    },
                    onSkipIntro = onSkipIntro,
                    onSkipOutro = onSkipOutro,
                    pipSupported = pipSupported,
                    onPipClick = onPipClick,
                    onFullscreenClick = onFullscreenToggle,
                    contentScale = ui.aspectRatioMode.contentScale,
                    frameAspectRatio = ui.aspectRatioMode.frameAspectRatio,
                    seekEnabled = preferences.gestureSeek,
                    pinchEnabled = preferences.gesturePinchZoom,
                )
            }
            else -> {
                if (currentVideo != null) {
                    PortraitPlayerContent(
                        title = currentTitle,
                        resolution = resolution,
                        seriesTitle = seriesTitle,
                        exoPlayer = exoPlayer,
                        progress = progress,
                        ui = ui,
                        isCasting = isCasting,
                        durationMs = displayDurationMs,
                        isSeeking = ui.isSeeking,
                        controlsVisible = ui.controlsVisible,
                        episodes = playlist,
                        currentIndex = ui.currentIndex,
                        isPlaying = showsPause,
                        hasPrevious = hasPrevious,
                        hasNext = hasNext,
                        canEditSeriesSkip = canEditSeriesSkip && !isCasting,
                        introEndMs = introEndMs,
                        outroStartMs = outroStartMs,
                        introMarked = seriesSkip.introSeconds != null,
                        outroMarked = seriesSkip.outroSeconds != null,
                        onMarkIntro = onMarkIntro,
                        onMarkOutro = onMarkOutro,
                        onClearIntro = onClearIntro,
                        onClearOutro = onClearOutro,
                        onSeekPreview = onSeekPreview,
                        onSeekPreviewFrame = onSeekPreviewFrame,
                        onSeekCommitted = onSeekCommitted,
                        onSeekingChange = onSeekingChange,
                        onSpeedBoostStart = chrome.onSpeedBoostStart,
                        onSpeedBoostEnd = chrome.onSpeedBoostEnd,
                        onBrightnessGestureStart = gestures::onBrightnessStart,
                        onBrightnessChange = gestures::onBrightnessChange,
                        onBrightnessGestureEnd = gestures::onBrightnessEnd,
                        onVolumeGestureStart = gestures::onVolumeStart,
                        onVolumeChange = gestures::onVolumeChange,
                        onVolumeGestureEnd = gestures::onVolumeEnd,
                        getCurrentBrightness = chrome.getCurrentBrightness,
                        getCurrentVolume = chrome.getCurrentVolume,
                        isAdjustingBrightness = ui.isAdjustingBrightness,
                        isAdjustingVolume = ui.isAdjustingVolume,
                        brightnessFraction = ui.brightnessFraction,
                        volumeFraction = ui.volumeFraction,
                        isSpeedBoosted = ui.isSpeedBoosted,
                        onVideoTap = { ui.toggleControlsVisibility() },
                        onPlayPauseClick = chrome.onPlayPauseClick,
                        onPreviousClick = chrome.onPreviousClick,
                        onNextClick = chrome.onNextClick,
                        onSeek = chrome.seekToPosition,
                        onScrubStart = chrome.onScrubStart,
                        onScrubEnd = chrome.onScrubEnd,
                        playbackSpeed = ui.playbackSpeed,
                        repeatOne = ui.repeatOne,
                        onEpisodeSelected = onEpisodeSelected,
                        onSpeedClick = { ui.showSpeedSheet = true },
                        onLoopClick = chrome.toggleRepeatOne,
                        onMoreClick = { ui.showMoreSheet = true },
                        onSkipIntro = onSkipIntro,
                        onSkipOutro = onSkipOutro,
                        onVideoDoubleTap = onDoubleTap,
                        onFullscreenClick = onEnterLandscapeFullscreen,
                        pipSupported = pipSupported,
                        onPipClick = onPipClick,
                        contentScale = ui.aspectRatioMode.contentScale,
                        frameAspectRatio = ui.aspectRatioMode.frameAspectRatio,
                        useTextureView = preferences.useTextureView,
                        seekEnabled = preferences.gestureSeek,
                        pinchEnabled = preferences.gesturePinchZoom,
                        brightnessEnabled = preferences.gestureBrightness,
                        volumeEnabled = preferences.gestureVolume,
                    )
                }
            }
        }

        if (!hidePage) {
            LiveSeekPreview(
                ui = ui,
                durationMs = displayDurationMs,
                forDoubleTap = true,
            )
        }

        if (castManager != null && ui.controlsVisible && !ui.isInPipMode && !hidePage) {
            CastButton(
                state = castState,
                onClick = { castSheet.open() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding(),
            )
        }

        val connected = castState as? CastUiState.Connected
        if (connected != null && !ui.isInPipMode && !hidePage) {
            CastMiniController(
                visible = true,
                title = connected.title,
                isPlaying = castPlaying,
                onPlayPause = {
                    if (castPlaying) castManager?.playback?.pause() else castManager?.playback?.play()
                },
                onDisconnect = { castManager?.disconnect() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }

        ui.playbackFailure?.let { failure ->
            Text(
                text = stringResource(
                    if (failure == PlaybackFailure.Read) {
                        R.string.playback_error_read
                    } else {
                        R.string.playback_error_decode
                    },
                ),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )
        }
    }
}
