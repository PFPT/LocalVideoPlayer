package com.pfpt.localvideoplayer.ui.components

import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.CastConnected
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.cast.CastUiState

@Composable
fun CastButton(
    state: CastUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val connecting = state is CastUiState.Connecting || state is CastUiState.Searching
    val connected = state is CastUiState.Connected
    val transition = rememberInfiniteTransition(label = "castButton")
    val rotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1200, easing = LinearEasing),
            repeatMode = RepeatMode.Restart,
        ),
        label = "castRotation",
    )
    val alpha by transition.animateFloat(
        initialValue = 0.45f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 900),
            repeatMode = RepeatMode.Reverse,
        ),
        label = "castAlpha",
    )

    IconButton(onClick = onClick, modifier = modifier) {
        Icon(
            imageVector = if (connected) Icons.Default.CastConnected else Icons.Default.Cast,
            contentDescription = stringResource(R.string.cast),
            tint = if (connected) {
                MaterialTheme.colorScheme.primary
            } else {
                MaterialTheme.colorScheme.onSurface
            },
            modifier = Modifier
                .size(24.dp)
                .then(
                    if (connecting) {
                        Modifier
                            .rotate(rotation)
                            .graphicsLayer { this.alpha = alpha }
                    } else {
                        Modifier
                    },
                ),
        )
    }
}
