package com.zhaoshuo.localvideoplayer.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Pause
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.download.DownloadFormat
import com.zhaoshuo.localvideoplayer.download.DownloadManager
import com.zhaoshuo.localvideoplayer.download.DownloadStatus
import com.zhaoshuo.localvideoplayer.download.DownloadTask
import com.zhaoshuo.localvideoplayer.ui.components.AddDownloadBottomSheet
import com.zhaoshuo.localvideoplayer.ui.components.GlassSurface
import com.zhaoshuo.localvideoplayer.ui.components.LumenEmptyState
import com.zhaoshuo.localvideoplayer.ui.components.LumenListHeader
import com.zhaoshuo.localvideoplayer.ui.components.LumenModalBottomSheet
import com.zhaoshuo.localvideoplayer.ui.components.RenameDownloadDialog
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme
import com.zhaoshuo.localvideoplayer.ui.theme.LumenThumbCorner
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    manager: DownloadManager,
    initialShareText: String?,
    onInitialShareConsumed: () -> Unit,
    onOpenCompleted: (DownloadTask) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalLumenTheme.current
    val tasks by manager.tasks.collectAsState()
    val maxConcurrent by manager.maxConcurrentDownloadsFlow.collectAsState()
    val connectionsPerDownload by manager.connectionsPerDownloadFlow.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var sheetShareText by remember { mutableStateOf<String?>(null) }
    var renameTask by remember { mutableStateOf<DownloadTask?>(null) }

    val grouped = remember(tasks) {
        DownloadTaskGroups(
            active = tasks.filter { task ->
                task.isMerging ||
                    task.status == DownloadStatus.QUEUED ||
                    task.status == DownloadStatus.RUNNING ||
                    task.status == DownloadStatus.PAUSED
            },
            completed = tasks.filter { it.status == DownloadStatus.COMPLETED },
            failed = tasks.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED },
        )
    }

    fun openAddSheet(shareText: String? = null) {
        sheetShareText = shareText
        showAddSheet = true
    }

    LaunchedEffect(initialShareText) {
        if (initialShareText != null) {
            openAddSheet(initialShareText)
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(theme.background)
            .windowInsetsPadding(WindowInsets.safeDrawing),
    ) {
        LumenListHeader(
            title = stringResource(R.string.downloads_title),
            onBack = onBack,
            applyStatusBarPadding = false,
            actions = {
                IconButton(onClick = { showSettingsSheet = true }) {
                    Icon(
                        Icons.Default.Settings,
                        contentDescription = stringResource(R.string.download_settings),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = { openAddSheet() }) {
                    Icon(
                        Icons.Default.Add,
                        contentDescription = stringResource(R.string.download_add),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
            },
        )

        if (tasks.isEmpty()) {
            LumenEmptyState(
                title = stringResource(R.string.downloads_title),
                subtitle = stringResource(R.string.download_empty_hint),
                icon = Icons.Default.Download,
                actionLabel = stringResource(R.string.download_add),
                onAction = { openAddSheet() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            )
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                if (grouped.active.isNotEmpty()) {
                    item { DownloadSectionHeader(stringResource(R.string.download_section_active)) }
                    items(grouped.active, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            manager = manager,
                            onOpenCompleted = onOpenCompleted,
                            onRename = { renameTask = task },
                        )
                    }
                }
                if (grouped.completed.isNotEmpty()) {
                    item { DownloadSectionHeader(stringResource(R.string.download_section_completed)) }
                    items(grouped.completed, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            manager = manager,
                            onOpenCompleted = onOpenCompleted,
                            onRename = { renameTask = task },
                        )
                    }
                }
                if (grouped.failed.isNotEmpty()) {
                    item { DownloadSectionHeader(stringResource(R.string.download_section_failed)) }
                    items(grouped.failed, key = { it.id }) { task ->
                        DownloadTaskCard(
                            task = task,
                            manager = manager,
                            onOpenCompleted = onOpenCompleted,
                            onRename = { renameTask = task },
                        )
                    }
                }
            }
        }
    }

    if (showSettingsSheet) {
        DownloadSettingsBottomSheet(
            maxConcurrent = maxConcurrent,
            connectionsPerDownload = connectionsPerDownload,
            onSelectConcurrent = manager::setMaxConcurrentDownloads,
            onSelectConnections = manager::setConnectionsPerDownload,
            onDismiss = { showSettingsSheet = false },
        )
    }

    if (showAddSheet) {
        AddDownloadBottomSheet(
            manager = manager,
            initialShareText = sheetShareText,
            onDismiss = {
                showAddSheet = false
                sheetShareText = null
                onInitialShareConsumed()
            },
            onSubmitted = {
                sheetShareText = null
                onInitialShareConsumed()
            },
        )
    }

    renameTask?.let { task ->
        val context = LocalContext.current
        RenameDownloadDialog(
            task = task,
            onDismiss = { renameTask = null },
            onConfirm = { newName ->
                manager.rename(task.id, newName) { success ->
                    if (success) {
                        renameTask = null
                    } else {
                        Toast.makeText(context, R.string.download_rename_failed, Toast.LENGTH_SHORT).show()
                    }
                }
            },
        )
    }
}

private data class DownloadTaskGroups(
    val active: List<DownloadTask>,
    val completed: List<DownloadTask>,
    val failed: List<DownloadTask>,
)

@Composable
private fun DownloadSectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = LocalLumenTheme.current.onSurfaceMuted,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DownloadSettingsBottomSheet(
    maxConcurrent: Int,
    connectionsPerDownload: Int,
    onSelectConcurrent: (Int) -> Unit,
    onSelectConnections: (Int) -> Unit,
    onDismiss: () -> Unit,
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val scope = rememberCoroutineScope()

    LumenModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(20.dp),
        ) {
            Text(
                text = stringResource(R.string.download_settings),
                style = MaterialTheme.typography.titleLarge,
            )
            ConcurrentDownloadSettings(
                maxConcurrent = maxConcurrent,
                connectionsPerDownload = connectionsPerDownload,
                onSelectConcurrent = onSelectConcurrent,
                onSelectConnections = onSelectConnections,
            )
            TextButton(
                onClick = {
                    scope.launch {
                        sheetState.hide()
                        onDismiss()
                    }
                },
                modifier = Modifier.align(Alignment.End),
            ) {
                Text(stringResource(R.string.cancel))
            }
        }
    }
}

@Composable
private fun ConcurrentDownloadSettings(
    maxConcurrent: Int,
    connectionsPerDownload: Int,
    onSelectConcurrent: (Int) -> Unit,
    onSelectConnections: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    Column(
        modifier = modifier.fillMaxWidth(),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.download_concurrent_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilterChip(
                    selected = maxConcurrent < 0,
                    onClick = { onSelectConcurrent(DownloadManager.UNLIMITED_CONCURRENT) },
                    label = { Text(stringResource(R.string.download_concurrent_unlimited)) },
                )
                DownloadManager.CONCURRENT_DOWNLOAD_OPTIONS.forEach { count ->
                    FilterChip(
                        selected = maxConcurrent == count,
                        onClick = { onSelectConcurrent(count) },
                        label = { Text(stringResource(R.string.download_concurrent_n, count)) },
                    )
                }
            }
        }
        Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = stringResource(R.string.download_connections_title),
                style = MaterialTheme.typography.labelLarge,
            )
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                DownloadManager.CONNECTIONS_PER_DOWNLOAD_OPTIONS.forEach { count ->
                    FilterChip(
                        selected = connectionsPerDownload == count,
                        onClick = { onSelectConnections(count) },
                        label = { Text(stringResource(R.string.download_connections_n, count)) },
                    )
                }
            }
        }
    }
}

@Composable
private fun DownloadTaskCard(
    task: DownloadTask,
    manager: DownloadManager,
    onOpenCompleted: (DownloadTask) -> Unit,
    onRename: () -> Unit,
) {
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
                .padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Text(
                        task.fileName,
                        fontWeight = FontWeight.SemiBold,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DownloadStatusBadge(task)
                }
                Box {
                    IconButton(onClick = { menuExpanded = true }) {
                        Icon(Icons.Default.MoreVert, contentDescription = stringResource(R.string.more_actions))
                    }
                    DownloadTaskMenu(
                        expanded = menuExpanded,
                        task = task,
                        canPlay = canPlay,
                        onDismiss = { menuExpanded = false },
                        onRename = onRename,
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

            task.error?.let {
                Text(
                    it,
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
                Icon(Icons.Default.Pause, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.size(8.dp))
                Text(stringResource(R.string.pause))
            }
        }
        DownloadStatus.PAUSED -> {
            FilledTonalButton(
                onClick = onResume,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
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
                    Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.size(8.dp))
                    Text(stringResource(R.string.download_resume))
                }
                TextButton(onClick = onRedownload) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
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
                Icon(Icons.Default.PlayArrow, contentDescription = null, modifier = Modifier.size(18.dp))
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
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
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
