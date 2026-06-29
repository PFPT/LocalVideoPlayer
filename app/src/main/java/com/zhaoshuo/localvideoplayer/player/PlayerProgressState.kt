package com.zhaoshuo.localvideoplayer.player

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.media3.exoplayer.ExoPlayer
import kotlinx.coroutines.delay
import kotlin.math.abs

private const val POSITION_POLL_INTERVAL_MS = 1000L
private const val POSITION_UPDATE_THRESHOLD_MS = 250L

@Stable
class PlayerProgressState {
    var currentPositionMs by mutableLongStateOf(0L)
    var bufferedPositionMs by mutableLongStateOf(0L)
    var durationMs by mutableLongStateOf(0L)

    fun syncFromPlayer(player: ExoPlayer, updatePosition: Boolean = true) {
        if (updatePosition) {
            currentPositionMs = player.currentPosition.coerceAtLeast(0L)
        }
        bufferedPositionMs = player.bufferedPosition.coerceAtLeast(0L)
        val duration = player.duration
        if (duration > 0) {
            durationMs = duration
        }
    }

    fun resetForNewItem() {
        currentPositionMs = 0L
        durationMs = 0L
        bufferedPositionMs = 0L
    }
}

@Composable
fun rememberPlayerProgressState(
    player: ExoPlayer,
    isSeeking: Boolean,
): PlayerProgressState {
    val state = remember { PlayerProgressState() }
    LaunchedEffect(player, isSeeking) {
        while (true) {
            if (!isSeeking) {
                val position = player.currentPosition.coerceAtLeast(0L)
                if (abs(position - state.currentPositionMs) >= POSITION_UPDATE_THRESHOLD_MS) {
                    state.currentPositionMs = position
                }
                val buffered = player.bufferedPosition.coerceAtLeast(0L)
                if (buffered != state.bufferedPositionMs) {
                    state.bufferedPositionMs = buffered
                }
                val duration = player.duration
                if (duration > 0 && duration != state.durationMs) {
                    state.durationMs = duration
                }
            }
            delay(POSITION_POLL_INTERVAL_MS)
        }
    }
    return state
}
