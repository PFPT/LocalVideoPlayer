package com.pfpt.localvideoplayer.ui.player

import android.content.pm.ActivityInfo
import android.content.res.Configuration
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.remember
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.LifecycleOwner
import androidx.media3.common.ColorInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.PlaybackParameters
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.MainActivity
import com.pfpt.localvideoplayer.appDurationResolver
import com.pfpt.localvideoplayer.cast.CastManager
import com.pfpt.localvideoplayer.data.PlayerPreferences
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.player.PlaybackSession
import com.pfpt.localvideoplayer.player.PlayerSession
import com.pfpt.localvideoplayer.player.PlayerViewModel
import com.pfpt.localvideoplayer.player.PlayerProgressState
import com.pfpt.localvideoplayer.player.abLoopTarget
import com.pfpt.localvideoplayer.player.isSoftwareDecodeRetryable
import com.pfpt.localvideoplayer.player.isSourceReadFailure
import com.pfpt.localvideoplayer.player.showsPauseIcon
import com.pfpt.localvideoplayer.ui.PlaybackFailure
import com.pfpt.localvideoplayer.ui.PlayerUiState
import com.pfpt.localvideoplayer.ui.components.displayVideoSize
import com.pfpt.localvideoplayer.util.OrientationController
import com.pfpt.localvideoplayer.util.PlaybackSystemControls
import com.pfpt.localvideoplayer.util.SeekPreviewFrames
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.ui.platform.LocalContext

@Composable
internal fun PlayerSessionEffects(
    exoPlayer: ExoPlayer,
    playlist: List<VideoItem>,
    ui: PlayerUiState,
    progress: PlayerProgressState,
    activity: MainActivity?,
    playbackSession: PlaybackSession,
    playerViewModel: PlayerViewModel,
    lifecycleOwner: LifecycleOwner,
    orientationController: OrientationController?,
    systemControls: PlaybackSystemControls?,
    configuration: Configuration,
    castManager: CastManager?,
    preferences: PlayerPreferences,
    displayDurationMs: Long,
    onDurationKnown: (uri: String, durationMs: Long) -> Unit,
    castingOnLeave: MutableState<Boolean>,
    scope: CoroutineScope,
    saveCurrentProgress: suspend () -> Unit,
    onPlayAdjacentEpisode: (Int) -> Unit,
    restoreSystemBars: () -> Unit,
    updateFullscreenSystemBars: () -> Unit,
    currentPipAspectRatio: () -> android.util.Rational,
) {
    val context = LocalContext.current

    fun updateVideoFormat() {
        val size = exoPlayer.displayVideoSize()
        if (size != null) {
            ui.videoWidth = size.first
            ui.videoHeight = size.second
        }
        val window = activity?.window ?: return
        val hdr = ColorInfo.isTransferHdr(exoPlayer.videoFormat?.colorInfo)
        window.colorMode = if (hdr) {
            ActivityInfo.COLOR_MODE_HDR
        } else {
            ActivityInfo.COLOR_MODE_DEFAULT
        }
    }

    fun handlePlayerError(error: PlaybackException) {
        val canRetry = error.isSoftwareDecodeRetryable() &&
            !ui.appliedSoftDecode &&
            !ui.softDecodeRetried
        if (canRetry) {
            ui.softDecodeRetried = true
            ui.softDecodeRetryPending = true
            ui.playbackFailure = null
            ui.replayPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            playbackSession.resumedIndices.remove(ui.currentIndex)
            scope.launch {
                runCatching { saveCurrentProgress() }
                ui.appliedSoftDecode = true
                ui.softDecodeGeneration += 1
            }
            return
        }
        if (ui.softDecodeRetryPending) return
        ui.playbackFailure = if (error.isSourceReadFailure()) {
            PlaybackFailure.Read
        } else {
            PlaybackFailure.Decode
        }
    }

    DisposableEffect(activity) {
        val pipListener: (Boolean) -> Unit = { inPip ->
            ui.isInPipMode = inPip
            if (inPip) {
                ui.controlsVisible = false
            } else {
                ui.resetZoom()
                ui.controlsVisible = true
                updateFullscreenSystemBars()
            }
        }
        val pipActionListener: () -> Unit = {
            progress.syncFromPlayer(exoPlayer)
            ui.currentIndex = exoPlayer.currentMediaItemIndex
            ui.isPlaying = showsPauseIcon(exoPlayer.playWhenReady, exoPlayer.playbackState)
            updateVideoFormat()
            if (activity?.isInPipMode == true) {
                activity.updatePipActions(
                    hasNext = exoPlayer.currentMediaItemIndex < playlist.lastIndex,
                    isPlaying = ui.isPlaying,
                    aspectRatio = currentPipAspectRatio(),
                )
            }
        }
        activity?.onPipModeChanged = pipListener
        activity?.onPipAction = pipActionListener
        onDispose {
            if (activity?.onPipModeChanged === pipListener) {
                activity.onPipModeChanged = null
            }
            if (activity?.onPipAction === pipActionListener) {
                activity.onPipAction = null
            }
        }
    }

    DisposableEffect(orientationController) {
        orientationController?.unlockForPlayer()
        onDispose {
            orientationController?.lockPortrait()
            systemControls?.restoreBrightness()
        }
    }

    LaunchedEffect(configuration.orientation) {
        ui.isLandscape = orientationController?.isLandscape() == true
    }

    LaunchedEffect(exoPlayer) {
        ui.currentIndex = exoPlayer.currentMediaItemIndex
        progress.syncFromPlayer(exoPlayer)
    }

    LaunchedEffect(exoPlayer, ui.abPointA, ui.abPointB, ui.currentIndex) {
        val pointB = ui.abPointB ?: return@LaunchedEffect
        val loopTo = abLoopTarget(ui.abPointA, pointB, pointB) ?: return@LaunchedEffect
        val message = exoPlayer.createMessage { _, _ ->
            val target = abLoopTarget(ui.abPointA, ui.abPointB, ui.abPointB ?: Long.MIN_VALUE)
            if (target != null) {
                exoPlayer.seekTo(ui.currentIndex, target)
            }
        }
            .setPosition(ui.currentIndex, pointB)
            .setDeleteAfterDelivery(false)
            .send()
        try {
            awaitCancellation()
        } finally {
            message.cancel()
        }
    }

    DisposableEffect(exoPlayer) {
        playbackSession.onEnterScreen()
        PlayerSession.attach(exoPlayer)
        PlayerSession.onPlayNext = { onPlayAdjacentEpisode(1) }
        PlayerSession.onStateChanged = {
            activity?.onPipAction?.invoke()
        }

        val listener = object : Player.Listener {
            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                ui.isPlaying = showsPauseIcon(playWhenReady, exoPlayer.playbackState)
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                ui.isPlaying = showsPauseIcon(exoPlayer.playWhenReady, playbackState)
                if (playbackState == Player.STATE_READY) {
                    ui.playbackFailure = null
                    ui.softDecodeRetryPending = false
                    progress.syncFromPlayer(exoPlayer)
                    updateVideoFormat()
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlayerError(error)
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                val previousIndex = ui.currentIndex
                val index = exoPlayer.currentMediaItemIndex
                if (index != previousIndex) {
                    ui.abPointA = null
                    ui.abPointB = null
                    SeekPreviewFrames.release()
                }
                ui.currentIndex = index
                ui.playbackFailure = null
                ui.hiddenPlaybackPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                progress.resetForNewItem()
                val knownDuration = playlist.getOrNull(index)?.durationMs ?: 0L
                if (knownDuration > 0L) {
                    progress.durationMs = knownDuration
                }
                progress.syncFromPlayer(exoPlayer)
                ui.resetZoom()
                updateVideoFormat()
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                updateVideoFormat()
            }
        }
        ui.softDecodeRetryPending = false
        ui.isPlaying = showsPauseIcon(exoPlayer.playWhenReady, exoPlayer.playbackState)
        exoPlayer.addListener(listener)
        exoPlayer.playerError?.let(::handlePlayerError)
        progress.syncFromPlayer(exoPlayer)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_STOP -> {
                    if (activity?.isInPipMode != true && activity?.isFinishing == true) {
                        exoPlayer.pause()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            SeekPreviewFrames.release()
            ui.brightnessHideJob?.cancel()
            ui.volumeHideJob?.cancel()
            castManager?.bindLocalPlayer(null)
            val stillOwned = playbackSession.player() === exoPlayer
            if (stillOwned) {
                val video = playlist.getOrNull(ui.currentIndex)
                val progressSnapshot = runCatching {
                    if (video == null) return@runCatching null
                    val duration = exoPlayer.duration.takeIf { it > 0 } ?: video.durationMs
                    if (duration <= 0) return@runCatching null
                    val position = exoPlayer.currentPosition.coerceAtLeast(0L)
                    Triple(video, position, duration)
                }.getOrNull()
                PlayerSession.onStateChanged = null
                PlayerSession.onPlayNext = null
                exoPlayer.removeListener(listener)
                if (ui.isSpeedBoosted) {
                    exoPlayer.setPlaybackSpeed(ui.savedPlaybackSpeed)
                }
                playbackSession.onLeaveScreen(
                    pauseLocal = castingOnLeave.value,
                    save = progressSnapshot == null,
                )
                progressSnapshot?.let { (snapshotVideo, positionMs, durationMs) ->
                    playerViewModel.enqueueSaveProgress(snapshotVideo, positionMs, durationMs)
                }
            }
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            ui.isFullscreen = false
            activity?.window?.colorMode = ActivityInfo.COLOR_MODE_DEFAULT
            restoreSystemBars()
            ui.resetSpeedBoost()
            orientationController?.lockPortrait()
        }
    }

    LaunchedEffect(ui.showDoubleTapSeek) {
        if (ui.showDoubleTapSeek) {
            delay(800)
            ui.showDoubleTapSeek = false
        }
    }

    LaunchedEffect(ui.playbackSpeed) {
        exoPlayer.playbackParameters = PlaybackParameters(ui.playbackSpeed, 1f)
    }

    LaunchedEffect(ui.repeatOne) {
        exoPlayer.repeatMode = if (ui.repeatOne) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(preferences.shuffleEnabled) {
        exoPlayer.shuffleModeEnabled = preferences.shuffleEnabled
    }

    val currentVideo = playlist.getOrNull(ui.currentIndex)
    val durationResolver = remember(context) { context.appDurationResolver() }
    LaunchedEffect(currentVideo?.uri) {
        val video = currentVideo ?: return@LaunchedEffect
        if (video.durationMs > 0L || progress.durationMs > 0L) return@LaunchedEffect
        val duration = withContext(Dispatchers.IO) {
            durationResolver.resolveAll(listOf(video))[video.uri.toString()] ?: 0L
        }
        if (duration > 0L && progress.durationMs <= 0L) {
            progress.durationMs = duration
            onDurationKnown(video.uri.toString(), duration)
        }
    }
    LaunchedEffect(displayDurationMs, currentVideo?.uri) {
        val video = currentVideo ?: return@LaunchedEffect
        if (displayDurationMs <= 0L || video.durationMs > 0L) return@LaunchedEffect
        onDurationKnown(video.uri.toString(), displayDurationMs)
    }
}
