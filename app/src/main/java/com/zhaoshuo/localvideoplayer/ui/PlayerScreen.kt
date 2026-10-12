package com.zhaoshuo.localvideoplayer.ui

import android.app.Activity
import android.content.Context
import android.content.pm.ActivityInfo
import android.media.AudioManager
import android.net.Uri
import android.util.Rational
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.common.C
import androidx.media3.common.ColorInfo
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.exoplayer.ExoPlayer
import com.zhaoshuo.localvideoplayer.MainActivity
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.appDurationResolver
import com.zhaoshuo.localvideoplayer.data.BookmarkStore
import com.zhaoshuo.localvideoplayer.data.PlaybackStore
import com.zhaoshuo.localvideoplayer.data.PinnedItemsStore
import com.zhaoshuo.localvideoplayer.data.PlayerPreferences
import com.zhaoshuo.localvideoplayer.data.PlayerPreferencesStore
import androidx.media3.common.PlaybackParameters
import kotlin.math.max
import com.zhaoshuo.localvideoplayer.data.SeriesSkipSettings
import com.zhaoshuo.localvideoplayer.data.SeriesSkipStore
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.player.PlaybackSession
import com.zhaoshuo.localvideoplayer.player.PlayerViewModel
import com.zhaoshuo.localvideoplayer.player.PlayerProgressState
import com.zhaoshuo.localvideoplayer.player.PlayerSession
import com.zhaoshuo.localvideoplayer.player.PlaylistState
import com.zhaoshuo.localvideoplayer.player.applyTrackSelection
import com.zhaoshuo.localvideoplayer.player.abLoopTarget
import com.zhaoshuo.localvideoplayer.player.episodeStartPositionMs
import com.zhaoshuo.localvideoplayer.player.seekTargetMs
import com.zhaoshuo.localvideoplayer.player.showsPauseIcon
import com.zhaoshuo.localvideoplayer.player.isSoftwareDecodeRetryable
import com.zhaoshuo.localvideoplayer.player.isSourceReadFailure
import com.zhaoshuo.localvideoplayer.player.rememberPlayerProgressState
import com.zhaoshuo.localvideoplayer.player.trackOptions
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.zhaoshuo.localvideoplayer.ui.components.AspectRatioSheet
import com.zhaoshuo.localvideoplayer.ui.components.BrightnessFeedbackOverlay
import com.zhaoshuo.localvideoplayer.ui.components.EpisodeListBottomSheet
import com.zhaoshuo.localvideoplayer.ui.components.FittedPlayerSurface
import com.zhaoshuo.localvideoplayer.ui.components.LiveSeekPreview
import com.zhaoshuo.localvideoplayer.ui.components.LiveSkipIntroOutro
import com.zhaoshuo.localvideoplayer.ui.components.LocalSubtitleAppearance
import com.zhaoshuo.localvideoplayer.ui.components.LocalTimelineMarks
import com.zhaoshuo.localvideoplayer.ui.components.SubtitleAppearance
import com.zhaoshuo.localvideoplayer.ui.components.TimelineMarks
import com.zhaoshuo.localvideoplayer.ui.components.LandscapePlayerLayout
import com.zhaoshuo.localvideoplayer.ui.components.PlayerMoreSheet
import com.zhaoshuo.localvideoplayer.ui.components.PortraitPlayerLayout
import com.zhaoshuo.localvideoplayer.ui.components.SpeedBoostOverlay
import com.zhaoshuo.localvideoplayer.ui.components.SpeedSelectorSheet
import com.zhaoshuo.localvideoplayer.ui.components.TrackSelectorSheet
import com.zhaoshuo.localvideoplayer.ui.components.VideoInfoDialog
import com.zhaoshuo.localvideoplayer.ui.components.VideoPlayerGesture
import com.zhaoshuo.localvideoplayer.ui.components.VolumeFeedbackOverlay
import com.zhaoshuo.localvideoplayer.ui.components.displayVideoSize
import com.zhaoshuo.localvideoplayer.util.OrientationController
import com.zhaoshuo.localvideoplayer.util.PlaybackSystemControls
import com.zhaoshuo.localvideoplayer.util.PictureInPictureHelper
import com.zhaoshuo.localvideoplayer.util.TitleParser
import com.zhaoshuo.localvideoplayer.util.SeekPreviewFrames
import com.zhaoshuo.localvideoplayer.util.VideoFrameCapture
import com.zhaoshuo.localvideoplayer.util.toMediaItem
import com.zhaoshuo.localvideoplayer.cast.CastUiState
import com.zhaoshuo.localvideoplayer.cast.LocalCastManager
import com.zhaoshuo.localvideoplayer.ui.components.CastButton
import com.zhaoshuo.localvideoplayer.ui.components.CastDeviceBottomSheet
import com.zhaoshuo.localvideoplayer.ui.components.CastMiniController
import com.zhaoshuo.localvideoplayer.ui.components.rememberCastSheetActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val MIN_SPEED_BOOST = 2.0f
private const val DOUBLE_TAP_SEEK_MS = 10_000L

@Composable
fun PlayerScreen(
    playlistState: PlaylistState,
    playbackStore: PlaybackStore,
    playerPreferencesStore: PlayerPreferencesStore,
    pinnedItemsStore: PinnedItemsStore,
    seriesSkipStore: SeriesSkipStore,
    bookmarkStore: BookmarkStore,
    playbackSession: PlaybackSession,
    onDurationKnown: (uri: String, durationMs: Long) -> Unit = { _, _ -> },
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val playerViewModel: PlayerViewModel = viewModel(
        factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return PlayerViewModel(playbackStore) as T
            }
        },
    )
    val activity = context as? MainActivity
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val playlist = playlistState.items
    val preferences by playerPreferencesStore.preferences.collectAsStateWithLifecycle(initialValue = PlayerPreferences())
    val pinnedKeys by pinnedItemsStore.pinnedKeys.collectAsStateWithLifecycle(initialValue = emptySet())
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val orientationController = remember(context) {
        (context as? Activity)?.let { OrientationController(it) }
    }
    val systemControls = remember(activity, audioManager) {
        (activity as? Activity)?.let { PlaybackSystemControls(it, audioManager) }
    }

    val ui = remember {
        PlayerUiState(
            initialIndex = playlistState.currentIndex,
            initialSoftDecode = preferences.softDecode,
            initialSpeed = preferences.defaultSpeed,
            initialAspect = preferences.aspectRatioMode,
            initialRepeatOne = preferences.repeatOne,
        ).also {
            it.isLandscape = orientationController?.isLandscape() == true
            it.isInPipMode = activity?.isInPipMode == true
        }
    }
    val pipSupported = remember(activity) { activity?.isPipSupported() == true }
    val exoPlayer by produceState<ExoPlayer?>(
        initialValue = null,
        playlist,
        ui.softDecodeGeneration,
    ) {
        if (playlist.isEmpty()) {
            value = null
            return@produceState
        }
        val playIndex = ui.currentIndex.coerceIn(0, playlist.lastIndex)
        val replay = ui.replayPositionMs
        ui.replayPositionMs = null
        value = playbackSession.open(
            items = playlist,
            startIndex = playIndex,
            requestedSoftDecode = ui.appliedSoftDecode,
            startPositionMs = replay,
        )
    }
    val player = exoPlayer
    if (player == null) {
        Box(
            modifier = modifier
                .fillMaxSize()
                .background(Color.Black),
        )
    } else {
        PlayerScreenContent(
            exoPlayer = player,
            playlist = playlist,
            playbackStore = playbackStore,
            playerPreferencesStore = playerPreferencesStore,
            pinnedItemsStore = pinnedItemsStore,
            seriesSkipStore = seriesSkipStore,
            bookmarkStore = bookmarkStore,
            playbackSession = playbackSession,
            onDurationKnown = onDurationKnown,
            onBack = onBack,
            modifier = modifier,
            preferences = preferences,
            pinnedKeys = pinnedKeys,
            ui = ui,
            pipSupported = pipSupported,
            playerViewModel = playerViewModel,
            activity = activity,
            view = view,
            lifecycleOwner = lifecycleOwner,
            configuration = configuration,
            audioManager = audioManager,
            orientationController = orientationController,
            systemControls = systemControls,
        )
    }
}

@Composable
private fun PlayerScreenContent(
    exoPlayer: ExoPlayer,
    playlist: List<VideoItem>,
    playbackStore: PlaybackStore,
    playerPreferencesStore: PlayerPreferencesStore,
    pinnedItemsStore: PinnedItemsStore,
    seriesSkipStore: SeriesSkipStore,
    bookmarkStore: BookmarkStore,
    playbackSession: PlaybackSession,
    onDurationKnown: (uri: String, durationMs: Long) -> Unit,
    onBack: () -> Unit,
    modifier: Modifier,
    preferences: PlayerPreferences,
    pinnedKeys: Set<String>,
    ui: PlayerUiState,
    pipSupported: Boolean,
    playerViewModel: PlayerViewModel,
    activity: MainActivity?,
    view: android.view.View,
    lifecycleOwner: androidx.lifecycle.LifecycleOwner,
    configuration: android.content.res.Configuration,
    audioManager: AudioManager,
    orientationController: OrientationController?,
    systemControls: PlaybackSystemControls?,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val bookmarkUri = playlist.getOrNull(ui.currentIndex)?.uri
    val bookmarkFlow = remember(bookmarkUri) {
        bookmarkUri?.let { bookmarkStore.bookmarks(it) } ?: flowOf(emptyList())
    }
    val bookmarks by bookmarkFlow.collectAsStateWithLifecycle(initialValue = emptyList())
    val castingOnLeave = remember { mutableStateOf(false) }
    val progress = rememberPlayerProgressState(
        player = exoPlayer,
        isSeeking = ui.isSeeking,
        controlsVisible = ui.controlsVisible,
    )
    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            val index = exoPlayer.currentMediaItemIndex
            val video = playlist.getOrNull(index) ?: return@rememberLauncherForActivityResult
            playbackSession.bindManualSubtitle(video.uri, uri)
            scope.launch { playbackSession.attachSubtitle(index) }
        }
    }
    LaunchedEffect(preferences.defaultSpeed, preferences.aspectRatioMode, preferences.repeatOne) {
        if (!ui.isSpeedBoosted) {
            ui.playbackSpeed = preferences.defaultSpeed
            ui.savedPlaybackSpeed = preferences.defaultSpeed
        }
        ui.aspectRatioMode = preferences.aspectRatioMode
        ui.repeatOne = preferences.repeatOne
    }
    val castManager = LocalCastManager.current
    val idleCastState = remember { MutableStateFlow<CastUiState>(CastUiState.Idle) }
    val idleFalse = remember { MutableStateFlow(false) }
    val idleZero = remember { MutableStateFlow(0L) }
    val castState by (castManager?.uiState ?: idleCastState).collectAsStateWithLifecycle()
    val isCasting by (castManager?.playback?.isCasting ?: idleFalse).collectAsStateWithLifecycle()
    val castDurationMs by (castManager?.playback?.durationMs ?: idleZero).collectAsStateWithLifecycle()
    val castPlaying by (castManager?.playback?.isPlaying ?: idleFalse).collectAsStateWithLifecycle()
    castingOnLeave.value = isCasting
    val castSheet = rememberCastSheetActions(castManager, exoPlayer)
    LaunchedEffect(exoPlayer, castManager) {
        castManager?.bindLocalPlayer(exoPlayer)
    }
    fun resolvedDurationMs(): Long {
        val live = progress.durationMs
        if (live > 0) return live
        return playlist.getOrNull(ui.currentIndex)?.durationMs ?: 0L
    }
    val displayDurationMs = if (isCasting && castDurationMs > 0) {
        castDurationMs
    } else {
        resolvedDurationMs()
    }
    LaunchedEffect(displayDurationMs, isCasting) {
        if (isCasting && displayDurationMs > 0) {
            progress.durationMs = displayDurationMs
        }
    }


    suspend fun saveCurrentProgress() {
        val video = playlist.getOrNull(ui.currentIndex) ?: return
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: video.durationMs
        if (duration <= 0) return
        playerViewModel.saveProgress(
            video = video,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
        )
    }

    suspend fun openEpisode(index: Int) {
        val video = playlist.getOrNull(index) ?: return
        val startMs = episodeStartPositionMs(
            resumePositionMs = playbackStore.getResumePositionMs(video.uri, video.durationMs),
            replayPositionMs = ui.replayPositionMs,
        )
        ui.replayPositionMs = null
        playbackSession.attachSubtitle(index)
        playbackSession.resumedIndices.add(index)
        if (startMs > 0L || exoPlayer.currentMediaItemIndex != index) {
            exoPlayer.seekTo(index, startMs)
        }
        progress.currentPositionMs = startMs
        ui.previewPositionMs = startMs
        ui.currentIndex = index
    }

    fun selectEpisode(index: Int) {
        if (index !in playlist.indices) return
        scope.launch {
            saveCurrentProgress()
            playbackSession.resumedIndices.remove(index)
            openEpisode(index)
        }
    }

    fun playAdjacentEpisode(delta: Int, markCurrentComplete: Boolean = false) {
        val target = ui.currentIndex + delta
        if (target !in playlist.indices) return
        scope.launch {
            if (markCurrentComplete) {
                val video = playlist.getOrNull(ui.currentIndex)
                val duration = exoPlayer.duration.takeIf { it > 0 } ?: video?.durationMs ?: 0L
                if (video != null && duration > 0) {
                    playerViewModel.saveProgress(
                        video = video,
                        positionMs = duration,
                        durationMs = duration,
                    )
                }
            } else {
                saveCurrentProgress()
            }
            playbackSession.resumedIndices.remove(target)
            openEpisode(target)
        }
    }


    fun handleBrightnessGestureStart() {
        val controls = systemControls ?: return
        ui.brightnessHideJob?.cancel()
        ui.brightnessHideJob = null
        ui.brightnessFraction = controls.currentBrightness()
        ui.isAdjustingBrightness = true
        ui.resetControlsTimer()
    }

    fun handleBrightnessChange(fraction: Float) {
        val controls = systemControls ?: return
        ui.brightnessFraction = fraction.coerceIn(0.01f, 1f)
        controls.setBrightness(ui.brightnessFraction)
    }

    fun handleBrightnessGestureEnd() {
        ui.brightnessHideJob?.cancel()
        ui.brightnessHideJob = scope.launch {
            delay(1_500)
            ui.isAdjustingBrightness = false
            ui.brightnessHideJob = null
        }
    }

    fun handleVolumeGestureStart() {
        val controls = systemControls ?: return
        ui.volumeHideJob?.cancel()
        ui.volumeHideJob = null
        ui.volumeFraction = controls.currentVolumeFraction()
        ui.isAdjustingVolume = true
        ui.resetControlsTimer()
    }

    fun handleVolumeChange(fraction: Float) {
        val controls = systemControls ?: return
        controls.setVolumeFraction(fraction.coerceIn(0f, 1f))
        ui.volumeFraction = controls.currentVolumeFraction()
    }

    fun handleVolumeGestureEnd() {
        ui.volumeHideJob?.cancel()
        ui.volumeHideJob = scope.launch {
            delay(1_500)
            ui.isAdjustingVolume = false
            ui.volumeHideJob = null
        }
    }


    fun handleSeekPreview(position: Long) {
        ui.previewPositionMs = position
    }

    fun handleSeekPreviewFrame(position: Long) {
        ui.previewPositionMs = position
    }

    fun commitSeek(targetMs: Long): Boolean {
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: resolvedDurationMs()
        val current = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (duration <= 0L && !exoPlayer.isCurrentMediaItemSeekable && targetMs <= current) {
            return false
        }
        exoPlayer.seekTo(targetMs)
        progress.currentPositionMs = targetMs
        ui.previewPositionMs = targetMs
        return true
    }

    fun handleSeekCommitted(position: Long) {
        commitSeek(position)
    }

    fun handleSeekingChange(seeking: Boolean) {
        ui.isSeeking = seeking
        if (seeking) {
            val position = exoPlayer.currentPosition.coerceAtLeast(0L)
            progress.currentPositionMs = position
            ui.seekStartPositionMs = position
            ui.previewPositionMs = position
        } else {
            ui.resetControlsTimer()
        }
    }

    fun restoreSystemBars() {
        val window = (context as? Activity)?.window ?: return
        val controller = WindowCompat.getInsetsController(window, view)
        controller.show(WindowInsetsCompat.Type.systemBars())
    }

    fun updateFullscreenSystemBars() {
        val window = (context as? Activity)?.window ?: return
        val controller = WindowCompat.getInsetsController(window, view)
        if (!ui.isFullscreen || ui.isInPipMode) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            return
        }
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun currentPipAspectRatio(): Rational =
        PictureInPictureHelper.aspectRatioForVideo(ui.videoWidth, ui.videoHeight)


    fun setFullscreen(enabled: Boolean) {
        if (!enabled && ui.controlsLocked) return
        ui.isFullscreen = enabled
        if (enabled) {
            ui.controlsVisible = false
        } else if (ui.forcedLandscapeForFullscreen) {
            orientationController?.lockPortrait()
            ui.forcedLandscapeForFullscreen = false
        }
        if (enabled) {
            updateFullscreenSystemBars()
        } else {
            restoreSystemBars()
        }
    }

    fun enterLandscapeFullscreen() {
        if (ui.controlsLocked) return
        if (orientationController?.isLandscape() != true) {
            orientationController?.togglePortraitLandscape()
            ui.forcedLandscapeForFullscreen = true
        }
        setFullscreen(true)
    }

    fun handleFullscreenTap(offset: Offset, width: Float, height: Float) {
        ui.toggleControlsVisibility()
    }

    fun handleBack() {
        when {
            ui.showSpeedSheet -> ui.showSpeedSheet = false
            ui.showEpisodeSheet -> ui.showEpisodeSheet = false
            ui.showAspectSheet -> ui.showAspectSheet = false
            ui.showMoreSheet -> ui.showMoreSheet = false
            ui.showTrackSheet != null -> ui.showTrackSheet = null
            ui.showInfoDialog -> ui.showInfoDialog = false
            ui.controlsLocked -> return
            ui.isFullscreen -> setFullscreen(false)
            else -> {
                ui.resetSpeedBoost()
                scope.launch {
                    saveCurrentProgress()
                    onBack()
                }
            }
        }
    }

    fun handlePipClick() {
        if (ui.controlsLocked) return
        ui.resetControlsTimer()
        if (pipSupported) {
            activity?.enterPipMode(
                hasNext = ui.currentIndex < playlist.lastIndex,
                isPlaying = showsPauseIcon(exoPlayer.playWhenReady, exoPlayer.playbackState),
                aspectRatio = currentPipAspectRatio(),
            )
        } else {
            Toast.makeText(
                context,
                context.getString(R.string.pip_not_supported),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun handleFullscreenToggleClick() {
        if (ui.controlsLocked) return
        ui.resetControlsTimer()
        setFullscreen(!ui.isFullscreen)
    }

    fun handleRotateClick() {
        if (ui.controlsLocked) return
        ui.resetControlsTimer()
        orientationController?.togglePortraitLandscape()
    }

    fun seekBy(deltaMs: Long) {
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: resolvedDurationMs()
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val target = seekTargetMs(
            positionMs = position,
            deltaMs = deltaMs,
            durationMs = duration,
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L),
            seekable = exoPlayer.isCurrentMediaItemSeekable,
        )
        if (!commitSeek(target)) return
        ui.seekStartPositionMs = position
        ui.doubleTapSeekDeltaMs = deltaMs
        ui.showDoubleTapSeek = true
    }

    fun togglePlayback() {
        if (isCasting) {
            if (castManager?.playback?.isPlaying?.value == true) {
                castManager.playback.pause()
            } else {
                castManager?.playback?.play()
            }
        } else if (showsPauseIcon(exoPlayer.playWhenReady, exoPlayer.playbackState)) {
            exoPlayer.pause()
        } else {
            exoPlayer.play()
        }
    }

    fun handleDoubleTap(offset: Offset, width: Float) {
        if (ui.controlsLocked) return
        if (ui.videoScale > 1f) {
            ui.resetZoom()
            return
        }
        if (!preferences.gestureDoubleTapSeek) {
            togglePlayback()
            return
        }
        val ratio = offset.x / width.coerceAtLeast(1f)
        when {
            ratio < 0.33f -> seekBy(-DOUBLE_TAP_SEEK_MS)
            ratio > 0.67f -> seekBy(DOUBLE_TAP_SEEK_MS)
            else -> togglePlayback()
        }
    }

    fun captureScreenshot() {
        if (ui.controlsLocked) return
        val video = playlist.getOrNull(ui.currentIndex) ?: return
        val positionMs = exoPlayer.currentPosition
        scope.launch {
            val saved = withContext(Dispatchers.IO) {
                VideoFrameCapture.captureAndSave(
                    context = context,
                    videoUri = video.uri,
                    positionMs = positionMs,
                )
            }
            Toast.makeText(
                context,
                if (saved) R.string.screenshot_saved else R.string.screenshot_failed,
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun handleLockToggle() {
        ui.controlsLocked = !ui.controlsLocked
        if (ui.controlsLocked) {
            ui.controlsVisible = false
            orientationController?.lockCurrentOrientation()
        } else {
            orientationController?.unlockForPlayer()
            ui.resetControlsTimer()
        }
    }

    BackHandler { handleBack() }

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
        PlayerSession.onPlayNext = { playAdjacentEpisode(1) }
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

    val hasPrevious = ui.currentIndex > 0
    val hasNext = ui.currentIndex < playlist.lastIndex
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
    val seriesKey = currentVideo?.seriesKey
    val seriesSkip by seriesSkipStore.observe(seriesKey).collectAsStateWithLifecycle(initialValue = SeriesSkipSettings())
    val effectiveIntroSeconds = seriesSkip.introSeconds ?: preferences.skipIntroSeconds
    val effectiveOutroSeconds = seriesSkip.outroSeconds ?: preferences.skipOutroSeconds
    val canEditSeriesSkip = !seriesKey.isNullOrBlank()
    val introEndMs = if (effectiveIntroSeconds > 0) effectiveIntroSeconds * 1_000L else 0L
    val outroStartMs = if (effectiveOutroSeconds > 0 && displayDurationMs > 0) {
        (displayDurationMs - effectiveOutroSeconds * 1_000L).coerceAtLeast(0L)
    } else {
        -1L
    }
    fun markSeriesIntro() {
        val key = seriesKey ?: return
        val seconds = (exoPlayer.currentPosition.coerceAtLeast(0L) / 1_000L).toInt().coerceIn(0, 600)
        if (seconds <= 0) {
            Toast.makeText(context, context.getString(R.string.series_skip_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            seriesSkipStore.setIntroSeconds(key, seconds)
            Toast.makeText(
                context,
                context.getString(R.string.series_intro_saved, seconds),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    fun markSeriesOutro() {
        val key = seriesKey ?: return
        val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (displayDurationMs <= 0 || positionMs >= displayDurationMs) {
            Toast.makeText(context, context.getString(R.string.series_skip_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        val seconds = ((displayDurationMs - positionMs) / 1_000L).toInt().coerceIn(0, 600)
        if (seconds <= 0) {
            Toast.makeText(context, context.getString(R.string.series_skip_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            seriesSkipStore.setOutroSeconds(key, seconds)
            Toast.makeText(
                context,
                context.getString(R.string.series_outro_saved, seconds),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
    fun clearSeriesIntro() {
        val key = seriesKey ?: return
        scope.launch {
            seriesSkipStore.clearIntro(key)
            Toast.makeText(context, context.getString(R.string.series_intro_cleared), Toast.LENGTH_SHORT).show()
        }
    }
    fun rememberSkipPosition(positionMs: Long) {
        progress.currentPositionMs = positionMs
        ui.hiddenPlaybackPositionMs = positionMs
        ui.skipPositionReady = true
    }
    fun skipIntro() {
        if (isCasting) return
        val position = effectiveIntroSeconds * 1_000L
        exoPlayer.seekTo(position)
        rememberSkipPosition(position)
    }
    fun skipOutro() {
        if (isCasting) return
        if (hasNext) {
            playAdjacentEpisode(1, markCurrentComplete = true)
        } else if (displayDurationMs > 0L) {
            exoPlayer.seekTo(displayDurationMs)
            rememberSkipPosition(displayDurationMs)
        }
    }
    fun clearSeriesOutro() {
        val key = seriesKey ?: return
        scope.launch {
            seriesSkipStore.clearOutro(key)
            Toast.makeText(context, context.getString(R.string.series_outro_cleared), Toast.LENGTH_SHORT).show()
        }
    }
    val favoriteKey = currentVideo?.let { "single:${it.uri}" }
    val isFavorite = favoriteKey in pinnedKeys
    val currentTitle = currentVideo?.title ?: ""
    val seriesTitle = remember(playlist) { deriveSeriesTitle(playlist, ui.currentIndex) }
    val resolution = if (ui.videoWidth > 0 && ui.videoHeight > 0) {
        "$ui.videoWidth x $ui.videoHeight"
    } else {
        null
    }
    val useLandscapeLayout = ui.isLandscape || ui.isFullscreen
    val showsPause = if (isCasting) castPlaying else ui.isPlaying
    LaunchedEffect(exoPlayer, effectiveIntroSeconds, effectiveOutroSeconds) {
        if (effectiveIntroSeconds <= 0 && effectiveOutroSeconds <= 0) {
            ui.skipPositionReady = false
            return@LaunchedEffect
        }
        while (true) {
            ui.hiddenPlaybackPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
            ui.skipPositionReady = true
            delay(250)
        }
    }
    LaunchedEffect(ui.controlsVisible, showsPause, ui.isSeeking, useLandscapeLayout, ui.controlsLocked, ui.isAdjustingBrightness, ui.isAdjustingVolume, ui.isFullscreen) {
        if (ui.controlsVisible && !ui.isSeeking && !ui.isAdjustingBrightness && !ui.isAdjustingVolume && (ui.controlsLocked || showsPause)) {
            delay(3000)
            ui.controlsVisible = false
        }
    }

    LaunchedEffect(ui.isFullscreen, ui.isInPipMode) {
        updateFullscreenSystemBars()
    }

    LaunchedEffect(ui.isInPipMode, showsPause, hasNext, ui.currentIndex, ui.videoWidth, ui.videoHeight) {
        if (ui.isInPipMode) {
            activity?.updatePipActions(
                hasNext = hasNext,
                isPlaying = showsPause,
                aspectRatio = currentPipAspectRatio(),
            )
        }
    }

    if (ui.showSpeedSheet) {
        SpeedSelectorSheet(
            currentSpeed = ui.playbackSpeed,
            onSpeedSelected = {
                ui.playbackSpeed = it
                ui.savedPlaybackSpeed = it
                ui.resetControlsTimer()
                scope.launch { playerPreferencesStore.setDefaultSpeed(it) }
            },
            onDismiss = { ui.showSpeedSheet = false },
        )
    }

    if (ui.showAspectSheet) {
        AspectRatioSheet(
            currentMode = ui.aspectRatioMode,
            onModeSelected = { mode ->
                ui.aspectRatioMode = mode
                ui.resetZoom()
                scope.launch { playerPreferencesStore.setAspectRatioMode(mode) }
            },
            onDismiss = { ui.showAspectSheet = false },
        )
    }

    if (ui.showEpisodeSheet) {
        EpisodeListBottomSheet(
            episodes = playlist,
            currentIndex = ui.currentIndex,
            onEpisodeSelected = ::selectEpisode,
            onDismiss = { ui.showEpisodeSheet = false },
        )
    }

    if (ui.showMoreSheet) {
        PlayerMoreSheet(
            shuffleEnabled = preferences.shuffleEnabled,
            softDecodeEnabled = ui.appliedSoftDecode,
            isFavorite = isFavorite,
            skipIntroSeconds = effectiveIntroSeconds,
            skipOutroSeconds = effectiveOutroSeconds,
            bindsSeriesSkip = !seriesKey.isNullOrBlank(),
            seriesSkipCustomized = seriesSkip.hasAny,
            subtitleTextSize = preferences.subtitleTextSize,
            subtitleYellow = preferences.subtitleYellow,
            subtitleDelayMs = preferences.subtitleDelayMs,
            abPointA = ui.abPointA,
            abPointB = ui.abPointB,
            onShuffleToggle = {
                scope.launch {
                    playerPreferencesStore.setShuffleEnabled(!preferences.shuffleEnabled)
                }
            },
            onReplay = {
                exoPlayer.seekTo(0L)
                exoPlayer.play()
            },
            onAudioTrack = { ui.showTrackSheet = C.TRACK_TYPE_AUDIO },
            onVideoTrack = { ui.showTrackSheet = C.TRACK_TYPE_VIDEO },
            onSubtitles = { ui.showTrackSheet = C.TRACK_TYPE_TEXT },
            onPickSubtitleFile = { subtitlePicker.launch("*/*") },
            onAspectRatio = {
                ui.showMoreSheet = false
                ui.showAspectSheet = true
            },
            onScreenshot = {
                ui.showMoreSheet = false
                captureScreenshot()
            },
            onSoftDecodeToggle = {
                scope.launch {
                    val next = !ui.appliedSoftDecode
                    playerPreferencesStore.setSoftDecode(next)
                    if (next != ui.appliedSoftDecode) {
                        ui.playbackFailure = null
                        ui.softDecodeRetried = false
                        ui.replayPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
                        playbackSession.resumedIndices.remove(ui.currentIndex)
                        ui.appliedSoftDecode = next
                        ui.softDecodeGeneration += 1
                    }
                }
            },
            onFavoriteToggle = {
                favoriteKey?.let { key ->
                    scope.launch { pinnedItemsStore.toggle(key) }
                }
            },
            onInfo = { ui.showInfoDialog = true },
            onCast = {
                ui.showMoreSheet = false
                castSheet.open()
            },
            onSkipIntroSecondsChange = { seconds ->
                scope.launch {
                    val key = seriesKey
                    if (key.isNullOrBlank()) {
                        playerPreferencesStore.setSkipIntroSeconds(seconds)
                    } else {
                        seriesSkipStore.setIntroSeconds(key, seconds)
                    }
                }
            },
            onSkipOutroSecondsChange = { seconds ->
                scope.launch {
                    val key = seriesKey
                    if (key.isNullOrBlank()) {
                        playerPreferencesStore.setSkipOutroSeconds(seconds)
                    } else {
                        seriesSkipStore.setOutroSeconds(key, seconds)
                    }
                }
            },
            onFollowGlobalSkip = {
                val key = seriesKey ?: return@PlayerMoreSheet
                scope.launch { seriesSkipStore.clear(key) }
            },
            onSubtitleTextSize = { size ->
                scope.launch { playerPreferencesStore.setSubtitleTextSize(size) }
            },
            onSubtitleYellowToggle = {
                scope.launch { playerPreferencesStore.setSubtitleYellow(!preferences.subtitleYellow) }
            },
            onSubtitleDelayChange = { delayMs ->
                scope.launch { playerPreferencesStore.setSubtitleDelayMs(delayMs) }
            },
            onSetAbPointA = {
                ui.abPointA = exoPlayer.currentPosition.coerceAtLeast(0L)
            },
            onSetAbPointB = {
                ui.abPointB = exoPlayer.currentPosition.coerceAtLeast(0L)
            },
            onClearAb = {
                ui.abPointA = null
                ui.abPointB = null
            },
            bookmarks = bookmarks,
            onAddBookmark = {
                val uri = playlist.getOrNull(ui.currentIndex)?.uri ?: return@PlayerMoreSheet
                scope.launch {
                    bookmarkStore.add(uri, exoPlayer.currentPosition.coerceAtLeast(0L))
                }
            },
            onJumpBookmark = { positionMs ->
                exoPlayer.seekTo(ui.currentIndex, positionMs)
                progress.currentPositionMs = positionMs
                ui.showMoreSheet = false
            },
            onRemoveBookmark = { positionMs ->
                val uri = playlist.getOrNull(ui.currentIndex)?.uri ?: return@PlayerMoreSheet
                scope.launch { bookmarkStore.remove(uri, positionMs) }
            },
            playbackSession = playbackSession,
            onSleepSelected = { playbackSession.setSleep(it) },
            onDismiss = { ui.showMoreSheet = false },
        )
    }

    if (castSheet.showSheet && castManager != null) {
        CastDeviceBottomSheet(
            state = castState,
            onDeviceClick = { device ->
                val video = currentVideo ?: return@CastDeviceBottomSheet
                castManager.connectAndPlay(
                    device = device,
                    title = video.title,
                    contentUri = video.uri,
                    startPositionMs = exoPlayer.currentPosition,
                    mimeType = context.contentResolver.getType(video.uri),
                )
                castSheet.dismiss(CastUiState.Connecting(device))
            },
            onDismiss = { castSheet.dismiss(castState) },
        )
    }

    ui.showTrackSheet?.let { type ->
        TrackSelectorSheet(
            title = when (type) {
                C.TRACK_TYPE_AUDIO -> context.getString(R.string.audio_track)
                C.TRACK_TYPE_VIDEO -> context.getString(R.string.video_track)
                else -> context.getString(R.string.subtitles)
            },
            options = exoPlayer.trackOptions(context, type),
            onOptionSelected = { optionId -> exoPlayer.applyTrackSelection(type, optionId) },
            onDismiss = { ui.showTrackSheet = null },
        )
    }

    if (ui.showInfoDialog && currentVideo != null) {
        VideoInfoDialog(
            video = currentVideo,
            resolution = resolution,
            onDismiss = { ui.showInfoDialog = false },
        )
    }

    CompositionLocalProvider(
        LocalSubtitleAppearance provides SubtitleAppearance(
            textScale = preferences.subtitleTextSize.scale,
            yellow = preferences.subtitleYellow,
            delayMs = preferences.subtitleDelayMs,
        ),
        LocalTimelineMarks provides TimelineMarks(
            bookmarkPositionsMs = bookmarks,
            abPointA = ui.abPointA,
            abPointB = ui.abPointB,
        ),
    ) {
    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            ui.isInPipMode -> {
                FittedPlayerSurface(
                    player = exoPlayer,
                    modifier = Modifier.fillMaxSize(),
                    useTextureView = preferences.useTextureView,
                )
            }
            useLandscapeLayout -> {
                LandscapePlayerContent(
                    exoPlayer = exoPlayer,
                    progress = progress,
                    durationMs = displayDurationMs,
                    videoUri = currentVideo?.uri,
                    videoScale = ui.videoScale,
                    useTextureView = preferences.useTextureView,
                    offsetX = ui.offsetX,
                    offsetY = ui.offsetY,
                    ui = ui,
                    isCasting = isCasting,
                    isSeeking = ui.isSeeking,
                    controlsVisible = ui.controlsVisible,
                    controlsLocked = ui.controlsLocked,
                    isFullscreen = ui.isFullscreen,
                    title = currentTitle,
                    resolution = resolution,
                    isPlaying = showsPause,
                    playbackSpeed = ui.playbackSpeed,
                    repeatOne = ui.repeatOne,
                    isSpeedBoosted = ui.isSpeedBoosted,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    canEditSeriesSkip = canEditSeriesSkip && !isCasting,
                    introEndMs = introEndMs,
                    outroStartMs = outroStartMs,
                    introMarked = seriesSkip.introSeconds != null,
                    outroMarked = seriesSkip.outroSeconds != null,
                    onMarkIntro = ::markSeriesIntro,
                    onMarkOutro = ::markSeriesOutro,
                    onClearIntro = ::clearSeriesIntro,
                    onClearOutro = ::clearSeriesOutro,
                    onScaleChange = { ui.videoScale = it },
                    onOffsetChange = { x, y ->
                        ui.offsetX = x
                        ui.offsetY = y
                    },
                    onSeekPreview = ::handleSeekPreview,
                    onSeekPreviewFrame = ::handleSeekPreviewFrame,
                    onSeekCommitted = { position ->
                        handleSeekCommitted(position)
                        ui.resetControlsTimer()
                    },
                    onTap = ::handleFullscreenTap,
                    onDoubleTap = ::handleDoubleTap,
                    onSeekingChange = ::handleSeekingChange,
                    onSpeedBoostStart = {
                        if (!ui.isSpeedBoosted) {
                            ui.savedPlaybackSpeed = ui.playbackSpeed
                            ui.playbackSpeed = max(MIN_SPEED_BOOST, ui.savedPlaybackSpeed)
                            ui.isSpeedBoosted = true
                            if (!exoPlayer.isPlaying) {
                                exoPlayer.play()
                            }
                        }
                    },
                    onSpeedBoostEnd = { ui.resetSpeedBoost() },
                    onBrightnessGestureStart = ::handleBrightnessGestureStart.takeIf { preferences.gestureBrightness },
                    onBrightnessChange = ::handleBrightnessChange.takeIf { preferences.gestureBrightness },
                    onBrightnessGestureEnd = ::handleBrightnessGestureEnd.takeIf { preferences.gestureBrightness },
                    onVolumeGestureStart = ::handleVolumeGestureStart.takeIf { preferences.gestureVolume },
                    onVolumeChange = ::handleVolumeChange.takeIf { preferences.gestureVolume },
                    onVolumeGestureEnd = ::handleVolumeGestureEnd.takeIf { preferences.gestureVolume },
                    getCurrentBrightness = { systemControls?.currentBrightness() ?: ui.brightnessFraction },
                    getCurrentVolume = { systemControls?.currentVolumeFraction() ?: ui.volumeFraction },
                    isAdjustingBrightness = ui.isAdjustingBrightness,
                    isAdjustingVolume = ui.isAdjustingVolume,
                    brightnessFraction = ui.brightnessFraction,
                    volumeFraction = ui.volumeFraction,
                    onBack = ::handleBack,
                    onPlayPauseClick = {
                        ui.resetControlsTimer()
                        togglePlayback()
                    },
                    onPreviousClick = {
                        ui.resetControlsTimer()
                        if (!isCasting && hasPrevious) {
                            playAdjacentEpisode(-1)
                        }
                    },
                    onNextClick = {
                        ui.resetControlsTimer()
                        if (!isCasting && hasNext) {
                            playAdjacentEpisode(1)
                        }
                    },
                    onSeek = { position ->
                        ui.resetControlsTimer()
                        if (isCasting) {
                            castManager?.playback?.seekTo(position)
                        } else {
                            exoPlayer.seekTo(position)
                        }
                        progress.currentPositionMs = position
                    },
                    onScrubStart = {
                        if (isCasting) castManager?.playback?.beginScrub()
                    },
                    onScrubEnd = {
                        if (isCasting) castManager?.playback?.endScrub()
                    },
                    onLockToggle = ::handleLockToggle,
                    onRotateClick = ::handleRotateClick,
                    onSpeedClick = {
                        ui.resetControlsTimer()
                        ui.showSpeedSheet = true
                    },
                    onLoopClick = {
                        ui.resetControlsTimer()
                        ui.repeatOne = !ui.repeatOne
                        scope.launch { playerPreferencesStore.setRepeatOne(ui.repeatOne) }
                    },
                    onEpisodePickerClick = {
                        ui.resetControlsTimer()
                        ui.showEpisodeSheet = true
                    },
                    onMoreClick = {
                        ui.resetControlsTimer()
                        ui.showMoreSheet = true
                    },
                    onSkipIntro = ::skipIntro,
                    onSkipOutro = ::skipOutro,
                    pipSupported = pipSupported,
                    onPipClick = ::handlePipClick,
                    onFullscreenClick = ::handleFullscreenToggleClick,
                    contentScale = ui.aspectRatioMode.contentScale,
                    frameAspectRatio = ui.aspectRatioMode.frameAspectRatio,
                    seekEnabled = preferences.gestureSeek,
                    pinchEnabled = preferences.gesturePinchZoom,
                )
            }
            else -> {
                if (currentVideo != null) {
                    PortraitPlayerContent(
                        title = currentTitle,
                        resolution = resolution,
                        seriesTitle = seriesTitle,
                        exoPlayer = exoPlayer,
                        progress = progress,
                        ui = ui,
                        isCasting = isCasting,
                        durationMs = displayDurationMs,
                        isSeeking = ui.isSeeking,
                        controlsVisible = ui.controlsVisible,
                        episodes = playlist,
                        currentIndex = ui.currentIndex,
                        isPlaying = showsPause,
                        hasPrevious = hasPrevious,
                        hasNext = hasNext,
                        canEditSeriesSkip = canEditSeriesSkip && !isCasting,
                        introEndMs = introEndMs,
                        outroStartMs = outroStartMs,
                        introMarked = seriesSkip.introSeconds != null,
                        outroMarked = seriesSkip.outroSeconds != null,
                        onMarkIntro = ::markSeriesIntro,
                        onMarkOutro = ::markSeriesOutro,
                        onClearIntro = ::clearSeriesIntro,
                        onClearOutro = ::clearSeriesOutro,
                        onSeekPreview = ::handleSeekPreview,
                        onSeekPreviewFrame = ::handleSeekPreviewFrame,
                        onSeekCommitted = ::handleSeekCommitted,
                        onSeekingChange = ::handleSeekingChange,
                        onSpeedBoostStart = {
                            if (!ui.isSpeedBoosted) {
                                ui.savedPlaybackSpeed = ui.playbackSpeed
                                ui.playbackSpeed = max(MIN_SPEED_BOOST, ui.savedPlaybackSpeed)
                                ui.isSpeedBoosted = true
                                if (!exoPlayer.isPlaying) {
                                    exoPlayer.play()
                                }
                            }
                        },
                        onSpeedBoostEnd = { ui.resetSpeedBoost() },
                        onBrightnessGestureStart = ::handleBrightnessGestureStart,
                        onBrightnessChange = ::handleBrightnessChange,
                        onBrightnessGestureEnd = ::handleBrightnessGestureEnd,
                        onVolumeGestureStart = ::handleVolumeGestureStart,
                        onVolumeChange = ::handleVolumeChange,
                        onVolumeGestureEnd = ::handleVolumeGestureEnd,
                        getCurrentBrightness = { systemControls?.currentBrightness() ?: ui.brightnessFraction },
                        getCurrentVolume = { systemControls?.currentVolumeFraction() ?: ui.volumeFraction },
                        isAdjustingBrightness = ui.isAdjustingBrightness,
                        isAdjustingVolume = ui.isAdjustingVolume,
                        brightnessFraction = ui.brightnessFraction,
                        volumeFraction = ui.volumeFraction,
                        isSpeedBoosted = ui.isSpeedBoosted,
                        onVideoTap = { ui.toggleControlsVisibility() },
                        onPlayPauseClick = {
                            ui.resetControlsTimer()
                            togglePlayback()
                        },
                        onPreviousClick = {
                            ui.resetControlsTimer()
                            if (!isCasting && hasPrevious) {
                                playAdjacentEpisode(-1)
                            }
                        },
                        onNextClick = {
                            ui.resetControlsTimer()
                            if (!isCasting && hasNext) {
                                playAdjacentEpisode(1)
                            }
                        },
                        onSeek = { position ->
                            if (isCasting) {
                                castManager?.playback?.seekTo(position)
                            } else {
                                exoPlayer.seekTo(position)
                            }
                            progress.currentPositionMs = position
                        },
                        onScrubStart = {
                            if (isCasting) castManager?.playback?.beginScrub()
                        },
                        onScrubEnd = {
                            if (isCasting) castManager?.playback?.endScrub()
                        },
                        playbackSpeed = ui.playbackSpeed,
                        repeatOne = ui.repeatOne,
                        onEpisodeSelected = ::selectEpisode,
                        onSpeedClick = { ui.showSpeedSheet = true },
                        onLoopClick = {
                            ui.repeatOne = !ui.repeatOne
                            scope.launch { playerPreferencesStore.setRepeatOne(ui.repeatOne) }
                        },
                        onMoreClick = { ui.showMoreSheet = true },
                        onSkipIntro = ::skipIntro,
                        onSkipOutro = ::skipOutro,
                        onVideoDoubleTap = ::handleDoubleTap,
                        onFullscreenClick = ::enterLandscapeFullscreen,
                        contentScale = ui.aspectRatioMode.contentScale,
                        frameAspectRatio = ui.aspectRatioMode.frameAspectRatio,
                        useTextureView = preferences.useTextureView,
                        seekEnabled = preferences.gestureSeek,
                        pinchEnabled = preferences.gesturePinchZoom,
                        brightnessEnabled = preferences.gestureBrightness,
                        volumeEnabled = preferences.gestureVolume,
                    )
                }
            }
        }

        LiveSeekPreview(
            ui = ui,
            durationMs = displayDurationMs,
            forDoubleTap = true,
        )

        if (castManager != null && ui.controlsVisible && !ui.isInPipMode) {
            CastButton(
                state = castState,
                onClick = { castSheet.open() },
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .statusBarsPadding(),
            )
        }

        val connected = castState as? CastUiState.Connected
        if (connected != null && !ui.isInPipMode) {
            CastMiniController(
                visible = true,
                title = connected.title,
                isPlaying = castPlaying,
                onPlayPause = {
                    if (castPlaying) castManager?.playback?.pause() else castManager?.playback?.play()
                },
                onDisconnect = { castManager?.disconnect() },
                modifier = Modifier
                    .align(Alignment.BottomCenter)
                    .navigationBarsPadding(),
            )
        }

        ui.playbackFailure?.let { failure ->
            Text(
                text = stringResource(
                    if (failure == PlaybackFailure.Read) {
                        R.string.playback_error_read
                    } else {
                        R.string.playback_error_decode
                    },
                ),
                color = Color.White,
                style = MaterialTheme.typography.bodyLarge,
                textAlign = TextAlign.Center,
                modifier = Modifier
                    .align(Alignment.Center)
                    .padding(horizontal = 32.dp),
            )
        }
    }
    }
}
@Composable
private fun LandscapePlayerContent(
    exoPlayer: ExoPlayer,
    progress: PlayerProgressState,
    durationMs: Long,
    videoUri: Uri?,
    videoScale: Float,
    useTextureView: Boolean,
    offsetX: Float,
    offsetY: Float,
    ui: PlayerUiState,
    isCasting: Boolean,
    isSeeking: Boolean,
    controlsVisible: Boolean,
    controlsLocked: Boolean,
    isFullscreen: Boolean,
    title: String,
    resolution: String?,
    isPlaying: Boolean,
    playbackSpeed: Float,
    repeatOne: Boolean,
    isSpeedBoosted: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    canEditSeriesSkip: Boolean,
    introEndMs: Long,
    outroStartMs: Long,
    introMarked: Boolean,
    outroMarked: Boolean,
    onMarkIntro: () -> Unit,
    onMarkOutro: () -> Unit,
    onClearIntro: () -> Unit,
    onClearOutro: () -> Unit,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekPreviewFrame: (Long) -> Unit,
    onSeekCommitted: (Long) -> Unit,
    onTap: (Offset, Float, Float) -> Unit,
    onDoubleTap: (Offset, Float) -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onSpeedBoostStart: () -> Unit,
    onSpeedBoostEnd: () -> Unit,
    onBrightnessGestureStart: (() -> Unit)? = null,
    onBrightnessChange: ((Float) -> Unit)? = null,
    onBrightnessGestureEnd: (() -> Unit)? = null,
    onVolumeGestureStart: (() -> Unit)? = null,
    onVolumeChange: ((Float) -> Unit)? = null,
    onVolumeGestureEnd: (() -> Unit)? = null,
    getCurrentBrightness: () -> Float,
    getCurrentVolume: () -> Float,
    isAdjustingBrightness: Boolean,
    isAdjustingVolume: Boolean,
    brightnessFraction: Float,
    volumeFraction: Float,
    onBack: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
    onLockToggle: () -> Unit,
    onRotateClick: () -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onEpisodePickerClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSkipIntro: () -> Unit,
    onSkipOutro: () -> Unit,
    pipSupported: Boolean,
    onPipClick: () -> Unit,
    onFullscreenClick: () -> Unit,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
    frameAspectRatio: Float? = null,
    seekEnabled: Boolean = true,
    pinchEnabled: Boolean = true,
) {
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .then(
                    if (!isFullscreen) {
                        Modifier.statusBarsPadding()
                    } else {
                        Modifier
                    },
                ),
        ) {
            VideoPlayerGesture(
        scale = videoScale,
        offsetX = offsetX,
        offsetY = offsetY,
        onScaleChange = onScaleChange,
        onOffsetChange = onOffsetChange,
        getCurrentPositionMs = { exoPlayer.currentPosition.coerceAtLeast(0L) },
        getDurationMs = {
            exoPlayer.duration.takeIf { it > 0 } ?: durationMs
        },
        getBufferedPositionMs = { exoPlayer.bufferedPosition.coerceAtLeast(0L) },
        isSeekable = { exoPlayer.isCurrentMediaItemSeekable },
        onSeekPreview = onSeekPreview,
        onSeekPreviewFrame = onSeekPreviewFrame,
        onSeekCommitted = onSeekCommitted,
        onTap = onTap,
        onDoubleTap = onDoubleTap,
        onSeekingChange = onSeekingChange,
        onSpeedBoostStart = onSpeedBoostStart,
        onSpeedBoostEnd = onSpeedBoostEnd,
        onBrightnessGestureStart = onBrightnessGestureStart,
        onBrightnessChange = onBrightnessChange,
        onBrightnessGestureEnd = onBrightnessGestureEnd,
        onVolumeGestureStart = onVolumeGestureStart,
        onVolumeChange = onVolumeChange,
        onVolumeGestureEnd = onVolumeGestureEnd,
        getCurrentBrightness = getCurrentBrightness,
        getCurrentVolume = getCurrentVolume,
        enabled = true,
        speedBoostOnly = controlsLocked,
        seekEnabled = seekEnabled,
        pinchEnabled = pinchEnabled,
        modifier = Modifier.fillMaxSize(),
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(Color.Black),
            contentAlignment = Alignment.Center,
        ) {
            FittedPlayerSurface(
                player = exoPlayer,
                modifier = Modifier.fillMaxSize(),
                contentScale = contentScale,
                frameAspectRatio = frameAspectRatio,
                useTextureView = useTextureView,
            )
        }
    }

    BrightnessFeedbackOverlay(
        visible = isAdjustingBrightness,
        brightnessFraction = brightnessFraction,
    )

    VolumeFeedbackOverlay(
        visible = isAdjustingVolume,
        volumeFraction = volumeFraction,
    )

    SpeedBoostOverlay(visible = isSpeedBoosted)
        }

        LandscapePlayerLayout(
        visible = controlsVisible || isSeeking,
        title = title,
        resolution = resolution,
        isPlaying = isPlaying,
        controlsLocked = controlsLocked,
        isFullscreen = isFullscreen,
        pipSupported = pipSupported,
        showEpisodePicker = true,
        progress = progress,
        ui = ui,
        isCasting = isCasting,
        durationMs = durationMs,
        playbackSpeed = playbackSpeed,
        repeatOne = repeatOne,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        canEditSeriesSkip = canEditSeriesSkip,
        introEndMs = introEndMs,
        outroStartMs = outroStartMs,
        introMarked = introMarked,
        outroMarked = outroMarked,
        onMarkIntro = onMarkIntro,
        onMarkOutro = onMarkOutro,
        onClearIntro = onClearIntro,
        onClearOutro = onClearOutro,
        onBack = onBack,
        onPlayPauseClick = onPlayPauseClick,
        onPreviousClick = onPreviousClick,
        onNextClick = onNextClick,
        onSeek = onSeek,
        onScrubStart = onScrubStart,
        onScrubEnd = onScrubEnd,
        onLockToggle = onLockToggle,
        onRotateClick = onRotateClick,
        onPipClick = onPipClick,
        onFullscreenClick = onFullscreenClick,
        onSpeedClick = onSpeedClick,
        onLoopClick = onLoopClick,
        onEpisodePickerClick = onEpisodePickerClick,
        onMoreClick = onMoreClick,
        modifier = Modifier.fillMaxSize(),
    )
        LiveSkipIntroOutro(
            progress = progress,
            ui = ui,
            isCasting = isCasting,
            introEndMs = introEndMs,
            outroStartMs = outroStartMs,
            durationMs = durationMs,
            onSkipIntro = onSkipIntro,
            onSkipOutro = onSkipOutro,
        )
        LiveSeekPreview(
            ui = ui,
            durationMs = durationMs,
            videoUri = videoUri,
        )
    }
}

@Composable
private fun PortraitPlayerContent(
    title: String,
    resolution: String?,
    seriesTitle: String,
    exoPlayer: ExoPlayer,
    progress: PlayerProgressState,
    ui: PlayerUiState,
    isCasting: Boolean,
    durationMs: Long,
    isSeeking: Boolean,
    controlsVisible: Boolean,
    episodes: List<VideoItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    canEditSeriesSkip: Boolean,
    introEndMs: Long,
    outroStartMs: Long,
    introMarked: Boolean,
    outroMarked: Boolean,
    onMarkIntro: () -> Unit,
    onMarkOutro: () -> Unit,
    onClearIntro: () -> Unit,
    onClearOutro: () -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekPreviewFrame: (Long) -> Unit,
    onSeekCommitted: (Long) -> Unit,
    onSeekingChange: (Boolean) -> Unit,
    onSpeedBoostStart: () -> Unit,
    onSpeedBoostEnd: () -> Unit,
    onBrightnessGestureStart: () -> Unit,
    onBrightnessChange: (Float) -> Unit,
    onBrightnessGestureEnd: () -> Unit,
    onVolumeGestureStart: () -> Unit,
    onVolumeChange: (Float) -> Unit,
    onVolumeGestureEnd: () -> Unit,
    getCurrentBrightness: () -> Float,
    getCurrentVolume: () -> Float,
    isAdjustingBrightness: Boolean,
    isAdjustingVolume: Boolean,
    brightnessFraction: Float,
    volumeFraction: Float,
    isSpeedBoosted: Boolean,
    onVideoTap: () -> Unit,
    onVideoDoubleTap: (Offset, Float) -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
    onScrubStart: () -> Unit = {},
    onScrubEnd: () -> Unit = {},
    playbackSpeed: Float,
    repeatOne: Boolean,
    onEpisodeSelected: (Int) -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSkipIntro: () -> Unit,
    onSkipOutro: () -> Unit,
    onFullscreenClick: () -> Unit,
    contentScale: androidx.compose.ui.layout.ContentScale = androidx.compose.ui.layout.ContentScale.Fit,
    frameAspectRatio: Float? = null,
    useTextureView: Boolean = false,
    seekEnabled: Boolean = true,
    pinchEnabled: Boolean = true,
    brightnessEnabled: Boolean = true,
    volumeEnabled: Boolean = true,
) {
    PortraitPlayerLayout(
        title = title,
        resolution = resolution,
        seriesTitle = seriesTitle,
        exoPlayer = exoPlayer,
        episodes = episodes,
        currentIndex = currentIndex,
        isPlaying = isPlaying,
        progress = progress,
        ui = ui,
        isCasting = isCasting,
        videoUri = episodes.getOrNull(currentIndex)?.uri,
        durationMs = durationMs,
        getDurationMs = { exoPlayer.duration.takeIf { it > 0 } ?: durationMs },
        getCurrentPositionMs = { exoPlayer.currentPosition.coerceAtLeast(0L) },
        getBufferedPositionMs = { exoPlayer.bufferedPosition.coerceAtLeast(0L) },
        isSeekable = { exoPlayer.isCurrentMediaItemSeekable },
        isSeeking = isSeeking,
        controlsVisible = controlsVisible,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        onSeekPreview = onSeekPreview,
        onSeekPreviewFrame = onSeekPreviewFrame,
        onSeekCommitted = onSeekCommitted,
        onSeekingChange = onSeekingChange,
        onSpeedBoostStart = onSpeedBoostStart,
        onSpeedBoostEnd = onSpeedBoostEnd,
        onBrightnessGestureStart = onBrightnessGestureStart,
        onBrightnessChange = onBrightnessChange,
        onBrightnessGestureEnd = onBrightnessGestureEnd,
        onVolumeGestureStart = onVolumeGestureStart,
        onVolumeChange = onVolumeChange,
        onVolumeGestureEnd = onVolumeGestureEnd,
        getCurrentBrightness = getCurrentBrightness,
        getCurrentVolume = getCurrentVolume,
        isAdjustingBrightness = isAdjustingBrightness,
        isAdjustingVolume = isAdjustingVolume,
        brightnessFraction = brightnessFraction,
        volumeFraction = volumeFraction,
        isSpeedBoosted = isSpeedBoosted,
        onVideoTap = onVideoTap,
        onVideoDoubleTap = onVideoDoubleTap,
        onPlayPauseClick = onPlayPauseClick,
        onPreviousClick = onPreviousClick,
        onNextClick = onNextClick,
        onSeek = onSeek,
        onScrubStart = onScrubStart,
        onScrubEnd = onScrubEnd,
        playbackSpeed = playbackSpeed,
        repeatOne = repeatOne,
        onEpisodeSelected = onEpisodeSelected,
        onSpeedClick = onSpeedClick,
        onLoopClick = onLoopClick,
        onMoreClick = onMoreClick,
        onSkipIntro = onSkipIntro,
        onSkipOutro = onSkipOutro,
        canEditSeriesSkip = canEditSeriesSkip,
        introEndMs = introEndMs,
        outroStartMs = outroStartMs,
        introMarked = introMarked,
        outroMarked = outroMarked,
        onMarkIntro = onMarkIntro,
        onMarkOutro = onMarkOutro,
        onClearIntro = onClearIntro,
        onClearOutro = onClearOutro,
        onFullscreenClick = onFullscreenClick,
        contentScale = contentScale,
        frameAspectRatio = frameAspectRatio,
        useTextureView = useTextureView,
        seekEnabled = seekEnabled,
        pinchEnabled = pinchEnabled,
        brightnessEnabled = brightnessEnabled,
        volumeEnabled = volumeEnabled,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun deriveSeriesTitle(playlist: List<VideoItem>, currentIndex: Int): String {
    if (playlist.isEmpty()) return ""
    val current = playlist[currentIndex.coerceIn(0, playlist.lastIndex)]

    if (playlist.size > 1) {
        val names = playlist
            .map { TitleParser.parse(it.title).seriesName.trim() }
            .filter { it.isNotBlank() }
        val topNorm = names
            .groupingBy { TitleParser.normalizeForGrouping(it) }
            .eachCount()
            .filterKeys { it.length >= 2 }
            .maxByOrNull { it.value }
            ?.key
        if (topNorm != null) {
            names.firstOrNull { TitleParser.normalizeForGrouping(it) == topNorm }?.let { return it }
        }
        val commonPrefix = playlist
            .map { it.title }
            .reduce { acc, title -> acc.commonPrefixWith(title) }
            .trim()
            .trimEnd('-', '_', ' ', '.')
        if (commonPrefix.length >= 2) {
            return commonPrefix
        }
    }

    val parsedName = TitleParser.parse(current.title).seriesName.trim()
    if (parsedName.length >= 2) return parsedName

    val baseTitle = current.title
        .substringBeforeLast("：")
        .substringBeforeLast(":")
        .trim()
    if (baseTitle.isNotEmpty() && baseTitle != current.title) {
        return baseTitle
    }
    current.bucketName?.takeIf { it.isNotBlank() }?.let { return it }
    return baseTitle.ifEmpty { current.title }
}
