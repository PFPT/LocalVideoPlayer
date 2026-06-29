package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun FittedPlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val presentationState = rememberPresentationState(player)
    PlayerSurface(
        player = player,
        modifier = modifier.resizeWithContentScale(
            contentScale = contentScale,
            sourceSizeDp = presentationState.videoSizeDp,
        ),
    )
}

fun Player.displayVideoSize(): Pair<Int, Int>? {
    val size: VideoSize = videoSize
    if (size.width <= 0 || size.height <= 0) return null
    val par = size.pixelWidthHeightRatio
    val width = if (par < 1f) (size.width * par).roundToInt() else size.width
    val height = if (par > 1f) (size.height / par).roundToInt() else size.height
    return width to height
}
