package com.pfpt.localvideoplayer.ui.download

import android.content.ClipData
import android.content.ClipboardManager
import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.download.DownloadFormat
import com.pfpt.localvideoplayer.download.DownloadManager
import com.pfpt.localvideoplayer.download.DownloadStatus
import com.pfpt.localvideoplayer.download.DownloadTask
import com.pfpt.localvideoplayer.ui.components.GlassSurface
import com.pfpt.localvideoplayer.ui.theme.LocalAppIcons
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenCardPadding
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceMd
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceSm
import com.pfpt.localvideoplayer.ui.theme.LumenThumbCorner

@Composable
internal fun DownloadTaskCard(
    task: DownloadTask,
    manager: DownloadManager,
    onOpenCompleted: (DownloadTask) -> Unit,
    onRename: () -> Unit,
) {
    val context = LocalContext.current
    val linkCopied = stringResource(R.string.download_link_copied)
    val canPlay = task.status == DownloadStatus.COMPLETED && !task.outputUri.isNullOrBlank()
    val showProgress = task.isMerging ||
        task.status == DownloadStatus.RUNNING ||
        task.status == DownloadStatus.PAUSED ||
        (task.status == DownloadStatus.FAILED && (task.hlsTotalSegments > 0 || task.downloadedBytes > 0))
    var menuExpanded by remember { mutableStateOf(false) }

    GlassSurface(
        modifier = Modifier.fillMaxWidth(),
        shape = RoundedCornerShape(LumenThumbCorner),
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .then(
                    if (canPlay) {
                        Modifier.clickable { onOpenCompleted(task) }
                    } else {
                        Modifier
                    },
                )
                .padding(LumenCardPadding),
            verticalArrangement = Arrangement.spacedBy(LumenSpaceMd),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(LumenSpaceSm),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    task.fileName,
                    style = MaterialTheme.typography.titleMedium,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f),
                )
                DownloadStatusBadge(task)
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(LocalAppIcons.current.moreVert, contentDescription = stringResource(R.string.more_actions))
                    }
                    DownloadTaskMenu(
                        expanded = menuExpanded,
                        task = task,
                        canPlay = canPlay,
                        onDismiss = { menuExpanded = false },
                        onRename = onRename,
                        onCopyLink = {
                            context.getSystemService(ClipboardManager::class.java)?.setPrimaryClip(
                                ClipData.newPlainText("download", task.url),
                            )
                            Toast.makeText(context, linkCopied, Toast.LENGTH_SHORT).show()
                        },
                        onRemove = { manager.remove(task.id) },
                    )
                }
            }

            if (showProgress) {
                if (task.isMerging) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    Text(
                        stringResource(R.string.download_status_merging),
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLumenTheme.current.onSurfaceMuted,
                    )
                } else {
                    val progress = when {
                        task.totalBytes > 0 -> DownloadFormat.formatProgressFraction(task.downloadedBytes, task.totalBytes)
                        task.hlsTotalSegments > 0 -> DownloadFormat.formatSegmentFraction(task.hlsCompletedSegments, task.hlsTotalSegments)
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    DownloadProgressMeta(task)
                }
            } else if (task.status == DownloadStatus.COMPLETED && task.totalBytes > 0) {
                Text(
                    DownloadFormat.formatBytes(task.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = LocalLumenTheme.current.onSurfaceMuted,
                )
            }

            task.error?.let { message ->
                Text(
                    message,
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                    maxLines = 4,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            DownloadPrimaryActions(
                task = task,
                canPlay = canPlay,
                onPause = { manager.pause(task.id) },
                onResume = { manager.resume(task.id) },
                onRedownload = { manager.redownload(task.id) },
                onPlay = { onOpenCompleted(task) },
            )
        }
    }
}

@Composable
private fun DownloadProgressMeta(task: DownloadTask) {
    val downloadedText = DownloadFormat.formatBytes(task.downloadedBytes)
    val totalText = if (task.totalBytes > 0) {
        DownloadFormat.formatBytes(task.totalBytes)
    } else {
        stringResource(R.string.download_progress_size_unknown)
    }
    val sizeText = if (task.hlsTotalSegments > 0) {
        stringResource(
            R.string.download_progress_size_hls,
            task.hlsCompletedSegments,
            task.hlsTotalSegments,
            downloadedText,
            totalText,
        )
    } else {
        stringResource(
            R.string.download_progress_size,
            downloadedText,
            totalText,
        )
    }
    val speedText = if (task.downloadSpeedBps > 0) {
        DownloadFormat.formatSpeed(task.downloadSpeedBps)
    } else {
        "—"
    }
    val etaText = if (task.totalBytes > 0 && task.downloadSpeedBps > 0 && task.downloadedBytes < task.totalBytes) {
        val remaining = ((task.totalBytes - task.downloadedBytes) / task.downloadSpeedBps).coerceAtLeast(0)
        DownloadFormat.formatEta(remaining)
    } else {
        null
    }

    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = sizeText,
            style = MaterialTheme.typography.bodySmall,
            color = LocalLumenTheme.current.onSurfaceMuted,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = speedText,
            style = MaterialTheme.typography.bodySmall,
            color = LocalLumenTheme.current.onSurfaceMuted,
            maxLines = 1,
        )
        if (etaText != null) {
            Text(
                text = etaText,
                style = MaterialTheme.typography.bodySmall,
                color = LocalLumenTheme.current.onSurfaceMuted,
                maxLines = 1,
            )
        }
    }
}

@Composable
private fun DownloadPrimaryActions(
    task: DownloadTask,
    canPlay: Boolean,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRedownload: () -> Unit,
    onPlay: () -> Unit,
) {
    if (task.isMerging) return

    when (task.status) {
        DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
            FilledTonalButton(
                onClick = onPause,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(LocalAppIcons.current.pause, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.pause))
            }
        }
        DownloadStatus.PAUSED -> {
            FilledTonalButton(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(LocalAppIcons.current.play, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.download_resume))
            }
        }
        DownloadStatus.FAILED -> {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledTonalButton(
                    onClick = onResume,
                    modifier = Modifier.weight(1f),
                ) {
                    Icon(LocalAppIcons.current.play, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.download_resume))
                }
                TextButton(onClick = onRedownload) {
                    Icon(LocalAppIcons.current.refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(4.dp))
                    Text(stringResource(R.string.download_redownload))
                }
            }
        }
        DownloadStatus.COMPLETED -> if (canPlay) {
            FilledTonalButton(
                onClick = onPlay,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(LocalAppIcons.current.play, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.download_play))
            }
        }
        DownloadStatus.CANCELED -> Unit
    }
}

@Composable
private fun DownloadStatusBadge(task: DownloadTask) {
    val theme = LocalLumenTheme.current
    val (label, containerColor, labelColor) = when {
        task.isMerging -> Triple(
            stringResource(R.string.download_status_merging),
            theme.accent.copy(alpha = 0.14f),
            theme.accent,
        )
        task.status == DownloadStatus.RUNNING -> Triple(
            stringResource(R.string.download_status_running),
            theme.accent.copy(alpha = 0.18f),
            theme.accent,
        )
        task.status == DownloadStatus.QUEUED -> Triple(
            stringResource(R.string.download_status_queued),
            theme.glassFill,
            theme.onSurfaceMuted,
        )
        task.status == DownloadStatus.PAUSED -> Triple(
            stringResource(R.string.download_status_paused),
            theme.glassFill,
            theme.onSurfaceMuted,
        )
        task.status == DownloadStatus.COMPLETED -> Triple(
            stringResource(R.string.download_status_completed),
            theme.glassFill,
            theme.onBackground,
        )
        task.status == DownloadStatus.CANCELED -> Triple(
            stringResource(R.string.download_status_canceled),
            theme.glassFill,
            theme.onSurfaceMuted,
        )
        else -> Triple(
            stringResource(R.string.download_status_failed),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelSmall,
        color = labelColor,
        modifier = Modifier
            .clip(RoundedCornerShape(8.dp))
            .background(containerColor)
            .padding(horizontal = 8.dp, vertical = 4.dp),
    )
}

@Composable
private fun DownloadTaskMenu(
    expanded: Boolean,
    task: DownloadTask,
    canPlay: Boolean,
    onDismiss: () -> Unit,
    onRename: () -> Unit,
    onCopyLink: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        DropdownMenuItem(
            text = {
                Column {
                    Text(stringResource(R.string.download_copy_link))
                    Text(
                        task.url,
                        style = MaterialTheme.typography.bodySmall,
                        color = LocalLumenTheme.current.onSurfaceMuted,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
            },
            onClick = { onDismiss(); onCopyLink() },
        )
        if (task.status == DownloadStatus.COMPLETED && canPlay) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.download_rename)) },
                onClick = { onDismiss(); onRename() },
            )
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.download_remove)) },
            onClick = { onDismiss(); onRemove() },
        )
    }
}
