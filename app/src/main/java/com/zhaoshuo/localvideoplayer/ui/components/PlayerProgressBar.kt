package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.background
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.ui.theme.LumenBuffered
import com.zhaoshuo.localvideoplayer.ui.theme.LumenSurface
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
) {
    val sliderPosition = if (durationMs > 0) {
        currentPositionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }
    val bufferedFraction = if (durationMs > 0) {
        bufferedPositionMs.toFloat() / durationMs.toFloat()
    } else {
        0f
    }

    Column(modifier = modifier) {
        if (!compact) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = formatDuration(currentPositionMs),
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
                    text = formatDuration(currentPositionMs),
                    style = MaterialTheme.typography.bodySmall,
                    color = Color.White,
                    modifier = Modifier.padding(end = 8.dp),
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
                        .background(LumenSurface.copy(alpha = 0.6f)),
                )
                Box(
                    modifier = Modifier
                        .fillMaxWidth(bufferedFraction.coerceIn(0f, 1f))
                        .height(3.dp)
                        .align(Alignment.CenterStart)
                        .clip(RoundedCornerShape(2.dp))
                        .background(LumenBuffered),
                )
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
                        if (durationMs > 0) {
                            onSeek((fraction * durationMs).toLong())
                        }
                    },
                    colors = SliderDefaults.colors(
                        thumbColor = Color.White,
                        activeTrackColor = Color.Transparent,
                        inactiveTrackColor = Color.Transparent,
                    ),
                    modifier = Modifier.fillMaxWidth(),
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
