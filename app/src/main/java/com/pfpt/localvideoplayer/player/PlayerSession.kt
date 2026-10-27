package com.pfpt.localvideoplayer.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

object PlayerSession {

    private var player: ExoPlayer? = null
    var hasNextProvider: () -> Boolean = { false }
    var onStateChanged: (() -> Unit)? = null
    var onPlayNext: (() -> Unit)? = null
    var onSkipBy: ((Int) -> Unit)? = null

    fun current(): ExoPlayer? = player

    fun attach(player: ExoPlayer) {
        this.player = player
    }

    fun detach(player: ExoPlayer) {
        if (this.player === player) {
            this.player = null
        }
    }

    fun seekForward(ms: Long = 10_000) {
        val current = player ?: return
        val duration = current.duration.coerceAtLeast(0L)
        if (duration <= 0) return
        val newPosition = (current.currentPosition + ms).coerceAtMost(duration)
        current.seekTo(newPosition)
        onStateChanged?.invoke()
    }

    fun skipBy(delta: Int) {
        val skip = onSkipBy
        if (skip != null) {
            skip(delta)
            onStateChanged?.invoke()
            return
        }
        val current = player ?: return
        val target = current.currentMediaItemIndex + delta
        if (target !in 0 until current.mediaItemCount) return
        current.seekTo(target, 0L)
        onStateChanged?.invoke()
    }

    fun seekToNext(): Boolean {
        val current = player ?: return false
        if (!current.hasNextMediaItem()) return false
        val callback = onPlayNext
        if (callback != null) {
            callback()
            onStateChanged?.invoke()
            return true
        }
        skipBy(1)
        return true
    }

    fun togglePlayPause() {
        val current = player ?: return
        if (showsPauseIcon(current.playWhenReady, current.playbackState)) {
            current.pause()
        } else {
            current.play()
        }
        onStateChanged?.invoke()
    }

    fun hasNext(): Boolean {
        return player?.hasNextMediaItem() == true
    }

    fun isPlaying(): Boolean {
        val current = player ?: return false
        return showsPauseIcon(current.playWhenReady, current.playbackState)
    }

    fun currentMediaItemIndex(): Int {
        return player?.currentMediaItemIndex ?: 0
    }
}
