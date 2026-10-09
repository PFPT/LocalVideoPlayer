package com.zhaoshuo.localvideoplayer.ui.components

import android.net.Uri
import androidx.compose.animation.Crossfade
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Movie
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import kotlinx.coroutines.delay
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme
import com.zhaoshuo.localvideoplayer.ui.theme.LumenThumbCorner
import com.zhaoshuo.localvideoplayer.util.VideoThumbnailCache

@Composable
fun LumenMediaCard(
    title: String,
    subtitle: String,
    thumbnailUri: Uri?,
    thumbnailDateModified: Long = 0L,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    progress: Float? = null,
    thumbnailContent: (@Composable () -> Unit)? = null,
    clickable: Boolean = true,
    opaqueBackground: Boolean = false,
    deferThumbnailLoad: Boolean = false,
    trailingContent: (@Composable () -> Unit)? = null,
) {
    val theme = LocalLumenTheme.current
    val cardShape = RoundedCornerShape(LumenThumbCorner)
    val backgroundColor = if (opaqueBackground) {
        theme.background.copy(alpha = 1f)
    } else {
        theme.glassFill
    }
    val rowModifier = modifier
        .fillMaxWidth()
        .clip(cardShape)
        .then(if (clickable) Modifier.clickable(onClick = onClick) else Modifier)
        .background(backgroundColor)
        .then(
            if (opaqueBackground) {
                Modifier
            } else {
                Modifier.border(1.dp, theme.glassBorder, cardShape)
            },
        )
        .padding(12.dp)

    Row(
        modifier = rowModifier,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Box(
            modifier = Modifier
                .width(96.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(LumenThumbCorner)),
        ) {
            if (thumbnailContent != null) {
                thumbnailContent()
            } else if (thumbnailUri != null) {
                MediaThumbnail(
                    thumbnailUri = thumbnailUri,
                    dateModifiedSeconds = thumbnailDateModified,
                    modifier = Modifier.fillMaxSize(),
                    deferLoad = deferThumbnailLoad,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.surface),
                )
            }
            if (progress != null && progress > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.4f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress.coerceIn(0f, 1f))
                        .height(3.dp)
                        .background(theme.accent),
                )
            }
        }
        Column(
            modifier = Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = theme.onSurfaceMuted,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
        trailingContent?.invoke()
    }
}

@Composable
fun MediaThumbnail(
    thumbnailUri: Uri,
    modifier: Modifier = Modifier,
    dateModifiedSeconds: Long = 0L,
    deferLoad: Boolean = false,
) {
    val context = LocalContext.current
    var thumbnail by remember(thumbnailUri, dateModifiedSeconds) {
        mutableStateOf(VideoThumbnailCache.peek(thumbnailUri, dateModifiedSeconds))
    }

    LaunchedEffect(thumbnailUri, dateModifiedSeconds, deferLoad) {
        if (deferLoad) return@LaunchedEffect
        val platform = VideoThumbnailCache.load(
            context = context,
            uri = thumbnailUri,
            dateModifiedSeconds = dateModifiedSeconds,
            allowFrameFallback = false,
        )
        if (platform != null) {
            thumbnail = platform
            return@LaunchedEffect
        }
        delay(VideoThumbnailCache.FRAME_FALLBACK_DELAY_MS)
        val framed = VideoThumbnailCache.load(
            context = context,
            uri = thumbnailUri,
            dateModifiedSeconds = dateModifiedSeconds,
            allowFrameFallback = true,
        )
        if (framed != null) {
            thumbnail = framed
        }
    }

    val theme = LocalLumenTheme.current
    Box(
        modifier = modifier.background(theme.surface),
        contentAlignment = Alignment.Center,
    ) {
        Crossfade(
            targetState = thumbnail,
            animationSpec = tween(durationMillis = 180),
            label = "media-thumbnail",
        ) { bitmap: ImageBitmap? ->
            if (bitmap != null) {
                Image(
                    bitmap = bitmap,
                    contentDescription = null,
                    modifier = Modifier.fillMaxSize(),
                    contentScale = ContentScale.Crop,
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(theme.surface),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(
                        imageVector = Icons.Filled.Movie,
                        contentDescription = null,
                        tint = theme.onSurfaceMuted.copy(alpha = 0.35f),
                        modifier = Modifier.size(28.dp),
                    )
                }
            }
        }
    }
}
