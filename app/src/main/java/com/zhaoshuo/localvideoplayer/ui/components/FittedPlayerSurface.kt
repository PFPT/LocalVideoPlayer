package com.zhaoshuo.localvideoplayer.ui.components

import android.graphics.Color
import android.graphics.Typeface
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.media3.common.Player
import androidx.media3.common.text.Cue
import androidx.media3.common.util.UnstableApi
import androidx.media3.ui.CaptionStyleCompat
import androidx.media3.ui.SubtitleView
import androidx.media3.ui.compose.PlayerSurface
import androidx.media3.ui.compose.SURFACE_TYPE_TEXTURE_VIEW
import androidx.media3.ui.compose.modifiers.resizeWithContentScale
import androidx.media3.ui.compose.state.rememberPresentationState
import androidx.compose.ui.layout.ContentScale
import androidx.media3.common.VideoSize
import kotlin.math.roundToInt

data class SubtitleAppearance(
    val textScale: Float = 1f,
    val yellow: Boolean = false,
    val delayMs: Int = 0,
)

val LocalSubtitleAppearance = staticCompositionLocalOf { SubtitleAppearance() }

@UnstableApi
@Composable
fun FittedPlayerSurface(
    player: Player,
    modifier: Modifier = Modifier,
    contentScale: ContentScale = ContentScale.Fit,
    frameAspectRatio: Float? = null,
) {
    val presentationState = rememberPresentationState(player)
    val appearance = LocalSubtitleAppearance.current

    BoxWithConstraints(modifier = modifier, contentAlignment = Alignment.Center) {
        val surfaceModifier = if (frameAspectRatio != null) {
            val viewRatio = if (maxHeight > 0.dp) {
                maxWidth / maxHeight
            } else {
                frameAspectRatio
            }
            if (viewRatio > frameAspectRatio) {
                Modifier.fillMaxHeight().aspectRatio(frameAspectRatio)
            } else {
                Modifier.fillMaxWidth().aspectRatio(frameAspectRatio)
            }
        } else {
            Modifier
                .fillMaxSize()
                .resizeWithContentScale(
                    contentScale = contentScale,
                    sourceSizeDp = presentationState.videoSizeDp,
                )
        }
        PlayerSurface(
            player = player,
            surfaceType = SURFACE_TYPE_TEXTURE_VIEW,
            modifier = surfaceModifier,
        )
        AndroidView(
            factory = { context ->
                SubtitleView(context).also { view -> view.tag = CueSubtitleBinder(view) }
            },
            update = { view ->
                (view.tag as CueSubtitleBinder).bind(player, appearance)
            },
            onRelease = { view ->
                (view.tag as CueSubtitleBinder).unbind()
            },
            modifier = Modifier
                .fillMaxSize()
                .align(Alignment.BottomCenter),
        )
    }
}

private class CueSubtitleBinder(private val view: SubtitleView) {
    private var boundPlayer: Player? = null
    private var listener: Player.Listener? = null
    private var pending: Runnable? = null
    private var delayMs: Int = 0
    private var appliedAppearance: SubtitleAppearance? = null

    fun bind(player: Player, appearance: SubtitleAppearance) {
        delayMs = appearance.delayMs
        if (appliedAppearance != appearance) {
            applyAppearance(appearance)
            appliedAppearance = appearance
        }
        if (boundPlayer === player) return
        unbind()
        val cueListener = object : Player.Listener {
            override fun onCues(cueGroup: androidx.media3.common.text.CueGroup) {
                showCues(cueGroup.cues)
            }
        }
        player.addListener(cueListener)
        boundPlayer = player
        listener = cueListener
    }

    fun unbind() {
        pending?.let { view.removeCallbacks(it) }
        pending = null
        val player = boundPlayer
        val cueListener = listener
        if (player != null && cueListener != null) {
            player.removeListener(cueListener)
        }
        boundPlayer = null
        listener = null
    }

    private fun showCues(cues: List<Cue>) {
        pending?.let { view.removeCallbacks(it) }
        pending = null
        val delay = delayMs
        if (delay > 0) {
            val task = Runnable { view.setCues(cues) }
            pending = task
            view.postDelayed(task, delay.toLong())
        } else {
            view.setCues(cues)
        }
    }

    private fun applyAppearance(appearance: SubtitleAppearance) {
        val fraction = (0.0533f * appearance.textScale).coerceIn(0.03f, 0.12f)
        view.setFractionalTextSize(fraction)
        val foreground = if (appearance.yellow) Color.YELLOW else Color.WHITE
        view.setStyle(
            CaptionStyleCompat(
                foreground,
                Color.TRANSPARENT,
                Color.TRANSPARENT,
                CaptionStyleCompat.EDGE_TYPE_OUTLINE,
                Color.BLACK,
                Typeface.DEFAULT,
            ),
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
