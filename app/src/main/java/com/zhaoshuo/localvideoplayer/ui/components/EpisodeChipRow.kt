package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.model.VideoItem

@Composable
fun EpisodeChipRow(
    episodes: List<VideoItem>,
    currentIndex: Int,
    onEpisodeSelected: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scrollState = rememberScrollState()
    val chipShape = RoundedCornerShape(8.dp)

    Row(
        modifier = modifier
            .fillMaxWidth()
            .horizontalScroll(scrollState),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        episodes.forEachIndexed { index, episode ->
            val isSelected = index == currentIndex
            val label = episode.episode?.toString() ?: (index + 1).toString()
            Box(
                modifier = Modifier
                    .size(44.dp)
                    .clip(chipShape)
                    .background(
                        if (isSelected) {
                            Color.White.copy(alpha = 0.28f)
                        } else {
                            Color.Black.copy(alpha = 0.28f)
                        },
                    )
                    .then(
                        if (isSelected) {
                            Modifier.border(1.dp, Color.White.copy(alpha = 0.45f), chipShape)
                        } else {
                            Modifier
                        },
                    )
                    .clickable { onEpisodeSelected(index) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    text = label,
                    style = MaterialTheme.typography.bodyLarge,
                    color = if (isSelected) Color.White else Color.White.copy(alpha = 0.8f),
                )
            }
        }
    }
}
