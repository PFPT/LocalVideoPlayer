package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.ui.theme.LumenBuffered
import com.zhaoshuo.localvideoplayer.util.formatDuration

@Composable
fun PlayerProgressBar(
    currentPositionMs: Long,
    bufferedPositionMs: Long,
    durationMs: Long,
    onSeek: (Long) -> Unit,
    modifier: Modifier = Modifier,
    showFullscreen: Boolean = false,
    isFullscreen: Boolean = false,
    onFullscreenClick: (() -> Unit)? = null,
    compact: Boolean = false,
    introEndMs: Long = 0L,
    outroStartMs: Long = -1L,
    canEditSeriesSkip: Boolean = false,
    introMarked: Boolean = false,
    outroMarked: Boolean = false,
    onMarkIntro: () -> Unit = {},
    onMarkOutro: () -> Unit = {},
    onClearIntro: () -> Unit = {},
    onClearOutro: () -> Unit = {},
) {
    var isScrubbing by remember { mutableStateOf(false) }
    var scrubFraction by remember { mutableFloatStateOf(0f) }

    val playerFraction = if (durationMs > 0) {
        (currentPositionMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val sliderPosition = if (isScrubbing) scrubFraction else playerFraction
    val displayPositionMs = if (isScrubbing && durationMs > 0) {
        (scrubFraction * durationMs).toLong().coerceIn(0L, durationMs)
    } else {
        currentPositionMs
    }
    val bufferedFraction = if (durationMs > 0) {
        bufferedPositionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }
    val introFraction = if (durationMs > 0 && introEndMs > 0L) {
        (introEndMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val outroFraction = if (durationMs > 0 && outroStartMs in 0 until durationMs) {
        (1f - outroStartMs.toFloat() / durationMs.toFloat()).coerceIn(0f, 1f)
    } else {
        0f
    }
    val skipMarkColor = Color(0xFFFFB74D).copy(alpha = 0.75f)

    Column(modifier = modifier) {
        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(displayPositionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                )
            }
        }
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            if (compact) {
                Text(
                    text = formatDuration(displayPositionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.padding(end = 8.dp),
                )
            }
            if (canEditSeriesSkip) {
                SkipMarkChip(
                    label = stringResource(R.string.skip_intro),
                    marked = introMarked,
                    onClick = onMarkIntro,
                    onLongClick = onClearIntro,
                    modifier = Modifier.padding(end = 6.dp),
                )
            }
            Box(
                modifier = Modifier
                    .weight(1f)
                    .height(24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Box(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(3.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(MaterialTheme.colorScheme.surface.copy(alpha = 0.6f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                        .height(3.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LumenBuffered),
                )
                if (introFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(introFraction)
                            .height(3.dp)
                            .align(Alignment.CenterStart)
                            .clip(RoundedCornerShape(2.dp))
                            .background(skipMarkColor),
                    )
                }
                if (outroFraction > 0f) {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth(outroFraction)
                            .height(3.dp)
                            .align(Alignment.CenterEnd)
                            .clip(RoundedCornerShape(2.dp))
                            .background(skipMarkColor),
                    )
                }
                Box(
                    modifier = Modifier
                        .fillMaxWidth(sliderPosition.coerceIn(0f, 1f))
                        .height(3.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(2.dp))
                        .background(Color.White),
                )
                Slider(
                    value = sliderPosition,
                    onValueChange = { fraction ->
                        if (durationMs <= 0) return@Slider
                        if (!isScrubbing) {
                            isScrubbing = true
                        }
                        scrubFraction = fraction.coerceIn(0f, 1f)
                    },
                    onValueChangeFinished = {
                        if (durationMs > 0 && isScrubbing) {
                            onSeek((scrubFraction * durationMs).toLong().coerceIn(0L, durationMs))
                        }
                        isScrubbing = false
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (canEditSeriesSkip) {
                SkipMarkChip(
                    label = stringResource(R.string.skip_outro),
                    marked = outroMarked,
                    onClick = onMarkOutro,
                    onLongClick = onClearOutro,
                    modifier = Modifier.padding(start = 6.dp),
                )
            }
            if (compact) {
                Text(
                    text = formatDuration(durationMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            if (showFullscreen && onFullscreenClick != null) {
                IconButton(
                    onClick = onFullscreenClick,
                    modifier = Modifier.size(32.dp),
                ) {
                    Icon(
                        imageVector = if (isFullscreen) Icons.Default.FullscreenExit else Icons.Default.Fullscreen,
                        contentDescription = stringResource(
                            if (isFullscreen) R.string.exit_fullscreen else R.string.fullscreen,
                        ),
                        tint = Color.White,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SkipMarkChip(
    label: String,
    marked: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    Text(
        text = label,
        style = MaterialTheme.typography.labelMedium,
        color = if (marked) Color(0xFFFFB74D) else Color.White.copy(alpha = 0.85f),
        modifier = modifier
            .clip(RoundedCornerShape(4.dp))
            .pointerInput(onClick, onLongClick) {
                detectTapGestures(
                    onTap = { onClick() },
                    onLongPress = { onLongClick() },
                )
            }
            .padding(horizontal = 6.dp, vertical = 4.dp),
    )
}
