package com.pfpt.localvideoplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.expandVertically
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.shrinkVertically
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.player.SleepChoice
import com.pfpt.localvideoplayer.player.formatSleepRemaining
import com.pfpt.localvideoplayer.ui.theme.LocalAppIcons
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenListHorizontalInset
import com.pfpt.localvideoplayer.ui.theme.LumenMiniBarCorner
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceMd
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceSm
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceXs
import kotlinx.coroutines.flow.StateFlow

@Composable
fun PlaybackMiniBar(
    visible: Boolean,
    title: String,
    sleepChoice: SleepChoice,
    sleepRemaining: StateFlow<Long?>,
    isPlaying: Boolean,
    onOpen: () -> Unit,
    onPlayPause: () -> Unit,
    onClose: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLumenTheme.current
    val remainingMs by sleepRemaining.collectAsStateWithLifecycle()
    val detail = when (sleepChoice) {
        SleepChoice.Off -> null
        SleepChoice.EndOfItem -> stringResource(R.string.sleep_end_item)
        is SleepChoice.Minutes -> remainingMs?.let { remaining ->
            stringResource(R.string.sleep_remaining, formatSleepRemaining(remaining))
        }
    }
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn() + expandVertically(),
        exit = fadeOut() + shrinkVertically(),
        modifier = modifier,
    ) {
        GlassSurface(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = LumenListHorizontalInset, vertical = LumenSpaceSm),
            shape = RoundedCornerShape(LumenMiniBarCorner),
            fill = theme.surface,
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LumenSpaceMd, vertical = LumenSpaceXs),
                horizontalArrangement = Arrangement.spacedBy(LumenSpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(
                    modifier = Modifier
                        .weight(1f)
                        .clickable(onClick = onOpen)
                        .padding(vertical = LumenSpaceSm),
                ) {
                    Text(
                        text = title.ifBlank { stringResource(R.string.playback_mini_playing) },
                        style = MaterialTheme.typography.bodyMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    if (!detail.isNullOrBlank()) {
                        Text(
                            text = detail,
                            style = MaterialTheme.typography.labelMedium,
                            color = theme.onSurfaceMuted,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                IconButton(onClick = onPlayPause) {
                    Icon(
                        imageVector = if (isPlaying) LocalAppIcons.current.pause else LocalAppIcons.current.play,
                        contentDescription = stringResource(
                            if (isPlaying) R.string.pip_pause else R.string.pip_play,
                        ),
                        tint = MaterialTheme.colorScheme.primary,
                    )
                }
                IconButton(onClick = onClose) {
                    Icon(
                        imageVector = LocalAppIcons.current.close,
                        contentDescription = stringResource(R.string.playback_stop),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}
