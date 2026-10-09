package com.zhaoshuo.localvideoplayer.ui.components

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.platform.LocalContext
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoshuo.localvideoplayer.cast.LocalCastManager
import com.zhaoshuo.localvideoplayer.player.PlayerProgressState
import com.zhaoshuo.localvideoplayer.ui.PlayerUiState
import com.zhaoshuo.localvideoplayer.util.SeekPreviewFrames
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.isActive

@Composable
fun LivePlayerProgressBar(
    progress: PlayerProgressState,
    ui: PlayerUiState,
    isCasting: Boolean,
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
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
) {
    val castPositionMs = rememberCastPositionMs()
    val positionMs = playbackDisplayPositionMs(
        progress = progress,
        ui = ui,
        isCasting = isCasting,
        castPositionMs = castPositionMs,
    )
    PlayerProgressBar(
        currentPositionMs = positionMs,
        bufferedPositionMs = progress.bufferedPositionMs,
        durationMs = durationMs,
        onSeek = onSeek,
        modifier = modifier,
        showFullscreen = showFullscreen,
        isFullscreen = isFullscreen,
        onFullscreenClick = onFullscreenClick,
        compact = compact,
        introEndMs = introEndMs,
        outroStartMs = outroStartMs,
        canEditSeriesSkip = canEditSeriesSkip,
        introMarked = introMarked,
        outroMarked = outroMarked,
        onMarkIntro = onMarkIntro,
        onMarkOutro = onMarkOutro,
        onClearIntro = onClearIntro,
        onClearOutro = onClearOutro,
        onScrubStart = onScrubStart,
        onScrubEnd = onScrubEnd,
    )
}

@Composable
fun LiveSeekPreview(
    ui: PlayerUiState,
    durationMs: Long,
    videoUri: Uri? = null,
    forDoubleTap: Boolean = false,
) {
    val context = LocalContext.current
    val seeking = if (forDoubleTap) ui.showDoubleTapSeek else ui.isSeeking
    var previewImage by remember { mutableStateOf<ImageBitmap?>(null) }
    LaunchedEffect(seeking, videoUri, forDoubleTap) {
        if (forDoubleTap || !seeking || videoUri == null) {
            previewImage = null
            return@LaunchedEffect
        }
        try {
            while (isActive && ui.isSeeking) {
                val target = ui.previewPositionMs
                val image = SeekPreviewFrames.load(context, videoUri, target)
                if (!isActive || !ui.isSeeking) return@LaunchedEffect
                if (ui.previewPositionMs != target) continue
                previewImage = image
                while (isActive && ui.isSeeking && ui.previewPositionMs == target) {
                    delay(50)
                }
            }
        } finally {
            previewImage = null
            SeekPreviewFrames.release()
        }
    }
    SeekPreviewOverlay(
        visible = seeking,
        previewPositionMs = ui.previewPositionMs,
        durationMs = durationMs,
        seekStartPositionMs = ui.seekStartPositionMs,
        previewImage = previewImage,
    )
}

@Composable
fun LiveSkipIntroOutro(
    progress: PlayerProgressState,
    ui: PlayerUiState,
    isCasting: Boolean,
    introEndMs: Long,
    outroStartMs: Long,
    durationMs: Long,
    onSkipIntro: () -> Unit,
    onSkipOutro: () -> Unit,
) {
    val castPositionMs = rememberCastPositionMs()
    if (isCasting) return
    val positionMs = if (ui.controlsVisible || ui.isSeeking) {
        playbackDisplayPositionMs(
            progress = progress,
            ui = ui,
            isCasting = false,
            castPositionMs = castPositionMs,
        )
    } else {
        ui.hiddenPlaybackPositionMs
    }
    val showSkipIntro = introEndMs > 0L && !ui.isSeeking && positionMs in 0L..introEndMs
    val showSkipOutro = outroStartMs >= 0L &&
        !ui.isSeeking &&
        durationMs > 0L &&
        positionMs >= outroStartMs
    SkipIntroOutroOverlay(
        showSkipIntro = showSkipIntro,
        showSkipOutro = showSkipOutro,
        onSkipIntro = onSkipIntro,
        onSkipOutro = onSkipOutro,
    )
}

@Composable
private fun rememberCastPositionMs(): Long {
    val castManager = LocalCastManager.current
    val positionFlow = remember(castManager) {
        castManager?.playback?.positionMs ?: MutableStateFlow(0L)
    }
    val positionMs by positionFlow.collectAsStateWithLifecycle()
    return positionMs
}

private fun playbackDisplayPositionMs(
    progress: PlayerProgressState,
    ui: PlayerUiState,
    isCasting: Boolean,
    castPositionMs: Long,
): Long {
    return when {
        ui.isSeeking -> ui.previewPositionMs
        isCasting -> castPositionMs
        else -> progress.currentPositionMs
    }
}
