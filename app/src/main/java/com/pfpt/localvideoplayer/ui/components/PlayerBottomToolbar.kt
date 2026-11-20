package com.pfpt.localvideoplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.ui.theme.LumenOnVideo
import com.pfpt.localvideoplayer.ui.theme.LumenAccentText

@Composable
fun PlayerBottomToolbar(
    playbackSpeed: Float,
    repeatOne: Boolean,
    showEpisodePicker: Boolean,
    onSpeedClick: () -> Unit,
    onLoopClick: () -> Unit,
    onEpisodePickerClick: () -> Unit,
    onMoreClick: (() -> Unit)? = null,
    contentColor: Color = LumenOnVideo,
    highlightedColor: Color = LumenAccentText,
    modifier: Modifier = Modifier,
) {
    Row(
        modifier = modifier
            .padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.spacedBy(24.dp, Alignment.CenterHorizontally),
    ) {
        ToolbarItem(
            text = "${formatSpeedLabel(playbackSpeed)}x",
            onClick = onSpeedClick,
            highlighted = true,
            contentColor = contentColor,
            highlightedColor = highlightedColor,
        )
        ToolbarItem(
            text = stringResource(R.string.loop),
            onClick = onLoopClick,
            highlighted = repeatOne,
            contentColor = contentColor,
            highlightedColor = highlightedColor,
        )
        if (showEpisodePicker) {
            ToolbarItem(
                text = stringResource(R.string.episode_picker),
                onClick = onEpisodePickerClick,
                contentColor = contentColor,
                highlightedColor = highlightedColor,
            )
        }
        if (onMoreClick != null) {
            ToolbarItem(
                text = stringResource(R.string.more_options),
                onClick = onMoreClick,
                contentColor = contentColor,
                highlightedColor = highlightedColor,
            )
        }
    }
}

@Composable
private fun ToolbarItem(
    text: String,
    onClick: () -> Unit,
    contentColor: Color,
    highlightedColor: Color,
    highlighted: Boolean = false,
) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = if (highlighted) highlightedColor else contentColor.copy(alpha = 0.9f),
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

private fun formatSpeedLabel(speed: Float): String {
    return if (speed == speed.toLong().toFloat()) {
        speed.toLong().toString()
    } else {
        speed.toString()
    }
}
