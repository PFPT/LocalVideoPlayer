package com.zhaoshuo.localvideoplayer.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.produceState
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.data.HiddenVideosStore
import com.zhaoshuo.localvideoplayer.data.PinnedItemsStore
import com.zhaoshuo.localvideoplayer.data.PlayCountStore
import com.zhaoshuo.localvideoplayer.data.PlaybackStore
import com.zhaoshuo.localvideoplayer.data.ThemePreferencesStore
import com.zhaoshuo.localvideoplayer.data.VideoRepository
import com.zhaoshuo.localvideoplayer.model.LibraryFilter
import com.zhaoshuo.localvideoplayer.model.LibraryTypeTab
import com.zhaoshuo.localvideoplayer.model.LibraryViewLayout
import com.zhaoshuo.localvideoplayer.model.PlaybackRecord
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import com.zhaoshuo.localvideoplayer.model.VideoSortOrder
import com.zhaoshuo.localvideoplayer.model.applyTypeTab
import com.zhaoshuo.localvideoplayer.model.entryKey
import com.zhaoshuo.localvideoplayer.model.filterLibrary
import com.zhaoshuo.localvideoplayer.model.partitionWithPinned
import com.zhaoshuo.localvideoplayer.model.sortLibrary
import com.zhaoshuo.localvideoplayer.model.withoutUris
import com.zhaoshuo.localvideoplayer.player.PlaylistState
import com.zhaoshuo.localvideoplayer.ui.components.DeleteVideoDialog
import com.zhaoshuo.localvideoplayer.ui.components.LibraryOptionsBottomSheet
import com.zhaoshuo.localvideoplayer.ui.components.LumenEmptyState
import com.zhaoshuo.localvideoplayer.ui.components.LumenLibraryTabBar
import com.zhaoshuo.localvideoplayer.ui.components.LumenListHeader
import com.zhaoshuo.localvideoplayer.ui.components.RecentPlaybackSection
import com.zhaoshuo.localvideoplayer.ui.components.SelectionTopBar
import com.zhaoshuo.localvideoplayer.ui.components.SeriesFolderItem
import com.zhaoshuo.localvideoplayer.ui.components.ThemeColorBottomSheet
import com.zhaoshuo.localvideoplayer.ui.components.VideoListItem
import com.zhaoshuo.localvideoplayer.ui.components.rememberEpisodeProgressList
import com.zhaoshuo.localvideoplayer.ui.components.rememberPlaybackProgress
import com.zhaoshuo.localvideoplayer.ui.components.rememberSeriesPlaybackStats
import com.zhaoshuo.localvideoplayer.ui.theme.LauncherIcon
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme
import com.zhaoshuo.localvideoplayer.ui.theme.ThemeMode
import com.zhaoshuo.localvideoplayer.ui.theme.ThemeState
import com.zhaoshuo.localvideoplayer.util.LauncherIconSwitcher
import com.zhaoshuo.localvideoplayer.util.M3uPlaylistParser
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
    pinnedItemsStore: PinnedItemsStore,
    playCountStore: PlayCountStore,
    themeStore: ThemePreferencesStore,
    themeState: ThemeState,
    onRequestPermission: () -> Unit,
    onPlayVideo: (PlaylistState) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onVideosRemoved: (List<Uri>) -> Unit,
    onPullRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = LocalLumenTheme.current
    var sortOrder by rememberSaveable { mutableStateOf(VideoSortOrder.DATE_DESC) }
    var collapseSingles by rememberSaveable { mutableStateOf(false) }
    var viewLayout by rememberSaveable { mutableStateOf(LibraryViewLayout.SECTIONED) }
    var libraryFilter by remember { mutableStateOf(LibraryFilter()) }
    var selectedTypeTab by rememberSaveable { mutableStateOf(LibraryTypeTab.ALL) }
    var showOptionsSheet by rememberSaveable { mutableStateOf(false) }
    var showThemeSheet by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>?>(null) }
    var pendingDeleteSubtitle by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val pinnedKeys by pinnedItemsStore.pinnedKeys.collectAsState(initial = emptySet())
    val playCounts by playCountStore.playCounts.collectAsState(initial = emptyMap())

    val processedLibrary by produceState(
        initialValue = emptyList<VideoLibraryEntry>(),
        libraryEntries,
        sortOrder,
        libraryFilter,
        selectedTypeTab,
        pinnedKeys,
        playCounts,
    ) {
        value = withContext(Dispatchers.Default) {
            var list = filterLibrary(libraryEntries, libraryFilter)
            list = applyTypeTab(list, selectedTypeTab, pinnedKeys)
            list = sortLibrary(list, sortOrder, playCounts)
            list
        }
    }
    val isUnfilteredAllTab =
        selectedTypeTab == LibraryTypeTab.ALL && libraryFilter == LibraryFilter()
    val processedMatchesLibrary = remember(libraryEntries, processedLibrary) {
        if (processedLibrary.isEmpty() || libraryEntries.isEmpty()) {
            false
        } else {
            val sourceKeys = libraryEntries.mapTo(HashSet(libraryEntries.size)) { entryKey(it) }
            val resultKeys = processedLibrary.mapTo(HashSet(processedLibrary.size)) { entryKey(it) }
            resultKeys == sourceKeys
        }
    }
    val displayLibrary = when {
        libraryEntries.isEmpty() -> emptyList()
        processedLibrary.isEmpty() && isUnfilteredAllTab -> libraryEntries
        processedLibrary.isEmpty() -> emptyList()
        isUnfilteredAllTab && !processedMatchesLibrary -> libraryEntries
        else -> processedLibrary
    }
    val pinnedSections = remember(displayLibrary, pinnedKeys, viewLayout) {
        if (viewLayout == LibraryViewLayout.SECTIONED) {
            partitionWithPinned(displayLibrary, pinnedKeys)
        } else {
            null
        }
    }
    val flatLibrary = remember(displayLibrary, viewLayout) {
        if (viewLayout == LibraryViewLayout.FLAT) {
            displayLibrary
        } else {
            emptyList()
        }
    }
    val hasActiveOptions = viewLayout != LibraryViewLayout.SECTIONED ||
        libraryFilter != LibraryFilter() ||
        sortOrder != VideoSortOrder.DATE_DESC ||
        selectedTypeTab == LibraryTypeTab.MARKED
    val typeTabs = remember {
        listOf(
            LibraryTypeTab.ALL,
            LibraryTypeTab.EPISODES,
            LibraryTypeTab.FOLDERS,
            LibraryTypeTab.SHORT_CLIPS,
            LibraryTypeTab.SINGLES,
            LibraryTypeTab.MARKED,
        )
    }
    val isMarkedTabActive = selectedTypeTab == LibraryTypeTab.MARKED
    val isSortingLibrary = libraryEntries.isNotEmpty() && processedLibrary.isEmpty()
    val listState = rememberLazyListState()

    LaunchedEffect(selectedTypeTab) {
        listState.animateScrollToItem(0)
    }
    val isListScrolling by remember {
        derivedStateOf { listState.isScrollInProgress }
    }
    val isListScrolled by remember {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val showScrollToTop =
        !selectionMode &&
            !isLibraryLoading &&
            hasPermission &&
            (displayLibrary.isNotEmpty() || recentRecords.isNotEmpty()) &&
            isListScrolled

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
            val title = uri.lastPathSegment?.substringAfterLast(':')
                ?: context.getString(R.string.selected_video_fallback)
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

    fun toggleMark(key: String) {
        scope.launch(Dispatchers.IO) {
            pinnedItemsStore.toggle(key)
        }
    }

    fun urisForEntry(entry: VideoLibraryEntry): List<Uri> {
        return when (entry) {
            is VideoLibraryEntry.Series -> entry.series.episodes.map { it.uri }
            is VideoLibraryEntry.Single -> listOf(entry.video.uri)
        }
    }

    fun urisForKeys(keys: Set<String>): List<Uri> {
        return displayLibrary
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
        playCountStore = playCountStore,
        onVideosRemoved = onVideosRemoved,
        onDeleteStarted = { isDeleting = true },
        onDeleteFinished = {
            exitSelectionMode()
            closeDeleteDialog()
        },
    )

    fun playCountKeyForVideo(video: VideoItem): String {
        val targetUri = video.uri.toString()
        libraryEntries.forEach { entry ->
            when (entry) {
                is VideoLibraryEntry.Single -> {
                    if (entry.video.uri.toString() == targetUri) return entryKey(entry)
                }
                is VideoLibraryEntry.Series -> {
                    if (entry.series.episodes.any { it.uri.toString() == targetUri }) {
                        return entryKey(entry)
                    }
                }
            }
        }
        return "single:${video.uri}"
    }

    fun incrementPlayCount(key: String) {
        scope.launch(Dispatchers.IO) {
            playCountStore.increment(key)
        }
    }

    fun playVideoItem(video: VideoItem) {
        incrementPlayCount(playCountKeyForVideo(video))
        scope.launch {
            val state = withContext(Dispatchers.IO) {
                repository.playlistStateFor(video, libraryEntries)
            }
            onPlayVideo(state)
        }
    }

    val libraryEntriesState by rememberUpdatedState(libraryEntries)
    val onPlayVideoState by rememberUpdatedState(onPlayVideo)
    val playVideoHandler = remember(scope, repository) {
        { video: VideoItem ->
            incrementPlayCount(playCountKeyForVideo(video))
            scope.launch {
                val state = withContext(Dispatchers.IO) {
                    repository.playlistStateFor(video, libraryEntriesState)
                }
                onPlayVideoState(state)
            }
        }
    }
    val openSeriesHandler = remember(onOpenSeries) { { seriesId: String -> onOpenSeries(seriesId) } }
    val openDeleteHandler = remember { { uris: List<Uri> -> openDeleteDialog(uris) } }
    val toggleSelectionHandler = remember {
        { key: String, isSelected: Boolean ->
            selectedKeys = if (isSelected) selectedKeys - key else selectedKeys + key
        }
    }
    val enterSelectionHandler = remember {
        { key: String ->
            selectionMode = true
            selectedKeys = setOf(key)
        }
    }

    @Composable
    fun SeriesLibraryRow(entry: VideoLibraryEntry.Series) {
        val key = entryKey(entry)
        val isSelected = key in selectedKeys
        val episodeProgress = rememberEpisodeProgressList(
            series = entry.series,
            playbackRecords = playbackRecords,
        )
        val playbackStats = rememberSeriesPlaybackStats(
            series = entry.series,
            episodeProgress = episodeProgress,
        )
        SeriesFolderItem(
            series = entry.series,
            playbackStats = playbackStats,
            selectionMode = selectionMode,
            selected = isSelected,
            deferThumbnailLoad = isListScrolling,
            marked = key in pinnedKeys,
            onToggleMark = { toggleMark(key) },
            onClick = {
                if (selectionMode) {
                    toggleSelectionHandler(key, isSelected)
                } else {
                    incrementPlayCount(key)
                    openSeriesHandler(entry.series.id)
                }
            },
            onLongClick = { enterSelectionHandler(key) },
            onSwipeDelete = { openDeleteHandler(urisForEntry(entry)) },
        )
    }

    @Composable
    fun SingleLibraryRow(entry: VideoLibraryEntry.Single) {
        val key = entryKey(entry)
        val isSelected = key in selectedKeys
        val progress = rememberPlaybackProgress(
            uriKey = entry.video.uri.toString(),
            playbackRecords = playbackRecords,
        )
        VideoListItem(
            video = entry.video,
            progress = progress,
            selectionMode = selectionMode,
            selected = isSelected,
            deferThumbnailLoad = isListScrolling,
            marked = key in pinnedKeys,
            onToggleMark = { toggleMark(key) },
            onClick = {
                if (selectionMode) {
                    toggleSelectionHandler(key, isSelected)
                } else {
                    playVideoHandler(entry.video)
                }
            },
            onLongClick = { enterSelectionHandler(key) },
            onSwipeDelete = { openDeleteHandler(listOf(entry.video.uri)) },
        )
    }

    @Composable
    fun AnyLibraryRow(entry: VideoLibraryEntry) {
        when (entry) {
            is VideoLibraryEntry.Series -> SeriesLibraryRow(entry)
            is VideoLibraryEntry.Single -> SingleLibraryRow(entry)
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
                playCountStore.removeForUris(uris)
            }
            onVideosRemoved(uris)
            exitSelectionMode()
            closeDeleteDialog()
        }
    }

    if (showOptionsSheet) {
        LibraryOptionsBottomSheet(
            currentOrder = sortOrder,
            viewLayout = viewLayout,
            libraryFilter = libraryFilter,
            collapseSingles = collapseSingles,
            onOrderSelected = { sortOrder = it },
            onViewLayoutSelected = { viewLayout = it },
            onFilterChanged = { libraryFilter = it },
            onCollapseSinglesChanged = { collapseSingles = it },
            onDismiss = { showOptionsSheet = false },
        )
    }

    val selectedLauncherIcon by themeStore.launcherIcon.collectAsState(initial = LauncherIcon.Default)
    if (showThemeSheet) {
        ThemeColorBottomSheet(
            themeMode = themeState.mode,
            initialColors = themeState.colors,
            selectedLauncherIcon = selectedLauncherIcon,
            onSave = { colors, icon ->
                scope.launch {
                    themeStore.saveThemeColors(colors)
                    if (icon != selectedLauncherIcon) {
                        themeStore.setLauncherIcon(icon)
                        LauncherIconSwitcher.apply(context, icon)
                        Toast.makeText(
                            context,
                            context.getString(R.string.theme_launcher_icon_updated),
                            Toast.LENGTH_SHORT,
                        ).show()
                    }
                }
            },
            onDismiss = { showThemeSheet = false },
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
        containerColor = MaterialTheme.colorScheme.background,
        floatingActionButton = {
            if (!selectionMode) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    if (showScrollToTop) {
                        VideoListFab(
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
                            icon = Icons.Default.ExpandLess,
                            contentDescription = stringResource(R.string.scroll_to_top),
                        )
                    }
                    VideoListFab(
                        onClick = { filePickerLauncher.launch(VIDEO_PICKER_MIME_TYPES) },
                        icon = Icons.Default.Add,
                        contentDescription = stringResource(R.string.pick_video),
                    )
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
                    totalCount = displayLibrary.size,
                    onSelectAll = {
                        selectedKeys = displayLibrary.map { entryKey(it) }.toSet()
                    },
                    onDelete = {
                        openDeleteDialog(urisForKeys(selectedKeys))
                    },
                    onCancel = ::exitSelectionMode,
                )
            } else {
                LumenListHeader(
                    title = stringResource(R.string.video_list_title),
                    titleContent = {
                        Icon(
                            imageVector = Icons.Filled.VideoLibrary,
                            contentDescription = stringResource(R.string.video_list_title),
                            tint = MaterialTheme.colorScheme.onBackground,
                        )
                    },
                    applyStatusBarPadding = false,
                    actions = {
                        IconButton(onClick = onOpenDownloads) {
                            Icon(
                                imageVector = Icons.Filled.Download,
                                contentDescription = stringResource(R.string.downloads_title),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        IconButton(
                            onClick = {
                                selectedTypeTab = if (isMarkedTabActive) {
                                    LibraryTypeTab.ALL
                                } else {
                                    LibraryTypeTab.MARKED
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (isMarkedTabActive) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Outlined.StarOutline
                                },
                                contentDescription = stringResource(R.string.tab_marked),
                                tint = if (isMarkedTabActive) {
                                    theme.accent
                                } else {
                                    MaterialTheme.colorScheme.onBackground
                                },
                            )
                        }
                        IconButton(
                            onClick = {
                                val nextMode = if (themeState.mode == ThemeMode.Dark) {
                                    ThemeMode.Light
                                } else {
                                    ThemeMode.Dark
                                }
                                scope.launch {
                                    themeStore.setThemeMode(nextMode)
                                }
                            },
                        ) {
                            Icon(
                                imageVector = if (themeState.mode == ThemeMode.Dark) {
                                    Icons.Default.LightMode
                                } else {
                                    Icons.Default.DarkMode
                                },
                                contentDescription = if (themeState.mode == ThemeMode.Dark) {
                                    stringResource(R.string.theme_mode_light)
                                } else {
                                    stringResource(R.string.theme_mode_dark)
                                },
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        IconButton(onClick = { showThemeSheet = true }) {
                            Icon(
                                imageVector = Icons.Default.Palette,
                                contentDescription = stringResource(R.string.theme_color),
                                tint = MaterialTheme.colorScheme.onBackground,
                            )
                        }
                        IconButton(onClick = { showOptionsSheet = true }) {
                            Box {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.library_options),
                                    tint = MaterialTheme.colorScheme.onBackground,
                                )
                                if (hasActiveOptions) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(theme.accent),
                                    )
                                }
                            }
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
                                onClick = onRequestPermission,
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
                                    isRefreshing = isRefreshing,
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

                                if (!selectionMode) {
                                    item(key = "library-tabs") {
                                        LumenLibraryTabBar(
                                            tabs = typeTabs,
                                            selectedTab = selectedTypeTab,
                                            onTabSelected = { selectedTypeTab = it },
                                            contentPadding = PaddingValues(0.dp),
                                            modifier = Modifier
                                                .fillMaxWidth()
                                                .padding(bottom = 4.dp),
                                        )
                                    }
                                }

                                if ((isLibraryLoading || isSortingLibrary) && displayLibrary.isEmpty() && libraryEntries.isEmpty()) {
                                    item(key = "library-loading") {
                                        Box(
                                            modifier = Modifier
                                                .fillParentMaxWidth()
                                                .padding(vertical = 48.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            CircularProgressIndicator(color = theme.accent)
                                        }
                                    }
                                }

                                if (!isLibraryLoading && !isSortingLibrary && libraryEntries.isEmpty() && recentRecords.isEmpty()) {
                                    item(key = "empty") {
                                        LumenEmptyState(
                                            title = stringResource(R.string.empty_video_list),
                                            subtitle = stringResource(R.string.empty_video_hint),
                                            icon = Icons.Default.VideoLibrary,
                                            modifier = Modifier
                                                .fillParentMaxSize()
                                                .padding(24.dp),
                                        )
                                    }
                                }

                                if (
                                    !isLibraryLoading &&
                                    !isSortingLibrary &&
                                    displayLibrary.isEmpty() &&
                                    libraryEntries.isNotEmpty()
                                ) {
                                    item(key = "no-filter-results") {
                                        Box(
                                            modifier = Modifier
                                                .fillParentMaxWidth()
                                                .padding(vertical = 48.dp),
                                            contentAlignment = Alignment.Center,
                                        ) {
                                            Text(
                                                text = stringResource(R.string.no_filter_results),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = theme.onSurfaceMuted,
                                            )
                                        }
                                    }
                                }

                                if (viewLayout == LibraryViewLayout.FLAT) {
                                    items(
                                        items = flatLibrary,
                                        key = { entry -> "flat-${viewLayout.name}-${entryKey(entry)}" },
                                        contentType = { entry ->
                                            when (entry) {
                                                is VideoLibraryEntry.Series -> 0
                                                is VideoLibraryEntry.Single -> 1
                                            }
                                        },
                                    ) { entry ->
                                        AnyLibraryRow(entry)
                                    }
                                } else {
                                    val sections = pinnedSections
                                    if (sections != null) {
                                        if (sections.pinned.isNotEmpty()) {
                                            item(key = "header-pinned-${viewLayout.name}") {
                                                LibrarySectionHeader(stringResource(R.string.section_pinned))
                                            }
                                            items(
                                                items = sections.pinned,
                                                key = { entry -> "sectioned-${viewLayout.name}-${entryKey(entry)}" },
                                                contentType = { entry ->
                                                    when (entry) {
                                                        is VideoLibraryEntry.Series -> 0
                                                        is VideoLibraryEntry.Single -> 1
                                                    }
                                                },
                                            ) { entry ->
                                                AnyLibraryRow(entry)
                                            }
                                        }

                                        if (sections.episodes.isNotEmpty()) {
                                            item(key = "header-episodes") {
                                                LibrarySectionHeader(stringResource(R.string.section_episodes))
                                            }
                                            items(
                                                items = sections.episodes,
                                                key = { entry -> entryKey(entry) },
                                                contentType = { 0 },
                                            ) { entry ->
                                                SeriesLibraryRow(entry)
                                            }
                                        }

                                        if (sections.folders.isNotEmpty()) {
                                            item(key = "header-folders") {
                                                LibrarySectionHeader(stringResource(R.string.section_folders))
                                            }
                                            items(
                                                items = sections.folders,
                                                key = { entry -> entryKey(entry) },
                                                contentType = { 0 },
                                            ) { entry ->
                                                SeriesLibraryRow(entry)
                                            }
                                        }

                                        if (sections.shortClips.isNotEmpty()) {
                                            item(key = "header-short-clips") {
                                                LibrarySectionHeader(stringResource(R.string.section_short_clips))
                                            }
                                            items(
                                                items = sections.shortClips,
                                                key = { entry -> entryKey(entry) },
                                                contentType = { 0 },
                                            ) { entry ->
                                                SeriesLibraryRow(entry)
                                            }
                                        }

                                        if (sections.singles.isNotEmpty()) {
                                            if (collapseSingles) {
                                                item(key = "expand-singles") {
                                                    Text(
                                                        text = stringResource(
                                                            R.string.expand_singles,
                                                            sections.singles.size,
                                                        ),
                                                        style = MaterialTheme.typography.labelLarge,
                                                        color = LocalLumenTheme.current.accent,
                                                        modifier = Modifier
                                                            .padding(top = 8.dp, bottom = 4.dp)
                                                            .clickable { collapseSingles = false },
                                                    )
                                                }
                                            } else {
                                                item(key = "header-singles") {
                                                    LibrarySectionHeader(
                                                        stringResource(
                                                            R.string.section_other_videos,
                                                            sections.singles.size,
                                                        ),
                                                    )
                                                }
                                                items(
                                                    items = sections.singles,
                                                    key = { entry -> entryKey(entry) },
                                                    contentType = { 1 },
                                                ) { entry ->
                                                    SingleLibraryRow(entry)
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                            PullToRefreshDefaults.Indicator(
                                modifier = Modifier.align(Alignment.TopCenter),
                                isRefreshing = isRefreshing,
                                state = pullRefreshState,
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun LibrarySectionHeader(title: String) {
    val theme = LocalLumenTheme.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = theme.onSurfaceMuted,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun VideoListFab(
    onClick: () -> Unit,
    icon: ImageVector,
    contentDescription: String,
) {
    val theme = LocalLumenTheme.current
    Box(
        modifier = Modifier
            .size(56.dp)
            .clip(CircleShape)
            .background(theme.surface.copy(alpha = 1f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = icon,
            contentDescription = contentDescription,
            tint = theme.accent,
            modifier = Modifier.size(24.dp),
        )
    }
}
