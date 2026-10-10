package com.zhaoshuo.localvideoplayer.ui

import android.content.Intent
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.scaleIn
import androidx.compose.animation.scaleOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.WindowInsetsSides
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.only
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
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
import androidx.compose.runtime.mutableIntStateOf
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
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.nestedscroll.nestedScroll
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.data.HiddenVideosStore
import com.zhaoshuo.localvideoplayer.data.LibraryPreferences
import com.zhaoshuo.localvideoplayer.data.LibraryPreferencesStore
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
import com.zhaoshuo.localvideoplayer.model.searchLibrary
import com.zhaoshuo.localvideoplayer.model.sortLibrary
import com.zhaoshuo.localvideoplayer.model.withoutUris
import com.zhaoshuo.localvideoplayer.player.PlaylistState
import com.zhaoshuo.localvideoplayer.cast.CastUiState
import com.zhaoshuo.localvideoplayer.cast.LocalCastManager
import com.zhaoshuo.localvideoplayer.ui.components.CastButton
import com.zhaoshuo.localvideoplayer.ui.components.CastDeviceBottomSheet
import com.zhaoshuo.localvideoplayer.ui.components.CastMiniController
import com.zhaoshuo.localvideoplayer.ui.components.rememberCastSheetActions
import com.zhaoshuo.localvideoplayer.ui.components.DeleteVideoDialog
import com.zhaoshuo.localvideoplayer.ui.components.LibraryOptionsBottomSheet
import com.zhaoshuo.localvideoplayer.ui.components.LumenEmptyState
import com.zhaoshuo.localvideoplayer.ui.components.LumenLibraryTabBar
import com.zhaoshuo.localvideoplayer.ui.components.LumenListHeader
import com.zhaoshuo.localvideoplayer.ui.components.RecentPlaybackSection
import com.zhaoshuo.localvideoplayer.ui.components.SelectionTopBar
import com.zhaoshuo.localvideoplayer.ui.components.SeriesFolderItem
import com.zhaoshuo.localvideoplayer.ui.components.VideoListItem
import com.zhaoshuo.localvideoplayer.ui.components.rememberEpisodeProgressList
import com.zhaoshuo.localvideoplayer.ui.components.rememberPlaybackProgress
import com.zhaoshuo.localvideoplayer.ui.components.rememberSeriesPlaybackStats
import com.zhaoshuo.localvideoplayer.ui.theme.LocalLumenTheme
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import com.zhaoshuo.localvideoplayer.ui.theme.ThemeMode
import com.zhaoshuo.localvideoplayer.ui.theme.ThemeState
import com.zhaoshuo.localvideoplayer.util.M3uPlaylistParser
import com.zhaoshuo.localvideoplayer.util.SeriesGrouper
import com.zhaoshuo.localvideoplayer.util.isM3u8Title
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

@OptIn(ExperimentalFoundationApi::class, ExperimentalMaterial3Api::class)
@Composable
fun VideoListScreen(
    libraryEntries: List<VideoLibraryEntry>,
    isLibraryLoading: Boolean,
    isRefreshing: Boolean,
    hasPermission: Boolean,
    repository: VideoRepository,
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
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState()
    }
    var previousTypeTab by rememberSaveable { mutableStateOf(selectedTypeTab) }

    LaunchedEffect(selectedTypeTab) {
        if (previousTypeTab != selectedTypeTab) {
            listState.animateScrollToItem(0)
            previousTypeTab = selectedTypeTab
        }
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
            (displayLibrary.isNotEmpty() || hasRecentPlayback) &&
            isListScrolled

    var previousScrollIndex by remember { mutableIntStateOf(0) }
    var previousScrollOffset by remember { mutableIntStateOf(0) }
    var fabVisible by remember { mutableStateOf(true) }
    LaunchedEffect(listState) {
        snapshotFlow {
            listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset
        }.collect { (index, offset) ->
            val scrollingDown =
                index > previousScrollIndex ||
                    (index == previousScrollIndex && offset > previousScrollOffset)
            val atTop = index == 0 && offset == 0
            fabVisible = atTop || !scrollingDown
            previousScrollIndex = index
            previousScrollOffset = offset
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

    BackHandler(enabled = selectionMode) {
        exitSelectionMode()
    }
    BackHandler(enabled = isSearching) {
        isSearching = false
        searchQuery = ""
    }

    fun titleForUri(uri: Uri): String {
        val key = uri.toString()
        libraryEntries.forEach { entry ->
            when (entry) {
                is VideoLibraryEntry.Single -> {
                    if (entry.video.uri.toString() == key) return entry.video.title
                }
                is VideoLibraryEntry.Series -> {
                    entry.series.episodes.firstOrNull { it.uri.toString() == key }?.let { return it.title }
                }
            }
        }
        return ""
    }

    val deleteFromDevice = rememberVideoDeleter(
        playbackStore = playbackStore,
        hiddenVideosStore = hiddenVideosStore,
        playCountStore = playCountStore,
        titleForUri = ::titleForUri,
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

    val libraryEntriesState by rememberUpdatedState(libraryEntries)
    val onPlayVideoState by rememberUpdatedState(onPlayVideo)

    suspend fun resolvePlaylist(video: VideoItem): PlaylistState {
        val entries = libraryEntriesState
        val fromLibrary = withContext(Dispatchers.IO) {
            repository.playlistStateFor(video, entries)
        }
        if (fromLibrary.items.size > 1) {
            return fromLibrary
        }
        val videos = entries.flatMap { entry ->
            when (entry) {
                is VideoLibraryEntry.Single -> listOf(entry.video)
                is VideoLibraryEntry.Series -> entry.series.episodes
            }
        }
        return withContext(Dispatchers.Default) {
            SeriesGrouper.quickPlaylistFor(video, videos.ifEmpty { listOf(video) })
        }
    }

    fun playVideoItem(video: VideoItem) {
        incrementPlayCount(playCountKeyForVideo(video))
        scope.launch {
            onPlayVideoState(resolvePlaylist(video))
        }
    }

    val playVideoHandler = remember(scope, repository) {
        { video: VideoItem ->
            incrementPlayCount(playCountKeyForVideo(video))
            scope.launch {
                onPlayVideoState(resolvePlaylist(video))
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
    fun SeriesLibraryRow(entry: VideoLibraryEntry.Series) {
        val key = entryKey(entry)
        val isSelected = key in selectedKeys
        val episodeProgress = rememberEpisodeProgressList(
            series = entry.series,
            playbackStore = playbackStore,
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
            playbackStore = playbackStore,
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
                    uris.associate { it.toString() to titleForUri(it) },
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
        modifier = modifier.nestedScroll(scrollBehavior.nestedScrollConnection),
        contentWindowInsets = WindowInsets.safeDrawing.only(
            WindowInsetsSides.Horizontal + WindowInsetsSides.Bottom,
        ),
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            AnimatedContent(
                targetState = selectionMode,
                transitionSpec = {
                    fadeIn(animationSpec = tween(180)) togetherWith
                        fadeOut(animationSpec = tween(180))
                },
                label = "library-top-bar",
                modifier = Modifier.statusBarsPadding(),
            ) { selecting ->
                if (selecting) {
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
                } else if (isSearching) {
                    LumenListHeader(
                        title = stringResource(R.string.search),
                        onBack = {
                            isSearching = false
                            searchQuery = ""
                        },
                        applyStatusBarPadding = false,
                        titleContent = {
                            BasicTextField(
                                value = searchQuery,
                                onValueChange = { searchQuery = it },
                                singleLine = true,
                                textStyle = MaterialTheme.typography.bodyLarge.copy(
                                    color = MaterialTheme.colorScheme.onBackground,
                                ),
                                cursorBrush = SolidColor(theme.accent),
                                modifier = Modifier.fillMaxWidth(),
                                decorationBox = { innerTextField ->
                                    Box(modifier = Modifier.fillMaxWidth()) {
                                        if (searchQuery.isEmpty()) {
                                            Text(
                                                text = stringResource(R.string.search_hint),
                                                style = MaterialTheme.typography.bodyLarge,
                                                color = theme.onSurfaceMuted,
                                            )
                                        }
                                        innerTextField()
                                    }
                                },
                            )
                        },
                        actions = {
                            if (searchQuery.isNotEmpty()) {
                                HeaderIconButton(
                                    onClick = { searchQuery = "" },
                                    imageVector = Icons.Default.Close,
                                    contentDescription = stringResource(R.string.search_clear),
                                )
                            }
                        },
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
                            if (castManager != null) {
                                CastButton(
                                    state = castState,
                                    onClick = { castSheet.open() },
                                    modifier = Modifier.size(40.dp),
                                )
                            }
                            HeaderIconButton(
                                onClick = { isSearching = true },
                                imageVector = Icons.Filled.Search,
                                contentDescription = stringResource(R.string.search),
                            )
                            HeaderIconButton(
                                onClick = onOpenDownloads,
                                imageVector = Icons.Filled.Download,
                                contentDescription = stringResource(R.string.downloads_title),
                            )
                            HeaderIconButton(
                                onClick = {
                                    val next = if (isMarkedTabActive) {
                                        LibraryTypeTab.ALL
                                    } else {
                                        LibraryTypeTab.MARKED
                                    }
                                    scope.launch { libraryPreferencesStore.setTypeTab(next) }
                                },
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
                            HeaderIconButton(
                                onClick = {
                                    val nextMode = when (themeState.mode) {
                                        ThemeMode.System -> ThemeMode.Light
                                        ThemeMode.Light -> ThemeMode.Dark
                                        ThemeMode.Dark -> ThemeMode.System
                                    }
                                    scope.launch {
                                        themeStore.setThemeMode(nextMode)
                                    }
                                },
                                imageVector = when (themeState.mode) {
                                    ThemeMode.System -> Icons.Default.BrightnessAuto
                                    ThemeMode.Dark -> Icons.Default.LightMode
                                    ThemeMode.Light -> Icons.Default.DarkMode
                                },
                                contentDescription = when (themeState.mode) {
                                    ThemeMode.System -> stringResource(R.string.theme_mode_system)
                                    ThemeMode.Dark -> stringResource(R.string.theme_mode_light)
                                    ThemeMode.Light -> stringResource(R.string.theme_mode_dark)
                                },
                            )
                            HeaderIconButton(
                                onClick = onOpenSettings,
                                imageVector = Icons.Filled.Settings,
                                contentDescription = stringResource(R.string.settings),
                            )
                            Box {
                                HeaderIconButton(
                                    onClick = { showOptionsSheet = true },
                                    imageVector = Icons.AutoMirrored.Filled.Sort,
                                    contentDescription = stringResource(R.string.library_options),
                                )
                                if (hasActiveOptions) {
                                    Box(
                                        modifier = Modifier
                                            .align(Alignment.TopEnd)
                                            .padding(end = 6.dp, top = 6.dp)
                                            .size(8.dp)
                                            .clip(CircleShape)
                                            .background(theme.accent),
                                    )
                                }
                            }
                        },
                    )
                }
            }
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
                            contentPadding = PaddingValues(
                                start = 16.dp,
                                end = 16.dp,
                                top = 12.dp,
                                bottom = 88.dp,
                            ),
                            verticalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            if (!selectionMode && !isSearching && hasRecentPlayback) {
                                item(key = "recent-playback") {
                                    RecentPlaybackSection(
                                        playbackStore = playbackStore,
                                        onRecordClick = ::playRecentRecord,
                                        modifier = Modifier.animateItem(placementSpec = null),
                                    )
                                }
                            }

                            if (!selectionMode) {
                                item(key = "library-tabs") {
                                    LumenLibraryTabBar(
                                        tabs = typeTabs,
                                        selectedTab = selectedTypeTab,
                                        onTabSelected = { tab ->
                                            scope.launch { libraryPreferencesStore.setTypeTab(tab) }
                                        },
                                        contentPadding = PaddingValues(0.dp),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(bottom = 4.dp)
                                            .animateItem(placementSpec = null),
                                    )
                                }
                            }

                            if ((isLibraryLoading || isSortingLibrary) && displayLibrary.isEmpty() && libraryEntries.isEmpty()) {
                                item(key = "library-loading") {
                                    Box(
                                        modifier = Modifier
                                            .fillParentMaxWidth()
                                            .padding(vertical = 48.dp)
                                            .animateItem(placementSpec = null),
                                        contentAlignment = Alignment.Center,
                                    ) {
                                        CircularProgressIndicator(color = theme.accent)
                                    }
                                }
                            }

                            if (!isLibraryLoading && !isSortingLibrary && libraryEntries.isEmpty() && !hasRecentPlayback) {
                                item(key = "empty") {
                                    LumenEmptyState(
                                        title = stringResource(R.string.empty_video_list),
                                        subtitle = stringResource(R.string.empty_video_hint),
                                        icon = Icons.Default.VideoLibrary,
                                        modifier = Modifier
                                            .fillParentMaxSize()
                                            .padding(24.dp)
                                            .animateItem(placementSpec = null),
                                    )
                                }
                            }

                            if (
                                !isLibraryLoading &&
                                !isSortingLibrary &&
                                visibleLibrary.isEmpty() &&
                                libraryEntries.isNotEmpty()
                            ) {
                                item(key = "no-filter-results") {
                                    LumenEmptyState(
                                        title = stringResource(
                                            if (searchQuery.isNotBlank()) {
                                                R.string.no_search_results
                                            } else {
                                                R.string.no_filter_results
                                            },
                                        ),
                                        icon = Icons.Filled.Search,
                                        modifier = Modifier
                                            .fillParentMaxWidth()
                                            .padding(24.dp)
                                            .animateItem(placementSpec = null),
                                    )
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
                                    Box(modifier = Modifier.animateItem(placementSpec = null)) {
                                        AnyLibraryRow(entry)
                                    }
                                }
                            } else {
                                val sections = pinnedSections
                                if (sections != null) {
                                    if (sections.pinned.isNotEmpty()) {
                                        item(key = "header-pinned-${viewLayout.name}") {
                                            LibrarySectionHeader(
                                                title = stringResource(R.string.section_pinned),
                                                modifier = Modifier.animateItem(placementSpec = null),
                                            )
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
                                            Box(modifier = Modifier.animateItem(placementSpec = null)) {
                                                AnyLibraryRow(entry)
                                            }
                                        }
                                    }

                                    if (sections.episodes.isNotEmpty()) {
                                        item(key = "header-episodes") {
                                            LibrarySectionHeader(
                                                title = stringResource(R.string.section_episodes),
                                                modifier = Modifier.animateItem(placementSpec = null),
                                            )
                                        }
                                        items(
                                            items = sections.episodes,
                                            key = { entry -> entryKey(entry) },
                                            contentType = { 0 },
                                        ) { entry ->
                                            Box(modifier = Modifier.animateItem(placementSpec = null)) {
                                                SeriesLibraryRow(entry)
                                            }
                                        }
                                    }

                                    if (sections.folders.isNotEmpty()) {
                                        item(key = "header-folders") {
                                            LibrarySectionHeader(
                                                title = stringResource(R.string.section_folders),
                                                modifier = Modifier.animateItem(placementSpec = null),
                                            )
                                        }
                                        items(
                                            items = sections.folders,
                                            key = { entry -> entryKey(entry) },
                                            contentType = { 0 },
                                        ) { entry ->
                                            Box(modifier = Modifier.animateItem(placementSpec = null)) {
                                                SeriesLibraryRow(entry)
                                            }
                                        }
                                    }

                                    if (sections.shortClips.isNotEmpty()) {
                                        item(key = "header-short-clips") {
                                            LibrarySectionHeader(
                                                title = stringResource(R.string.section_short_clips),
                                                modifier = Modifier.animateItem(placementSpec = null),
                                            )
                                        }
                                        items(
                                            items = sections.shortClips,
                                            key = { entry -> entryKey(entry) },
                                            contentType = { 0 },
                                        ) { entry ->
                                            Box(modifier = Modifier.animateItem(placementSpec = null)) {
                                                SeriesLibraryRow(entry)
                                            }
                                        }
                                    }

                                        if (sections.singles.isNotEmpty()) {
                                            item(key = "singles-header") {
                                                SinglesCollapseHeader(
                                                    title = if (collapseSingles) {
                                                        stringResource(
                                                            R.string.expand_singles,
                                                            sections.singles.size,
                                                        )
                                                    } else {
                                                        stringResource(
                                                            R.string.section_other_videos,
                                                            sections.singles.size,
                                                        )
                                                    },
                                                    expanded = !collapseSingles,
                                                    onClick = {
                                                        scope.launch {
                                                            libraryPreferencesStore.setCollapseSingles(!collapseSingles)
                                                        }
                                                    },
                                                    modifier = Modifier.animateItem(placementSpec = null),
                                                )
                                            }
                                            if (!collapseSingles) {
                                                items(
                                                    items = sections.singles,
                                                    key = { entry -> entryKey(entry) },
                                                    contentType = { 1 },
                                                ) { entry ->
                                                    Box(modifier = Modifier.animateItem(placementSpec = null)) {
                                                        SingleLibraryRow(entry)
                                                    }
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

@Composable
private fun LibrarySectionHeader(
    title: String,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLumenTheme.current
    Text(
        text = title,
        style = MaterialTheme.typography.labelLarge,
        color = theme.onSurfaceMuted,
        modifier = modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun SinglesCollapseHeader(
    title: String,
    expanded: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val theme = LocalLumenTheme.current
    val chevronRotation by animateFloatAsState(
        targetValue = if (expanded) 180f else 0f,
        label = "singles-chevron",
    )
    Row(
        modifier = modifier
            .fillMaxWidth()
            .animateContentSize(
                animationSpec = spring(stiffness = Spring.StiffnessLow),
            )
            .clickable(onClick = onClick)
            .padding(top = 8.dp, bottom = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(
            text = title,
            style = MaterialTheme.typography.labelLarge,
            color = if (expanded) theme.onSurfaceMuted else theme.accent,
            modifier = Modifier.weight(1f),
        )
        Icon(
            imageVector = Icons.Default.ExpandMore,
            contentDescription = null,
            tint = if (expanded) theme.onSurfaceMuted else theme.accent,
            modifier = Modifier
                .size(18.dp)
                .rotate(chevronRotation),
        )
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

@Composable
private fun HeaderIconButton(
    onClick: () -> Unit,
    imageVector: ImageVector,
    contentDescription: String,
    tint: Color = MaterialTheme.colorScheme.onBackground,
) {
    Box(
        modifier = Modifier
            .size(40.dp)
            .clip(CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            imageVector = imageVector,
            contentDescription = contentDescription,
            tint = tint,
            modifier = Modifier.size(22.dp),
        )
    }
}
