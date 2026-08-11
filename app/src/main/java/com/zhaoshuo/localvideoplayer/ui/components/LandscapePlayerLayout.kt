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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R

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
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
    onLockToggle: () -> Unit,
    onRotateClick: () -> Unit,
    onPipClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onEpisodePickerClick: () -> Unit,
    onMoreClick: () -> Unit = {},
    canEditSeriesSkip: Boolean = false,
    introEndMs: Long = 0L,
    outroStartMs: Long = -1L,
    introMarked: Boolean = false,
    outroMarked: Boolean = false,
    onMarkIntro: () -> Unit = {},
    onMarkOutro: () -> Unit = {},
    onClearIntro: () -> Unit = {},
    onClearOutro: () -> Unit = {},
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
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
    onLockToggle: () -> Unit,
    onRotateClick: () -> Unit,
    onPipClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onEpisodePickerClick: () -> Unit,
    onMoreClick: () -> Unit,
    canEditSeriesSkip: Boolean,
    introEndMs: Long,
    outroStartMs: Long,
    introMarked: Boolean,
    outroMarked: Boolean,
    onMarkIntro: () -> Unit,
    onMarkOutro: () -> Unit,
    onClearIntro: () -> Unit,
    onClearOutro: () -> Unit,
) {
    Box(modifier = Modifier.fillMaxSize()) {
        if (controlsLocked) {
            Box(
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .fillMaxHeight()
                    .width(52.dp)
                    .windowInsetsPadding(
                        WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.End),
                    )
                    .padding(vertical = 6.dp),
                contentAlignment = Alignment.Center,
            ) {
                SideRailButton(onClick = onLockToggle) {
                    Icon(
                        imageVector = Icons.Default.Lock,
                        contentDescription = stringResource(R.string.unlock_controls),
                        tint = Color(0xFFFFD700),
                        modifier = Modifier.size(22.dp),
                    )
                }
            }
        } else {
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
                    onScrubStart = onScrubStart,
                    onScrubEnd = onScrubEnd,
                    canEditSeriesSkip = canEditSeriesSkip,
                    introEndMs = introEndMs,
                    outroStartMs = outroStartMs,
                    introMarked = introMarked,
                    outroMarked = outroMarked,
                    onMarkIntro = onMarkIntro,
                    onMarkOutro = onMarkOutro,
                    onClearIntro = onClearIntro,
                    onClearOutro = onClearOutro,
                    modifier = Modifier.fillMaxWidth(),
                )
                PlayerBottomToolbar(
                    playbackSpeed = playbackSpeed,
                    repeatOne = repeatOne,
                    showEpisodePicker = showEpisodePicker,
                    onSpeedClick = onSpeedClick,
                    onLoopClick = onLoopClick,
                    onEpisodePickerClick = onEpisodePickerClick,
                    onMoreClick = onMoreClick,
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
                    controlsLocked = false,
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
}
