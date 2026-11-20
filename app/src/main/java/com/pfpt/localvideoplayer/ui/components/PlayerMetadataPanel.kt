package com.pfpt.localvideoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.util.formatDuration

@Composable
fun PlayerMetadataPanel(
    seriesTitle: String,
    currentIndex: Int,
    totalEpisodes: Int,
    currentEpisode: VideoItem,
    episodes: List<VideoItem>,
    playbackSpeed: Float,
    repeatOne: Boolean,
    resolution: String?,
    onEpisodeSelected: (Int) -> Unit,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onMoreClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLumenTheme.current
    val listState = rememberLazyListState()

    LaunchedEffect(currentIndex, episodes.size) {
        if (episodes.size > 1 && currentIndex in episodes.indices) {
            listState.animateScrollToItem(currentIndex)
        }
    }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(theme.background)
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = seriesTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = theme.onBackground,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (totalEpisodes > 1) {
                Text(
                    text = stringResource(R.string.episode_progress, currentIndex + 1, totalEpisodes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceMuted,
                    modifier = Modifier.padding(start = 8.dp, bottom = 4.dp),
                )
            }
        }

        PlayerBottomToolbar(
            playbackSpeed = playbackSpeed,
            repeatOne = repeatOne,
            showEpisodePicker = false,
            onSpeedClick = onSpeedClick,
            onLoopClick = onLoopClick,
            onEpisodePickerClick = {},
            onMoreClick = onMoreClick,
            contentColor = theme.onBackground,
            highlightedColor = theme.accent,
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        if (episodes.size > 1) {
            Text(
                text = stringResource(R.string.episode_picker),
                style = MaterialTheme.typography.titleMedium,
                color = theme.onBackground,
                modifier = Modifier.padding(top = 20.dp),
            )
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxWidth()
                    .weight(1f)
                    .padding(top = 8.dp),
            ) {
                itemsIndexed(
                    items = episodes,
                    key = { _, episode -> episode.uri.toString() },
                ) { index, episode ->
                    EpisodeListItem(
                        episode = episode,
                        isCurrent = index == currentIndex,
                        onClick = { onEpisodeSelected(index) },
                        titleColor = if (index == currentIndex) theme.accent else theme.onBackground,
                        durationColor = theme.onSurfaceMuted,
                        checkTint = theme.accent,
                    )
                }
            }
        } else {
            if (currentEpisode.title != seriesTitle) {
                Text(
                    text = currentEpisode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceMuted,
                    modifier = Modifier.padding(top = 16.dp),
                )
            }
            val metaParts = buildList {
                if (currentEpisode.durationMs > 0) {
                    add(formatDuration(currentEpisode.durationMs))
                }
                if (!resolution.isNullOrBlank()) {
                    add(resolution)
                }
            }
            if (metaParts.isNotEmpty()) {
                Text(
                    text = metaParts.joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = theme.onSurfaceMuted,
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
