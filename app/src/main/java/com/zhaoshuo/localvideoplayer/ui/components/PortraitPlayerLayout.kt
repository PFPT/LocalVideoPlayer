package com.zhaoshuo.localvideoplayer.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.media3.exoplayer.ExoPlayer
import androidx.compose.ui.unit.dp
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
    onVideoTap: () -> Unit,
    onVideoDoubleTap: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onEpisodeSelected: (Int) -> Unit,
    onViewAllClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
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
                getCurrentPositionMs = { seekPositionMs },
                durationMs = durationMs,
                onSeekPreview = onSeekPreview,
                onSeekPreviewFrame = onSeekPreviewFrame,
                onSeekCommitted = onSeekCommitted,
                onTap = { _, _, _ -> onVideoTap() },
                onDoubleTap = onVideoDoubleTap,
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
                enabled = durationMs > 0,
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
                    )
                }
            }
            Box(
                modifier = Modifier
                    .matchParentSize()
                    .then(
                        if (controlsVisible || isSeeking) {
                            Modifier.background(
                                Brush.verticalGradient(
                                    colors = listOf(
                                        Color.Black.copy(alpha = 0.5f),
                                        Color.Transparent,
                                        Color.Transparent,
                                        Color.Black.copy(alpha = 0.6f),
                                    ),
                                ),
                            )
                        } else {
                            Modifier
                        },
                    ),
            )
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
            PlayerTopBar(
                title = title,
                resolution = resolution,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .statusBarsPadding()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
            )
            if (controlsVisible || isSeeking) {
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
            }
            PlayerProgressBar(
                currentPositionMs = currentPositionMs,
                bufferedPositionMs = bufferedPositionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                showFullscreen = true,
                isFullscreen = false,
                onFullscreenClick = onFullscreenClick,
                compact = true,
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 10.dp),
            )
            }
        }
        PlayerMetadataPanel(
            seriesTitle = seriesTitle,
            currentIndex = currentIndex,
            totalEpisodes = episodes.size,
            currentEpisode = episodes[currentIndex],
            episodes = episodes,
            onEpisodeSelected = onEpisodeSelected,
            onViewAllClick = onViewAllClick,
            modifier = Modifier
                .fillMaxWidth()
                .weight(0.62f)
                .navigationBarsPadding(),
        )
    }
}
