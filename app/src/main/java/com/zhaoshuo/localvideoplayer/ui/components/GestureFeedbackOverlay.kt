package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BrightnessMedium
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.ui.theme.LumenAccentText

private val BarHeight = 132.dp
private val BarWidth = 6.dp
private val PanelWidth = 40.dp
private val EdgeHorizontalInset = 28.dp

@Composable
fun BrightnessFeedbackOverlay(
    visible: Boolean,
    brightnessFraction: Float,
    modifier: Modifier = Modifier,
) {
    EdgeLevelFeedbackOverlay(
        visible = visible,
        fraction = brightnessFraction,
        contentAlignment = Alignment.CenterStart,
        edgePadding = Modifier.padding(start = EdgeHorizontalInset),
        gradient = Brush.horizontalGradient(
            colors = listOf(Color.Black.copy(alpha = 0.35f), Color.Transparent),
        ),
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.BrightnessMedium,
            contentDescription = stringResource(R.string.brightness_label),
            tint = Color.White,
            modifier = Modifier.height(20.dp),
        )
    }
}

@Composable
fun VolumeFeedbackOverlay(
    visible: Boolean,
    volumeFraction: Float,
    modifier: Modifier = Modifier,
) {
    EdgeLevelFeedbackOverlay(
        visible = visible,
        fraction = volumeFraction,
        contentAlignment = Alignment.CenterEnd,
        edgePadding = Modifier.padding(end = EdgeHorizontalInset),
        gradient = Brush.horizontalGradient(
            colors = listOf(Color.Transparent, Color.Black.copy(alpha = 0.35f)),
        ),
        modifier = modifier,
    ) {
        Icon(
            imageVector = Icons.Default.VolumeUp,
            contentDescription = stringResource(R.string.volume_label),
            tint = Color.White,
            modifier = Modifier.height(20.dp),
        )
    }
}

@Composable
fun SpeedBoostOverlay(
    visible: Boolean,
    modifier: Modifier = Modifier,
) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.TopCenter,
        ) {
            Text(
                text = stringResource(R.string.speed_boost_active),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
                modifier = Modifier
                    .padding(top = 72.dp)
                    .background(Color.Black.copy(alpha = 0.55f), RoundedCornerShape(20.dp))
                    .padding(horizontal = 16.dp, vertical = 8.dp),
            )
        }
    }
}

@Composable
private fun EdgeLevelFeedbackOverlay(
    visible: Boolean,
    fraction: Float,
    contentAlignment: Alignment,
    edgePadding: Modifier,
    gradient: Brush,
    modifier: Modifier = Modifier,
    icon: @Composable () -> Unit,
) {
    val clampedFraction = fraction.coerceIn(0f, 1f)
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(edgePadding),
            contentAlignment = contentAlignment,
        ) {
            Box(
                modifier = Modifier
                    .fillMaxHeight()
                    .width(56.dp)
                    .background(gradient),
                contentAlignment = contentAlignment,
            ) {
                Column(
                    modifier = Modifier.width(PanelWidth),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    icon()
                    Text(
                        text = "${(clampedFraction * 100).toInt()}%",
                        style = MaterialTheme.typography.titleMedium,
                        color = Color.White,
                        textAlign = TextAlign.Center,
                        modifier = Modifier.padding(top = 6.dp, bottom = 8.dp),
                    )
                    Box(
                        modifier = Modifier
                            .width(BarWidth)
                            .height(BarHeight)
                            .background(Color.White.copy(alpha = 0.2f), RoundedCornerShape(3.dp)),
                        contentAlignment = Alignment.BottomCenter,
                    ) {
                        Box(
                            modifier = Modifier
                                .width(BarWidth)
                                .height((BarHeight * clampedFraction).coerceAtLeast(4.dp))
                                .background(LumenAccentText, RoundedCornerShape(3.dp)),
                        )
                    }
                }
            }
        }
    }
}
