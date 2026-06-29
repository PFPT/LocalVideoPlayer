package com.zhaoshuo.localvideoplayer.player

import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer

object PlayerSession {

    private var player: ExoPlayer? = null
    var hasNextProvider: () -> Boolean = { false }
    var onStateChanged: (() -> Unit)? = null

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

    fun seekToNext(): Boolean {
        val current = player ?: return false
        if (!current.hasNextMediaItem()) return false
        current.seekToNextMediaItem()
        onStateChanged?.invoke()
        return true
    }

    fun togglePlayPause() {
        val current = player ?: return
        if (current.isPlaying) {
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
        return player?.isPlaying == true
    }

    fun currentMediaItemIndex(): Int {
        return player?.currentMediaItemIndex ?: 0
    }
}
