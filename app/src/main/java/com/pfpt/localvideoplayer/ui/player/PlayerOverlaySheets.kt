package com.pfpt.localvideoplayer.ui.player

import androidx.compose.runtime.Composable
import androidx.compose.ui.platform.LocalContext
import androidx.media3.common.C
import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.cast.CastManager
import com.pfpt.localvideoplayer.cast.CastUiState
import com.pfpt.localvideoplayer.data.BookmarkStore
import com.pfpt.localvideoplayer.data.PinnedItemsStore
import com.pfpt.localvideoplayer.data.PlayerPreferences
import com.pfpt.localvideoplayer.data.PlayerPreferencesStore
import com.pfpt.localvideoplayer.data.SeriesSkipSettings
import com.pfpt.localvideoplayer.data.SeriesSkipStore
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.player.PlaybackSession
import com.pfpt.localvideoplayer.player.PlayerProgressState
import com.pfpt.localvideoplayer.player.applyTrackSelection
import com.pfpt.localvideoplayer.player.trackOptions
import com.pfpt.localvideoplayer.ui.PlayerUiState
import com.pfpt.localvideoplayer.ui.components.AspectRatioSheet
import com.pfpt.localvideoplayer.ui.components.CastDeviceBottomSheet
import com.pfpt.localvideoplayer.ui.components.CastSheetActions
import com.pfpt.localvideoplayer.ui.components.EpisodeListBottomSheet
import com.pfpt.localvideoplayer.ui.components.PlayerMoreSheet
import com.pfpt.localvideoplayer.ui.components.SpeedSelectorSheet
import com.pfpt.localvideoplayer.ui.components.TrackSelectorSheet
import com.pfpt.localvideoplayer.ui.components.VideoInfoDialog
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

@Composable
internal fun PlayerOverlaySheets(
    ui: PlayerUiState,
    exoPlayer: ExoPlayer,
    playlist: List<VideoItem>,
    progress: PlayerProgressState,
    preferences: PlayerPreferences,
    scope: CoroutineScope,
    playerPreferencesStore: PlayerPreferencesStore,
    pinnedItemsStore: PinnedItemsStore,
    seriesSkipStore: SeriesSkipStore,
    bookmarkStore: BookmarkStore,
    playbackSession: PlaybackSession,
    castManager: CastManager?,
    castState: CastUiState,
    castSheet: CastSheetActions,
    currentVideo: VideoItem?,
    seriesKey: String?,
    seriesSkip: SeriesSkipSettings,
    effectiveIntroSeconds: Int,
    effectiveOutroSeconds: Int,
    isFavorite: Boolean,
    favoriteKey: String?,
    bookmarks: List<Long>,
    resolution: String?,
    onEpisodeSelected: (Int) -> Unit,
    onPickSubtitleFile: () -> Unit,
    onScreenshot: () -> Unit,
) {
    val context = LocalContext.current
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
            onEpisodeSelected = onEpisodeSelected,
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
            onPickSubtitleFile = onPickSubtitleFile,
            onAspectRatio = {
                ui.showMoreSheet = false
                ui.showAspectSheet = true
            },
            onScreenshot = {
                ui.showMoreSheet = false
                onScreenshot()
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
}
