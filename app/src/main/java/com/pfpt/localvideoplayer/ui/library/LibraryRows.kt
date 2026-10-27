package com.pfpt.localvideoplayer.ui.library

import android.net.Uri
import androidx.compose.runtime.Composable
import com.pfpt.localvideoplayer.data.PlaybackStore
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.model.VideoLibraryEntry
import com.pfpt.localvideoplayer.model.entryKey
import com.pfpt.localvideoplayer.ui.components.SeriesFolderItem
import com.pfpt.localvideoplayer.ui.components.VideoListItem
import com.pfpt.localvideoplayer.ui.components.rememberEpisodeProgressList
import com.pfpt.localvideoplayer.ui.components.rememberPlaybackProgress
import com.pfpt.localvideoplayer.ui.components.rememberSeriesPlaybackStats

@Composable
internal fun SeriesLibraryRow(
    entry: VideoLibraryEntry.Series,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    isListScrolling: Boolean,
    pinnedKeys: Set<String>,
    playbackStore: PlaybackStore,
    onToggleMark: (String) -> Unit,
    onToggleSelection: (String, Boolean) -> Unit,
    onOpenSeries: (String) -> Unit,
    onIncrementPlayCount: (String) -> Unit,
    onEnterSelection: (String) -> Unit,
    onDeleteUris: (List<Uri>) -> Unit,
) {
    val key = entryKey(entry)
    val isSelected = key in selectedKeys
    val episodeProgress = rememberEpisodeProgressList(
        series = entry.series,
        playbackStore = playbackStore,
    )
    val playbackStats = rememberSeriesPlaybackStats(
        series = entry.series,
        episodeProgress = episodeProgress,
    )
    SeriesFolderItem(
        series = entry.series,
        playbackStats = playbackStats,
        selectionMode = selectionMode,
        selected = isSelected,
        deferThumbnailLoad = isListScrolling,
        marked = key in pinnedKeys,
        onToggleMark = { onToggleMark(key) },
        onClick = {
            if (selectionMode) {
                onToggleSelection(key, isSelected)
            } else {
                onIncrementPlayCount(key)
                onOpenSeries(entry.series.id)
            }
        },
        onLongClick = { onEnterSelection(key) },
        onSwipeDelete = { onDeleteUris(urisForEntry(entry)) },
    )
}

@Composable
internal fun SingleLibraryRow(
    entry: VideoLibraryEntry.Single,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    isListScrolling: Boolean,
    pinnedKeys: Set<String>,
    playbackStore: PlaybackStore,
    onToggleMark: (String) -> Unit,
    onToggleSelection: (String, Boolean) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onEnterSelection: (String) -> Unit,
    onDeleteUris: (List<Uri>) -> Unit,
) {
    val key = entryKey(entry)
    val isSelected = key in selectedKeys
    val progress = rememberPlaybackProgress(
        uriKey = entry.video.uri.toString(),
        playbackStore = playbackStore,
    )
    VideoListItem(
        video = entry.video,
        progress = progress,
        selectionMode = selectionMode,
        selected = isSelected,
        deferThumbnailLoad = isListScrolling,
        marked = key in pinnedKeys,
        onToggleMark = { onToggleMark(key) },
        onClick = {
            if (selectionMode) {
                onToggleSelection(key, isSelected)
            } else {
                onPlayVideo(entry.video)
            }
        },
        onLongClick = { onEnterSelection(key) },
        onSwipeDelete = { onDeleteUris(listOf(entry.video.uri)) },
    )
}

@Composable
internal fun AnyLibraryRow(
    entry: VideoLibraryEntry,
    selectedKeys: Set<String>,
    selectionMode: Boolean,
    isListScrolling: Boolean,
    pinnedKeys: Set<String>,
    playbackStore: PlaybackStore,
    onToggleMark: (String) -> Unit,
    onToggleSelection: (String, Boolean) -> Unit,
    onOpenSeries: (String) -> Unit,
    onIncrementPlayCount: (String) -> Unit,
    onPlayVideo: (VideoItem) -> Unit,
    onEnterSelection: (String) -> Unit,
    onDeleteUris: (List<Uri>) -> Unit,
) {
    when (entry) {
        is VideoLibraryEntry.Series -> SeriesLibraryRow(
            entry = entry,
            selectedKeys = selectedKeys,
            selectionMode = selectionMode,
            isListScrolling = isListScrolling,
            pinnedKeys = pinnedKeys,
            playbackStore = playbackStore,
            onToggleMark = onToggleMark,
            onToggleSelection = onToggleSelection,
            onOpenSeries = onOpenSeries,
            onIncrementPlayCount = onIncrementPlayCount,
            onEnterSelection = onEnterSelection,
            onDeleteUris = onDeleteUris,
        )
        is VideoLibraryEntry.Single -> SingleLibraryRow(
            entry = entry,
            selectedKeys = selectedKeys,
            selectionMode = selectionMode,
            isListScrolling = isListScrolling,
            pinnedKeys = pinnedKeys,
            playbackStore = playbackStore,
            onToggleMark = onToggleMark,
            onToggleSelection = onToggleSelection,
            onPlayVideo = onPlayVideo,
            onEnterSelection = onEnterSelection,
            onDeleteUris = onDeleteUris,
        )
    }
}
