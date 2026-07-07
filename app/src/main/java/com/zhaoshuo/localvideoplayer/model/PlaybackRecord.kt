package com.zhaoshuo.localvideoplayer.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class PlaybackRecord(
    val uri: Uri,
    val title: String,
    val positionMs: Long,
    val durationMs: Long,
    val lastPlayedAt: Long,
) {
    val progress: Float?
        get() = if (durationMs > 0) {
            (positionMs.toFloat() / durationMs).coerceIn(0f, 1f)
        } else {
            null
        }

    val isResumable: Boolean
        get() {
            if (durationMs <= 0 || positionMs < 5_000L) return false
            return positionMs < durationMs * 0.95
        }
}
