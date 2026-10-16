package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.SkipNext
import androidx.compose.material.icons.filled.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.ui.theme.LumenCompactMainPlayButtonSize
import com.zhaoshuo.localvideoplayer.ui.theme.LumenCompactOverlayPlayButtonSize
import com.zhaoshuo.localvideoplayer.ui.theme.LumenMainPlayButtonSize
import com.zhaoshuo.localvideoplayer.ui.theme.LumenOverlayPlayButtonSize

@Composable
fun PlayerCenterControls(
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    modifier: Modifier = Modifier,
    compact: Boolean = false,
) {
    val mainButtonSize = if (compact) LumenCompactMainPlayButtonSize else LumenMainPlayButtonSize
    val overlayButtonSize = if (compact) LumenCompactOverlayPlayButtonSize else LumenOverlayPlayButtonSize
    val mainIconSize = if (compact) 24.dp else 36.dp
    val skipIconSize = if (compact) 20.dp else 28.dp
    val buttonSpacing = if (compact) 20.dp else 32.dp

    Row(
        modifier = modifier,
        horizontalArrangement = Arrangement.spacedBy(buttonSpacing),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        GlassSurface(
            modifier = Modifier
                .size(overlayButtonSize)
                .clickable(enabled = hasPrevious, onClick = onPreviousClick),
            shape = CircleShape,
            fillAlpha = 0.35f,
        ) {
            Icon(
                imageVector = Icons.Default.SkipPrevious,
                contentDescription = stringResource(R.string.previous),
                tint = if (hasPrevious) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(skipIconSize),
            )
        }
        GlassSurface(
            modifier = Modifier
                .size(mainButtonSize)
                .clickable(onClick = onPlayPauseClick),
            shape = CircleShape,
            fillAlpha = 0.35f,
        ) {
            Icon(
                imageVector = if (isPlaying) Icons.Default.Pause else Icons.Default.PlayArrow,
                contentDescription = stringResource(if (isPlaying) R.string.pause else R.string.play),
                tint = Color.White,
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(mainIconSize),
            )
        }
        GlassSurface(
            modifier = Modifier
                .size(overlayButtonSize)
                .clickable(enabled = hasNext, onClick = onNextClick),
            shape = CircleShape,
            fillAlpha = 0.35f,
        ) {
            Icon(
                imageVector = Icons.Default.SkipNext,
                contentDescription = stringResource(R.string.next),
                tint = if (hasNext) Color.White else Color.White.copy(alpha = 0.3f),
                modifier = Modifier
                    .align(Alignment.Center)
                    .size(skipIconSize),
            )
        }
    }
}
