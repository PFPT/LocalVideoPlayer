package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.media3.exoplayer.ExoPlayer
import com.zhaoshuo.localvideoplayer.model.VideoItem

@Composable
fun PortraitPlayerLayout(
    title: String,
    resolution: String?,
    seriesTitle: String,
    exoPlayer: ExoPlayer,
    episodes: List<VideoItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    currentPositionMs: Long,
    seekPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    getDurationMs: () -> Long = { durationMs },
    getCurrentPositionMs: () -> Long = { seekPositionMs },
    isSeeking: Boolean,
    previewPositionMs: Long,
    seekStartPositionMs: Long,
    controlsVisible: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onSeekPreview: (Long) -> Unit,
    onSeekPreviewFrame: (Long) -> Unit,
    onSeekCommitted: (Long) -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onSpeedBoostStart: () -> Unit,
    onSpeedBoostEnd: () -> Unit,
    onBrightnessGestureStart: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessGestureEnd: () -> Unit,
    onVolumeGestureStart: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeGestureEnd: () -> Unit,
    getCurrentBrightness: () -> Float,
    getCurrentVolume: () -> Float,
    isAdjustingBrightness: Boolean,
    isAdjustingVolume: Boolean,
    brightnessFraction: Float,
    volumeFraction: Float,
    isSpeedBoosted: Boolean,
    onVideoTap: () -> Unit,
    onVideoDoubleTap: (Offset, Float) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
    playbackSpeed: Float,
    repeatOne: Boolean,
    onEpisodeSelected: (Int) -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onMoreClick: () -> Unit,
    showSkipIntro: Boolean,
    showSkipOutro: Boolean,
    onSkipIntro: () -> Unit,
    onSkipOutro: () -> Unit,
    onFullscreenClick: () -> Unit,
    canEditSeriesSkip: Boolean = false,
    introEndMs: Long = 0L,
    outroStartMs: Long = -1L,
    introMarked: Boolean = false,
    outroMarked: Boolean = false,
    onMarkIntro: () -> Unit = {},
    onMarkOutro: () -> Unit = {},
    onClearIntro: () -> Unit = {},
    onClearOutro: () -> Unit = {},
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
    frameAspectRatio: Float? = null,
    seekEnabled: Boolean = true,
    pinchEnabled: Boolean = true,
    brightnessEnabled: Boolean = true,
    volumeEnabled: Boolean = true,
    modifier: Modifier = Modifier,
) {
    val showChrome = controlsVisible || isSeeking
    Column(modifier = modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.38f)
                .background(Color.Black),
        ) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .statusBarsPadding(),
            ) {
                VideoPlayerGesture(
                    scale = 1f,
                    offsetX = 0f,
                    offsetY = 0f,
                    onScaleChange = {},
                    onOffsetChange = { _, _ -> },
                    getCurrentPositionMs = getCurrentPositionMs,
                    getDurationMs = getDurationMs,
                    onSeekPreview = onSeekPreview,
                    onSeekPreviewFrame = onSeekPreviewFrame,
                    onSeekCommitted = onSeekCommitted,
                    onTap = { _, _, _ -> onVideoTap() },
                    onDoubleTap = onVideoDoubleTap,
                    onSeekingChange = onSeekingChange,
                    onSpeedBoostStart = onSpeedBoostStart,
                    onSpeedBoostEnd = onSpeedBoostEnd,
                    onBrightnessGestureStart = onBrightnessGestureStart.takeIf { brightnessEnabled },
                    onBrightnessChange = onBrightnessChange.takeIf { brightnessEnabled },
                    onBrightnessGestureEnd = onBrightnessGestureEnd.takeIf { brightnessEnabled },
                    onVolumeGestureStart = onVolumeGestureStart.takeIf { volumeEnabled },
                    onVolumeChange = onVolumeChange.takeIf { volumeEnabled },
                    onVolumeGestureEnd = onVolumeGestureEnd.takeIf { volumeEnabled },
                    getCurrentBrightness = getCurrentBrightness,
                    getCurrentVolume = getCurrentVolume,
                    enabled = durationMs > 0,
                    seekEnabled = seekEnabled,
                    pinchEnabled = pinchEnabled,
                    modifier = Modifier
                        .fillMaxSize()
                        .background(Color.Black),
                ) {
                    Box(
                        modifier = Modifier.fillMaxSize(),
                        contentAlignment = Alignment.Center,
                    ) {
                        FittedPlayerSurface(
                            player = exoPlayer,
                            modifier = Modifier.fillMaxSize(),
                            contentScale = contentScale,
                            frameAspectRatio = frameAspectRatio,
                        )
                    }
                }
                androidx.compose.animation.AnimatedVisibility(
                    visible = showChrome,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.matchParentSize(),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxSize()
                            .background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f),
                                    ),
                                ),
                            ),
                    )
                }
                SeekPreviewOverlay(
                    visible = isSeeking,
                    previewPositionMs = previewPositionMs,
                    durationMs = durationMs,
                    seekStartPositionMs = seekStartPositionMs,
                )
                BrightnessFeedbackOverlay(
                    visible = isAdjustingBrightness,
                    brightnessFraction = brightnessFraction,
                )
                VolumeFeedbackOverlay(
                    visible = isAdjustingVolume,
                    volumeFraction = volumeFraction,
                )
                SpeedBoostOverlay(visible = isSpeedBoosted)
                SkipIntroOutroOverlay(
                    showSkipIntro = showSkipIntro,
                    showSkipOutro = showSkipOutro,
                    onSkipIntro = onSkipIntro,
                    onSkipOutro = onSkipOutro,
                )
                androidx.compose.animation.AnimatedVisibility(
                    visible = showChrome,
                    enter = fadeIn(),
                    exit = fadeOut(),
                    modifier = Modifier.matchParentSize(),
                ) {
                    Box(modifier = Modifier.fillMaxSize()) {
                        PlayerTopBar(
                            title = title,
                            resolution = resolution,
                            modifier = Modifier
                                .align(Alignment.TopStart)
                                .statusBarsPadding()
                                .padding(horizontal = 16.dp, vertical = 12.dp),
                        )
                        Box(
                            modifier = Modifier.align(Alignment.Center),
                            contentAlignment = Alignment.Center,
                        ) {
                            PlayerCenterControls(
                                isPlaying = isPlaying,
                                hasPrevious = hasPrevious,
                                hasNext = hasNext,
                                onPlayPauseClick = onPlayPauseClick,
                                onPreviousClick = onPreviousClick,
                                onNextClick = onNextClick,
                            )
                        }
                        PlayerProgressBar(
                            currentPositionMs = currentPositionMs,
                            bufferedPositionMs = bufferedPositionMs,
                            durationMs = durationMs,
                            onSeek = onSeek,
                            onScrubStart = onScrubStart,
                            onScrubEnd = onScrubEnd,
                            showFullscreen = true,
                            isFullscreen = false,
                            onFullscreenClick = onFullscreenClick,
                            compact = true,
                            canEditSeriesSkip = canEditSeriesSkip,
                            introEndMs = introEndMs,
                            outroStartMs = outroStartMs,
                            introMarked = introMarked,
                            outroMarked = outroMarked,
                            onMarkIntro = onMarkIntro,
                            onMarkOutro = onMarkOutro,
                            onClearIntro = onClearIntro,
                            onClearOutro = onClearOutro,
                            modifier = Modifier
                                .align(Alignment.BottomCenter)
                                .fillMaxWidth()
                                .padding(horizontal = 16.dp, vertical = 10.dp),
                        )
                    }
                }
            }
        }
        PlayerMetadataPanel(
            seriesTitle = seriesTitle,
            currentIndex = currentIndex,
            totalEpisodes = episodes.size,
            currentEpisode = episodes[currentIndex],
            episodes = episodes,
            playbackSpeed = playbackSpeed,
            repeatOne = repeatOne,
            resolution = resolution,
            onEpisodeSelected = onEpisodeSelected,
            onSpeedClick = onSpeedClick,
            onLoopClick = onLoopClick,
            onMoreClick = onMoreClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.62f)
                .navigationBarsPadding(),
        )
    }
}
