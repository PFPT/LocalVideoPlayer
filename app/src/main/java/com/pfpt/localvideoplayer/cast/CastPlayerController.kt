package com.pfpt.localvideoplayer.cast

import androidx.media3.common.Player
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

interface CastPlaybackFacade {
    val positionMs: StateFlow<Long>
    val durationMs: StateFlow<Long>
    val isPlaying: StateFlow<Boolean>
    val isCasting: StateFlow<Boolean>
    fun play()
    fun pause()
    fun seekTo(positionMs: Long)
    fun beginScrub()
    fun endScrub()
}

class CastPlayerController : CastPlaybackFacade {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var positionJob: Job? = null
    private var scrubbing = false
    private var castPlayer: Player? = null
    private var localPlayer: Player? = null

    private val _positionMs = MutableStateFlow(0L)
    override val positionMs: StateFlow<Long> = _positionMs.asStateFlow()

    private val _durationMs = MutableStateFlow(0L)
    override val durationMs: StateFlow<Long> = _durationMs.asStateFlow()

    private val _isPlaying = MutableStateFlow(false)
    override val isPlaying: StateFlow<Boolean> = _isPlaying.asStateFlow()

    private val _isCasting = MutableStateFlow(false)
    override val isCasting: StateFlow<Boolean> = _isCasting.asStateFlow()

    private val listener = object : Player.Listener {
        override fun onEvents(player: Player, events: Player.Events) {
            if (scrubbing) return
            if (events.containsAny(
                    Player.EVENT_PLAYBACK_STATE_CHANGED,
                    Player.EVENT_PLAY_WHEN_READY_CHANGED,
                    Player.EVENT_IS_PLAYING_CHANGED,
                    Player.EVENT_MEDIA_ITEM_TRANSITION,
                    Player.EVENT_POSITION_DISCONTINUITY,
                    Player.EVENT_TIMELINE_CHANGED,
                )
            ) {
                refreshOnce()
                syncPositionTicker()
            }
        }
    }

    fun attachLocal(player: Player?) {
        localPlayer = player
    }

    fun attachCast(player: Player?) {
        castPlayer?.removeListener(listener)
        castPlayer = player
        _isCasting.value = player != null
        player?.addListener(listener)
        refreshOnce()
        syncPositionTicker()
    }

    fun startListening() {
        _isCasting.value = castPlayer != null
        castPlayer?.addListener(listener)
        refreshOnce()
        syncPositionTicker()
    }

    fun stopListening() {
        positionJob?.cancel()
        positionJob = null
        castPlayer?.removeListener(listener)
        _isCasting.value = false
        castPlayer = null
        scrubbing = false
        _isPlaying.value = false
    }

    override fun play() {
        activePlayer()?.playWhenReady = true
        _isPlaying.value = true
        syncPositionTicker()
    }

    override fun pause() {
        activePlayer()?.playWhenReady = false
        _isPlaying.value = false
        syncPositionTicker()
    }

    override fun seekTo(positionMs: Long) {
        activePlayer()?.seekTo(positionMs.coerceAtLeast(0L))
        _positionMs.value = positionMs.coerceAtLeast(0L)
    }

    override fun beginScrub() {
        scrubbing = true
    }

    override fun endScrub() {
        scrubbing = false
        refreshOnce()
    }

    private fun activePlayer(): Player? {
        return if (_isCasting.value) castPlayer else localPlayer
    }

    private fun refreshOnce() {
        val player = activePlayer() ?: return
        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
        val duration = player.duration
        if (duration > 0) {
            _durationMs.value = duration
        }
        _isPlaying.value = player.isPlaying ||
            (player.playWhenReady && player.playbackState == Player.STATE_BUFFERING)
    }

    private fun syncPositionTicker() {
        positionJob?.cancel()
        if (!_isCasting.value || !_isPlaying.value) {
            positionJob = null
            return
        }
        positionJob = scope.launch {
            while (isActive && _isCasting.value && _isPlaying.value) {
                if (!scrubbing) {
                    val player = castPlayer
                    if (player != null) {
                        _positionMs.value = player.currentPosition.coerceAtLeast(0L)
                        val duration = player.duration
                        if (duration > 0) {
                            _durationMs.value = duration
                        }
                    }
                }
                delay(POSITION_TICK_MS)
            }
        }
    }

    companion object {
        private const val POSITION_TICK_MS = 500L
    }
}
