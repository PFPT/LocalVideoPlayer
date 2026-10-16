package com.pfpt.localvideoplayer.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInHorizontally
import androidx.compose.animation.slideOutHorizontally
import androidx.compose.foundation.background
import androidx.compose.foundation.border
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.Checkbox
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.SwipeToDismissBox
import androidx.compose.material3.SwipeToDismissBoxValue
import androidx.compose.material3.Text
import androidx.compose.material3.rememberSwipeToDismissBoxState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.pfpt.localvideoplayer.data.PlaybackStore
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.model.PlaybackRecord
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenDelete
import com.pfpt.localvideoplayer.ui.theme.LumenThumbCorner

@Composable
fun RecentPlaybackSection(
    playbackStore: PlaybackStore,
    onRecordClick: (PlaybackRecord) -> Unit,
    modifier: Modifier = Modifier,
) {
    val records by playbackStore.recentRecords.collectAsStateWithLifecycle(
        initialValue = playbackStore.snapshot.value.recent,
    )
    if (records.isEmpty()) return

    Column(modifier = modifier.padding(bottom = 8.dp)) {
        Text(
            text = stringResource(R.string.recent_playback),
            style = MaterialTheme.typography.titleMedium,
            modifier = Modifier.padding(vertical = 8.dp),
        )
        LazyRow(
            contentPadding = PaddingValues(0.dp),
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
            append(com.pfpt.localvideoplayer.util.formatDuration(record.durationMs))
        }
    }

    val theme = LocalLumenTheme.current
    val cardShape = RoundedCornerShape(LumenThumbCorner)
    Column(
        modifier = modifier
            .width(120.dp)
            .clip(cardShape)
            .clickable(onClick = onClick)
            .background(theme.glassFill)
            .border(1.dp, theme.glassBorder, cardShape)
            .padding(8.dp),
    ) {
        Box(
            modifier = Modifier
                .width(104.dp)
                .height(72.dp)
                .clip(RoundedCornerShape(LumenThumbCorner)),
        ) {
            MediaThumbnail(
                thumbnailUri = record.uri,
                modifier = Modifier.fillMaxSize(),
            )
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
                        .background(theme.accent),
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
                color = theme.onSurfaceMuted,
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

    val theme = LocalLumenTheme.current
    val opaqueBackground = theme.background.copy(alpha = 1f)
    SwipeToDismissBox(
        state = dismissState,
        enableDismissFromStartToEnd = false,
        backgroundContent = {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .clip(RoundedCornerShape(LumenThumbCorner))
                    .background(LumenDelete),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Column(
                    modifier = Modifier
                        .width(80.dp)
                        .fillMaxHeight(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center,
                ) {
                    Icon(
                        imageVector = Icons.Default.Delete,
                        contentDescription = stringResource(R.string.delete),
                        tint = Color.White,
                        modifier = Modifier.height(24.dp),
                    )
                    Text(
                        text = stringResource(R.string.delete),
                        color = Color.White,
                        style = MaterialTheme.typography.titleMedium.copy(
                            fontWeight = FontWeight.SemiBold,
                        ),
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
        },
        modifier = modifier.clip(RoundedCornerShape(LumenThumbCorner)),
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .background(opaqueBackground),
        ) {
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
    val contentStartPadding by animateDpAsState(
        targetValue = if (selectionMode) 48.dp else 0.dp,
        label = "selection-content-padding",
    )
    val selectedScale by animateFloatAsState(
        targetValue = if (selected && selectionMode) 0.97f else 1f,
        label = "selection-scale",
    )

    Box(
        modifier = modifier
            .fillMaxWidth()
            .graphicsLayer {
                scaleX = selectedScale
                scaleY = selectedScale
            }
            .combinedClickable(
                onClick = onClick,
                onLongClick = onLongClick,
            ),
    ) {
        AnimatedVisibility(
            visible = selectionMode,
            enter = slideInHorizontally { -it / 2 } + fadeIn(),
            exit = slideOutHorizontally { -it / 2 } + fadeOut(),
            modifier = Modifier
                .align(Alignment.CenterStart)
                .padding(start = 4.dp),
        ) {
            Checkbox(
                checked = selected,
                onCheckedChange = { onClick() },
            )
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = contentStartPadding),
        ) {
            content()
            if (selected && selectionMode) {
                Box(
                    modifier = Modifier
                        .matchParentSize()
                        .background(
                            MaterialTheme.colorScheme.primary.copy(alpha = 0.12f),
                        ),
                )
            }
        }
    }
}
