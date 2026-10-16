package com.pfpt.localvideoplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.data.PlaybackStore
import com.pfpt.localvideoplayer.model.SeriesGroupKind
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.model.VideoSeries
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.util.formatDuration
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val dateAddedFormatter = ThreadLocal.withInitial {
    SimpleDateFormat("yyyy/MM/dd", Locale.getDefault())
}

data class SeriesPlaybackStats(
    val totalDuration: Long,
    val watchedCount: Int,
    val seriesProgress: Float?,
)

@Composable
fun rememberPlaybackProgress(
    uriKey: String,
    playbackStore: PlaybackStore,
): Float? {
    val progress by remember(uriKey, playbackStore) {
        playbackStore.progressOf(uriKey)
    }.collectAsStateWithLifecycle(
        initialValue = playbackStore.snapshot.value.records[uriKey]?.progress,
    )
    return progress
}

@Composable
fun rememberEpisodeProgressList(
    series: VideoSeries,
    playbackStore: PlaybackStore,
): List<Float> {
    val uriKeys = remember(series.id, series.episodes) {
        series.episodes.map { it.uri.toString() }
    }
    val progress by remember(uriKeys, playbackStore) {
        playbackStore.episodeProgress(uriKeys)
    }.collectAsStateWithLifecycle(
        initialValue = uriKeys.map { key -> playbackStore.snapshot.value.records[key]?.progress ?: 0f },
    )
    return progress
}

@Composable
fun rememberSeriesPlaybackStats(
    series: VideoSeries,
    episodeProgress: List<Float>,
): SeriesPlaybackStats {
    return remember(series.id, series.episodes, episodeProgress) {
        val totalDuration = series.episodes.sumOf { it.durationMs }
        val watchedCount = episodeProgress.count { it > 0.05f }
        val seriesProgress = if (series.episodes.isNotEmpty()) {
            watchedCount.toFloat() / series.episodes.size
        } else {
            null
        }
        SeriesPlaybackStats(
            totalDuration = totalDuration,
            watchedCount = watchedCount,
            seriesProgress = seriesProgress,
        )
    }
}

@Composable
fun VideoListItem(
    video: VideoItem,
    onClick: () -> Unit,
    progress: Float? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSwipeDelete: (() -> Unit)? = null,
    deferThumbnailLoad: Boolean = false,
    marked: Boolean = false,
    onToggleMark: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val subtitle = rememberVideoSubtitle(video, progress)
    val markButton: (@Composable () -> Unit)? = if (onToggleMark != null && !selectionMode) {
        { MarkToggleButton(marked = marked, onToggleMark = onToggleMark) }
    } else {
        null
    }

    SwipeableListItem(
        enabled = onSwipeDelete != null && !selectionMode,
        onSwipeDelete = onSwipeDelete ?: {},
    ) {
        SelectableListItem(
            selected = selected,
            selectionMode = selectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
        ) {
            LumenMediaCard(
                title = video.title,
                subtitle = subtitle,
                thumbnailUri = video.uri,
                thumbnailDateModified = video.dateModified,
                onClick = onClick,
                progress = progress,
                clickable = false,
                opaqueBackground = true,
                deferThumbnailLoad = deferThumbnailLoad,
                modifier = modifier,
                trailingContent = markButton,
            )
        }
    }
}

@Composable
fun SeriesFolderItem(
    series: VideoSeries,
    onClick: () -> Unit,
    playbackStats: SeriesPlaybackStats,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSwipeDelete: (() -> Unit)? = null,
    deferThumbnailLoad: Boolean = false,
    marked: Boolean = false,
    onToggleMark: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val totalDuration = playbackStats.totalDuration
    val firstEpisode = series.episodes.firstOrNull()
    val watchedCount = playbackStats.watchedCount
    val seriesProgress = playbackStats.seriesProgress

    val subtitle = when (series.groupKind) {
        SeriesGroupKind.EPISODE -> buildString {
            append(stringResource(R.string.series_folder_subtitle, series.episodes.size, formatDuration(totalDuration)))
            if (watchedCount > 0) {
                append(" · ")
                append(stringResource(R.string.watched_episodes, watchedCount, series.episodes.size))
            }
        }
        else -> buildString {
            append(stringResource(R.string.folder_subtitle, series.episodes.size, formatDuration(totalDuration)))
            if (watchedCount > 0) {
                append(" · ")
                append(stringResource(R.string.watched_videos, watchedCount, series.episodes.size))
            }
        }
    }
    val markButton: (@Composable () -> Unit)? = if (onToggleMark != null && !selectionMode) {
        { MarkToggleButton(marked = marked, onToggleMark = onToggleMark) }
    } else {
        null
    }

    SwipeableListItem(
        enabled = onSwipeDelete != null && !selectionMode,
        onSwipeDelete = onSwipeDelete ?: {},
    ) {
        SelectableListItem(
            selected = selected,
            selectionMode = selectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
        ) {
            LumenMediaCard(
                title = series.title,
                subtitle = subtitle,
                thumbnailUri = firstEpisode?.uri,
                onClick = onClick,
                progress = seriesProgress,
                clickable = false,
                opaqueBackground = true,
                deferThumbnailLoad = deferThumbnailLoad,
                modifier = modifier,
                thumbnailContent = {
                    Box(modifier = Modifier.fillMaxSize()) {
                        if (firstEpisode != null) {
                            MediaThumbnail(
                                thumbnailUri = firstEpisode.uri,
                                dateModifiedSeconds = firstEpisode.dateModified,
                                modifier = Modifier.fillMaxSize(),
                                deferLoad = deferThumbnailLoad,
                            )
                        }
                        Icon(
                            imageVector = Icons.Default.Folder,
                            contentDescription = null,
                            tint = LocalLumenTheme.current.accent.copy(alpha = 0.85f),
                            modifier = Modifier
                                .align(Alignment.Center)
                                .width(28.dp)
                                .height(28.dp),
                        )
                    }
                },
                trailingContent = markButton,
            )
        }
    }
}

@Composable
fun EpisodeListItem(
    episode: VideoItem,
    episodeNumber: Int?,
    onClick: () -> Unit,
    progress: Float? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSwipeDelete: (() -> Unit)? = null,
    deferThumbnailLoad: Boolean = false,
    modifier: Modifier = Modifier,
) {
    val title = if (episodeNumber != null) {
        stringResource(R.string.episode_label, episodeNumber) + " · " + episode.title
    } else {
        episode.title
    }
    val subtitle = rememberEpisodeSubtitle(episode, progress)

    SwipeableListItem(
        enabled = onSwipeDelete != null && !selectionMode,
        onSwipeDelete = onSwipeDelete ?: {},
    ) {
        SelectableListItem(
            selected = selected,
            selectionMode = selectionMode,
            onClick = onClick,
            onLongClick = onLongClick,
        ) {
            LumenMediaCard(
                title = title,
                subtitle = subtitle,
                thumbnailUri = episode.uri,
                thumbnailDateModified = episode.dateModified,
                onClick = onClick,
                progress = progress,
                clickable = false,
                opaqueBackground = true,
                deferThumbnailLoad = deferThumbnailLoad,
                modifier = modifier,
            )
        }
    }
}

@Composable
private fun rememberVideoSubtitle(video: VideoItem, progress: Float?): String {
    val base = remember(video.id, video.durationMs, video.dateAdded) {
        "${formatDuration(video.durationMs)} · ${formatDateAdded(video.dateAdded)}"
    }
    if (progress == null || progress <= 0.01f) return base
    val watchedSuffix = stringResource(R.string.watched_percent, (progress * 100).toInt())
    return remember(base, progress) { "$base · $watchedSuffix" }
}

@Composable
private fun rememberEpisodeSubtitle(episode: VideoItem, progress: Float?): String {
    val base = remember(episode.id, episode.durationMs) {
        formatDuration(episode.durationMs)
    }
    if (progress == null || progress <= 0.01f) return base
    val watchedSuffix = stringResource(R.string.watched_percent, (progress * 100).toInt())
    return remember(base, progress) { "$base · $watchedSuffix" }
}

private fun formatDateAdded(dateAdded: Long): String {
    val millis = if (dateAdded < 1_000_000_000_000L) dateAdded * 1000 else dateAdded
    return dateAddedFormatter.get()!!.format(Date(millis))
}

@Composable
private fun MarkToggleButton(
    marked: Boolean,
    onToggleMark: () -> Unit,
) {
    val theme = LocalLumenTheme.current
    IconButton(onClick = onToggleMark) {
        Icon(
            imageVector = if (marked) Icons.Filled.Star else Icons.Outlined.StarOutline,
            contentDescription = stringResource(if (marked) R.string.unmark else R.string.mark),
            tint = if (marked) theme.accent else theme.accent.copy(alpha = 0.45f),
            modifier = Modifier.size(22.dp),
        )
    }
}
