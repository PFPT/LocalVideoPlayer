package com.zhaoshuo.localvideoplayer.ui.components

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
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme
import com.zhaoshuo.localvideoplayer.ui.theme.LumenMetadataGradientEnd
import com.zhaoshuo.localvideoplayer.ui.theme.LumenMetadataGradientStart
import com.zhaoshuo.localvideoplayer.util.formatDuration

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
            .background(
                Brush.verticalGradient(
                    colors = listOf(LumenMetadataGradientStart, LumenMetadataGradientEnd),
                ),
            )
            .padding(horizontal = 20.dp, vertical = 16.dp),
    ) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.Bottom,
        ) {
            Text(
                text = seriesTitle,
                style = MaterialTheme.typography.headlineMedium,
                color = Color.White,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.weight(1f),
            )
            if (totalEpisodes > 1) {
                Text(
                    text = stringResource(R.string.episode_progress, currentIndex + 1, totalEpisodes),
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.8f),
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
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 12.dp),
        )

        if (episodes.size > 1) {
            Text(
                text = stringResource(R.string.episode_picker),
                style = MaterialTheme.typography.titleMedium,
                color = Color.White,
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
                    key = { _, episode -> episode.id },
                ) { index, episode ->
                    EpisodeListItem(
                        episode = episode,
                        isCurrent = index == currentIndex,
                        onClick = { onEpisodeSelected(index) },
                        titleColor = if (index == currentIndex) theme.accent else Color.White,
                        durationColor = Color.White.copy(alpha = 0.6f),
                        checkTint = theme.accent,
                    )
                }
            }
        } else {
            if (currentEpisode.title != seriesTitle) {
                Text(
                    text = currentEpisode.title,
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color.White.copy(alpha = 0.7f),
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
                    color = Color.White.copy(alpha = 0.55f),
                    modifier = Modifier.padding(top = 8.dp),
                )
            }
        }
    }
}
