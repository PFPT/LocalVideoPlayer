package com.pfpt.localvideoplayer.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
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
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
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
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.MainActivity
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.data.BookmarkStore
import com.pfpt.localvideoplayer.data.PlaybackStore
import com.pfpt.localvideoplayer.data.PinnedItemsStore
import com.pfpt.localvideoplayer.data.PlayerPreferences
import com.pfpt.localvideoplayer.data.PlayerPreferencesStore
import com.pfpt.localvideoplayer.data.SeriesSkipSettings
import com.pfpt.localvideoplayer.data.SeriesSkipStore
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.player.PlaybackSession
import com.pfpt.localvideoplayer.player.PlayerViewModel
import com.pfpt.localvideoplayer.player.PlayerProgressState
import com.pfpt.localvideoplayer.player.PlaylistState
import com.pfpt.localvideoplayer.player.episodeStartPositionMs
import com.pfpt.localvideoplayer.player.seekTargetMs
import com.pfpt.localvideoplayer.player.showsPauseIcon
import com.pfpt.localvideoplayer.player.rememberPlayerProgressState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pfpt.localvideoplayer.ui.components.FittedPlayerSurface
import com.pfpt.localvideoplayer.ui.components.LiveSeekPreview
import com.pfpt.localvideoplayer.ui.components.LocalSubtitleAppearance
import com.pfpt.localvideoplayer.ui.components.LocalTimelineMarks
import com.pfpt.localvideoplayer.ui.components.SubtitleAppearance
import com.pfpt.localvideoplayer.ui.components.TimelineMarks
import com.pfpt.localvideoplayer.ui.components.LandscapePlayerContent
import com.pfpt.localvideoplayer.ui.components.PortraitPlayerContent
import com.pfpt.localvideoplayer.ui.player.PlayerOverlaySheets
import com.pfpt.localvideoplayer.ui.player.PlayerSessionEffects
import com.pfpt.localvideoplayer.ui.player.playerChromeActions
import com.pfpt.localvideoplayer.util.OrientationController
import com.pfpt.localvideoplayer.util.PlaybackSystemControls
import com.pfpt.localvideoplayer.util.PictureInPictureHelper
import com.pfpt.localvideoplayer.util.TitleParser
import com.pfpt.localvideoplayer.util.VideoFrameCapture
import com.pfpt.localvideoplayer.cast.CastUiState
import com.pfpt.localvideoplayer.cast.LocalCastManager
import com.pfpt.localvideoplayer.ui.components.CastButton
import com.pfpt.localvideoplayer.ui.components.CastMiniController
import com.pfpt.localvideoplayer.ui.components.rememberCastSheetActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flowOf
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

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
        playbackSession.skipBy(delta, markCurrentComplete)
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

    PlayerSessionEffects(
        exoPlayer = exoPlayer,
        playlist = playlist,
        ui = ui,
        progress = progress,
        activity = activity,
        playbackSession = playbackSession,
        playerViewModel = playerViewModel,
        lifecycleOwner = lifecycleOwner,
        orientationController = orientationController,
        systemControls = systemControls,
        configuration = configuration,
        castManager = castManager,
        preferences = preferences,
        displayDurationMs = displayDurationMs,
        onDurationKnown = onDurationKnown,
        castingOnLeave = castingOnLeave,
        scope = scope,
        saveCurrentProgress = { saveCurrentProgress() },
        onPlayAdjacentEpisode = { playAdjacentEpisode(it) },
        restoreSystemBars = { restoreSystemBars() },
        updateFullscreenSystemBars = { updateFullscreenSystemBars() },
        currentPipAspectRatio = { currentPipAspectRatio() },
    )

    val hasPrevious = ui.currentIndex > 0
    val hasNext = ui.currentIndex < playlist.lastIndex
    val currentVideo = playlist.getOrNull(ui.currentIndex)
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

    PlayerOverlaySheets(
        ui = ui,
        exoPlayer = exoPlayer,
        playlist = playlist,
        progress = progress,
        preferences = preferences,
        scope = scope,
        playerPreferencesStore = playerPreferencesStore,
        pinnedItemsStore = pinnedItemsStore,
        seriesSkipStore = seriesSkipStore,
        bookmarkStore = bookmarkStore,
        playbackSession = playbackSession,
        castManager = castManager,
        castState = castState,
        castSheet = castSheet,
        currentVideo = currentVideo,
        seriesKey = seriesKey,
        seriesSkip = seriesSkip,
        effectiveIntroSeconds = effectiveIntroSeconds,
        effectiveOutroSeconds = effectiveOutroSeconds,
        isFavorite = isFavorite,
        favoriteKey = favoriteKey,
        bookmarks = bookmarks,
        resolution = resolution,
        onEpisodeSelected = ::selectEpisode,
        onPickSubtitleFile = { subtitlePicker.launch("*/*") },
        onScreenshot = ::captureScreenshot,
    )

    val chrome = playerChromeActions(
        ui = ui,
        exoPlayer = exoPlayer,
        progress = progress,
        isCasting = isCasting,
        castManager = castManager,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        systemControls = systemControls,
        togglePlayback = ::togglePlayback,
        playAdjacentEpisode = { playAdjacentEpisode(it) },
        scope = scope,
        playerPreferencesStore = playerPreferencesStore,
    )

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
                    onSpeedBoostStart = chrome.onSpeedBoostStart,
                    onSpeedBoostEnd = chrome.onSpeedBoostEnd,
                    onBrightnessGestureStart = ::handleBrightnessGestureStart.takeIf { preferences.gestureBrightness },
                    onBrightnessChange = ::handleBrightnessChange.takeIf { preferences.gestureBrightness },
                    onBrightnessGestureEnd = ::handleBrightnessGestureEnd.takeIf { preferences.gestureBrightness },
                    onVolumeGestureStart = ::handleVolumeGestureStart.takeIf { preferences.gestureVolume },
                    onVolumeChange = ::handleVolumeChange.takeIf { preferences.gestureVolume },
                    onVolumeGestureEnd = ::handleVolumeGestureEnd.takeIf { preferences.gestureVolume },
                    getCurrentBrightness = chrome.getCurrentBrightness,
                    getCurrentVolume = chrome.getCurrentVolume,
                    isAdjustingBrightness = ui.isAdjustingBrightness,
                    isAdjustingVolume = ui.isAdjustingVolume,
                    brightnessFraction = ui.brightnessFraction,
                    volumeFraction = ui.volumeFraction,
                    onBack = ::handleBack,
                    onPlayPauseClick = chrome.onPlayPauseClick,
                    onPreviousClick = chrome.onPreviousClick,
                    onNextClick = chrome.onNextClick,
                    onSeek = { position ->
                        ui.resetControlsTimer()
                        chrome.seekToPosition(position)
                    },
                    onScrubStart = chrome.onScrubStart,
                    onScrubEnd = chrome.onScrubEnd,
                    onLockToggle = ::handleLockToggle,
                    onRotateClick = ::handleRotateClick,
                    onSpeedClick = {
                        ui.resetControlsTimer()
                        ui.showSpeedSheet = true
                    },
                    onLoopClick = {
                        ui.resetControlsTimer()
                        chrome.toggleRepeatOne()
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
                        onSpeedBoostStart = chrome.onSpeedBoostStart,
                        onSpeedBoostEnd = chrome.onSpeedBoostEnd,
                        onBrightnessGestureStart = ::handleBrightnessGestureStart,
                        onBrightnessChange = ::handleBrightnessChange,
                        onBrightnessGestureEnd = ::handleBrightnessGestureEnd,
                        onVolumeGestureStart = ::handleVolumeGestureStart,
                        onVolumeChange = ::handleVolumeChange,
                        onVolumeGestureEnd = ::handleVolumeGestureEnd,
                        getCurrentBrightness = chrome.getCurrentBrightness,
                        getCurrentVolume = chrome.getCurrentVolume,
                        isAdjustingBrightness = ui.isAdjustingBrightness,
                        isAdjustingVolume = ui.isAdjustingVolume,
                        brightnessFraction = ui.brightnessFraction,
                        volumeFraction = ui.volumeFraction,
                        isSpeedBoosted = ui.isSpeedBoosted,
                        onVideoTap = { ui.toggleControlsVisibility() },
                        onPlayPauseClick = chrome.onPlayPauseClick,
                        onPreviousClick = chrome.onPreviousClick,
                        onNextClick = chrome.onNextClick,
                        onSeek = chrome.seekToPosition,
                        onScrubStart = chrome.onScrubStart,
                        onScrubEnd = chrome.onScrubEnd,
                        playbackSpeed = ui.playbackSpeed,
                        repeatOne = ui.repeatOne,
                        onEpisodeSelected = ::selectEpisode,
                        onSpeedClick = { ui.showSpeedSheet = true },
                        onLoopClick = chrome.toggleRepeatOne,
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
