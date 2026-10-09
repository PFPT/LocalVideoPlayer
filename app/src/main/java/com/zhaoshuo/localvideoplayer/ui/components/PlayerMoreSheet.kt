package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.data.SubtitleTextSize
import com.zhaoshuo.localvideoplayer.player.PlaybackSession
import com.zhaoshuo.localvideoplayer.player.SleepChoice
import com.zhaoshuo.localvideoplayer.player.formatSleepRemaining
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme
import kotlin.math.roundToInt

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun PlayerMoreSheet(
    shuffleEnabled: Boolean,
    softDecodeEnabled: Boolean,
    isFavorite: Boolean,
    skipIntroSeconds: Int,
    skipOutroSeconds: Int,
    subtitleTextSize: SubtitleTextSize,
    subtitleYellow: Boolean,
    subtitleDelayMs: Int,
    abPointA: Long?,
    abPointB: Long?,
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
    onSubtitleTextSize: (SubtitleTextSize) -> Unit,
    onSubtitleYellowToggle: () -> Unit,
    onSubtitleDelayChange: (Int) -> Unit,
    onSetAbPointA: () -> Unit,
    onSetAbPointB: () -> Unit,
    onClearAb: () -> Unit,
    bookmarks: List<Long>,
    onAddBookmark: () -> Unit,
    onJumpBookmark: (Long) -> Unit,
    onRemoveBookmark: (Long) -> Unit,
    playbackSession: PlaybackSession,
    onSleepSelected: (SleepChoice) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val theme = LocalLumenTheme.current
    val sleepChoice by playbackSession.state.collectAsStateWithLifecycle()
    val sleepRemainingMs by playbackSession.sleepRemainingMs.collectAsStateWithLifecycle()
    val selectedSleep = sleepChoice.sleepChoice

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
                title = stringResource(R.string.ab_set_a) + (abPointA?.let { " · ${formatAb(it)}" } ?: ""),
                onClick = onSetAbPointA,
            )
            MoreActionRow(
                title = stringResource(R.string.ab_set_b) + (abPointB?.let { " · ${formatAb(it)}" } ?: ""),
                onClick = onSetAbPointB,
            )
            MoreActionRow(
                title = stringResource(R.string.ab_clear),
                onClick = onClearAb,
            )
            MoreActionRow(
                title = stringResource(R.string.bookmark_add),
                onClick = onAddBookmark,
            )
            bookmarks.forEach { positionMs ->
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 16.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = formatAb(positionMs),
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.onBackground,
                        modifier = Modifier
                            .weight(1f)
                            .clickable { onJumpBookmark(positionMs) }
                            .padding(vertical = 8.dp),
                    )
                    Text(
                        text = stringResource(R.string.bookmark_delete),
                        color = theme.onSurfaceMuted,
                        modifier = Modifier
                            .clickable { onRemoveBookmark(positionMs) }
                            .padding(vertical = 8.dp),
                    )
                }
            }
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
                    text = stringResource(R.string.sleep_timer),
                    style = MaterialTheme.typography.titleSmall,
                    color = theme.onSurfaceMuted,
                )
                val remainingMs = sleepRemainingMs
                if (selectedSleep is SleepChoice.Minutes && remainingMs != null) {
                    Text(
                        text = stringResource(R.string.sleep_remaining, formatSleepRemaining(remainingMs)),
                        style = MaterialTheme.typography.bodyMedium,
                        color = theme.accent,
                    )
                }
                FlowRow(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    SleepChoiceChip(
                        label = stringResource(R.string.sleep_off),
                        selected = selectedSleep is SleepChoice.Off,
                        onClick = { onSleepSelected(SleepChoice.Off) },
                    )
                    SleepChoice.minutePresets.forEach { minutes ->
                        SleepChoiceChip(
                            label = stringResource(R.string.sleep_minutes, minutes),
                            selected = selectedSleep is SleepChoice.Minutes && selectedSleep.minutes == minutes,
                            onClick = { onSleepSelected(SleepChoice.Minutes(minutes)) },
                        )
                    }
                    SleepChoiceChip(
                        label = stringResource(R.string.sleep_end_item),
                        selected = selectedSleep is SleepChoice.EndOfItem,
                        onClick = { onSleepSelected(SleepChoice.EndOfItem) },
                    )
                }
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
                Text(
                    text = stringResource(R.string.subtitle_size),
                    style = MaterialTheme.typography.bodyMedium,
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    SubtitleTextSize.entries.forEach { size ->
                        val label = when (size) {
                            SubtitleTextSize.SMALL -> R.string.subtitle_size_small
                            SubtitleTextSize.STANDARD -> R.string.subtitle_size_standard
                            SubtitleTextSize.LARGE -> R.string.subtitle_size_large
                        }
                        Text(
                            text = stringResource(label),
                            color = if (size == subtitleTextSize) theme.accent else theme.onBackground,
                            modifier = Modifier
                                .clickable { onSubtitleTextSize(size) }
                                .padding(vertical = 8.dp, horizontal = 4.dp),
                        )
                    }
                }
                Text(
                    text = stringResource(R.string.subtitle_color_yellow),
                    color = if (subtitleYellow) theme.accent else theme.onBackground,
                    modifier = Modifier
                        .clickable(onClick = onSubtitleYellowToggle)
                        .padding(vertical = 8.dp),
                )
                Text(
                    text = stringResource(R.string.subtitle_delay) + " · ${subtitleDelayMs / 1000f}s",
                    style = MaterialTheme.typography.bodyMedium,
                )
                Slider(
                    value = subtitleDelayMs.toFloat(),
                    onValueChange = { raw ->
                        onSubtitleDelayChange((raw / 500f).roundToInt() * 500)
                    },
                    valueRange = -5_000f..5_000f,
                    steps = 19,
                )
            }
        }
    }
}

private fun formatAb(positionMs: Long): String {
    val totalSeconds = (positionMs / 1000L).coerceAtLeast(0L)
    val minutes = totalSeconds / 60L
    val seconds = totalSeconds % 60L
    return "%d:%02d".format(minutes, seconds)
}

@Composable
private fun SleepChoiceChip(
    label: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    val theme = LocalLumenTheme.current
    Text(
        text = label,
        color = if (selected) theme.accent else theme.onBackground,
        style = MaterialTheme.typography.bodyMedium,
        modifier = Modifier
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
    )
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
