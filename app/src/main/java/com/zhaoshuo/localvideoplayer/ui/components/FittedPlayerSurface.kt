package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.CueGroup
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.VideoSize
import kotlin.math.roundToInt

@UnstableApi
@Composable
fun FittedPlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
) {
    val presentationState = rememberPresentationState(player)
    var cueGroup by remember { mutableStateOf<List<androidx.media3.common.text.Cue>>(emptyList()) }

    DisposableEffect(player) {
        val listener = object : Player.Listener {
            override fun onCues(cues: CueGroup) {
                cueGroup = cues.cues
            }
        }
        player.addListener(listener)
        onDispose { player.removeListener(listener) }
    }

    Box(modifier = modifier) {
        PlayerSurface(
            player = player,
            modifier = Modifier
                .fillMaxSize()
                .resizeWithContentScale(
                    contentScale = contentScale,
                    sourceSizeDp = presentationState.videoSizeDp,
                )
                .align(Alignment.Center),
        )
        AndroidView(
            factory = { context ->
                SubtitleView(context).apply {
                    setUserDefaultStyle()
                    setUserDefaultTextSize()
                }
            },
            update = { view ->
                view.setCues(cueGroup)
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter),
        )
    }
}

fun Player.displayVideoSize(): Pair<Int, Int>? {
    val size: VideoSize = videoSize
    if (size.width <= 0 || size.height <= 0) return null
    val par = size.pixelWidthHeightRatio
    val width = if (par < 1f) (size.width * par).roundToInt() else size.width
    val height = if (par > 1f) (size.height / par).roundToInt() else size.height
    return width to height
}
