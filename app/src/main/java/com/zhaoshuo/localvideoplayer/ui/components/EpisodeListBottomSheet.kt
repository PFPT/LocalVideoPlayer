package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun EpisodeListBottomSheet(
    episodes: List<VideoItem>,
    currentIndex: Int,
    onEpisodeSelected: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val theme = LocalLumenTheme.current

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
                EpisodeListItem(
                    episode = episode,
                    isCurrent = index == currentIndex,
                    onClick = {
                        onEpisodeSelected(index)
                        onDismiss()
                    },
                    titleColor = if (index == currentIndex) theme.accent else theme.onBackground,
                    durationColor = theme.onSurfaceMuted,
                    checkTint = theme.accent,
                    horizontalPadding = 24,
                )
            }
        }
    }
}
