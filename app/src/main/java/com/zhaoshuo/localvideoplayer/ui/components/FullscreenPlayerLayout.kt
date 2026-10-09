package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.player.PlayerProgressState
import com.zhaoshuo.localvideoplayer.ui.PlayerUiState
import com.zhaoshuo.localvideoplayer.util.formatSystemTime
import kotlinx.coroutines.delay

@Composable
internal fun FullscreenPlayerLayout(
    controlsVisible: Boolean,
    isPlaying: Boolean,
    controlsLocked: Boolean,
    pipSupported: Boolean,
    showEpisodePicker: Boolean,
    progress: PlayerProgressState,
    ui: PlayerUiState,
    isCasting: Boolean,
    durationMs: Long,
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
    Box(
        modifier = modifier.fillMaxSize(),
    ) {
        if (controlsLocked) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier
                    .align(Alignment.CenterEnd)
                    .navigationBarsPadding()
                    .padding(end = 16.dp),
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
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter),
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Black.copy(alpha = 0.65f), Color.Transparent),
                            ),
                        )
                        .padding(horizontal = 4.dp, vertical = 8.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    SideRailButton(onClick = onBack) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back),
                            tint = Color.White,
                            modifier = Modifier.size(22.dp),
                        )
                    }
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        FullscreenSystemClock()
                        BatteryLevelIndicator()
                    }
                }
            }

            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.BottomCenter),
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .navigationBarsPadding()
                        .background(
                            Brush.verticalGradient(
                                colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.85f)),
                            ),
                        )
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                ) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 12.dp),
                        contentAlignment = Alignment.Center,
                    ) {
                        PlayerCenterControls(
                            isPlaying = isPlaying,
                            hasPrevious = hasPrevious,
                            hasNext = hasNext,
                            onPlayPauseClick = onPlayPauseClick,
                            onPreviousClick = onPreviousClick,
                            onNextClick = onNextClick,
                            compact = true,
                        )
                    }
                    LivePlayerProgressBar(
                        progress = progress,
                        ui = ui,
                        isCasting = isCasting,
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
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(top = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        PlayerSideActions(
                            controlsLocked = false,
                            pipSupported = pipSupported,
                            isFullscreen = true,
                            horizontal = true,
                            showBack = false,
                            onBack = onBack,
                            onLockToggle = onLockToggle,
                            onRotateClick = onRotateClick,
                            onPipClick = onPipClick,
                            onFullscreenClick = onFullscreenClick,
                        )
                        PlayerBottomToolbar(
                            playbackSpeed = playbackSpeed,
                            repeatOne = repeatOne,
                            showEpisodePicker = showEpisodePicker,
                            onSpeedClick = onSpeedClick,
                            onLoopClick = onLoopClick,
                            onEpisodePickerClick = onEpisodePickerClick,
                            onMoreClick = onMoreClick,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun FullscreenSystemClock(modifier: Modifier = Modifier) {
    var timeText by remember { mutableStateOf(formatSystemTime()) }
    LaunchedEffect(Unit) {
        while (true) {
            timeText = formatSystemTime()
            delay(1000L)
        }
    }
    Text(
        text = timeText,
        style = MaterialTheme.typography.titleMedium,
        color = Color.White,
        modifier = modifier,
    )
}
