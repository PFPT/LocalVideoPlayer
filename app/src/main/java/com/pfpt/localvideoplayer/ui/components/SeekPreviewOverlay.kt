package com.pfpt.localvideoplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowLeft
import androidx.compose.material.icons.automirrored.filled.KeyboardArrowRight
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.pfpt.localvideoplayer.ui.theme.LumenAccentText
import com.pfpt.localvideoplayer.ui.theme.LumenOnBackground
import com.pfpt.localvideoplayer.util.formatDuration
import kotlin.math.abs

@Composable
fun SeekPreviewOverlay(
    visible: Boolean,
    previewPositionMs: Long,
    durationMs: Long,
    seekStartPositionMs: Long,
    previewImage: ImageBitmap? = null,
    modifier: Modifier = Modifier,
) {
    val deltaMs = previewPositionMs - seekStartPositionMs
    val deltaText = when {
        deltaMs > 0 -> "+${formatDuration(deltaMs)}"
        deltaMs < 0 -> "-${formatDuration(abs(deltaMs))}"
        else -> "+00:00"
    }

    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center,
        ) {
            Surface(
                shape = RoundedCornerShape(12.dp),
                color = LumenOnBackground.copy(alpha = 0.75f),
            ) {
                Column(
                    modifier = Modifier.padding(horizontal = 20.dp, vertical = 14.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    if (previewImage != null) {
                        Image(
                            bitmap = previewImage,
                            contentDescription = null,
                            contentScale = ContentScale.Fit,
                            modifier = Modifier
                                .padding(bottom = 8.dp)
                                .width(160.dp)
                                .height(90.dp),
                        )
                    }
                    Text(
                        text = deltaText,
                        color = LumenAccentText,
                        fontSize = 22.sp,
                        fontWeight = FontWeight.Bold,
                    )
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(top = 6.dp),
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowLeft,
                            contentDescription = null,
                            tint = Color.White,
                        )
                        Text(
                            text = "${formatDuration(previewPositionMs)} / ${formatDuration(durationMs)}",
                            color = Color.White,
                            style = MaterialTheme.typography.bodyLarge,
                            modifier = Modifier.padding(horizontal = 12.dp),
                        )
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.KeyboardArrowRight,
                            contentDescription = null,
                            tint = Color.White,
                        )
                    }
                }
            }
        }
    }
}
