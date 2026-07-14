package com.zhaoshuo.localvideoplayer.ui

import android.widget.Toast
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material3.AssistChip
import androidx.compose.material3.AssistChipDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
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
import com.zhaoshuo.localvideoplayer.ui.components.RenameDownloadDialog

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    manager: DownloadManager,
    initialShareText: String?,
    onInitialShareConsumed: () -> Unit,
    onOpenCompleted: (DownloadTask) -> Unit,
    onBack: () -> Unit,
) {
    val tasks by manager.tasks.collectAsState()
    val maxConcurrent by manager.maxConcurrentDownloadsFlow.collectAsState()
    val connectionsPerDownload by manager.connectionsPerDownloadFlow.collectAsState()
    var showAddSheet by remember { mutableStateOf(false) }
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

    LaunchedEffect(initialShareText) {
        if (initialShareText != null) {
            sheetShareText = initialShareText
            showAddSheet = true
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.downloads_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = {
                        sheetShareText = null
                        showAddSheet = true
                    }) {
                        Icon(Icons.Default.Add, stringResource(R.string.download_add))
                    }
                },
            )
        },
    ) { padding ->
        if (tasks.isEmpty()) {
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 24.dp),
                contentAlignment = Alignment.Center,
            ) {
                Column(
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    ConcurrentDownloadSettings(
                        maxConcurrent = maxConcurrent,
                        connectionsPerDownload = connectionsPerDownload,
                        onSelectConcurrent = manager::setMaxConcurrentDownloads,
                        onSelectConnections = manager::setConnectionsPerDownload,
                    )
                    Text(
                        text = stringResource(R.string.download_empty_hint),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Button(onClick = {
                        sheetShareText = null
                        showAddSheet = true
                    }) {
                        Text(stringResource(R.string.download_add))
                    }
                }
            }
        } else {
            LazyColumn(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    .padding(horizontal = 16.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                item {
                    ConcurrentDownloadSettings(
                        maxConcurrent = maxConcurrent,
                        connectionsPerDownload = connectionsPerDownload,
                        onSelectConcurrent = manager::setMaxConcurrentDownloads,
                        onSelectConnections = manager::setConnectionsPerDownload,
                        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
                    )
                }
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
                item { Text("", modifier = Modifier.padding(bottom = 8.dp)) }
            }
        }
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
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 4.dp, bottom = 2.dp),
    )
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
        verticalArrangement = Arrangement.spacedBy(12.dp),
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

    Card(
        modifier = if (canPlay) {
            Modifier
                .fillMaxWidth()
                .clickable { onOpenCompleted(task) }
        } else {
            Modifier.fillMaxWidth()
        },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.Top,
            ) {
                Column(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        task.fileName,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    DownloadStatusChip(task)
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
                        onPlay = { onOpenCompleted(task) },
                        onPause = { manager.pause(task.id) },
                        onResume = { manager.resume(task.id) },
                        onRedownload = { manager.redownload(task.id) },
                        onRename = onRename,
                        onRemove = { manager.remove(task.id) },
                    )
                }
            }

            if (showProgress) {
                if (task.isMerging) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                } else {
                    val percent = DownloadFormat.formatProgressPercent(
                        task.downloadedBytes,
                        task.totalBytes,
                        task.hlsCompletedSegments,
                        task.hlsTotalSegments,
                    )
                    val progress = when {
                        task.totalBytes > 0 -> DownloadFormat.formatProgressFraction(task.downloadedBytes, task.totalBytes)
                        task.hlsTotalSegments > 0 -> DownloadFormat.formatSegmentFraction(task.hlsCompletedSegments, task.hlsTotalSegments)
                        else -> 0f
                    }
                    LinearProgressIndicator(
                        progress = { progress },
                        modifier = Modifier.fillMaxWidth(),
                    )
                    Text("$percent%", style = MaterialTheme.typography.bodySmall)
                    Text(downloadProgressDetail(task), style = MaterialTheme.typography.bodySmall)
                }
            } else if (task.status == DownloadStatus.COMPLETED && task.totalBytes > 0) {
                Text(
                    DownloadFormat.formatBytes(task.totalBytes),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
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
        }
    }
}

@Composable
private fun DownloadStatusChip(task: DownloadTask) {
    val (label, containerColor, labelColor) = when {
        task.isMerging -> Triple(
            stringResource(R.string.download_status_merging),
            MaterialTheme.colorScheme.tertiaryContainer,
            MaterialTheme.colorScheme.onTertiaryContainer,
        )
        task.status == DownloadStatus.RUNNING -> Triple(
            stringResource(R.string.download_status_running),
            MaterialTheme.colorScheme.primaryContainer,
            MaterialTheme.colorScheme.onPrimaryContainer,
        )
        task.status == DownloadStatus.QUEUED -> Triple(
            stringResource(R.string.download_status_queued),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        task.status == DownloadStatus.PAUSED -> Triple(
            stringResource(R.string.download_status_paused),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        task.status == DownloadStatus.COMPLETED -> Triple(
            stringResource(R.string.download_status_completed),
            MaterialTheme.colorScheme.secondaryContainer,
            MaterialTheme.colorScheme.onSecondaryContainer,
        )
        task.status == DownloadStatus.CANCELED -> Triple(
            stringResource(R.string.download_status_canceled),
            MaterialTheme.colorScheme.surfaceVariant,
            MaterialTheme.colorScheme.onSurfaceVariant,
        )
        else -> Triple(
            stringResource(R.string.download_status_failed),
            MaterialTheme.colorScheme.errorContainer,
            MaterialTheme.colorScheme.onErrorContainer,
        )
    }
    AssistChip(
        onClick = {},
        label = { Text(label, style = MaterialTheme.typography.labelSmall) },
        colors = AssistChipDefaults.assistChipColors(
            containerColor = containerColor,
            labelColor = labelColor,
        ),
    )
}

@Composable
private fun DownloadTaskMenu(
    expanded: Boolean,
    task: DownloadTask,
    canPlay: Boolean,
    onDismiss: () -> Unit,
    onPlay: () -> Unit,
    onPause: () -> Unit,
    onResume: () -> Unit,
    onRedownload: () -> Unit,
    onRename: () -> Unit,
    onRemove: () -> Unit,
) {
    DropdownMenu(expanded = expanded, onDismissRequest = onDismiss) {
        when (task.status) {
            DownloadStatus.RUNNING, DownloadStatus.QUEUED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.pause)) },
                    onClick = { onDismiss(); onPause() },
                )
            }
            DownloadStatus.PAUSED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.download_resume)) },
                    onClick = { onDismiss(); onResume() },
                )
            }
            DownloadStatus.FAILED -> {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.download_resume)) },
                    onClick = { onDismiss(); onResume() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.download_redownload)) },
                    onClick = { onDismiss(); onRedownload() },
                )
            }
            DownloadStatus.COMPLETED -> if (canPlay) {
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.download_play)) },
                    onClick = { onDismiss(); onPlay() },
                )
                DropdownMenuItem(
                    text = { Text(stringResource(R.string.download_rename)) },
                    onClick = { onDismiss(); onRename() },
                )
            }
            DownloadStatus.CANCELED -> Unit
        }
        DropdownMenuItem(
            text = { Text(stringResource(R.string.download_remove)) },
            onClick = { onDismiss(); onRemove() },
        )
    }
}

@Composable
private fun downloadProgressDetail(task: DownloadTask): String {
    val downloadedText = DownloadFormat.formatBytes(task.downloadedBytes)
    val totalText = if (task.totalBytes > 0) {
        DownloadFormat.formatBytes(task.totalBytes)
    } else {
        stringResource(R.string.download_progress_size_unknown)
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
        "—"
    }
    val percent = DownloadFormat.formatProgressPercent(
        task.downloadedBytes,
        task.totalBytes,
        task.hlsCompletedSegments,
        task.hlsTotalSegments,
    )
    return if (task.hlsTotalSegments > 0) {
        stringResource(
            R.string.download_progress_detail,
            percent,
            task.hlsCompletedSegments,
            task.hlsTotalSegments,
            downloadedText,
            totalText,
            speedText,
            etaText,
        )
    } else {
        stringResource(
            R.string.download_progress_simple,
            percent,
            downloadedText,
            totalText,
            speedText,
            etaText,
        )
    }
}
