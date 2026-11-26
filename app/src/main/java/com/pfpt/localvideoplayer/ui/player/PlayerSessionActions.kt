package com.pfpt.localvideoplayer.ui.player

import android.content.Context
import android.util.Rational
import android.widget.Toast
import androidx.media3.exoplayer.ExoPlayer
import com.pfpt.localvideoplayer.MainActivity
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.data.PlaybackStore
import com.pfpt.localvideoplayer.data.SeriesBookmark
import com.pfpt.localvideoplayer.data.SeriesSkipStore
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.player.PlaybackSession
import com.pfpt.localvideoplayer.player.PlayerProgressState
import com.pfpt.localvideoplayer.player.PlayerViewModel
import com.pfpt.localvideoplayer.player.PlaylistState
import com.pfpt.localvideoplayer.player.SeekCommit
import com.pfpt.localvideoplayer.player.episodeStartPositionMs
import com.pfpt.localvideoplayer.player.seekCommit
import com.pfpt.localvideoplayer.player.seekTargetMs
import com.pfpt.localvideoplayer.player.FloatingPlayerWindow
import com.pfpt.localvideoplayer.player.showsPauseIcon
import com.pfpt.localvideoplayer.ui.PlayerUiState
import com.pfpt.localvideoplayer.util.PictureInPictureHelper
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal class PlayerSessionActions(
    private val context: Context,
    private val exoPlayer: ExoPlayer,
    private val playlist: List<VideoItem>,
    private val ui: PlayerUiState,
    private val progress: PlayerProgressState,
    private val playbackStore: PlaybackStore,
    private val playbackSession: PlaybackSession,
    private val playerViewModel: PlayerViewModel,
    private val scope: CoroutineScope,
    private val activity: MainActivity?,
    private val seriesSkipStore: SeriesSkipStore,
    private val seriesKey: String?,
    private val seriesPlaylistFor: (VideoItem) -> PlaylistState,
    private val onReplacePlaylist: (PlaylistState) -> Unit,
    private val episodes: List<VideoItem>,
    private val displayDurationMs: Long,
    private val effectiveIntroSeconds: Int,
    private val isCasting: Boolean,
    private val hasNext: Boolean,
    private val resolvedDurationMs: () -> Long,
) {
    suspend fun saveCurrentProgress() {
        val video = playlist.getOrNull(ui.currentIndex) ?: return
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: video.durationMs
        if (duration <= 0) return
        playerViewModel.saveProgress(
            video = video,
            positionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            durationMs = duration,
        )
    }

    suspend fun openEpisode(index: Int) {
        val video = playlist.getOrNull(index) ?: return
        val startMs = episodeStartPositionMs(
            resumePositionMs = playbackStore.getResumePositionMs(video.uri, video.durationMs),
            replayPositionMs = ui.replayPositionMs,
        )
        ui.replayPositionMs = null
        playbackSession.attachSubtitle(index)
        playbackSession.resumedIndices.add(index)
        if (startMs > 0L || exoPlayer.currentMediaItemIndex != index) {
            exoPlayer.seekTo(index, startMs)
        }
        progress.currentPositionMs = startMs
        ui.previewPositionMs = startMs
        ui.currentIndex = index
    }

    fun selectEpisode(index: Int) {
        if (index !in playlist.indices) return
        scope.launch {
            saveCurrentProgress()
            playbackSession.resumedIndices.remove(index)
            openEpisode(index)
        }
    }

    fun playAdjacentEpisode(delta: Int, markCurrentComplete: Boolean = false) {
        val target = ui.currentIndex + delta
        if (target !in playlist.indices) return
        playbackSession.skipBy(delta, markCurrentComplete)
    }

    fun onSeekPreview(position: Long) {
        ui.previewPositionMs = position
    }

    fun onSeekPreviewFrame(position: Long) {
        ui.previewPositionMs = position
    }

    fun commitSeek(targetMs: Long): Boolean {
        val decision = seekCommit(
            targetMs = targetMs,
            playerDurationMs = exoPlayer.duration,
            resolvedDurationMs = resolvedDurationMs(),
            currentPositionMs = exoPlayer.currentPosition.coerceAtLeast(0L),
            seekable = exoPlayer.isCurrentMediaItemSeekable,
        )
        return when (decision) {
            SeekCommit.Reject -> false
            is SeekCommit.Apply -> {
                exoPlayer.seekTo(decision.positionMs)
                progress.currentPositionMs = decision.positionMs
                ui.previewPositionMs = decision.positionMs
                true
            }
        }
    }

    fun onSeekCommitted(position: Long) {
        commitSeek(position)
    }

    fun onSeekingChange(seeking: Boolean) {
        ui.isSeeking = seeking
        if (seeking) {
            val position = exoPlayer.currentPosition.coerceAtLeast(0L)
            progress.currentPositionMs = position
            ui.seekStartPositionMs = position
            ui.previewPositionMs = position
        } else {
            ui.resetControlsTimer()
        }
    }

    fun seekBy(deltaMs: Long) {
        val duration = exoPlayer.duration.takeIf { it > 0 } ?: resolvedDurationMs()
        val position = exoPlayer.currentPosition.coerceAtLeast(0L)
        val target = seekTargetMs(
            positionMs = position,
            deltaMs = deltaMs,
            durationMs = duration,
            bufferedPositionMs = exoPlayer.bufferedPosition.coerceAtLeast(0L),
            seekable = exoPlayer.isCurrentMediaItemSeekable,
        )
        if (!commitSeek(target)) return
        ui.seekStartPositionMs = position
        ui.doubleTapSeekDeltaMs = deltaMs
        ui.showDoubleTapSeek = true
    }

    fun currentPipAspectRatio(): Rational =
        PictureInPictureHelper.aspectRatioForVideo(ui.videoWidth, ui.videoHeight)

    fun onPipClick() {
        if (ui.controlsLocked) return
        ui.resetControlsTimer()
        FloatingPlayerWindow.request(context)
    }

    fun markIntro() {
        val key = seriesKey ?: return
        val seconds = (exoPlayer.currentPosition.coerceAtLeast(0L) / 1_000L).toInt().coerceIn(0, 600)
        if (seconds <= 0) {
            Toast.makeText(context, context.getString(R.string.series_skip_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            seriesSkipStore.setIntroSeconds(key, seconds)
            Toast.makeText(
                context,
                context.getString(R.string.series_intro_saved, seconds),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun markOutro() {
        val key = seriesKey ?: return
        val positionMs = exoPlayer.currentPosition.coerceAtLeast(0L)
        if (displayDurationMs <= 0 || positionMs >= displayDurationMs) {
            Toast.makeText(context, context.getString(R.string.series_skip_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        val seconds = ((displayDurationMs - positionMs) / 1_000L).toInt().coerceIn(0, 600)
        if (seconds <= 0) {
            Toast.makeText(context, context.getString(R.string.series_skip_invalid), Toast.LENGTH_SHORT).show()
            return
        }
        scope.launch {
            seriesSkipStore.setOutroSeconds(key, seconds)
            Toast.makeText(
                context,
                context.getString(R.string.series_outro_saved, seconds),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }

    fun clearIntro() {
        val key = seriesKey ?: return
        scope.launch {
            seriesSkipStore.clearIntro(key)
            Toast.makeText(context, context.getString(R.string.series_intro_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    fun clearOutro() {
        val key = seriesKey ?: return
        scope.launch {
            seriesSkipStore.clearOutro(key)
            Toast.makeText(context, context.getString(R.string.series_outro_cleared), Toast.LENGTH_SHORT).show()
        }
    }

    fun skipIntro() {
        if (isCasting) return
        val position = effectiveIntroSeconds * 1_000L
        exoPlayer.seekTo(position)
        rememberSkipPosition(position)
    }

    fun skipOutro() {
        if (isCasting) return
        if (hasNext) {
            playAdjacentEpisode(1, markCurrentComplete = true)
        } else if (displayDurationMs > 0L) {
            exoPlayer.seekTo(displayDurationMs)
            rememberSkipPosition(displayDurationMs)
        }
    }

    fun jumpBookmark(bookmark: SeriesBookmark) {
        val target = episodes.getOrNull(bookmark.episodeIndex) ?: return
        val series = seriesPlaylistFor(target)
        val sameList = playlist.size == series.items.size &&
            playlist.indices.all { index ->
                playlist[index].uri.toString() == series.items[index].uri.toString()
            }
        ui.replayPositionMs = bookmark.positionMs
        ui.showMoreSheet = false
        val index = series.currentIndex
        if (sameList) {
            scope.launch {
                saveCurrentProgress()
                playbackSession.resumedIndices.remove(index)
                openEpisode(index)
                exoPlayer.seekTo(index, bookmark.positionMs)
                progress.currentPositionMs = bookmark.positionMs
            }
        } else {
            ui.currentIndex = index
            onReplacePlaylist(series)
        }
    }

    private fun rememberSkipPosition(positionMs: Long) {
        progress.currentPositionMs = positionMs
        ui.hiddenPlaybackPositionMs = positionMs
        ui.skipPositionReady = true
    }
}
