package com.pfpt.localvideoplayer.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.material3.pulltorefresh.PullToRefreshDefaults
import androidx.compose.material3.pulltorefresh.pullToRefresh
import androidx.compose.material3.pulltorefresh.rememberPullToRefreshState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
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
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.data.HiddenVideosStore
import com.pfpt.localvideoplayer.data.LibraryPreferences
import com.pfpt.localvideoplayer.data.LibraryPreferencesStore
import com.pfpt.localvideoplayer.data.PinnedItemsStore
import com.pfpt.localvideoplayer.data.PlayCountStore
import com.pfpt.localvideoplayer.data.PlaybackStore
import com.pfpt.localvideoplayer.data.ThemePreferencesStore
import com.pfpt.localvideoplayer.data.LibraryViewModel
import com.pfpt.localvideoplayer.model.LibraryFilter
import com.pfpt.localvideoplayer.model.LibraryTypeTab
import com.pfpt.localvideoplayer.model.LibraryViewLayout
import com.pfpt.localvideoplayer.model.PlaybackRecord
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.model.VideoLibraryEntry
import com.pfpt.localvideoplayer.model.VideoSortOrder
import com.pfpt.localvideoplayer.model.applyTypeTab
import com.pfpt.localvideoplayer.model.entryKey
import com.pfpt.localvideoplayer.model.filterLibrary
import com.pfpt.localvideoplayer.model.showPinnedHomeRow
import com.pfpt.localvideoplayer.model.partitionWithPinned
import com.pfpt.localvideoplayer.model.searchLibrary
import com.pfpt.localvideoplayer.model.sortLibrary
import com.pfpt.localvideoplayer.player.PlaylistState
import com.pfpt.localvideoplayer.cast.CastUiState
import com.pfpt.localvideoplayer.cast.LocalCastManager
import com.pfpt.localvideoplayer.ui.components.CastDeviceBottomSheet
import com.pfpt.localvideoplayer.ui.components.CastMiniController
import com.pfpt.localvideoplayer.ui.components.rememberCastSheetActions
import com.pfpt.localvideoplayer.ui.components.LibraryOptionsBottomSheet
import com.pfpt.localvideoplayer.ui.components.LumenLibraryTabBar
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceXs
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.pfpt.localvideoplayer.ui.theme.ThemeMode
import com.pfpt.localvideoplayer.ui.theme.ThemeState
import com.pfpt.localvideoplayer.util.M3uPlaylistParser
import com.pfpt.localvideoplayer.ui.library.AnyLibraryRow
import com.pfpt.localvideoplayer.ui.library.LibraryHomeList
import com.pfpt.localvideoplayer.ui.library.LibraryScreenChrome
import com.pfpt.localvideoplayer.ui.library.playCountKeyForVideo
import com.pfpt.localvideoplayer.ui.library.subtitleForDelete
import com.pfpt.localvideoplayer.ui.library.titleForUri
import com.pfpt.localvideoplayer.ui.library.urisForEntry
import com.pfpt.localvideoplayer.util.isM3u8Title
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.snapshotFlow

private val VIDEO_PICKER_MIME_TYPES = arrayOf(
    "video/*",
    "application/vnd.apple.mpegurl",
    "application/x-mpegURL",
)

private data class LibraryProcessCache(
    val signature: String,
    val entries: List<VideoLibraryEntry>,
)

private object LibraryDisplayCache {
    @Volatile
    var snapshot: LibraryProcessCache? = null
}

private fun libraryProcessSignature(
    libraryEntries: List<VideoLibraryEntry>,
    sortOrder: VideoSortOrder,
    libraryFilter: LibraryFilter,
    selectedTypeTab: LibraryTypeTab,
    pinnedKeys: Set<String>,
    playCounts: Map<String, Int>,
): String = buildString {
    append(sortOrder.name)
    append('|')
    append(selectedTypeTab.name)
    append('|')
    append(libraryFilter.duration.name)
    append('|')
    append(libraryFilter.date.name)
    append('|')
    append(pinnedKeys.size)
    append('|')
    append(pinnedKeys.hashCode())
    append('|')
    append(playCounts.size)
    append('|')
    append(playCounts.hashCode())
    append('|')
    append(libraryEntries.size)
    append('|')
    libraryEntries.forEach { entry ->
        append(entryKey(entry))
        append(';')
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    libraryEntries: List<VideoLibraryEntry>,
    isLibraryLoading: Boolean,
    cacheResolved: Boolean,
    isRefreshing: Boolean,
    hasPermission: Boolean,
    libraryViewModel: LibraryViewModel,
    hiddenVideosStore: HiddenVideosStore,
    playbackStore: PlaybackStore,
    pinnedItemsStore: PinnedItemsStore,
    playCountStore: PlayCountStore,
    themeStore: ThemePreferencesStore,
    libraryPreferencesStore: LibraryPreferencesStore,
    themeState: ThemeState,
    onRequestPermission: () -> Unit,
    onPlayVideo: (PlaylistState) -> Unit,
    onOpenSeries: (String) -> Unit,
    onOpenDownloads: () -> Unit,
    onOpenSettings: () -> Unit,
    onVideosRemoved: (List<Uri>) -> Unit,
    onPullRefresh: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = LocalLumenTheme.current
    val castManager = LocalCastManager.current
    val idleCastState = remember { kotlinx.coroutines.flow.MutableStateFlow<CastUiState>(CastUiState.Idle) }
    val idlePlaying = remember { kotlinx.coroutines.flow.MutableStateFlow(false) }
    val castState by (castManager?.uiState ?: idleCastState).collectAsStateWithLifecycle()
    val castPlaying by (castManager?.playback?.isPlaying ?: idlePlaying).collectAsStateWithLifecycle()
    val castSheet = rememberCastSheetActions(castManager)
    val hasRecentPlayback by remember(playbackStore) {
        playbackStore.snapshot
            .map { it.recent.isNotEmpty() }
            .distinctUntilChanged()
    }.collectAsStateWithLifecycle(initialValue = playbackStore.snapshot.value.recent.isNotEmpty())
    var castTarget by remember { mutableStateOf<VideoItem?>(null) }
    val libraryPrefs by libraryPreferencesStore.preferences.collectAsStateWithLifecycle(initialValue = LibraryPreferences())
    val sortOrder = libraryPrefs.sortOrder
    val collapseSingles = libraryPrefs.collapseSingles
    val viewLayout = libraryPrefs.viewLayout
    val libraryFilter = libraryPrefs.libraryFilter
    val selectedTypeTab = libraryPrefs.typeTab
    var isSearching by rememberSaveable { mutableStateOf(false) }
    var searchQuery by rememberSaveable { mutableStateOf("") }
    var showOptionsSheet by rememberSaveable { mutableStateOf(false) }
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedKeys by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>?>(null) }
    var pendingDeleteSubtitle by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val pinnedKeys by pinnedItemsStore.pinnedKeys.collectAsStateWithLifecycle(initialValue = emptySet())
    val playCounts by playCountStore.playCounts.collectAsStateWithLifecycle(initialValue = emptyMap())

    val processSignature = remember(
        libraryEntries,
        sortOrder,
        libraryFilter,
        selectedTypeTab,
        pinnedKeys,
        playCounts,
    ) {
        libraryProcessSignature(
            libraryEntries = libraryEntries,
            sortOrder = sortOrder,
            libraryFilter = libraryFilter,
            selectedTypeTab = selectedTypeTab,
            pinnedKeys = pinnedKeys,
            playCounts = playCounts,
        )
    }
    val cachedSnapshot = LibraryDisplayCache.snapshot
    val cachedProcessed = when {
        cachedSnapshot == null -> emptyList()
        cachedSnapshot.signature == processSignature -> cachedSnapshot.entries
        else -> cachedSnapshot.entries
    }

    val processedLibrary by produceState(
        initialValue = cachedProcessed,
        processSignature,
    ) {
        val result = withContext(Dispatchers.Default) {
            var list = filterLibrary(libraryEntries, libraryFilter)
            list = applyTypeTab(list, selectedTypeTab, pinnedKeys)
            list = sortLibrary(list, sortOrder, playCounts)
            list
        }
        LibraryDisplayCache.snapshot = LibraryProcessCache(processSignature, result)
        value = result
    }
    val isUnfilteredAllTab =
        selectedTypeTab == LibraryTypeTab.ALL && libraryFilter == LibraryFilter()
    val displayLibrary = when {
        libraryEntries.isEmpty() -> emptyList()
        processedLibrary.isNotEmpty() -> processedLibrary
        cachedProcessed.isNotEmpty() -> cachedProcessed
        isUnfilteredAllTab -> libraryEntries
        else -> emptyList()
    }
    val visibleLibrary by produceState(
        initialValue = displayLibrary,
        displayLibrary,
        searchQuery,
    ) {
        value = withContext(Dispatchers.Default) {
            searchLibrary(displayLibrary, searchQuery)
        }
    }
    val pinnedSections = remember(visibleLibrary, pinnedKeys, viewLayout) {
        if (viewLayout == LibraryViewLayout.SECTIONED) {
            partitionWithPinned(visibleLibrary, pinnedKeys)
        } else {
            null
        }
    }
    val pinnedHomeEntries = pinnedSections?.pinned.orEmpty()
    val showPinnedRow = showPinnedHomeRow(
        sectioned = viewLayout == LibraryViewLayout.SECTIONED,
        tab = selectedTypeTab,
        searching = isSearching,
        selecting = selectionMode,
        pinnedCount = pinnedHomeEntries.size,
    )
    val flatLibrary = remember(visibleLibrary, viewLayout) {
        if (viewLayout == LibraryViewLayout.FLAT) {
            visibleLibrary
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
    val isSortingLibrary = libraryEntries.isNotEmpty() &&
        processedLibrary.isEmpty() &&
        cachedProcessed.isEmpty()
    val listStates = LibraryTypeTab.entries.associateWith { tab ->
        rememberSaveable(
            saver = LazyListState.Saver,
            key = "library-scroll-${tab.name}",
        ) {
            LazyListState()
        }
    }
    val listState = listStates.getValue(selectedTypeTab)
    val isListScrolling by remember(listState) {
        derivedStateOf { listState.isScrollInProgress }
    }
    val visibleItemKeys by remember(listState) {
        derivedStateOf {
            listState.layoutInfo.visibleItemsInfo.map { it.key }.toSet()
        }
    }
    val isListScrolled by remember(listState) {
        derivedStateOf {
            listState.firstVisibleItemIndex > 0 || listState.firstVisibleItemScrollOffset > 0
        }
    }
    val showScrollToTop =
        !selectionMode &&
            !isLibraryLoading &&
            hasPermission &&
            (displayLibrary.isNotEmpty() || hasRecentPlayback) &&
            isListScrolled

    var fabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        var previousIndex = listState.firstVisibleItemIndex
        var previousOffset = listState.firstVisibleItemScrollOffset
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val scrollingDown =
                index > previousIndex ||
                    (index == previousIndex && offset > previousOffset)
            val atTop = index == 0 && offset == 0
            fabVisible = atTop || !scrollingDown
            previousIndex = index
            previousOffset = offset
        }
    }

    val scrollBehavior = TopAppBarDefaults.pinnedScrollBehavior()

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

    fun urisForKeys(keys: Set<String>): List<Uri> {
        return displayLibrary
            .filter { entryKey(it) in keys }
            .flatMap { urisForEntry(it) }
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
        pendingDeleteSubtitle = subtitleForDelete(uris, libraryEntries)
        showDeleteDialog = true
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedKeys = emptySet()
    }

    BackHandler(enabled = selectionMode) {
        exitSelectionMode()
    }
    BackHandler(enabled = isSearching) {
        isSearching = false
        searchQuery = ""
    }

    val deleteFromDevice = rememberVideoDeleter(
        playbackStore = playbackStore,
        hiddenVideosStore = hiddenVideosStore,
        playCountStore = playCountStore,
        titleForUri = { uri -> titleForUri(uri, libraryEntries) },
        onVideosRemoved = onVideosRemoved,
        onDeleteStarted = { isDeleting = true },
        onDeleteFinished = {
            exitSelectionMode()
            closeDeleteDialog()
        },
    )

    fun incrementPlayCount(key: String) {
        scope.launch(Dispatchers.IO) {
            playCountStore.increment(key)
        }
    }

    val libraryEntriesState by rememberUpdatedState(libraryEntries)
    val onPlayVideoState by rememberUpdatedState(onPlayVideo)

    suspend fun resolvePlaylist(video: VideoItem): PlaylistState {
        return libraryViewModel.playlistStateFor(video, libraryEntriesState)
    }

    fun playVideoItem(video: VideoItem) {
        incrementPlayCount(playCountKeyForVideo(video, libraryEntries))
        scope.launch {
            onPlayVideoState(resolvePlaylist(video))
        }
    }

    val playVideoHandler: (VideoItem) -> Unit = remember(scope, libraryViewModel) {
        { video: VideoItem ->
            incrementPlayCount(playCountKeyForVideo(video, libraryEntries))
            scope.launch {
                onPlayVideoState(resolvePlaylist(video))
            }
            Unit
        }
    }
    val openSeriesHandler = remember(onOpenSeries) { { seriesId: String -> onOpenSeries(seriesId) } }
    val openDeleteHandler = remember { { uris: List<Uri> -> openDeleteDialog(uris) } }
    val toggleSelectionHandler = remember {
        { key: String, isSelected: Boolean ->
            selectedKeys = if (isSelected) selectedKeys - key else selectedKeys + key
        }
    }
    val enterSelectionHandler = { key: String ->
        selectionMode = true
        selectedKeys = setOf(key)
        castTarget = displayLibrary.firstNotNullOfOrNull { entry ->
            when (entry) {
                is VideoLibraryEntry.Single ->
                    entry.video.takeIf { entryKey(entry) == key }
                is VideoLibraryEntry.Series ->
                    entry.series.episodes.firstOrNull().takeIf { entryKey(entry) == key }
            }
        }
    }

    @Composable
    fun LibraryListRow(entry: VideoLibraryEntry, listKey: Any) {
        AnyLibraryRow(
            entry = entry,
            selectedKeys = selectedKeys,
            selectionMode = selectionMode,
            deferThumbnailLoad = !thumbnailShouldLoad(
                isScrolling = isListScrolling,
                itemKey = listKey,
                visibleKeys = visibleItemKeys,
            ),
            pinnedKeys = pinnedKeys,
            playbackStore = playbackStore,
            onToggleMark = ::toggleMark,
            onToggleSelection = toggleSelectionHandler,
            onOpenSeries = openSeriesHandler,
            onIncrementPlayCount = ::incrementPlayCount,
            onPlayVideo = playVideoHandler,
            onEnterSelection = enterSelectionHandler,
            onDeleteUris = openDeleteHandler,
        )
    }

    fun openPinned(entry: VideoLibraryEntry) {
        when (entry) {
            is VideoLibraryEntry.Series -> {
                incrementPlayCount(entryKey(entry))
                onOpenSeries(entry.series.id)
            }
            is VideoLibraryEntry.Single -> playVideoHandler(entry.video)
        }
    }

    fun playRecentRecord(record: PlaybackRecord) {
        val targetUri = record.uri.toString()
        val matched = libraryEntriesState.flatMap { entry ->
            when (entry) {
                is VideoLibraryEntry.Single -> listOf(entry.video)
                is VideoLibraryEntry.Series -> entry.series.episodes
            }
        }.find { it.uri.toString() == targetUri }

        val video = matched ?: VideoItem(
            id = record.uri.hashCode().toLong(),
            uri = record.uri,
            title = record.title,
            durationMs = record.durationMs,
            dateAdded = record.lastPlayedAt / 1000,
            thumbnailUri = null,
        )
        playVideoItem(video)
    }

    fun hideVideos(uris: List<Uri>) {
        scope.launch {
            withContext(Dispatchers.IO) {
                hiddenVideosStore.hideAll(
                    uris,
                    uris.associate { it.toString() to titleForUri(it, libraryEntries) },
                )
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
            onOrderSelected = { scope.launch { libraryPreferencesStore.setSortOrder(it) } },
            onViewLayoutSelected = { scope.launch { libraryPreferencesStore.setViewLayout(it) } },
            onFilterChanged = { scope.launch { libraryPreferencesStore.setLibraryFilter(it) } },
            onCollapseSinglesChanged = { scope.launch { libraryPreferencesStore.setCollapseSingles(it) } },
            onDismiss = { showOptionsSheet = false },
        )
    }

    if (castSheet.showSheet && castManager != null) {
        CastDeviceBottomSheet(
            state = castState,
            onDeviceClick = { device ->
                val selectedVideo = when {
                    castTarget != null -> castTarget
                    selectedKeys.size == 1 -> {
                        val key = selectedKeys.first()
                        displayLibrary.firstNotNullOfOrNull { entry ->
                            when (entry) {
                                is VideoLibraryEntry.Single ->
                                    entry.video.takeIf { entryKey(entry) == key }
                                is VideoLibraryEntry.Series ->
                                    entry.series.episodes.firstOrNull()
                                        .takeIf { entryKey(entry) == key }
                            }
                        }
                    }
                    else -> null
                }
                val recent = playbackStore.snapshot.value.recent.firstOrNull()
                val title: String
                val uri: android.net.Uri
                val position: Long
                when {
                    selectedVideo != null -> {
                        title = selectedVideo.title
                        uri = selectedVideo.uri
                        position = playbackStore.snapshot.value.records[selectedVideo.uri.toString()]?.positionMs ?: 0L
                    }
                    recent != null -> {
                        title = recent.title
                        uri = recent.uri
                        position = recent.positionMs
                    }
                    else -> {
                        Toast.makeText(context, R.string.cast_unavailable, Toast.LENGTH_SHORT).show()
                        return@CastDeviceBottomSheet
                    }
                }
                castManager.connectAndPlay(
                    device = device,
                    title = title,
                    contentUri = uri,
                    startPositionMs = position,
                    mimeType = context.contentResolver.getType(uri),
                )
                castTarget = null
                castSheet.dismiss(CastUiState.Connecting(device))
            },
            onDismiss = {
                castTarget = null
                castSheet.dismiss(castState)
            },
        )
    }

    Scaffold(
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LibraryScreenChrome(
                selectionMode = selectionMode,
                isSearching = isSearching,
                searchQuery = searchQuery,
                onSearchQueryChange = { searchQuery = it },
                onExitSearch = {
                    isSearching = false
                    searchQuery = ""
                },
                onStartSearch = { isSearching = true },
                selectedCount = selectedKeys.size,
                totalCount = displayLibrary.size,
                onSelectAll = {
                    selectedKeys = displayLibrary.map { entryKey(it) }.toSet()
                },
                onDeleteSelection = {
                    openDeleteDialog(urisForKeys(selectedKeys))
                },
                onExitSelection = ::exitSelectionMode,
                castManager = castManager,
                castState = castState,
                onOpenCast = { castSheet.open() },
                onOpenDownloads = onOpenDownloads,
                isMarkedTabActive = isMarkedTabActive,
                onToggleMarked = {
                    val next = if (isMarkedTabActive) {
                        LibraryTypeTab.ALL
                    } else {
                        LibraryTypeTab.MARKED
                    }
                    scope.launch { libraryPreferencesStore.setTypeTab(next) }
                },
                themeMode = themeState.mode,
                onCycleTheme = {
                    val nextMode = when (themeState.mode) {
                        ThemeMode.System -> ThemeMode.Light
                        ThemeMode.Light -> ThemeMode.Dark
                        ThemeMode.Dark -> ThemeMode.System
                    }
                    scope.launch {
                        themeStore.setThemeMode(nextMode)
                    }
                },
                onOpenSettings = onOpenSettings,
                onOpenOptions = { showOptionsSheet = true },
                hasActiveOptions = hasActiveOptions,
                showDeleteDialog = showDeleteDialog && pendingDeleteUris != null,
                deleteCount = pendingDeleteUris?.size ?: 0,
                deleteSubtitle = pendingDeleteSubtitle,
                isDeleting = isDeleting,
                onDismissDelete = ::closeDeleteDialog,
                onHideFromList = { pendingDeleteUris?.let(::hideVideos) },
                onDeleteFromDevice = { pendingDeleteUris?.let(deleteFromDevice) },
            )
        },
        floatingActionButton = {
            AnimatedVisibility(
                visible = !selectionMode && fabVisible,
                enter = fadeIn() + scaleIn() + slideInVertically { it / 2 },
                exit = fadeOut() + scaleOut() + slideOutVertically { it / 2 },
            ) {
                Column(
                    horizontalAlignment = Alignment.End,
                    verticalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    AnimatedVisibility(visible = showScrollToTop) {
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
        bottomBar = {
            val connected = castState as? CastUiState.Connected
            CastMiniController(
                visible = connected != null,
                title = connected?.title.orEmpty(),
                isPlaying = castPlaying,
                onPlayPause = {
                    if (castPlaying) castManager?.playback?.pause() else castManager?.playback?.play()
                },
                onDisconnect = { castManager?.disconnect() },
            )
        },
    ) { innerPadding ->
        val pullRefreshState = rememberPullToRefreshState()
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding),
        ) {
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
                    Column(modifier = Modifier.fillMaxSize()) {
                        if (!selectionMode) {
                            LumenLibraryTabBar(
                                tabs = typeTabs,
                                selectedTab = selectedTypeTab,
                                onTabSelected = { tab ->
                                    scope.launch { libraryPreferencesStore.setTypeTab(tab) }
                                },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = LumenSpaceXs, bottom = LumenSpaceXs),
                            )
                        }
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxWidth()
                                .pullToRefresh(
                                    state = pullRefreshState,
                                    isRefreshing = isRefreshing,
                                    onRefresh = onPullRefresh,
                                    enabled = !selectionMode,
                                ),
                        ) {
                        LibraryHomeList(
                            listState = listState,
                            selectionMode = selectionMode,
                            isSearching = isSearching,
                            hasRecentPlayback = hasRecentPlayback,
                            playbackStore = playbackStore,
                            libraryEntries = libraryEntries,
                            onRecordClick = ::playRecentRecord,
                            showPinnedRow = showPinnedRow,
                            pinnedHomeEntries = pinnedHomeEntries,
                            onPinnedClick = ::openPinned,
                            cacheResolved = cacheResolved,
                            libraryEntryCount = libraryEntries.size,
                            isLibraryLoading = isLibraryLoading,
                            isSortingLibrary = isSortingLibrary,
                            visibleLibrary = visibleLibrary,
                            searchQuery = searchQuery,
                            viewLayout = viewLayout,
                            flatLibrary = flatLibrary,
                            pinnedSections = pinnedSections,
                            collapseSingles = collapseSingles,
                            onToggleCollapseSingles = {
                                scope.launch {
                                    libraryPreferencesStore.setCollapseSingles(!collapseSingles)
                                }
                            },
                            row = { entry, listKey ->
                                LibraryListRow(entry, listKey)
                            },
                        )
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

