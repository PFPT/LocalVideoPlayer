package com.pfpt.localvideoplayer.ui.components

import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.res.stringResource
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.util.formatDuration

@Composable
fun VideoInfoDialog(
    video: VideoItem,
    resolution: String?,
    onDismiss: () -> Unit,
) {
    val lines = buildList {
        add(video.title)
        if (video.durationMs > 0) {
            add(stringResource(R.string.player_info_duration, formatDuration(video.durationMs)))
        }
        if (!resolution.isNullOrBlank()) {
            add(stringResource(R.string.player_info_resolution, resolution))
        }
        add(stringResource(R.string.player_info_path, video.uri.toString()))
    }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.player_info_title)) },
        text = { Text(lines.joinToString("\n\n")) },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.confirm))
            }
        },
    )
}
