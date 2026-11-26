package com.pfpt.localvideoplayer.ui

import android.widget.Toast
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.download.DownloadListStatusFilter
import com.pfpt.localvideoplayer.download.DownloadManager
import com.pfpt.localvideoplayer.download.DownloadStatus
import com.pfpt.localvideoplayer.download.DownloadTask
import com.pfpt.localvideoplayer.download.filterDownloadTasks
import com.pfpt.localvideoplayer.ui.components.AddDownloadBottomSheet
import com.pfpt.localvideoplayer.ui.components.LumenEmptyState
import com.pfpt.localvideoplayer.ui.components.LumenListHeader
import com.pfpt.localvideoplayer.ui.components.LumenModalBottomSheet
import com.pfpt.localvideoplayer.ui.components.RenameDownloadDialog
import com.pfpt.localvideoplayer.ui.components.SwipeableListItem
import com.pfpt.localvideoplayer.ui.download.DownloadTaskCard
import com.pfpt.localvideoplayer.ui.theme.LocalAppIcons
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenListHorizontalInset
import com.pfpt.localvideoplayer.ui.theme.LumenListItemGap
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceMd
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceSm
import kotlinx.coroutines.launch

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DownloadManagerScreen(
    manager: DownloadManager,
    onOpenCompleted: (DownloadTask) -> Unit,
    onBack: () -> Unit,
) {
    val theme = LocalLumenTheme.current
    val tasks by manager.tasks.collectAsStateWithLifecycle()
    val maxConcurrent by manager.maxConcurrentDownloadsFlow.collectAsStateWithLifecycle()
    val connectionsPerDownload by manager.connectionsPerDownloadFlow.collectAsStateWithLifecycle()
    var showAddSheet by remember { mutableStateOf(false) }
    var showSettingsSheet by remember { mutableStateOf(false) }
    var sheetShareText by remember { mutableStateOf<String?>(null) }
    var renameTask by remember { mutableStateOf<DownloadTask?>(null) }
    var query by remember { mutableStateOf("") }
    var statusFilter by remember { mutableStateOf(DownloadListStatusFilter.All) }

    val visibleTasks = remember(tasks, query, statusFilter) {
        filterDownloadTasks(tasks, query, statusFilter)
    }
    val grouped = remember(visibleTasks) {
        DownloadTaskGroups(
            active = visibleTasks.filter { task ->
                task.isMerging ||
                    task.status == DownloadStatus.QUEUED ||
                    task.status == DownloadStatus.RUNNING ||
                    task.status == DownloadStatus.PAUSED
            },
            completed = visibleTasks.filter { it.status == DownloadStatus.COMPLETED },
            failed = visibleTasks.filter { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED },
        )
    }
    val canPauseAll = tasks.any { task ->
        !task.isMerging &&
            (task.status == DownloadStatus.QUEUED || task.status == DownloadStatus.RUNNING)
    }
    val canResumeAll = tasks.any { it.status == DownloadStatus.PAUSED || it.status == DownloadStatus.FAILED }
    val canClearCompleted = tasks.any { it.status == DownloadStatus.COMPLETED }
    val canClearFailed = tasks.any { it.status == DownloadStatus.FAILED || it.status == DownloadStatus.CANCELED }

    fun openAddSheet(shareText: String? = null) {
        sheetShareText = shareText
        showAddSheet = true
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
                        LocalAppIcons.current.settings,
                        contentDescription = stringResource(R.string.download_settings),
                        tint = MaterialTheme.colorScheme.onBackground,
                    )
                }
                IconButton(onClick = { openAddSheet() }) {
                    Icon(
                        LocalAppIcons.current.add,
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
                icon = LocalAppIcons.current.download,
                actionLabel = stringResource(R.string.download_add),
                onAction = { openAddSheet() },
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 24.dp),
            )
        } else {
            DownloadListControls(
                query = query,
                onQueryChange = { query = it },
                statusFilter = statusFilter,
                onStatusFilter = { statusFilter = it },
                canPauseAll = canPauseAll,
                canResumeAll = canResumeAll,
                canClearCompleted = canClearCompleted,
                canClearFailed = canClearFailed,
                onPauseAll = manager::pauseAll,
                onResumeAll = manager::resumeAll,
                onClearCompleted = manager::clearCompleted,
                onClearFailed = manager::clearFailed,
            )
            if (visibleTasks.isEmpty()) {
                Box(
                    modifier = Modifier
                        .fillMaxSize()
                        .padding(horizontal = 24.dp),
                    contentAlignment = Alignment.Center,
                ) {
                    Text(
                        text = stringResource(R.string.download_filter_empty),
                        style = MaterialTheme.typography.bodyLarge,
                        color = theme.onSurfaceMuted,
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(
                        horizontal = LumenListHorizontalInset,
                        vertical = LumenSpaceMd,
                    ),
                    verticalArrangement = Arrangement.spacedBy(LumenListItemGap),
                ) {
                    if (grouped.active.isNotEmpty()) {
                        item { DownloadSectionHeader(stringResource(R.string.download_section_active)) }
                        items(grouped.active, key = { it.id }) { task ->
                            DownloadTaskRow(
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
                            DownloadTaskRow(
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
                            DownloadTaskRow(
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
            },
            onSubmitted = {
                sheetShareText = null
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
private fun DownloadListControls(
    query: String,
    onQueryChange: (String) -> Unit,
    statusFilter: DownloadListStatusFilter,
    onStatusFilter: (DownloadListStatusFilter) -> Unit,
    canPauseAll: Boolean,
    canResumeAll: Boolean,
    canClearCompleted: Boolean,
    canClearFailed: Boolean,
    onPauseAll: () -> Unit,
    onResumeAll: () -> Unit,
    onClearCompleted: () -> Unit,
    onClearFailed: () -> Unit,
) {
    val theme = LocalLumenTheme.current
    val chipColors = FilterChipDefaults.filterChipColors(
        containerColor = theme.glassFill.copy(alpha = 0.04f),
        labelColor = theme.onSurfaceMuted,
        selectedContainerColor = theme.glassFill,
        selectedLabelColor = theme.onBackground,
    )
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LumenListHorizontalInset)
            .padding(top = LumenSpaceSm),
        verticalArrangement = Arrangement.spacedBy(LumenSpaceSm),
    ) {
        OutlinedTextField(
            value = query,
            onValueChange = onQueryChange,
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            placeholder = { Text(stringResource(R.string.download_search_hint)) },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = theme.glassBorder,
                unfocusedBorderColor = theme.glassBorder.copy(alpha = 0.5f),
                focusedContainerColor = theme.glassFill.copy(alpha = 0.04f),
                unfocusedContainerColor = theme.glassFill.copy(alpha = 0.04f),
                cursorColor = theme.accent,
                focusedTextColor = theme.onBackground,
                unfocusedTextColor = theme.onBackground,
                focusedPlaceholderColor = theme.onSurfaceMuted,
                unfocusedPlaceholderColor = theme.onSurfaceMuted,
            ),
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(LumenSpaceSm),
        ) {
            FilterChip(
                selected = statusFilter == DownloadListStatusFilter.All,
                onClick = { onStatusFilter(DownloadListStatusFilter.All) },
                label = { Text(stringResource(R.string.download_filter_all)) },
                colors = chipColors,
            )
            FilterChip(
                selected = statusFilter == DownloadListStatusFilter.Active,
                onClick = { onStatusFilter(DownloadListStatusFilter.Active) },
                label = { Text(stringResource(R.string.download_filter_active)) },
                colors = chipColors,
            )
            FilterChip(
                selected = statusFilter == DownloadListStatusFilter.Completed,
                onClick = { onStatusFilter(DownloadListStatusFilter.Completed) },
                label = { Text(stringResource(R.string.download_filter_completed)) },
                colors = chipColors,
            )
            FilterChip(
                selected = statusFilter == DownloadListStatusFilter.Failed,
                onClick = { onStatusFilter(DownloadListStatusFilter.Failed) },
                label = { Text(stringResource(R.string.download_filter_failed)) },
                colors = chipColors,
            )
        }
        if (canPauseAll || canResumeAll || canClearCompleted || canClearFailed) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                if (canPauseAll) {
                    TextButton(onClick = onPauseAll) {
                        Text(stringResource(R.string.download_pause_all))
                    }
                }
                if (canResumeAll) {
                    TextButton(onClick = onResumeAll) {
                        Text(stringResource(R.string.download_resume_all))
                    }
                }
                if (canClearCompleted) {
                    TextButton(onClick = onClearCompleted) {
                        Text(stringResource(R.string.download_clear_completed))
                    }
                }
                if (canClearFailed) {
                    TextButton(onClick = onClearFailed) {
                        Text(stringResource(R.string.download_clear_failed))
                    }
                }
            }
        }
    }
}

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
private fun DownloadTaskRow(
    task: DownloadTask,
    manager: DownloadManager,
    onOpenCompleted: (DownloadTask) -> Unit,
    onRename: () -> Unit,
) {
    SwipeableListItem(
        enabled = true,
        onSwipeDelete = { manager.remove(task.id) },
    ) {
        DownloadTaskCard(
            task = task,
            manager = manager,
            onOpenCompleted = onOpenCompleted,
            onRename = onRename,
        )
    }
}

