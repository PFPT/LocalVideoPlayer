package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.ui.theme.LumenAccentText
import com.zhaoshuo.localvideoplayer.util.formatDuration

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListBottomSheet(
    episodes: List<VideoItem>,
    currentIndex: Int,
    onEpisodeSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    LumenModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(modifier = Modifier.padding(bottom = 32.dp)) {
            Text(
                text = stringResource(R.string.episode_picker),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
            )
            episodes.forEachIndexed { index, episode ->
                val episodeLabel = episode.episode?.let {
                    stringResource(R.string.episode_label, it)
                } ?: episode.title
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable {
                            onEpisodeSelected(index)
                            onDismiss()
                        }
                        .padding(horizontal = 24.dp, vertical = 14.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = episodeLabel,
                        style = MaterialTheme.typography.bodyLarge,
                        color = if (index == currentIndex) LumenAccentText else Color.White,
                        modifier = Modifier.weight(1f),
                    )
                    Text(
                        text = formatDuration(episode.durationMs),
                        style = MaterialTheme.typography.bodySmall,
                        modifier = Modifier.padding(end = 8.dp),
                    )
                    if (index == currentIndex) {
                        Icon(
                            imageVector = Icons.Default.Check,
                            contentDescription = null,
                            tint = LumenAccentText,
                        )
                    }
                }
            }
        }
    }
}
