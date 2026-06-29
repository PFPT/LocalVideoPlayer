package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.width
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Folder
import androidx.compose.material3.Icon
import androidx.compose.runtime.Composable
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.model.PlaybackRecord
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoSeries
import com.zhaoshuo.localvideoplayer.ui.theme.LumenAccentText
import com.zhaoshuo.localvideoplayer.util.formatDuration
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
fun rememberSeriesPlaybackStats(
    series: VideoSeries,
    playbackRecords: Map<String, PlaybackRecord>,
): SeriesPlaybackStats {
    val progressSignature = series.episodes.joinToString(";") { episode ->
        val progress = playbackRecords[episode.uri.toString()]?.progress ?: 0f
        if (progress > 0.05f) "1" else "0"
    }
    return remember(series.id, progressSignature) {
        val totalDuration = series.episodes.sumOf { it.durationMs }
        val watchedCount = series.episodes.count { episode ->
            playbackRecords[episode.uri.toString()]?.progress?.let { it > 0.05f } == true
        }
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
    playbackRecord: PlaybackRecord? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSwipeDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val progress = playbackRecord?.progress
    val subtitle = buildVideoSubtitle(video, playbackRecord)

    val itemContent = @Composable {
        LumenMediaCard(
            title = video.title,
            subtitle = subtitle,
            videoId = video.id,
            onClick = onClick,
            progress = progress,
            clickable = false,
            opaqueBackground = true,
            modifier = modifier,
        )
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
            itemContent()
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
    modifier: Modifier = Modifier,
) {
    val totalDuration = playbackStats.totalDuration
    val firstEpisode = series.episodes.firstOrNull()
    val watchedCount = playbackStats.watchedCount
    val seriesProgress = playbackStats.seriesProgress

    val subtitle = buildString {
        append(stringResource(R.string.series_folder_subtitle, series.episodes.size, formatDuration(totalDuration)))
        if (watchedCount > 0) {
            append(" · ")
            append(stringResource(R.string.watched_episodes, watchedCount, series.episodes.size))
        }
    }

    val itemContent = @Composable {
        LumenMediaCard(
            title = series.title,
            subtitle = subtitle,
            videoId = firstEpisode?.id,
            onClick = onClick,
            progress = seriesProgress,
            clickable = false,
            opaqueBackground = true,
            modifier = modifier,
            thumbnailContent = {
                Box(modifier = Modifier.fillMaxSize()) {
                    if (firstEpisode != null) {
                        MediaThumbnail(
                            videoId = firstEpisode.id,
                            modifier = Modifier.fillMaxSize(),
                        )
                    }
                    Icon(
                        imageVector = Icons.Default.Folder,
                        contentDescription = null,
                        tint = LumenAccentText.copy(alpha = 0.85f),
                        modifier = Modifier
                            .align(Alignment.Center)
                            .width(28.dp)
                            .height(28.dp),
                    )
                }
            },
        )
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
            itemContent()
        }
    }
}

@Composable
fun EpisodeListItem(
    episode: VideoItem,
    episodeNumber: Int?,
    onClick: () -> Unit,
    playbackRecord: PlaybackRecord? = null,
    selectionMode: Boolean = false,
    selected: Boolean = false,
    onLongClick: () -> Unit = {},
    onSwipeDelete: (() -> Unit)? = null,
    modifier: Modifier = Modifier,
) {
    val title = if (episodeNumber != null) {
        stringResource(R.string.episode_label, episodeNumber) + " · " + episode.title
    } else {
        episode.title
    }
    val progress = playbackRecord?.progress
    val subtitle = buildEpisodeSubtitle(episode, playbackRecord)

    val itemContent = @Composable {
        LumenMediaCard(
            title = title,
            subtitle = subtitle,
            videoId = episode.id,
            onClick = onClick,
            progress = progress,
            clickable = false,
            opaqueBackground = true,
            modifier = modifier,
        )
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
            itemContent()
        }
    }
}

@Composable
private fun buildVideoSubtitle(video: VideoItem, record: PlaybackRecord?): String {
    return buildString {
        append(formatDuration(video.durationMs))
        append(" · ")
        append(formatDateAdded(video.dateAdded))
        record?.progress?.let { progress ->
            if (progress > 0.01f) {
                append(" · ")
                append(stringResource(R.string.watched_percent, (progress * 100).toInt()))
            }
        }
    }
}

@Composable
private fun buildEpisodeSubtitle(episode: VideoItem, record: PlaybackRecord?): String {
    return buildString {
        append(formatDuration(episode.durationMs))
        record?.progress?.let { progress ->
            if (progress > 0.01f) {
                append(" · ")
                append(stringResource(R.string.watched_percent, (progress * 100).toInt()))
            }
        }
    }
}

private fun formatDateAdded(dateAdded: Long): String {
    val millis = if (dateAdded < 1_000_000_000_000L) dateAdded * 1000 else dateAdded
    return dateAddedFormatter.get()!!.format(Date(millis))
}
