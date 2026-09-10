package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.ChevronRight
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PlayerMoreSheet(
    shuffleEnabled: Boolean,
    softDecodeEnabled: Boolean,
    isFavorite: Boolean,
    skipIntroSeconds: Int,
    skipOutroSeconds: Int,
    onShuffleToggle: () -> Unit,
    onReplay: () -> Unit,
    onAudioTrack: () -> Unit,
    onVideoTrack: () -> Unit,
    onSubtitles: () -> Unit,
    onPickSubtitleFile: () -> Unit,
    onAspectRatio: () -> Unit,
    onScreenshot: () -> Unit,
    onSoftDecodeToggle: () -> Unit,
    onFavoriteToggle: () -> Unit,
    onInfo: () -> Unit,
    onCast: () -> Unit,
    onSkipIntroSecondsChange: (Int) -> Unit,
    onSkipOutroSecondsChange: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val theme = LocalLumenTheme.current

    LumenModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp)
                .padding(bottom = 32.dp),
        ) {
            Text(
                text = stringResource(R.string.more_options),
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
            )

            MoreActionRow(
                title = stringResource(R.string.shuffle),
                selected = shuffleEnabled,
                onClick = onShuffleToggle,
            )
            MoreActionRow(
                title = stringResource(R.string.replay),
                onClick = {
                    onReplay()
                    onDismiss()
                },
            )
            MoreActionRow(
                title = stringResource(R.string.audio_track),
                trailing = true,
                onClick = onAudioTrack,
            )
            MoreActionRow(
                title = stringResource(R.string.video_track),
                trailing = true,
                onClick = onVideoTrack,
            )
            MoreActionRow(
                title = stringResource(R.string.subtitles),
                trailing = true,
                onClick = onSubtitles,
            )
            MoreActionRow(
                title = stringResource(R.string.subtitle_pick_file),
                trailing = true,
                onClick = onPickSubtitleFile,
            )
            MoreActionRow(
                title = stringResource(R.string.aspect_ratio),
                trailing = true,
                onClick = onAspectRatio,
            )
            MoreActionRow(
                title = stringResource(R.string.screenshot),
                trailing = true,
                onClick = onScreenshot,
            )
            MoreActionRow(
                title = stringResource(R.string.soft_decode),
                selected = softDecodeEnabled,
                onClick = onSoftDecodeToggle,
            )
            MoreActionRow(
                title = stringResource(if (isFavorite) R.string.unmark else R.string.favorite),
                selected = isFavorite,
                onClick = onFavoriteToggle,
            )
            MoreActionRow(
                title = stringResource(R.string.info),
                trailing = true,
                onClick = onInfo,
            )
            MoreActionRow(
                title = stringResource(R.string.cast),
                trailing = true,
                onClick = {
                    onCast()
                    onDismiss()
                },
            )

            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Text(
                    text = stringResource(R.string.settings_player),
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.onSurfaceMuted,
                )
                Text(
                    text = stringResource(R.string.skip_intro_seconds) + " · ${skipIntroSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = skipIntroSeconds.toFloat(),
                    onValueChange = { onSkipIntroSecondsChange(it.toInt()) },
                    valueRange = 0f..180f,
                )
                Text(
                    text = stringResource(R.string.skip_outro_seconds) + " · ${skipOutroSeconds}s",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = skipOutroSeconds.toFloat(),
                    onValueChange = { onSkipOutroSecondsChange(it.toInt()) },
                    valueRange = 0f..180f,
                )
            }
        }
    }
}

@Composable
private fun MoreActionRow(
    title: String,
    onClick: () -> Unit,
    selected: Boolean = false,
    trailing: Boolean = false,
) {
    val theme = LocalLumenTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 14.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) theme.accent else theme.onBackground,
            modifier = Modifier.weight(1f),
        )
        when {
            selected -> Icon(
                imageVector = Icons.Default.Check,
                contentDescription = null,
                tint = theme.accent,
            )
            trailing -> Icon(
                imageVector = Icons.Default.ChevronRight,
                contentDescription = null,
                tint = theme.onSurfaceMuted,
            )
        }
    }
}
