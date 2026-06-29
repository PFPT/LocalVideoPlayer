package com.zhaoshuo.localvideoplayer.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.data.HiddenVideosStore
import com.zhaoshuo.localvideoplayer.data.PlaybackStore
import com.zhaoshuo.localvideoplayer.data.VideoRepository
import com.zhaoshuo.localvideoplayer.model.PlaybackRecord
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import com.zhaoshuo.localvideoplayer.model.VideoSortOrder
import com.zhaoshuo.localvideoplayer.model.sortLibrary
import com.zhaoshuo.localvideoplayer.model.withoutUris
import com.zhaoshuo.localvideoplayer.player.PlaylistState
import com.zhaoshuo.localvideoplayer.ui.components.DeleteVideoDialog
import com.zhaoshuo.localvideoplayer.ui.components.LumenListHeader
import com.zhaoshuo.localvideoplayer.ui.components.RecentPlaybackSection
import com.zhaoshuo.localvideoplayer.ui.components.SelectionTopBar
import com.zhaoshuo.localvideoplayer.ui.components.SeriesFolderItem
import com.zhaoshuo.localvideoplayer.ui.components.SortBottomSheet
import com.zhaoshuo.localvideoplayer.ui.components.VideoListItem
import com.zhaoshuo.localvideoplayer.ui.components.rememberSeriesPlaybackStats
import com.zhaoshuo.localvideoplayer.ui.theme.LumenAccentText
import com.zhaoshuo.localvideoplayer.ui.theme.LumenBackground
import com.zhaoshuo.localvideoplayer.ui.theme.LumenOnSurfaceMuted
import com.zhaoshuo.localvideoplayer.util.M3uPlaylistParser
import com.zhaoshuo.localvideoplayer.util.Permissions
import com.zhaoshuo.localvideoplayer.util.isM3u8Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private val VIDEO_PICKER_MIME_TYPES = arrayOf(
    "video/*",
    "application/vnd.apple.mpegurl",
    "application/x-mpegURL",
)

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    libraryEntries: List<VideoLibraryEntry>,
    isLibraryLoading: Boolean,
    isRefreshing: Boolean,
    hasPermission: Boolean,
    recentRecords: List<PlaybackRecord>,
    playbackRecords: Map<String, PlaybackRecord>,
    repository: VideoRepository,
    hiddenVideosStore: HiddenVideosStore,
    playbackStore: PlaybackStore,
    onPermissionGranted: () -> Unit,
    onPlayVideo: (PlaylistState) -> Unit,
    onOpenSeries: (String) -> Unit,
    onVideosRemoved: (List<Uri>) -> Unit,
    onPullRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var sortOrder by rememberSaveable { mutableStateOf(VideoSortOrder.DATE_DESC) }
    var showSortSheet by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>?>(null) }
    var pendingDeleteSubtitle by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        if (granted) onPermissionGranted()
    }

    LaunchedEffect(Unit) {
        val granted = androidx.core.content.ContextCompat.checkSelfPermission(
            context,
            Permissions.readVideoPermission,
        ) == android.content.pm.PackageManager.PERMISSION_GRANTED
        if (granted) {
            onPermissionGranted()
        } else {
            permissionLauncher.launch(Permissions.readVideoPermission)
        }
    }

    val sortedLibrary by produceState(
        initialValue = libraryEntries,
        libraryEntries,
        sortOrder,
    ) {
        value = withContext(Dispatchers.Default) {
            sortLibrary(libraryEntries, sortOrder)
        }
    }
    val listState = rememberLazyListState()
    val showScrollToTop by remember {
        derivedStateOf {
            !selectionMode &&
                !isLibraryLoading &&
                hasPermission &&
                (sortedLibrary.isNotEmpty() || recentRecords.isNotEmpty()) &&
                (listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0)
        }
    }

    val filePickerLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            try {
                context.contentResolver.takePersistableUriPermission(
                    uri,
                    Intent.FLAG_GRANT_READ_URI_PERMISSION,
                )
            } catch (_: SecurityException) {
            }
            val title = uri.lastPathSegment?.substringAfterLast(':') ?: "Selected Video"
            val pickedItem = VideoItem(
                id = uri.hashCode().toLong(),
                uri = uri,
                title = title,
                durationMs = 0L,
                dateAdded = System.currentTimeMillis() / 1000,
                thumbnailUri = null,
            )
            val playlistItems = if (isM3u8Title(title)) {
                M3uPlaylistParser.expandPlaylist(context, uri, title)
            } else {
                listOf(pickedItem)
            }
            onPlayVideo(PlaylistState(items = playlistItems, currentIndex = 0))
        }
    }

    fun entryKey(entry: VideoLibraryEntry): String {
        return when (entry) {
            is VideoLibraryEntry.Series -> "series:${entry.series.id}"
            is VideoLibraryEntry.Single -> "single:${entry.video.uri}"
        }
    }

    fun urisForEntry(entry: VideoLibraryEntry): List<Uri> {
        return when (entry) {
            is VideoLibraryEntry.Series -> entry.series.episodes.map { it.uri }
            is VideoLibraryEntry.Single -> listOf(entry.video.uri)
        }
    }

    fun urisForKeys(keys: Set<String>): List<Uri> {
        return sortedLibrary
            .filter { entryKey(it) in keys }
            .flatMap { urisForEntry(it) }
    }

    fun titleForUris(uris: List<Uri>): String? {
        if (uris.size != 1) return null
        val target = uris.first().toString()
        libraryEntries.forEach { entry ->
            when (entry) {
                is VideoLibraryEntry.Single -> {
                    if (entry.video.uri.toString() == target) return entry.video.title
                }
                is VideoLibraryEntry.Series -> {
                    entry.series.episodes.firstOrNull { it.uri.toString() == target }?.let {
                        return it.title
                    }
                }
            }
        }
        return null
    }

    fun subtitleForDelete(uris: List<Uri>): String? {
        if (uris.size == 1) return titleForUris(uris)
        val uriSet = uris.map { it.toString() }.toSet()
        libraryEntries.forEach { entry ->
            if (entry is VideoLibraryEntry.Series) {
                val entryUris = entry.series.episodes.map { it.uri.toString() }.toSet()
                if (entryUris == uriSet) return entry.series.title
            }
        }
        return null
    }

    fun closeDeleteDialog() {
        showDeleteDialog = false
        isDeleting = false
        pendingDeleteUris = null
        pendingDeleteSubtitle = null
    }

    fun openDeleteDialog(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pendingDeleteUris = uris
        pendingDeleteSubtitle = subtitleForDelete(uris)
        showDeleteDialog = true
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedKeys = emptySet()
    }

    val deleteFromDevice = rememberVideoDeleter(
        playbackStore = playbackStore,
        hiddenVideosStore = hiddenVideosStore,
        onVideosRemoved = onVideosRemoved,
        onDeleteStarted = { isDeleting = true },
        onDeleteFinished = {
            exitSelectionMode()
            closeDeleteDialog()
        },
    )

    fun playVideoItem(video: VideoItem) {
        scope.launch {
            val state = withContext(Dispatchers.IO) {
                repository.playlistStateFor(video, libraryEntries)
            }
            onPlayVideo(state)
        }
    }

    fun playRecentRecord(record: PlaybackRecord) {
        val targetUri = record.uri.toString()
        val matched = libraryEntries.flatMap { entry ->
            when (entry) {
                is VideoLibraryEntry.Single -> listOf(entry.video)
                is VideoLibraryEntry.Series -> entry.series.episodes
            }
        }.find { it.uri.toString() == targetUri }

        if (matched != null) {
            playVideoItem(matched)
        } else {
            val fallback = VideoItem(
                id = record.uri.hashCode().toLong(),
                uri = record.uri,
                title = record.title,
                durationMs = record.durationMs,
                dateAdded = record.lastPlayedAt / 1000,
                thumbnailUri = null,
            )
            playVideoItem(fallback)
        }
    }

    fun hideVideos(uris: List<Uri>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                hiddenVideosStore.hideAll(uris)
                playbackStore.removeRecords(uris)
            }
            onVideosRemoved(uris)
            exitSelectionMode()
            closeDeleteDialog()
        }
    }

    if (showSortSheet) {
        SortBottomSheet(
            currentOrder = sortOrder,
            onOrderSelected = { sortOrder = it },
            onDismiss = { showSortSheet = false },
        )
    }

    if (showDeleteDialog) {
        val uris = pendingDeleteUris
        if (uris != null) {
            DeleteVideoDialog(
                count = uris.size,
                subtitle = pendingDeleteSubtitle,
                isDeleting = isDeleting,
                onDismiss = ::closeDeleteDialog,
                onHideFromList = { hideVideos(uris) },
                onDeleteFromDevice = { deleteFromDevice(uris) },
            )
        }
    }

    Scaffold(
        modifier = modifier,
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = LumenBackground,
        floatingActionButton = {
            if (!selectionMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    if (showScrollToTop) {
                        FloatingActionButton(
                            onClick = {
                                scope.launch {
                                    val layoutInfo = listState.layoutInfo
                                    val visibleItems = layoutInfo.visibleItemsInfo
                                    if (visibleItems.isEmpty()) return@launch
                                    val averageItemSize =
                                        visibleItems.sumOf { it.size } / visibleItems.size +
                                            layoutInfo.mainAxisItemSpacing
                                    val scrollPixels =
                                        (
                                            averageItemSize * listState.firstVisibleItemIndex +
                                                listState.firstVisibleItemScrollOffset
                                            ).toFloat()
                                    if (scrollPixels <= 0f) return@launch
                                    val durationMillis =
                                        (listState.firstVisibleItemIndex * 40 + 200).coerceIn(300, 600)
                                    listState.animateScrollBy(
                                        value = -scrollPixels,
                                        animationSpec = tween(
                                            durationMillis = durationMillis,
                                            easing = LinearEasing,
                                        ),
                                    )
                                }
                            },
                            containerColor = LumenAccentText.copy(alpha = 0.15f),
                            contentColor = LumenAccentText,
                            modifier = Modifier.clip(CircleShape),
                        ) {
                            Icon(
                                imageVector = Icons.Default.KeyboardArrowUp,
                                contentDescription = stringResource(R.string.scroll_to_top),
                            )
                        }
                    }
                    FloatingActionButton(
                        onClick = { filePickerLauncher.launch(VIDEO_PICKER_MIME_TYPES) },
                        containerColor = LumenAccentText.copy(alpha = 0.15f),
                        contentColor = LumenAccentText,
                        modifier = Modifier.clip(CircleShape),
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.pick_video),
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
            if (selectionMode) {
                SelectionTopBar(
                    selectedCount = selectedKeys.size,
                    totalCount = sortedLibrary.size,
                    onSelectAll = {
                        selectedKeys = sortedLibrary.map { entryKey(it) }.toSet()
                    },
                    onDelete = {
                        openDeleteDialog(urisForKeys(selectedKeys))
                    },
                    onCancel = ::exitSelectionMode,
                )
            } else {
                LumenListHeader(
                    title = stringResource(R.string.video_list_title),
                    applyStatusBarPadding = false,
                    actions = {
                        IconButton(
                            onClick = { showSortSheet = true },
                            modifier = Modifier.size(24.dp),
                        ) {
                            Icon(
                                imageVector = Icons.AutoMirrored.Filled.Sort,
                                contentDescription = stringResource(R.string.sort),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                    },
                )
            }

            val pullRefreshState = rememberPullToRefreshState()

            Box(modifier = Modifier.fillMaxSize()) {
                when {
                    !hasPermission -> {
                        Column(
                            modifier = Modifier
                                .align(Alignment.Center)
                                .padding(24.dp),
                            horizontalAlignment = Alignment.CenterHorizontally,
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Text(
                                text = stringResource(R.string.permission_required),
                                style = MaterialTheme.typography.titleMedium,
                            )
                            TextButton(
                                onClick = {
                                    permissionLauncher.launch(Permissions.readVideoPermission)
                                },
                            ) {
                                Text(stringResource(R.string.grant_permission))
                            }
                        }
                    }
                    else -> {
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .pullToRefresh(
                                    state = pullRefreshState,
                                    isRefreshing = isRefreshing || isLibraryLoading,
                                    onRefresh = onPullRefresh,
                                    enabled = !selectionMode,
                                ),
                        ) {
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = PaddingValues(horizontal = 16.dp, vertical = 12.dp),
                                verticalArrangement = Arrangement.spacedBy(12.dp),
                            ) {
                                if (!selectionMode && recentRecords.isNotEmpty()) {
                                    item(key = "recent-playback") {
                                        RecentPlaybackSection(
                                            records = recentRecords,
                                            onRecordClick = ::playRecentRecord,
                                        )
                                    }
                                }

                                if (isLibraryLoading && sortedLibrary.isEmpty()) {
                                    item(key = "library-loading") {
                                        Box(
                                            modifier = Modifier
                                                .fillParentMaxWidth()
                                                .padding(vertical = 48.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(color = LumenAccentText)
                                        }
                                    }
                                }

                                if (!isLibraryLoading && sortedLibrary.isEmpty() && recentRecords.isEmpty()) {
                                    item(key = "empty") {
                                        Column(
                                            modifier = Modifier
                                                .fillParentMaxSize()
                                                .padding(24.dp),
                                            horizontalAlignment = Alignment.CenterHorizontally,
                                            verticalArrangement = Arrangement.Center,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.empty_video_list),
                                                style = MaterialTheme.typography.titleMedium,
                                            )
                                            Text(
                                                text = stringResource(R.string.empty_video_hint),
                                                style = MaterialTheme.typography.bodyMedium,
                                                color = LumenOnSurfaceMuted,
                                            )
                                        }
                                    }
                                }

                                items(
                                    items = sortedLibrary,
                                    key = { entry -> entryKey(entry) },
                                    contentType = { entry ->
                                        when (entry) {
                                            is VideoLibraryEntry.Series -> 0
                                            is VideoLibraryEntry.Single -> 1
                                        }
                                    },
                                ) { entry ->
                                    val key = entryKey(entry)
                                    val isSelected = key in selectedKeys

                                    when (entry) {
                                        is VideoLibraryEntry.Series -> {
                                            val playbackStats = rememberSeriesPlaybackStats(
                                                series = entry.series,
                                                playbackRecords = playbackRecords,
                                            )
                                            SeriesFolderItem(
                                                series = entry.series,
                                                playbackStats = playbackStats,
                                                selectionMode = selectionMode,
                                                selected = isSelected,
                                                onClick = {
                                                    if (selectionMode) {
                                                        selectedKeys = if (isSelected) {
                                                            selectedKeys - key
                                                        } else {
                                                            selectedKeys + key
                                                        }
                                                    } else {
                                                        onOpenSeries(entry.series.id)
                                                    }
                                                },
                                                onLongClick = {
                                                    selectionMode = true
                                                    selectedKeys = setOf(key)
                                                },
                                                onSwipeDelete = {
                                                    openDeleteDialog(urisForEntry(entry))
                                                },
                                            )
                                        }
                                        is VideoLibraryEntry.Single -> {
                                            VideoListItem(
                                                video = entry.video,
                                                playbackRecord = playbackRecords[entry.video.uri.toString()],
                                                selectionMode = selectionMode,
                                                selected = isSelected,
                                                onClick = {
                                                    if (selectionMode) {
                                                        selectedKeys = if (isSelected) {
                                                            selectedKeys - key
                                                        } else {
                                                            selectedKeys + key
                                                        }
                                                    } else {
                                                        playVideoItem(entry.video)
                                                    }
                                                },
                                                onLongClick = {
                                                    selectionMode = true
                                                    selectedKeys = setOf(key)
                                                },
                                                onSwipeDelete = {
                                                    openDeleteDialog(listOf(entry.video.uri))
                                                },
                                            )
                                        }
                                    }
                                }
                            }
                            PullToRefreshDefaults.Indicator(
                                modifier = Modifier.align(Alignment.TopCenter),
                                isRefreshing = isRefreshing || isLibraryLoading,
                                state = pullRefreshState,
                            )
                        }
                    }
                }
            }
        }
    }
}
