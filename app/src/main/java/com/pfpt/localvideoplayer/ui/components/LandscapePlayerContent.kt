package com.pfpt.localvideoplayer.ui.components

import android.net.Uri
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.player.PlayerProgressState
import com.pfpt.localvideoplayer.ui.PlayerUiState

@Composable
internal fun LandscapePlayerContent(
    exoPlayer: ExoPlayer,
    progress: PlayerProgressState,
    durationMs: Long,
    videoUri: Uri?,
    videoScale: Float,
    useTextureView: Boolean,
    offsetX: Float,
    offsetY: Float,
    ui: PlayerUiState,
    isCasting: Boolean,
    isSeeking: Boolean,
    controlsVisible: Boolean,
    controlsLocked: Boolean,
    isFullscreen: Boolean,
    title: String,
    resolution: String?,
    isPlaying: Boolean,
    playbackSpeed: Float,
    repeatOne: Boolean,
    isSpeedBoosted: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    canEditSeriesSkip: Boolean,
    introEndMs: Long,
    outroStartMs: Long,
    introMarked: Boolean,
    outroMarked: Boolean,
    onMarkIntro: () -> Unit,
    onMarkOutro: () -> Unit,
    onClearIntro: () -> Unit,
    onClearOutro: () -> Unit,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekPreviewFrame: (Long) -> Unit,
    onSeekCommitted: (Long) -> Unit,
    onTap: (Offset, Float, Float) -> Unit,
    onDoubleTap: (Offset, Float) -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onSpeedBoostStart: () -> Unit,
    onSpeedBoostEnd: () -> Unit,
    onBrightnessGestureStart: (() -> Unit)? = null,
    onBrightnessChange: ((Float) -> Unit)? = null,
    onBrightnessGestureEnd: (() -> Unit)? = null,
    onVolumeGestureStart: (() -> Unit)? = null,
    onVolumeChange: ((Float) -> Unit)? = null,
    onVolumeGestureEnd: (() -> Unit)? = null,
    getCurrentBrightness: () -> Float,
    getCurrentVolume: () -> Float,
    isAdjustingBrightness: Boolean,
    isAdjustingVolume: Boolean,
    brightnessFraction: Float,
    volumeFraction: Float,
    onBack: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
    onLockToggle: () -> Unit,
    onRotateClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onEpisodePickerClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSkipIntro: () -> Unit,
    onSkipOutro: () -> Unit,
    pipSupported: Boolean,
    onPipClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
    frameAspectRatio: Float? = null,
    seekEnabled: Boolean = true,
    pinchEnabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isFullscreen) {
                        Modifier.statusBarsPadding()
                    } else {
                        Modifier
                    },
                ),
        ) {
            VideoPlayerGesture(
        scale = videoScale,
        offsetX = offsetX,
        offsetY = offsetY,
        onScaleChange = onScaleChange,
        onOffsetChange = onOffsetChange,
        getCurrentPositionMs = { exoPlayer.currentPosition.coerceAtLeast(0L) },
        getDurationMs = {
            exoPlayer.duration.takeIf { it > 0 } ?: durationMs
        },
        getBufferedPositionMs = { exoPlayer.bufferedPosition.coerceAtLeast(0L) },
        isSeekable = { exoPlayer.isCurrentMediaItemSeekable },
        onSeekPreview = onSeekPreview,
        onSeekPreviewFrame = onSeekPreviewFrame,
        onSeekCommitted = onSeekCommitted,
        onTap = onTap,
        onDoubleTap = onDoubleTap,
        onSeekingChange = onSeekingChange,
        onSpeedBoostStart = onSpeedBoostStart,
        onSpeedBoostEnd = onSpeedBoostEnd,
        onBrightnessGestureStart = onBrightnessGestureStart,
        onBrightnessChange = onBrightnessChange,
        onBrightnessGestureEnd = onBrightnessGestureEnd,
        onVolumeGestureStart = onVolumeGestureStart,
        onVolumeChange = onVolumeChange,
        onVolumeGestureEnd = onVolumeGestureEnd,
        getCurrentBrightness = getCurrentBrightness,
        getCurrentVolume = getCurrentVolume,
        enabled = true,
        speedBoostOnly = controlsLocked,
        seekEnabled = seekEnabled,
        pinchEnabled = pinchEnabled,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            FittedPlayerSurface(
                player = exoPlayer,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                frameAspectRatio = frameAspectRatio,
                useTextureView = useTextureView,
            )
        }
    }

    BrightnessFeedbackOverlay(
        visible = isAdjustingBrightness,
        brightnessFraction = brightnessFraction,
    )

    VolumeFeedbackOverlay(
        visible = isAdjustingVolume,
        volumeFraction = volumeFraction,
    )

    SpeedBoostOverlay(visible = isSpeedBoosted)
        }

        LandscapePlayerLayout(
        visible = controlsVisible || isSeeking,
        title = title,
        resolution = resolution,
        isPlaying = isPlaying,
        controlsLocked = controlsLocked,
        isFullscreen = isFullscreen,
        pipSupported = pipSupported,
        showEpisodePicker = true,
        progress = progress,
        ui = ui,
        isCasting = isCasting,
        durationMs = durationMs,
        playbackSpeed = playbackSpeed,
        repeatOne = repeatOne,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        canEditSeriesSkip = canEditSeriesSkip,
        introEndMs = introEndMs,
        outroStartMs = outroStartMs,
        introMarked = introMarked,
        outroMarked = outroMarked,
        onMarkIntro = onMarkIntro,
        onMarkOutro = onMarkOutro,
        onClearIntro = onClearIntro,
        onClearOutro = onClearOutro,
        onBack = onBack,
        onPlayPauseClick = onPlayPauseClick,
        onPreviousClick = onPreviousClick,
        onNextClick = onNextClick,
        onSeek = onSeek,
        onScrubStart = onScrubStart,
        onScrubEnd = onScrubEnd,
        onLockToggle = onLockToggle,
        onRotateClick = onRotateClick,
        onPipClick = onPipClick,
        onFullscreenClick = onFullscreenClick,
        onSpeedClick = onSpeedClick,
        onLoopClick = onLoopClick,
        onEpisodePickerClick = onEpisodePickerClick,
        onMoreClick = onMoreClick,
        modifier = Modifier.fillMaxSize(),
    )
        LiveSkipIntroOutro(
            progress = progress,
            ui = ui,
            isCasting = isCasting,
            introEndMs = introEndMs,
            outroStartMs = outroStartMs,
            durationMs = durationMs,
            onSkipIntro = onSkipIntro,
            onSkipOutro = onSkipOutro,
        )
        LiveSeekPreview(
            ui = ui,
            durationMs = durationMs,
            videoUri = videoUri,
        )
    }
}
