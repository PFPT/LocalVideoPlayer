package com.pfpt.localvideoplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.util.formatDuration

@Composable
fun EpisodeListItem(
    episode: VideoItem,
    isCurrent: Boolean,
    onClick: () -> Unit,
    titleColor: Color,
    durationColor: Color,
    checkTint: Color,
    modifier: Modifier = Modifier,
    horizontalPadding: Int = 0,
) {
    val episodeLabel = episode.episode?.let {
        stringResource(R.string.episode_label, it)
    } ?: episode.title
    Row(
        modifier = modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = horizontalPadding.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = episodeLabel,
            style = MaterialTheme.typography.bodyLarge,
            color = titleColor,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = formatDuration(episode.durationMs),
            style = MaterialTheme.typography.bodySmall,
            color = durationColor,
            modifier = Modifier.padding(start = 8.dp, end = 8.dp),
        )
        if (isCurrent) {
            Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = checkTint,
            )
        }
    }
}
