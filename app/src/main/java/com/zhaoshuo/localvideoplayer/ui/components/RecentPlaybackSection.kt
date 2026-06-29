package com.zhaoshuo.localvideoplayer.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.model.PlaybackRecord
import com.zhaoshuo.localvideoplayer.ui.theme.LumenAccentText
import com.zhaoshuo.localvideoplayer.ui.theme.LumenBackground
import com.zhaoshuo.localvideoplayer.ui.theme.LumenOnSurfaceMuted
import com.zhaoshuo.localvideoplayer.ui.theme.LumenSurface
import com.zhaoshuo.localvideoplayer.ui.theme.LumenThumbCorner

@Composable
fun RecentPlaybackSection(
    records: List<PlaybackRecord>,
    onRecordClick: (PlaybackRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    if (records.isEmpty()) return

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.recent_playback),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(horizontal = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            items(records, key = { it.uri.toString() }) { record ->
                RecentPlaybackCard(
                    record = record,
                    onClick = { onRecordClick(record) },
                )
            }
        }
    }
}

@Composable
private fun RecentPlaybackCard(
    record: PlaybackRecord,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val progress = record.progress
    val subtitle = buildString {
        if (progress != null && progress > 0.01f) {
            append(stringResource(R.string.watched_percent, (progress * 100).toInt()))
            append(" · ")
        }
        if (record.durationMs > 0) {
            append(com.zhaoshuo.localvideoplayer.util.formatDuration(record.durationMs))
        }
    }

    Column(
        modifier = modifier
            .width(120.dp)
            .clip(RoundedCornerShape(LumenThumbCorner))
            .clickable(onClick = onClick)
            .background(LumenSurface.copy(alpha = 0.35f))
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(LumenThumbCorner)),
        ) {
            val videoId = record.uri.lastPathSegment?.toLongOrNull()
            if (videoId != null) {
                MediaThumbnail(
                    videoId = videoId,
                    modifier = Modifier.fillMaxSize(),
                )
            } else {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .background(LumenSurface),
                )
            }
            if (progress != null && progress > 0.01f) {
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth()
                        .height(3.dp)
                        .background(Color.Black.copy(alpha = 0.4f)),
                )
                Box(
                    modifier = Modifier
                        .align(Alignment.BottomStart)
                        .fillMaxWidth(progress)
                        .height(3.dp)
                        .background(LumenAccentText),
                )
            }
        }
        Text(
            text = record.title,
            style = MaterialTheme.typography.bodySmall,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.padding(top = 6.dp),
        )
        if (subtitle.isNotBlank()) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.labelSmall,
                color = LumenOnSurfaceMuted,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
                modifier = Modifier.padding(top = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SwipeableListItem(
    enabled: Boolean,
    onSwipeDelete: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    if (!enabled) {
        Box(modifier = modifier) { content() }
        return
    }

    val dismissState = rememberSwipeToDismissBoxState(
        confirmValueChange = { value ->
            if (value == SwipeToDismissBoxValue.EndToStart) {
                onSwipeDelete()
            }
            false
        },
    )

    LaunchedEffect(dismissState.currentValue) {
        if (dismissState.currentValue != SwipeToDismissBoxValue.Settled) {
            dismissState.reset()
        }
    }

    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(LumenThumbCorner))
                    .background(Color(0xFFE53935))
                    .padding(horizontal = 20.dp),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Text(
                    text = stringResource(R.string.delete),
                    color = Color.White,
                    style = MaterialTheme.typography.titleMedium,
                )
            }
        },
        modifier = modifier.clip(RoundedCornerShape(LumenThumbCorner)),
    ) {
        Box(modifier = Modifier.background(LumenBackground)) {
            content()
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SelectableListItem(
    selected: Boolean,
    selectionMode: Boolean,
    onClick: () -> Unit,
    onLongClick: () -> Unit,
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit,
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        if (selectionMode) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
                modifier = Modifier
                    .align(Alignment.CenterStart)
                    .padding(start = 4.dp),
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = if (selectionMode) 48.dp else 0.dp),
        ) {
            content()
        }
    }
}
