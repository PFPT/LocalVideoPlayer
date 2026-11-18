package com.pfpt.localvideoplayer.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.ui.theme.LumenOnVideo
import com.pfpt.localvideoplayer.ui.theme.LumenOnVideoMuted

@Composable
fun PlayerTopBar(
    title: String,
    resolution: String?,
    modifier: Modifier = Modifier,
) {
    Column(modifier = modifier.fillMaxWidth()) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            color = LumenOnVideo,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
        )
        if (!resolution.isNullOrBlank()) {
            Text(
                text = resolution,
                style = MaterialTheme.typography.bodySmall,
                color = LumenOnVideoMuted,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}
