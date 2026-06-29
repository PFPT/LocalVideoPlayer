package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp

@Composable
fun LandscapePlayerLayout(
    visible: Boolean,
    title: String,
    resolution: String?,
    isPlaying: Boolean,
    controlsLocked: Boolean,
    isFullscreen: Boolean = false,
    pipSupported: Boolean = false,
    showEpisodePicker: Boolean = false,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    playbackSpeed: Float,
    repeatOne: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onLockToggle: () -> Unit,
    onRotateClick: () -> Unit,
    onPipClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onEpisodePickerClick: () -> Unit,
    onBackgroundTap: () -> Unit = {},
    modifier: Modifier = Modifier,
) {
    if (isFullscreen) {
        FullscreenPlayerLayout(
            controlsVisible = visible,
            isPlaying = isPlaying,
            controlsLocked = controlsLocked,
            pipSupported = pipSupported,
            showEpisodePicker = showEpisodePicker,
            currentPositionMs = currentPositionMs,
            durationMs = durationMs,
            bufferedPositionMs = bufferedPositionMs,
            playbackSpeed = playbackSpeed,
            repeatOne = repeatOne,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            onBack = onBack,
            onPlayPauseClick = onPlayPauseClick,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            onSeek = onSeek,
            onLockToggle = onLockToggle,
            onRotateClick = onRotateClick,
            onPipClick = onPipClick,
            onFullscreenClick = onFullscreenClick,
            onSpeedClick = onSpeedClick,
            onLoopClick = onLoopClick,
            onEpisodePickerClick = onEpisodePickerClick,
            onBackgroundTap = onBackgroundTap,
            modifier = modifier,
        )
        return
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        LandscapePlayerLayoutContent(
                title = title,
                resolution = resolution,
                isPlaying = isPlaying,
                controlsLocked = controlsLocked,
                pipSupported = pipSupported,
                showEpisodePicker = showEpisodePicker,
                currentPositionMs = currentPositionMs,
                durationMs = durationMs,
                bufferedPositionMs = bufferedPositionMs,
                playbackSpeed = playbackSpeed,
                repeatOne = repeatOne,
                hasPrevious = hasPrevious,
                hasNext = hasNext,
                onBack = onBack,
                onPlayPauseClick = onPlayPauseClick,
                onPreviousClick = onPreviousClick,
                onNextClick = onNextClick,
                onSeek = onSeek,
                onLockToggle = onLockToggle,
                onRotateClick = onRotateClick,
                onPipClick = onPipClick,
                onFullscreenClick = onFullscreenClick,
                onSpeedClick = onSpeedClick,
                onLoopClick = onLoopClick,
                onEpisodePickerClick = onEpisodePickerClick,
            )
    }
}

@Composable
private fun LandscapePlayerLayoutContent(
    title: String,
    resolution: String?,
    isPlaying: Boolean,
    controlsLocked: Boolean,
    pipSupported: Boolean,
    showEpisodePicker: Boolean,
    currentPositionMs: Long,
    durationMs: Long,
    bufferedPositionMs: Long,
    playbackSpeed: Float,
    repeatOne: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onBack: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onLockToggle: () -> Unit,
    onRotateClick: () -> Unit,
    onPipClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onEpisodePickerClick: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.TopCenter)
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                    ),
                )
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal),
                )
                .padding(horizontal = 16.dp, vertical = 8.dp),
        ) {
            PlayerTopBar(
                title = title,
                resolution = resolution,
            )
        }

        PlayerCenterControls(
            isPlaying = isPlaying,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            onPlayPauseClick = onPlayPauseClick,
            onPreviousClick = onPreviousClick,
            onNextClick = onNextClick,
            modifier = Modifier.align(Alignment.Center),
        )

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .align(Alignment.BottomCenter)
                .navigationBarsPadding()
                .background(
                    Brush.verticalGradient(
                        colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                    ),
                )
                .padding(horizontal = 16.dp, vertical = 10.dp),
        ) {
            PlayerProgressBar(
                currentPositionMs = currentPositionMs,
                bufferedPositionMs = bufferedPositionMs,
                durationMs = durationMs,
                onSeek = onSeek,
                modifier = Modifier.fillMaxWidth(),
            )
            PlayerBottomToolbar(
                playbackSpeed = playbackSpeed,
                repeatOne = repeatOne,
                showEpisodePicker = showEpisodePicker,
                onSpeedClick = onSpeedClick,
                onLoopClick = onLoopClick,
                onEpisodePickerClick = onEpisodePickerClick,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 6.dp),
            )
        }

        Column(
            modifier = Modifier
                .align(Alignment.CenterEnd)
                .fillMaxHeight()
                .width(52.dp)
                .background(Color.Black.copy(alpha = 0.7f))
                .windowInsetsPadding(
                    WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End),
                )
                .padding(vertical = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            PlayerSideActions(
                controlsLocked = controlsLocked,
                pipSupported = pipSupported,
                isFullscreen = false,
                horizontal = false,
                onBack = onBack,
                onLockToggle = onLockToggle,
                onRotateClick = onRotateClick,
                onPipClick = onPipClick,
                onFullscreenClick = onFullscreenClick,
            )
        }
    }
}
