package com.pfpt.localvideoplayer.ui

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
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.MainActivity
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.data.BookmarkStore
import com.pfpt.localvideoplayer.data.seriesBookmarks
import com.pfpt.localvideoplayer.data.PlaybackStore
import com.pfpt.localvideoplayer.data.PinnedItemsStore
import com.pfpt.localvideoplayer.data.PlayerPreferences
import com.pfpt.localvideoplayer.data.PlayerPreferencesStore
import com.pfpt.localvideoplayer.data.SeriesPlaybackStore
import com.pfpt.localvideoplayer.data.SeriesSkipSettings
import com.pfpt.localvideoplayer.data.SeriesSkipStore
import com.pfpt.localvideoplayer.player.SeriesPlaybackSettings
import com.pfpt.localvideoplayer.player.playbackSpeedAfterSeriesLoad
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.player.PlaybackSession
import com.pfpt.localvideoplayer.player.PlayerViewModel
import com.pfpt.localvideoplayer.player.PlayerProgressState
import com.pfpt.localvideoplayer.player.PlaylistState
import com.pfpt.localvideoplayer.player.displayDurationMs as playbackDisplayDurationMs
import com.pfpt.localvideoplayer.player.resolvedDurationMs as playbackResolvedDurationMs
import com.pfpt.localvideoplayer.player.SpeedSnapshot
import com.pfpt.localvideoplayer.player.showsPauseIcon
import com.pfpt.localvideoplayer.player.speedAfterDefault
import com.pfpt.localvideoplayer.player.rememberPlayerProgressState
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.pfpt.localvideoplayer.ui.components.LocalSubtitleAppearance
import com.pfpt.localvideoplayer.ui.components.LocalTimelineMarks
import com.pfpt.localvideoplayer.ui.components.SubtitleAppearance
import com.pfpt.localvideoplayer.ui.components.TimelineMarks
import com.pfpt.localvideoplayer.ui.player.PlayerGestureController
import com.pfpt.localvideoplayer.ui.player.PlayerSessionActions
import com.pfpt.localvideoplayer.ui.player.PlayerOverlaySheets
import com.pfpt.localvideoplayer.ui.player.PlayerSessionEffects
import com.pfpt.localvideoplayer.ui.player.PlayerSurface
import com.pfpt.localvideoplayer.ui.player.playerChromeActions
import com.pfpt.localvideoplayer.ui.player.rememberOpenedPlayer
import com.pfpt.localvideoplayer.util.OrientationController
import com.pfpt.localvideoplayer.util.PlaybackSystemControls
import com.pfpt.localvideoplayer.util.TitleParser
import com.pfpt.localvideoplayer.util.VideoFrameCapture
import com.pfpt.localvideoplayer.cast.CastUiState
import com.pfpt.localvideoplayer.cast.LocalCastManager
import com.pfpt.localvideoplayer.ui.components.rememberCastSheetActions
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.collect
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
    seriesPlaybackStore: SeriesPlaybackStore,
    bookmarkStore: BookmarkStore,
    playbackSession: PlaybackSession,
    seriesPlaylistFor: (VideoItem) -> PlaylistState,
    onReplacePlaylist: (PlaylistState) -> Unit,
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
    val exoPlayer = rememberOpenedPlayer(
        playlist = playlist,
        ui = ui,
        playbackSession = playbackSession,
    )
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
            seriesPlaybackStore = seriesPlaybackStore,
            bookmarkStore = bookmarkStore,
            playbackSession = playbackSession,
            seriesPlaylistFor = seriesPlaylistFor,
            onReplacePlaylist = onReplacePlaylist,
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
    seriesPlaybackStore: SeriesPlaybackStore,
    bookmarkStore: BookmarkStore,
    playbackSession: PlaybackSession,
    seriesPlaylistFor: (VideoItem) -> PlaylistState,
    onReplacePlaylist: (PlaylistState) -> Unit,
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
    val flight = currentPlayerFlight()
    val scope = rememberCoroutineScope()
    val playing = playlist.getOrNull(ui.currentIndex)
    val seriesState = playing?.let(seriesPlaylistFor) ?: PlaylistState(
        items = listOfNotNull(playing),
        currentIndex = 0,
    )
    val seriesUris = seriesState.items.map { it.uri.toString() }
    val seriesMarks by remember(seriesUris) {
        bookmarkStore.bookmarksFor(seriesState.items.map { it.uri })
    }.collectAsStateWithLifecycle(initialValue = emptyMap())
    val seriesBookmarkRows = seriesBookmarks(
        episodes = seriesState.items.map { it.uri.toString() to it.title },
        marksByUri = seriesMarks,
    )
    val bookmarks = seriesMarks[playing?.uri?.toString()].orEmpty()
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
    LaunchedEffect(preferences.aspectRatioMode, preferences.repeatOne) {
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
    val castSheet = rememberCastSheetActions(castManager, exoPlayer)
    LaunchedEffect(exoPlayer, castManager) {
        castManager?.bindLocalPlayer(exoPlayer)
    }
    fun resolvedDurationMs(): Long {
        return playbackResolvedDurationMs(
            liveDurationMs = progress.durationMs,
            catalogDurationMs = playlist.getOrNull(ui.currentIndex)?.durationMs ?: 0L,
        )
    }
    val displayDurationMs = playbackDisplayDurationMs(
        isCasting = isCasting,
        castDurationMs = castDurationMs,
        localDurationMs = resolvedDurationMs(),
    )
    LaunchedEffect(displayDurationMs, isCasting) {
        if (isCasting && displayDurationMs > 0) {
            progress.durationMs = displayDurationMs
        }
    }


    val gestures = remember(systemControls, ui, scope) {
        PlayerGestureController(systemControls, ui, scope)
    }
    val seriesKey = playlist.getOrNull(ui.currentIndex)?.seriesKey
    val hasPrevious = ui.currentIndex > 0
    val hasNext = ui.currentIndex < playlist.lastIndex
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
    val session = PlayerSessionActions(
        context = context,
        exoPlayer = exoPlayer,
        playlist = playlist,
        ui = ui,
        progress = progress,
        playbackStore = playbackStore,
        playbackSession = playbackSession,
        playerViewModel = playerViewModel,
        scope = scope,
        activity = activity,
        pipSupported = pipSupported,
        seriesSkipStore = seriesSkipStore,
        seriesKey = seriesKey,
        seriesPlaylistFor = seriesPlaylistFor,
        onReplacePlaylist = onReplacePlaylist,
        episodes = seriesState.items,
        displayDurationMs = displayDurationMs,
        effectiveIntroSeconds = effectiveIntroSeconds,
        isCasting = isCasting,
        hasNext = hasNext,
        resolvedDurationMs = ::resolvedDurationMs,
    )

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
                    session.saveCurrentProgress()
                    if (!ui.isInPipMode) {
                        playlist.getOrNull(ui.currentIndex)?.uri?.toString()?.let { uri ->
                            flight?.beginReturn(uri)
                        }
                    }
                    onBack()
                }
            }
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
            ratio < 0.33f -> session.seekBy(-DOUBLE_TAP_SEEK_MS)
            ratio > 0.67f -> session.seekBy(DOUBLE_TAP_SEEK_MS)
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

    var loadedSeriesPlayback by remember { mutableStateOf<SeriesPlaybackSettings?>(null) }
    LaunchedEffect(seriesPlaybackStore, seriesKey) {
        loadedSeriesPlayback = null
        seriesPlaybackStore.observe(seriesKey).collect { loadedSeriesPlayback = it }
    }
    val seriesPlayback = loadedSeriesPlayback ?: SeriesPlaybackSettings()
    val loadedSpeed = playbackSpeedAfterSeriesLoad(loadedSeriesPlayback, preferences.defaultSpeed)
    LaunchedEffect(loadedSpeed) {
        val effectiveSpeed = loadedSpeed ?: return@LaunchedEffect
        val next = speedAfterDefault(
            snapshot = SpeedSnapshot(
                playbackSpeed = ui.playbackSpeed,
                savedPlaybackSpeed = ui.savedPlaybackSpeed,
                boosted = ui.isSpeedBoosted,
            ),
            defaultSpeed = effectiveSpeed,
        )
        ui.playbackSpeed = next.playbackSpeed
        ui.savedPlaybackSpeed = next.savedPlaybackSpeed
        ui.isSpeedBoosted = next.boosted
    }

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
        scope = scope,
        saveCurrentProgress = { session.saveCurrentProgress() },
        onPlayAdjacentEpisode = { session.playAdjacentEpisode(it) },
        restoreSystemBars = { restoreSystemBars() },
        updateFullscreenSystemBars = { updateFullscreenSystemBars() },
        currentPipAspectRatio = { session.currentPipAspectRatio() },
        seriesKey = seriesKey,
        seriesPlayback = loadedSeriesPlayback,
    )

    val currentVideo = playlist.getOrNull(ui.currentIndex)
    val favoriteKey = currentVideo?.let { "single:${it.uri}" }
    val isFavorite = favoriteKey in pinnedKeys
    val currentTitle = currentVideo?.title ?: ""
    val seriesTitle = remember(playlist) { deriveSeriesTitle(playlist, ui.currentIndex) }
    val resolution = if (ui.videoWidth > 0 && ui.videoHeight > 0) {
        "${ui.videoWidth} x ${ui.videoHeight}"
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
                aspectRatio = session.currentPipAspectRatio(),
            )
        }
    }

    val hidePage = hidePlayerPageForTransition(playerTransitionActive())
    if (!hidePage) {
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
            seriesPlaybackStore = seriesPlaybackStore,
            bookmarkStore = bookmarkStore,
            playbackSession = playbackSession,
            castManager = castManager,
            castState = castState,
            castSheet = castSheet,
            currentVideo = currentVideo,
            seriesKey = seriesKey,
            seriesSkip = seriesSkip,
            seriesPlayback = seriesPlayback,
            effectiveIntroSeconds = effectiveIntroSeconds,
            effectiveOutroSeconds = effectiveOutroSeconds,
            isFavorite = isFavorite,
            favoriteKey = favoriteKey,
            bookmarks = seriesBookmarkRows,
            showEpisodeTitles = seriesState.items.size > 1,
            onJumpBookmark = session::jumpBookmark,
            onRemoveBookmark = { bookmark ->
                scope.launch {
                    bookmarkStore.remove(Uri.parse(bookmark.uri), bookmark.positionMs)
                }
            },
            resolution = resolution,
            onEpisodeSelected = session::selectEpisode,
            onPickSubtitleFile = { subtitlePicker.launch("*/*") },
            onScreenshot = ::captureScreenshot,
        )
    }

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
        playAdjacentEpisode = { session.playAdjacentEpisode(it) },
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
        PlayerSurface(
            modifier = modifier,
            hidePage = hidePage,
            ui = ui,
            exoPlayer = exoPlayer,
            preferences = preferences,
            progress = progress,
            displayDurationMs = displayDurationMs,
            currentVideo = currentVideo,
            playlist = playlist,
            useLandscapeLayout = useLandscapeLayout,
            isCasting = isCasting,
            currentTitle = currentTitle,
            seriesTitle = seriesTitle,
            resolution = resolution,
            showsPause = showsPause,
            hasPrevious = hasPrevious,
            hasNext = hasNext,
            canEditSeriesSkip = canEditSeriesSkip,
            introEndMs = introEndMs,
            outroStartMs = outroStartMs,
            seriesSkip = seriesSkip,
            chrome = chrome,
            gestures = gestures,
            pipSupported = pipSupported,
            castManager = castManager,
            castState = castState,
            castPlaying = castPlaying,
            castSheet = castSheet,
            onMarkIntro = session::markIntro,
            onMarkOutro = session::markOutro,
            onClearIntro = session::clearIntro,
            onClearOutro = session::clearOutro,
            onSeekPreview = session::onSeekPreview,
            onSeekPreviewFrame = session::onSeekPreviewFrame,
            onSeekCommitted = session::onSeekCommitted,
            onSeekingChange = session::onSeekingChange,
            onFullscreenTap = ::handleFullscreenTap,
            onDoubleTap = ::handleDoubleTap,
            onBack = ::handleBack,
            onLockToggle = ::handleLockToggle,
            onRotateClick = ::handleRotateClick,
            onSkipIntro = session::skipIntro,
            onSkipOutro = session::skipOutro,
            onPipClick = session::onPipClick,
            onFullscreenToggle = ::handleFullscreenToggleClick,
            onEnterLandscapeFullscreen = ::enterLandscapeFullscreen,
            onEpisodeSelected = session::selectEpisode,
        )
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
