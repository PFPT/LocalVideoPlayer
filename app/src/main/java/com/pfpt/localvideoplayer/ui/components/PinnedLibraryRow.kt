package com.pfpt.localvideoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.data.PlaybackStore
import com.pfpt.localvideoplayer.model.SeriesGroupKind
import com.pfpt.localvideoplayer.model.VideoLibraryEntry
import com.pfpt.localvideoplayer.model.entryKey
import com.pfpt.localvideoplayer.ui.rememberPlayerSharedOpen
import com.pfpt.localvideoplayer.ui.theme.LocalAppIcons
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenCardPadding
import com.pfpt.localvideoplayer.ui.theme.LumenListItemGap
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceSm
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceXs
import com.pfpt.localvideoplayer.ui.theme.LumenThumbCorner
import com.pfpt.localvideoplayer.util.formatDuration

@Composable
fun PinnedLibraryRow(
    entries: List<VideoLibraryEntry>,
    playbackStore: PlaybackStore,
    onEntryClick: (VideoLibraryEntry) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (entries.isEmpty()) return
    Column(modifier = modifier.padding(bottom = LumenSpaceSm)) {
        Text(
            text = stringResource(R.string.section_pinned),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = LumenSpaceSm),
        )
        LazyRow(
            contentPadding = PaddingValues(0.dp),
            horizontalArrangement = Arrangement.spacedBy(LumenListItemGap),
        ) {
            items(entries, key = { entryKey(it) }) { entry ->
                PinnedLibraryCard(
                    entry = entry,
                    playbackStore = playbackStore,
                    onClick = { onEntryClick(entry) },
                )
            }
        }
    }
}

@Composable
private fun PinnedLibraryCard(
    entry: VideoLibraryEntry,
    playbackStore: PlaybackStore,
    onClick: () -> Unit,
) {
    val video = when (entry) {
        is VideoLibraryEntry.Single -> entry.video
        is VideoLibraryEntry.Series -> entry.series.episodes.firstOrNull()
    }
    val opensPlayer = entry is VideoLibraryEntry.Single
    val sharedOpen = rememberPlayerSharedOpen(
        candidateKey = video?.uri?.toString(),
        opensPlayer = opensPlayer,
        dateModifiedSeconds = video?.dateModified ?: 0L,
        onClick = onClick,
    )
    val progress = if (entry is VideoLibraryEntry.Single) {
        rememberPlaybackProgress(entry.video.uri.toString(), playbackStore)
    } else if (entry is VideoLibraryEntry.Series) {
        val episodeProgress = rememberEpisodeProgressList(entry.series, playbackStore)
        rememberSeriesPlaybackStats(entry.series, episodeProgress).seriesProgress
    } else {
        null
    }
    val title = when (entry) {
        is VideoLibraryEntry.Single -> entry.video.title
        is VideoLibraryEntry.Series -> entry.series.title
    }
    val subtitle = pinnedSubtitle(entry)
    val theme = LocalLumenTheme.current
    val cardShape = RoundedCornerShape(LumenThumbCorner)
    Column(
        modifier = Modifier
            .width(120.dp)
            .clip(cardShape)
            .clickable(onClick = sharedOpen.onClick)
            .background(theme.glassFill)
            .border(1.dp, theme.glassBorder, cardShape)
            .padding(LumenCardPadding),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(72.dp)
                .clip(RoundedCornerShape(LumenThumbCorner)),
        ) {
            if (video != null) {
                MediaThumbnail(
                    thumbnailUri = video.uri,
                    dateModifiedSeconds = video.dateModified,
                    modifier = Modifier.fillMaxSize(),
                    sharedContentKey = sharedOpen.contentKey,
                    onFlightMeasured = sharedOpen.onMeasured,
                )
            }
            if (entry is VideoLibraryEntry.Series) {
                Icon(
                    imageVector = LocalAppIcons.current.folder,
                    contentDescription = null,
                    tint = theme.accent.copy(alpha = 0.85f),
                    modifier = Modifier
                        .align(Alignment.Center)
                        .width(28.dp)
                        .height(28.dp),
                )
            }
            MediaProgressRail(
                progress = progress,
                modifier = Modifier.align(Alignment.BottomStart),
            )
        }
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = LumenSpaceSm),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = theme.onSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = LumenSpaceXs),
            )
        }
    }
}

@Composable
private fun pinnedSubtitle(entry: VideoLibraryEntry): String {
    return when (entry) {
        is VideoLibraryEntry.Single -> {
            if (entry.video.durationMs > 0) formatDuration(entry.video.durationMs) else ""
        }
        is VideoLibraryEntry.Series -> {
            val total = entry.series.episodes.sumOf { it.durationMs }
            val count = entry.series.episodes.size
            if (entry.series.groupKind == SeriesGroupKind.EPISODE) {
                stringResource(R.string.series_folder_subtitle, count, formatDuration(total))
            } else {
                stringResource(R.string.folder_subtitle, count, formatDuration(total))
            }
        }
    }
}
