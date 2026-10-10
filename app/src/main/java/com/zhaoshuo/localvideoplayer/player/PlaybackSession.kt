package com.zhaoshuo.localvideoplayer.player

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.SystemClock
import androidx.lifecycle.AndroidViewModel
import androidx.media3.common.C
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import com.zhaoshuo.localvideoplayer.data.PlaybackStore
import com.zhaoshuo.localvideoplayer.model.VideoItem
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext

class PlaybackSessionViewModel(application: Application) : AndroidViewModel(application) {
    val session = PlaybackSession(application, PlaybackStore(application))

    override fun onCleared() {
        session.stop()
    }
}

data class PlaybackBarState(
    val active: Boolean = false,
    val onPlayerScreen: Boolean = false,
    val title: String = "",
    val isPlaying: Boolean = false,
    val sleepChoice: SleepChoice = SleepChoice.Off,
)

class PlaybackSession(
    context: Context,
    private val playbackStore: PlaybackStore,
) {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private val gate = Mutex()
    private val manualSubtitles = mutableMapOf<String, String>()
    private val _state = MutableStateFlow(PlaybackBarState())
    val state: StateFlow<PlaybackBarState> = _state.asStateFlow()
    private val _sleepRemainingMs = MutableStateFlow<Long?>(null)
    val sleepRemainingMs: StateFlow<Long?> = _sleepRemainingMs.asStateFlow()
    val resumedIndices = mutableSetOf<Int>()

    private var player: ExoPlayer? = null
    private var playlist: List<VideoItem> = emptyList()
    private var playlistKey: String = ""
    private var softDecode: Boolean = false
    private var onPlayerScreen: Boolean = false
    private var returnToExisting: Boolean = false
    private var sleepChoice: SleepChoice = SleepChoice.Off
    private var sleepDeadlineMs: Long? = null
    private var stopping = false
    private var sleepJob: Job? = null
    private var tickJob: Job? = null
    private var progressJob: Job? = null
    private var preloadJob: Job? = null
    private var listener: Player.Listener? = null

    fun player(): ExoPlayer? = player

    fun playlistState(): PlaylistState? {
        if (playlist.isEmpty()) return null
        val index = player?.currentMediaItemIndex ?: 0
        return PlaylistState(items = playlist, currentIndex = index.coerceIn(0, playlist.lastIndex))
    }

    fun markReturnToExisting() {
        returnToExisting = true
    }

    suspend fun open(
        items: List<VideoItem>,
        startIndex: Int,
        requestedSoftDecode: Boolean,
        startPositionMs: Long?,
    ): ExoPlayer = gate.withLock {
        require(items.isNotEmpty())
        val requestedKey = playbackPlaylistKey(items.map { it.uri.toString() })
        val action = playbackOpenAction(
            activeKey = playlistKey.takeIf { player != null },
            requestedKey = requestedKey,
            activeSoftDecode = softDecode,
            requestedSoftDecode = requestedSoftDecode,
            hasPlayer = player != null,
        )
        val safeIndex = startIndex.coerceIn(0, items.lastIndex)
        if (action == PlaybackOpenAction.Reuse) {
            val existing = player ?: error("missing player")
            val resumeExisting = returnToExisting
            returnToExisting = false
            playlist = items
            appContext.startForegroundService(Intent(appContext, PlaybackMediaService::class.java))
            if (shouldSeekToRequestedIndex(action, resumeExisting, existing.currentMediaItemIndex, safeIndex)) {
                resumedIndices.remove(safeIndex)
                seekToResume(safeIndex)
            }
            publish()
            return@withLock existing
        }
        returnToExisting = false
        val video = items[safeIndex]
        val subtitle = withContext(Dispatchers.IO) { resolveSubtitle(video) }
        val startMs = startPositionMs ?: playbackStore.getResumePositionMs(video.uri, video.durationMs)
        teardown(save = true)
        val created = buildExoPlayer(
            context = appContext,
            playlist = items,
            startIndex = safeIndex,
            softDecode = requestedSoftDecode,
            subtitleUri = subtitle?.let(Uri::parse),
            startPositionMs = startMs.coerceAtLeast(0L),
        )
        playlist = items
        playlistKey = requestedKey
        softDecode = requestedSoftDecode
        resumedIndices.clear()
        resumedIndices.add(safeIndex)
        install(created)
        publish()
        preloadAround(safeIndex)
        created
    }

    fun onEnterScreen() {
        onPlayerScreen = true
        publish()
    }

    fun onLeaveScreen(pauseLocal: Boolean, save: Boolean = true) {
        if (pauseLocal) {
            player?.pause()
        }
        onPlayerScreen = false
        if (save) {
            scope.launch { saveProgress() }
        }
        publish()
    }

    fun stop() {
        stopping = true
        player?.pause()
        publish()
        scope.launch {
            gate.withLock {
                teardown(save = true)
            }
        }
    }

    fun togglePlayPause() {
        val current = player ?: return
        if (current.isPlaying) current.pause() else current.play()
        publish()
    }

    fun setSleep(choice: SleepChoice) {
        val current = player ?: return
        sleepJob?.cancel()
        tickJob?.cancel()
        sleepDeadlineMs = null
        _sleepRemainingMs.value = null
        when (choice) {
            SleepChoice.Off -> {
                sleepChoice = SleepChoice.Off
                current.pauseAtEndOfMediaItems = false
            }
            SleepChoice.EndOfItem -> {
                sleepChoice = SleepChoice.EndOfItem
                current.pauseAtEndOfMediaItems = true
            }
            is SleepChoice.Minutes -> {
                sleepChoice = choice
                current.pauseAtEndOfMediaItems = false
                val deadline = sleepDeadlineElapsed(choice, SystemClock.elapsedRealtime()) ?: return
                sleepDeadlineMs = deadline
                sleepJob = scope.launch {
                    val wait = sleepRemaining(deadline, SystemClock.elapsedRealtime()) ?: return@launch
                    delay(wait)
                    player?.pause()
                    clearSleep()
                }
                ensureTick()
            }
        }
        publish()
    }

    fun bindManualSubtitle(videoUri: Uri, subtitleUri: Uri) {
        manualSubtitles[videoUri.toString()] = subtitleUri.toString()
    }

    suspend fun attachSubtitle(index: Int) {
        val video = playlist.getOrNull(index) ?: return
        val desired = withContext(Dispatchers.IO) { resolveSubtitle(video) }
        withContext(Dispatchers.Main.immediate) {
            applySubtitle(index, desired, allowCurrent = true)
        }
    }

    suspend fun saveProgress() {
        val snapshot = withContext(Dispatchers.Main.immediate) { capture() } ?: return
        val (video, positionMs, durationMs) = snapshot
        playbackStore.saveProgress(
            uri = video.uri,
            title = video.title,
            positionMs = positionMs,
            durationMs = durationMs,
        )
    }

    private fun install(created: ExoPlayer) {
        val playbackListener = object : Player.Listener {
            override fun onIsPlayingChanged(isPlaying: Boolean) {
                publish()
            }

            override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
                if (
                    !playWhenReady &&
                    reason == Player.PLAY_WHEN_READY_CHANGE_REASON_END_OF_MEDIA_ITEM &&
                    sleepChoice is SleepChoice.EndOfItem
                ) {
                    clearSleep()
                }
                publish()
            }

            override fun onMediaItemTransition(mediaItem: androidx.media3.common.MediaItem?, reason: Int) {
                val index = created.currentMediaItemIndex
                if (
                    sleepChoice is SleepChoice.EndOfItem &&
                    reason != Player.MEDIA_ITEM_TRANSITION_REASON_PLAYLIST_CHANGED
                ) {
                    clearSleep()
                }
                publish()
                scope.launch {
                    if (reason == Player.MEDIA_ITEM_TRANSITION_REASON_AUTO && index !in resumedIndices) {
                        seekToResume(index)
                    } else {
                        attachSubtitle(index)
                    }
                    preloadAround(index)
                }
            }
        }
        listener = playbackListener
        created.addListener(playbackListener)
        player = created
        PlayerSession.attach(created)
        appContext.startForegroundService(Intent(appContext, PlaybackMediaService::class.java))
        progressJob?.cancel()
        progressJob = scope.launch {
            while (isActive) {
                delay(PROGRESS_SAVE_INTERVAL_MS)
                saveProgress()
            }
        }
    }

    private suspend fun teardown(save: Boolean) {
        if (save) {
            saveProgress()
        }
        clearSleep()
        progressJob?.cancel()
        preloadJob?.cancel()
        tickJob?.cancel()
        val current = player
        val playbackListener = listener
        if (current != null && playbackListener != null) {
            current.removeListener(playbackListener)
        }
        listener = null
        if (current != null) {
            PlayerSession.detach(current)
        }
        appContext.stopService(Intent(appContext, PlaybackMediaService::class.java))
        current?.release()
        player = null
        playlist = emptyList()
        playlistKey = ""
        resumedIndices.clear()
        manualSubtitles.clear()
        onPlayerScreen = false
        stopping = false
        publish()
    }

    private suspend fun seekToResume(index: Int) {
        val video = playlist.getOrNull(index) ?: return
        attachSubtitle(index)
        val startMs = playbackStore.getResumePositionMs(video.uri, video.durationMs).coerceAtLeast(0L)
        val current = player ?: return
        if (startMs > 0L || current.currentMediaItemIndex != index) {
            current.seekTo(index, startMs)
        }
        resumedIndices.add(index)
        publish()
    }

    private fun preloadAround(current: Int) {
        preloadJob?.cancel()
        val items = playlist
        preloadJob = scope.launch {
            val indices = indicesToPreload(current, items.size)
            val desired = withContext(Dispatchers.IO) {
                indices.associateWith { index ->
                    items.getOrNull(index)?.let { resolveSubtitle(it) }
                }
            }
            withContext(Dispatchers.Main.immediate) {
                val currentPlayer = player ?: return@withContext
                val now = currentPlayer.currentMediaItemIndex
                desired.forEach { (index, subtitle) ->
                    val existing = configuredSubtitle(currentPlayer, index)
                    if (shouldPreloadSubtitle(index, now, existing, subtitle)) {
                        applySubtitle(index, subtitle, allowCurrent = false)
                    }
                }
            }
        }
    }

    private fun resolveSubtitle(video: VideoItem): String? {
        val sidecar = findSidecarSubtitle(appContext, video)?.toString()
        return subtitleForVideo(video.uri.toString(), manualSubtitles, sidecar)
    }

    private fun applySubtitle(index: Int, desired: String?, allowCurrent: Boolean) {
        val current = player ?: return
        if (desired == null) return
        val existing = configuredSubtitle(current, index)
        val isCurrent = current.currentMediaItemIndex == index
        val attach = if (isCurrent) {
            allowCurrent && shouldAttachOnArrival(existing, desired)
        } else {
            shouldPreloadSubtitle(index, current.currentMediaItemIndex, existing, desired)
        }
        if (!attach) return
        val video = playlist.getOrNull(index) ?: return
        val textDisabled = current.trackSelectionParameters.disabledTrackTypes.contains(C.TRACK_TYPE_TEXT)
        val position = if (isCurrent) current.currentPosition.coerceAtLeast(0L) else 0L
        current.replaceMediaItem(index, video.toMediaItemWithSubtitle(appContext, Uri.parse(desired)))
        if (isCurrent && position > 0L) {
            current.seekTo(index, position)
        }
        if (textDisabled) {
            current.trackSelectionParameters = current.trackSelectionParameters
                .buildUpon()
                .setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                .build()
        }
    }

    private fun configuredSubtitle(current: ExoPlayer, index: Int): String? {
        val item = runCatching { current.getMediaItemAt(index) }.getOrNull() ?: return null
        return item.localConfiguration?.subtitleConfigurations?.firstOrNull()?.uri?.toString()
    }

    private fun clearSleep() {
        sleepJob?.cancel()
        sleepJob = null
        sleepDeadlineMs = null
        sleepChoice = SleepChoice.Off
        _sleepRemainingMs.value = null
        player?.pauseAtEndOfMediaItems = false
        publish()
    }

    private fun ensureTick() {
        if (tickJob?.isActive == true) return
        tickJob = scope.launch {
            while (isActive) {
                val deadline = sleepDeadlineMs ?: break
                val remaining = sleepRemaining(deadline, SystemClock.elapsedRealtime())
                _sleepRemainingMs.value = remaining
                if (remaining == null || remaining == 0L) break
                delay(1_000)
            }
        }
    }

    private fun capture(): Triple<VideoItem, Long, Long>? {
        val current = player ?: return null
        val video = playlist.getOrNull(current.currentMediaItemIndex) ?: return null
        val duration = current.duration.takeIf { it > 0 } ?: video.durationMs
        if (duration <= 0) return null
        return Triple(video, current.currentPosition.coerceAtLeast(0L), duration)
    }

    private fun publish() {
        val current = player
        val index = current?.currentMediaItemIndex ?: 0
        _state.value = PlaybackBarState(
            active = current != null && !stopping,
            onPlayerScreen = onPlayerScreen,
            title = playlist.getOrNull(index)?.title.orEmpty(),
            isPlaying = current?.isPlaying == true && !stopping,
            sleepChoice = sleepChoice,
        )
    }

    private companion object {
        const val PROGRESS_SAVE_INTERVAL_MS = 5_000L
    }
}
