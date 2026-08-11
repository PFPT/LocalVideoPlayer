package com.zhaoshuo.localvideoplayer.player

import android.net.Uri
import androidx.lifecycle.ViewModel
import com.zhaoshuo.localvideoplayer.data.PlaybackStore
import com.zhaoshuo.localvideoplayer.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

class PlayerViewModel(
    private val playbackStore: PlaybackStore,
) : ViewModel() {
    suspend fun saveProgress(
        video: VideoItem,
        positionMs: Long,
        durationMs: Long,
    ) {
        if (durationMs <= 0) return
        withContext(Dispatchers.IO) {
            playbackStore.saveProgress(
                uri = video.uri,
                title = video.title,
                positionMs = positionMs.coerceAtLeast(0L),
                durationMs = durationMs,
            )
        }
    }

    suspend fun resumePosition(uri: Uri, durationMs: Long): Long {
        return playbackStore.getResumePositionMs(uri, durationMs)
    }
}
