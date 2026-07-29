package com.zhaoshuo.localvideoplayer.ui

import android.app.Activity
import android.content.Context
import android.media.AudioManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
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
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.media3.common.MediaItem
import androidx.media3.common.Player
import androidx.media3.common.VideoSize
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.zhaoshuo.localvideoplayer.MainActivity
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.data.PlaybackStore
import com.zhaoshuo.localvideoplayer.data.PinnedItemsStore
import com.zhaoshuo.localvideoplayer.data.PlayerPreferences
import com.zhaoshuo.localvideoplayer.data.PlayerPreferencesStore
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.player.applyTrackSelection
import com.zhaoshuo.localvideoplayer.player.buildExoPlayer
import com.zhaoshuo.localvideoplayer.player.PlayerProgressState
import com.zhaoshuo.localvideoplayer.player.PlayerSession
import com.zhaoshuo.localvideoplayer.player.PlaylistState
import com.zhaoshuo.localvideoplayer.player.rememberPlayerProgressState
import com.zhaoshuo.localvideoplayer.player.toMediaItemWithSubtitle
import com.zhaoshuo.localvideoplayer.player.trackOptions
import com.zhaoshuo.localvideoplayer.ui.components.BrightnessFeedbackOverlay
import com.zhaoshuo.localvideoplayer.ui.components.VolumeFeedbackOverlay
import com.zhaoshuo.localvideoplayer.ui.components.SpeedBoostOverlay
import com.zhaoshuo.localvideoplayer.ui.components.EpisodeListBottomSheet
import com.zhaoshuo.localvideoplayer.ui.components.FittedPlayerSurface
import com.zhaoshuo.localvideoplayer.ui.components.LandscapePlayerLayout
import com.zhaoshuo.localvideoplayer.ui.components.PortraitPlayerLayout
import com.zhaoshuo.localvideoplayer.ui.components.PlayerMoreSheet
import com.zhaoshuo.localvideoplayer.ui.components.SkipIntroOutroOverlay
import com.zhaoshuo.localvideoplayer.ui.components.TrackSelectorSheet
import com.zhaoshuo.localvideoplayer.ui.components.VideoInfoDialog
import com.zhaoshuo.localvideoplayer.ui.components.displayVideoSize
import com.zhaoshuo.localvideoplayer.ui.components.SeekPreviewOverlay
import com.zhaoshuo.localvideoplayer.ui.components.SpeedSelectorSheet
import com.zhaoshuo.localvideoplayer.ui.components.VideoPlayerGesture
import com.zhaoshuo.localvideoplayer.util.OrientationController
import com.zhaoshuo.localvideoplayer.util.PlaybackSystemControls
import com.zhaoshuo.localvideoplayer.util.CastHelper
import com.zhaoshuo.localvideoplayer.util.toMediaItem
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch

private const val SPEED_BOOST = 2.0f
private const val PROGRESS_SAVE_INTERVAL_MS = 5_000L

@Composable
fun PlayerScreen(
    playlistState: PlaylistState,
    playbackStore: PlaybackStore,
    playerPreferencesStore: PlayerPreferencesStore,
    pinnedItemsStore: PinnedItemsStore,
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val activity = context as? MainActivity
    val view = LocalView.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val configuration = LocalConfiguration.current
    val playlist = playlistState.items
    val preferences by playerPreferencesStore.preferences.collectAsState(initial = PlayerPreferences())
    val pinnedKeys by pinnedItemsStore.pinnedKeys.collectAsState(initial = emptySet())
    val audioManager = remember(context) {
        context.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    }
    val orientationController = remember(context) {
        (context as? Activity)?.let { OrientationController(it) }
    }
    val systemControls = remember(activity, audioManager) {
        (activity as? Activity)?.let { PlaybackSystemControls(it, audioManager) }
    }

    var currentIndex by remember { mutableIntStateOf(playlistState.currentIndex) }
    var isPlaying by remember { mutableStateOf(true) }
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    var savedPlaybackSpeed by remember { mutableFloatStateOf(1.0f) }
    var isSpeedBoosted by remember { mutableStateOf(false) }
    var controlsVisible by remember { mutableStateOf(true) }
    var controlsLocked by remember { mutableStateOf(false) }
    var isFullscreen by remember { mutableStateOf(false) }
    var forcedLandscapeForFullscreen by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showEpisodeSheet by remember { mutableStateOf(false) }
    var showMoreSheet by remember { mutableStateOf(false) }
    var showTrackSheet by remember { mutableStateOf<Int?>(null) }
    var showInfoDialog by remember { mutableStateOf(false) }
    var subtitleUri by remember { mutableStateOf<Uri?>(null) }
    var isLandscape by remember { mutableStateOf(orientationController?.isLandscape() == true) }
    var isInPipMode by remember { mutableStateOf(activity?.isInPipMode == true) }
    var videoScale by remember { mutableFloatStateOf(1f) }
    var offsetX by remember { mutableFloatStateOf(0f) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var isSeeking by remember { mutableStateOf(false) }
    var previewPositionMs by remember { mutableLongStateOf(0L) }
    var seekStartPositionMs by remember { mutableLongStateOf(0L) }
    var lastPreviewSeekAt by remember { mutableLongStateOf(0L) }
    val resumedIndices = remember(playlist) { mutableSetOf<Int>() }
    var repeatOne by remember { mutableStateOf(false) }
    var videoWidth by remember { mutableIntStateOf(0) }
    var videoHeight by remember { mutableIntStateOf(0) }
    var isAdjustingBrightness by remember { mutableStateOf(false) }
    var isAdjustingVolume by remember { mutableStateOf(false) }
    var brightnessFraction by remember { mutableFloatStateOf(0.5f) }
    var volumeFraction by remember { mutableFloatStateOf(0.5f) }
    val pipSupported = remember(activity) { activity?.isPipSupported() == true }

    val exoPlayer = remember(playlist, preferences.softDecode) {
        buildExoPlayer(
            context = context,
            playlist = playlist,
            startIndex = playlistState.currentIndex,
            softDecode = preferences.softDecode,
            subtitleUri = subtitleUri,
        )
    }
    val subtitlePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent(),
    ) { uri ->
        if (uri != null) {
            subtitleUri = uri
            val index = exoPlayer.currentMediaItemIndex
            val position = exoPlayer.currentPosition
            val video = playlist.getOrNull(index)
            if (video != null) {
                exoPlayer.setMediaItem(video.toMediaItemWithSubtitle(uri), position)
                exoPlayer.prepare()
                exoPlayer.playWhenReady = isPlaying
            }
        }
    }
    val progress = rememberPlayerProgressState(exoPlayer, isSeeking)
    val displayPositionMs = if (isSeeking) previewPositionMs else progress.currentPositionMs

    fun resetSpeedBoost() {
        if (isSpeedBoosted) {
            playbackSpeed = savedPlaybackSpeed
            isSpeedBoosted = false
        }
    }

    suspend fun saveCurrentProgress() {
        val video = playlist.getOrNull(currentIndex) ?: return
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: video.durationMs
        if (duration <= 0) return
        playbackStore.saveProgress(
            uri = video.uri,
            title = video.title,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
        )
    }

    suspend fun seekToResumePosition(index: Int) {
        if (isSeeking || index in resumedIndices) return
        val video = playlist.getOrNull(index) ?: return
        val startMs = playbackStore.getResumePositionMs(video.uri, video.durationMs)
        resumedIndices.add(index)
        if (startMs > 0) {
            exoPlayer.seekTo(index, startMs)
            progress.currentPositionMs = startMs
            previewPositionMs = startMs
        }
    }

    fun selectEpisode(index: Int) {
        if (index !in playlist.indices) return
        scope.launch {
            saveCurrentProgress()
            val video = playlist[index]
            val startMs = playbackStore.getResumePositionMs(video.uri, video.durationMs)
            resumedIndices.add(index)
            exoPlayer.seekTo(index, startMs)
            progress.currentPositionMs = startMs
            previewPositionMs = startMs
            currentIndex = index
        }
    }

    fun resetControlsTimer() {
        controlsVisible = true
    }

    fun handleBrightnessGestureStart() {
        val controls = systemControls ?: return
        brightnessFraction = controls.currentBrightness()
        isAdjustingBrightness = true
        resetControlsTimer()
    }

    fun handleBrightnessChange(fraction: Float) {
        val controls = systemControls ?: return
        brightnessFraction = fraction.coerceIn(0.01f, 1f)
        controls.setBrightness(brightnessFraction)
    }

    fun handleBrightnessGestureEnd() {
        isAdjustingBrightness = false
    }

    fun handleVolumeGestureStart() {
        val controls = systemControls ?: return
        volumeFraction = controls.currentVolumeFraction()
        isAdjustingVolume = true
        resetControlsTimer()
    }

    fun handleVolumeChange(fraction: Float) {
        val controls = systemControls ?: return
        controls.setVolumeFraction(fraction.coerceIn(0f, 1f))
        volumeFraction = controls.currentVolumeFraction()
    }

    fun handleVolumeGestureEnd() {
        isAdjustingVolume = false
    }

    fun resetZoom() {
        videoScale = 1f
        offsetX = 0f
        offsetY = 0f
    }

    fun handleSeekPreview(position: Long) {
        previewPositionMs = position
    }

    fun handleSeekPreviewFrame(position: Long) {
        val now = System.currentTimeMillis()
        if (now - lastPreviewSeekAt < 100L) return
        lastPreviewSeekAt = now
        exoPlayer.seekTo(currentIndex, position)
    }

    fun handleSeekCommitted(position: Long) {
        exoPlayer.seekTo(currentIndex, position)
        progress.currentPositionMs = position
        previewPositionMs = position
    }

    fun handleSeekingChange(seeking: Boolean) {
        isSeeking = seeking
        if (seeking) {
            seekStartPositionMs = progress.currentPositionMs
            previewPositionMs = progress.currentPositionMs
        } else {
            resetControlsTimer()
        }
    }

    fun updateFullscreenSystemBars() {
        val window = (context as? Activity)?.window ?: return
        val controller = WindowCompat.getInsetsController(window, view)
        if (!isFullscreen) {
            controller.show(WindowInsetsCompat.Type.systemBars())
            return
        }
        controller.systemBarsBehavior =
            WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        controller.hide(WindowInsetsCompat.Type.systemBars())
    }

    fun toggleControlsVisibility() {
        if (controlsLocked) return
        controlsVisible = !controlsVisible
        if (controlsVisible) {
            resetControlsTimer()
        }
    }

    fun setFullscreen(enabled: Boolean) {
        isFullscreen = enabled
        if (enabled) {
            controlsVisible = false
        } else if (forcedLandscapeForFullscreen) {
            orientationController?.lockPortrait()
            forcedLandscapeForFullscreen = false
        }
        val window = (context as? Activity)?.window ?: return
        val controller = WindowCompat.getInsetsController(window, view)
        if (enabled) {
            updateFullscreenSystemBars()
        } else {
            controller.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    fun enterLandscapeFullscreen() {
        if (orientationController?.isLandscape() != true) {
            orientationController?.togglePortraitLandscape()
            forcedLandscapeForFullscreen = true
        }
        setFullscreen(true)
    }

    fun handleFullscreenTap(offset: Offset, width: Float, height: Float) {
        toggleControlsVisibility()
    }

    fun handleBack() {
        when {
            showSpeedSheet -> showSpeedSheet = false
            showEpisodeSheet -> showEpisodeSheet = false
            showMoreSheet -> showMoreSheet = false
            showTrackSheet != null -> showTrackSheet = null
            showInfoDialog -> showInfoDialog = false
            isFullscreen -> setFullscreen(false)
            else -> {
                resetSpeedBoost()
                scope.launch {
                    saveCurrentProgress()
                    onBack()
                }
            }
        }
    }

    BackHandler { handleBack() }

    fun updateVideoFormat() {
        val size = exoPlayer.displayVideoSize() ?: return
        videoWidth = size.first
        videoHeight = size.second
    }

    DisposableEffect(activity) {
        val pipListener: (Boolean) -> Unit = { inPip ->
            isInPipMode = inPip
            if (!inPip) {
                resetZoom()
            }
        }
        val pipActionListener: () -> Unit = {
            progress.syncFromPlayer(exoPlayer)
            currentIndex = exoPlayer.currentMediaItemIndex
            isPlaying = exoPlayer.isPlaying
            updateVideoFormat()
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
        isLandscape = orientationController?.isLandscape() == true
        resetSpeedBoost()
    }

    LaunchedEffect(playlist) {
        seekToResumePosition(playlistState.currentIndex)
    }

    LaunchedEffect(exoPlayer, currentIndex) {
        while (true) {
            delay(PROGRESS_SAVE_INTERVAL_MS)
            saveCurrentProgress()
        }
    }

    DisposableEffect(exoPlayer) {
        PlayerSession.attach(exoPlayer)
        PlayerSession.onStateChanged = {
            activity?.onPipAction?.invoke()
        }

        val listener = object : Player.Listener {
            override fun onIsPlayingChanged(playing: Boolean) {
                isPlaying = playing
            }

            override fun onPlaybackStateChanged(playbackState: Int) {
                if (playbackState == Player.STATE_READY) {
                    progress.syncFromPlayer(exoPlayer)
                    updateVideoFormat()
                }
            }

            override fun onMediaItemTransition(mediaItem: MediaItem?, reason: Int) {
                currentIndex = exoPlayer.currentMediaItemIndex
                progress.resetForNewItem()
                resetZoom()
                updateVideoFormat()
                scope.launch {
                    seekToResumePosition(currentIndex)
                }
            }

            override fun onVideoSizeChanged(videoSize: VideoSize) {
                updateVideoFormat()
            }
        }
        exoPlayer.addListener(listener)

        val lifecycleObserver = LifecycleEventObserver { _, event ->
            when (event) {
                Lifecycle.Event.ON_PAUSE -> {
                    if (activity?.isInPipMode != true) {
                        exoPlayer.pause()
                    }
                }
                Lifecycle.Event.ON_STOP -> {
                    if (activity?.isInPipMode != true) {
                        exoPlayer.pause()
                    }
                }
                else -> Unit
            }
        }
        lifecycleOwner.lifecycle.addObserver(lifecycleObserver)

        onDispose {
            PlayerSession.onStateChanged = null
            PlayerSession.detach(exoPlayer)
            exoPlayer.removeListener(listener)
            lifecycleOwner.lifecycle.removeObserver(lifecycleObserver)
            setFullscreen(false)
            resetSpeedBoost()
            orientationController?.lockPortrait()
            scope.launch {
                saveCurrentProgress()
            }
            exoPlayer.release()
        }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    LaunchedEffect(repeatOne) {
        exoPlayer.repeatMode = if (repeatOne) {
            Player.REPEAT_MODE_ONE
        } else {
            Player.REPEAT_MODE_OFF
        }
    }

    LaunchedEffect(preferences.shuffleEnabled) {
        exoPlayer.shuffleModeEnabled = preferences.shuffleEnabled
    }

    val hasPrevious = currentIndex > 0
    val hasNext = currentIndex < playlist.lastIndex
    val currentVideo = playlist.getOrNull(currentIndex)
    val favoriteKey = currentVideo?.let { "single:${it.uri}" }
    val isFavorite = favoriteKey in pinnedKeys
    val currentTitle = currentVideo?.title ?: ""
    val seriesTitle = remember(playlist) { deriveSeriesTitle(playlist, currentIndex) }
    val resolution = if (videoWidth > 0 && videoHeight > 0) {
        "$videoWidth x $videoHeight"
    } else {
        null
    }
    val useLandscapeLayout = isLandscape || isFullscreen
    val showSkipIntro = preferences.skipIntroSeconds > 0 &&
        displayPositionMs in 0..preferences.skipIntroSeconds * 1_000L
    val showSkipOutro = preferences.skipOutroSeconds > 0 &&
        progress.durationMs > 0 &&
        displayPositionMs >= progress.durationMs - preferences.skipOutroSeconds * 1_000L

    LaunchedEffect(controlsVisible, isPlaying, isSeeking, useLandscapeLayout, controlsLocked, isAdjustingBrightness, isAdjustingVolume, isFullscreen) {
        if (controlsVisible && isPlaying && !isSeeking && !controlsLocked && !isAdjustingBrightness && !isAdjustingVolume) {
            delay(3000)
            controlsVisible = false
        }
    }

    LaunchedEffect(isFullscreen) {
        updateFullscreenSystemBars()
    }

    LaunchedEffect(isInPipMode, isPlaying, hasNext, currentIndex) {
        if (isInPipMode) {
            activity?.updatePipActions(hasNext, isPlaying)
        }
    }

    if (showSpeedSheet) {
        SpeedSelectorSheet(
            currentSpeed = playbackSpeed,
            onSpeedSelected = {
                playbackSpeed = it
                savedPlaybackSpeed = it
                resetControlsTimer()
            },
            onDismiss = { showSpeedSheet = false },
        )
    }

    if (showEpisodeSheet) {
        EpisodeListBottomSheet(
            episodes = playlist,
            currentIndex = currentIndex,
            onEpisodeSelected = ::selectEpisode,
            onDismiss = { showEpisodeSheet = false },
        )
    }

    if (showMoreSheet) {
        PlayerMoreSheet(
            shuffleEnabled = preferences.shuffleEnabled,
            softDecodeEnabled = preferences.softDecode,
            isFavorite = isFavorite,
            skipIntroSeconds = preferences.skipIntroSeconds,
            skipOutroSeconds = preferences.skipOutroSeconds,
            onShuffleToggle = {
                scope.launch {
                    playerPreferencesStore.setShuffleEnabled(!preferences.shuffleEnabled)
                }
            },
            onReplay = {
                exoPlayer.seekTo(0L)
                exoPlayer.play()
            },
            onAudioTrack = { showTrackSheet = C.TRACK_TYPE_AUDIO },
            onVideoTrack = { showTrackSheet = C.TRACK_TYPE_VIDEO },
            onSubtitles = { showTrackSheet = C.TRACK_TYPE_TEXT },
            onPickSubtitleFile = { subtitlePicker.launch("*/*") },
            onSoftDecodeToggle = {
                scope.launch {
                    playerPreferencesStore.setSoftDecode(!preferences.softDecode)
                }
            },
            onFavoriteToggle = {
                favoriteKey?.let { key ->
                    scope.launch { pinnedItemsStore.toggle(key) }
                }
            },
            onInfo = { showInfoDialog = true },
            onCast = {
                currentVideo?.let { CastHelper.startCast(context, it.title, it.uri, exoPlayer) }
            },
            onSkipIntroSecondsChange = { seconds ->
                scope.launch { playerPreferencesStore.setSkipIntroSeconds(seconds) }
            },
            onSkipOutroSecondsChange = { seconds ->
                scope.launch { playerPreferencesStore.setSkipOutroSeconds(seconds) }
            },
            onDismiss = { showMoreSheet = false },
        )
    }

    showTrackSheet?.let { type ->
        TrackSelectorSheet(
            title = when (type) {
                C.TRACK_TYPE_AUDIO -> context.getString(R.string.audio_track)
                C.TRACK_TYPE_VIDEO -> context.getString(R.string.video_track)
                else -> context.getString(R.string.subtitles)
            },
            options = exoPlayer.trackOptions(context, type),
            onOptionSelected = { optionId -> exoPlayer.applyTrackSelection(type, optionId) },
            onDismiss = { showTrackSheet = null },
        )
    }

    if (showInfoDialog && currentVideo != null) {
        VideoInfoDialog(
            video = currentVideo,
            resolution = resolution,
            onDismiss = { showInfoDialog = false },
        )
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
    ) {
        when {
            isInPipMode -> {
                FittedPlayerSurface(
                    player = exoPlayer,
                    modifier = Modifier.fillMaxSize(),
                )
            }
            useLandscapeLayout -> {
                LandscapePlayerContent(
                    exoPlayer = exoPlayer,
                    progress = progress,
                    videoScale = videoScale,
                    offsetX = offsetX,
                    offsetY = offsetY,
                    displayPositionMs = displayPositionMs,
                    isSeeking = isSeeking,
                    previewPositionMs = previewPositionMs,
                    seekStartPositionMs = seekStartPositionMs,
                    controlsVisible = controlsVisible,
                    controlsLocked = controlsLocked,
                    isFullscreen = isFullscreen,
                    title = currentTitle,
                    resolution = resolution,
                    isPlaying = isPlaying,
                    playbackSpeed = playbackSpeed,
                    repeatOne = repeatOne,
                    isSpeedBoosted = isSpeedBoosted,
                    hasPrevious = hasPrevious,
                    hasNext = hasNext,
                    showSkipIntro = showSkipIntro,
                    showSkipOutro = showSkipOutro,
                    onScaleChange = { videoScale = it },
                    onOffsetChange = { x, y ->
                        offsetX = x
                        offsetY = y
                    },
                    onSeekPreview = ::handleSeekPreview,
                    onSeekPreviewFrame = ::handleSeekPreviewFrame,
                    onSeekCommitted = { position ->
                        handleSeekCommitted(position)
                        resetControlsTimer()
                    },
                    onTap = ::handleFullscreenTap,
                    onDoubleTap = {
                        if (isFullscreen) {
                            if (videoScale > 1f) {
                                resetZoom()
                            } else {
                                setFullscreen(false)
                            }
                        } else {
                            resetZoom()
                        }
                    },
                    onSeekingChange = ::handleSeekingChange,
                    onSpeedBoostStart = {
                        if (!isSpeedBoosted) {
                            savedPlaybackSpeed = playbackSpeed
                            playbackSpeed = SPEED_BOOST
                            isSpeedBoosted = true
                            if (!exoPlayer.isPlaying) {
                                exoPlayer.play()
                            }
                        }
                    },
                    onSpeedBoostEnd = ::resetSpeedBoost,
                    onBrightnessGestureStart = ::handleBrightnessGestureStart,
                    onBrightnessChange = ::handleBrightnessChange,
                    onBrightnessGestureEnd = ::handleBrightnessGestureEnd,
                    onVolumeGestureStart = ::handleVolumeGestureStart,
                    onVolumeChange = ::handleVolumeChange,
                    onVolumeGestureEnd = ::handleVolumeGestureEnd,
                    getCurrentBrightness = { systemControls?.currentBrightness() ?: brightnessFraction },
                    getCurrentVolume = { systemControls?.currentVolumeFraction() ?: volumeFraction },
                    isAdjustingBrightness = isAdjustingBrightness,
                    isAdjustingVolume = isAdjustingVolume,
                    brightnessFraction = brightnessFraction,
                    volumeFraction = volumeFraction,
                    onBack = ::handleBack,
                    onPlayPauseClick = {
                        resetControlsTimer()
                        if (exoPlayer.isPlaying) {
                            exoPlayer.pause()
                        } else {
                            exoPlayer.play()
                        }
                    },
                    onPreviousClick = {
                        resetControlsTimer()
                        if (hasPrevious) {
                            exoPlayer.seekToPreviousMediaItem()
                        }
                    },
                    onNextClick = {
                        resetControlsTimer()
                        if (hasNext) {
                            exoPlayer.seekToNextMediaItem()
                        }
                    },
                    onSeek = { position ->
                        resetControlsTimer()
                        exoPlayer.seekTo(position)
                        progress.currentPositionMs = position
                    },
                    onLockToggle = {
                        controlsLocked = !controlsLocked
                        if (controlsLocked) {
                            controlsVisible = true
                            orientationController?.lockCurrentOrientation()
                        } else {
                            orientationController?.unlockForPlayer()
                        }
                        resetControlsTimer()
                    },
                    onRotateClick = {
                        resetControlsTimer()
                        if (!controlsLocked) {
                            orientationController?.togglePortraitLandscape()
                        }
                    },
                    onSpeedClick = {
                        resetControlsTimer()
                        showSpeedSheet = true
                    },
                    onLoopClick = {
                        resetControlsTimer()
                        repeatOne = !repeatOne
                    },
                    onEpisodePickerClick = {
                        resetControlsTimer()
                        showEpisodeSheet = true
                    },
                    onMoreClick = {
                        resetControlsTimer()
                        showMoreSheet = true
                    },
                    onSkipIntro = {
                        val position = preferences.skipIntroSeconds * 1_000L
                        exoPlayer.seekTo(position)
                        progress.currentPositionMs = position
                    },
                    onSkipOutro = {
                        if (hasNext) {
                            exoPlayer.seekToNextMediaItem()
                        } else {
                            exoPlayer.seekTo(progress.durationMs)
                        }
                    },
                    pipSupported = pipSupported,
                    onPipClick = {
                        resetControlsTimer()
                        if (pipSupported) {
                            activity?.enterPipMode(hasNext, isPlaying)
                        } else {
                            Toast.makeText(
                                context,
                                context.getString(R.string.pip_not_supported),
                                Toast.LENGTH_SHORT,
                            ).show()
                        }
                    },
                    onFullscreenClick = {
                        resetControlsTimer()
                        setFullscreen(!isFullscreen)
                    },
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
                        displayPositionMs = displayPositionMs,
                        isSeeking = isSeeking,
                        previewPositionMs = previewPositionMs,
                        seekStartPositionMs = seekStartPositionMs,
                        controlsVisible = controlsVisible,
                        episodes = playlist,
                        currentIndex = currentIndex,
                        isPlaying = isPlaying,
                        hasPrevious = hasPrevious,
                        hasNext = hasNext,
                        showSkipIntro = showSkipIntro,
                        showSkipOutro = showSkipOutro,
                        onSeekPreview = ::handleSeekPreview,
                        onSeekPreviewFrame = ::handleSeekPreviewFrame,
                        onSeekCommitted = ::handleSeekCommitted,
                        onSeekingChange = ::handleSeekingChange,
                        onSpeedBoostStart = {
                            if (!isSpeedBoosted) {
                                savedPlaybackSpeed = playbackSpeed
                                playbackSpeed = SPEED_BOOST
                                isSpeedBoosted = true
                                if (!exoPlayer.isPlaying) {
                                    exoPlayer.play()
                                }
                            }
                        },
                        onSpeedBoostEnd = ::resetSpeedBoost,
                        onBrightnessGestureStart = ::handleBrightnessGestureStart,
                        onBrightnessChange = ::handleBrightnessChange,
                        onBrightnessGestureEnd = ::handleBrightnessGestureEnd,
                        onVolumeGestureStart = ::handleVolumeGestureStart,
                        onVolumeChange = ::handleVolumeChange,
                        onVolumeGestureEnd = ::handleVolumeGestureEnd,
                        getCurrentBrightness = { systemControls?.currentBrightness() ?: brightnessFraction },
                        getCurrentVolume = { systemControls?.currentVolumeFraction() ?: volumeFraction },
                        isAdjustingBrightness = isAdjustingBrightness,
                        isAdjustingVolume = isAdjustingVolume,
                        brightnessFraction = brightnessFraction,
                        volumeFraction = volumeFraction,
                        isSpeedBoosted = isSpeedBoosted,
                        onVideoTap = ::toggleControlsVisibility,
                        onPlayPauseClick = {
                            resetControlsTimer()
                            if (exoPlayer.isPlaying) {
                                exoPlayer.pause()
                            } else {
                                exoPlayer.play()
                            }
                        },
                        onPreviousClick = {
                            resetControlsTimer()
                            if (hasPrevious) {
                                exoPlayer.seekToPreviousMediaItem()
                            }
                        },
                        onNextClick = {
                            resetControlsTimer()
                            if (hasNext) {
                                exoPlayer.seekToNextMediaItem()
                            }
                        },
                        onSeek = { position ->
                            exoPlayer.seekTo(position)
                            progress.currentPositionMs = position
                        },
                        playbackSpeed = playbackSpeed,
                        repeatOne = repeatOne,
                        onEpisodeSelected = ::selectEpisode,
                        onSpeedClick = { showSpeedSheet = true },
                        onLoopClick = { repeatOne = !repeatOne },
                        onMoreClick = { showMoreSheet = true },
                        onSkipIntro = {
                            val position = preferences.skipIntroSeconds * 1_000L
                            exoPlayer.seekTo(position)
                            progress.currentPositionMs = position
                        },
                        onSkipOutro = {
                            if (hasNext) {
                                exoPlayer.seekToNextMediaItem()
                            } else {
                                exoPlayer.seekTo(progress.durationMs)
                            }
                        },
                        onVideoDoubleTap = ::enterLandscapeFullscreen,
                        onFullscreenClick = ::enterLandscapeFullscreen,
                    )
                }
            }
        }
    }
}

@Composable
private fun LandscapePlayerContent(
    exoPlayer: ExoPlayer,
    progress: PlayerProgressState,
    videoScale: Float,
    offsetX: Float,
    offsetY: Float,
    displayPositionMs: Long,
    isSeeking: Boolean,
    previewPositionMs: Long,
    seekStartPositionMs: Long,
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
    showSkipIntro: Boolean,
    showSkipOutro: Boolean,
    onScaleChange: (Float) -> Unit,
    onOffsetChange: (Float, Float) -> Unit,
    onSeekPreview: (Long) -> Unit,
    onSeekPreviewFrame: (Long) -> Unit,
    onSeekCommitted: (Long) -> Unit,
    onTap: (Offset, Float, Float) -> Unit,
    onDoubleTap: () -> Unit,
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
    onBack: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
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
        getCurrentPositionMs = { progress.currentPositionMs },
        durationMs = progress.durationMs,
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
            )
            SkipIntroOutroOverlay(
                showSkipIntro = showSkipIntro,
                showSkipOutro = showSkipOutro,
                onSkipIntro = onSkipIntro,
                onSkipOutro = onSkipOutro,
            )
        }
    }

    SeekPreviewOverlay(
        visible = isSeeking,
        previewPositionMs = previewPositionMs,
        durationMs = progress.durationMs,
        seekStartPositionMs = seekStartPositionMs,
    )

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
        visible = controlsVisible || controlsLocked || isSeeking,
        title = title,
        resolution = resolution,
        isPlaying = isPlaying,
        controlsLocked = controlsLocked,
        isFullscreen = isFullscreen,
        pipSupported = pipSupported,
        showEpisodePicker = true,
        currentPositionMs = displayPositionMs,
        durationMs = progress.durationMs,
        bufferedPositionMs = progress.bufferedPositionMs,
        playbackSpeed = playbackSpeed,
        repeatOne = repeatOne,
        hasPrevious = hasPrevious,
        hasNext = hasNext,
        onBack = onBack,
        onPlayPauseClick = onPlayPauseClick,
        onPreviousClick = onPreviousClick,
        onNextClick = onNextClick,
        onSeek = onSeek,
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
    }
}

@Composable
private fun PortraitPlayerContent(
    title: String,
    resolution: String?,
    seriesTitle: String,
    exoPlayer: ExoPlayer,
    progress: PlayerProgressState,
    displayPositionMs: Long,
    isSeeking: Boolean,
    previewPositionMs: Long,
    seekStartPositionMs: Long,
    controlsVisible: Boolean,
    episodes: List<VideoItem>,
    currentIndex: Int,
    isPlaying: Boolean,
    hasPrevious: Boolean,
    hasNext: Boolean,
    showSkipIntro: Boolean,
    showSkipOutro: Boolean,
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
    onVideoDoubleTap: () -> Unit,
    onPlayPauseClick: () -> Unit,
    onPreviousClick: () -> Unit,
    onNextClick: () -> Unit,
    onSeek: (Long) -> Unit,
    playbackSpeed: Float,
    repeatOne: Boolean,
    onEpisodeSelected: (Int) -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onMoreClick: () -> Unit,
    onSkipIntro: () -> Unit,
    onSkipOutro: () -> Unit,
    onFullscreenClick: () -> Unit,
) {
    PortraitPlayerLayout(
        title = title,
        resolution = resolution,
        seriesTitle = seriesTitle,
        exoPlayer = exoPlayer,
        episodes = episodes,
        currentIndex = currentIndex,
        isPlaying = isPlaying,
        currentPositionMs = displayPositionMs,
        seekPositionMs = progress.currentPositionMs,
        bufferedPositionMs = progress.bufferedPositionMs,
        durationMs = progress.durationMs,
        isSeeking = isSeeking,
        previewPositionMs = previewPositionMs,
        seekStartPositionMs = seekStartPositionMs,
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
        playbackSpeed = playbackSpeed,
        repeatOne = repeatOne,
        onEpisodeSelected = onEpisodeSelected,
        onSpeedClick = onSpeedClick,
        onLoopClick = onLoopClick,
        onMoreClick = onMoreClick,
        showSkipIntro = showSkipIntro,
        showSkipOutro = showSkipOutro,
        onSkipIntro = onSkipIntro,
        onSkipOutro = onSkipOutro,
        onFullscreenClick = onFullscreenClick,
        modifier = Modifier.fillMaxSize(),
    )
}

private fun deriveSeriesTitle(playlist: List<VideoItem>, currentIndex: Int): String {
    if (playlist.isEmpty()) return ""
    val current = playlist[currentIndex]
    current.bucketName?.let { return it }
    val baseTitle = current.title
        .substringBeforeLast("：")
        .substringBeforeLast(":")
        .trim()
    if (baseTitle.isNotEmpty() && baseTitle != current.title) {
        return baseTitle
    }
    if (playlist.size > 1) {
        val commonPrefix = playlist
            .map { it.title }
            .reduce { acc, title -> acc.commonPrefixWith(title) }
            .trim()
            .trimEnd('-', '_', ' ', '.')
        if (commonPrefix.length >= 2) {
            return commonPrefix
        }
    }
    return baseTitle.ifEmpty { current.title }
}
