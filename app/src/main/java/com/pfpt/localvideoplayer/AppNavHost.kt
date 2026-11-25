package com.pfpt.localvideoplayer

import android.net.Uri
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.navArgument
import com.pfpt.localvideoplayer.data.BookmarkStore
import com.pfpt.localvideoplayer.data.FolderSourceStore
import com.pfpt.localvideoplayer.data.HiddenVideosStore
import com.pfpt.localvideoplayer.data.LibraryPreferencesStore
import com.pfpt.localvideoplayer.data.LibraryViewModel
import com.pfpt.localvideoplayer.data.PinnedItemsStore
import com.pfpt.localvideoplayer.data.PlayCountStore
import com.pfpt.localvideoplayer.data.PlaybackStore
import com.pfpt.localvideoplayer.data.PlayerPreferencesStore
import com.pfpt.localvideoplayer.data.SeriesPlaybackStore
import com.pfpt.localvideoplayer.data.SeriesSkipStore
import com.pfpt.localvideoplayer.data.ThemePreferencesStore
import com.pfpt.localvideoplayer.data.VideoRepository
import com.pfpt.localvideoplayer.download.DownloadManager
import com.pfpt.localvideoplayer.model.VideoLibraryEntry
import com.pfpt.localvideoplayer.player.PlaybackSession
import com.pfpt.localvideoplayer.player.PlaylistState
import com.pfpt.localvideoplayer.ui.DownloadManagerScreen
import com.pfpt.localvideoplayer.ui.PlayerScreen
import com.pfpt.localvideoplayer.ui.SeriesDetailScreen
import com.pfpt.localvideoplayer.ui.SettingsScreen
import com.pfpt.localvideoplayer.ui.VideoListScreen
import com.pfpt.localvideoplayer.ui.theme.ThemeState
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.launch

internal const val ROUTE_PLAYER = "player"

private const val ROUTE_VIDEO_LIST = "video_list"
private const val ROUTE_SERIES = "series/{seriesId}"
private const val ROUTE_DOWNLOADS = "downloads"
private const val ROUTE_SETTINGS = "settings"

@Composable
internal fun AppNavHost(
    navController: NavHostController,
    downloadsNavigationTick: Int,
    openPlayerTick: Int,
    playbackSession: PlaybackSession,
    libraryViewModel: LibraryViewModel,
    repository: VideoRepository,
    hiddenVideosStore: HiddenVideosStore,
    playbackStore: PlaybackStore,
    pinnedItemsStore: PinnedItemsStore,
    playCountStore: PlayCountStore,
    themeStore: ThemePreferencesStore,
    libraryPreferencesStore: LibraryPreferencesStore,
    themeState: ThemeState,
    hasPermission: Boolean,
    onRequestPermission: () -> Unit,
    playerPreferencesStore: PlayerPreferencesStore,
    seriesSkipStore: SeriesSkipStore,
    seriesPlaybackStore: SeriesPlaybackStore,
    bookmarkStore: BookmarkStore,
    folderSourceStore: FolderSourceStore,
    downloadManager: DownloadManager,
    scope: CoroutineScope,
) {
    var playlistState by remember { mutableStateOf<PlaylistState?>(null) }

    LaunchedEffect(downloadsNavigationTick) {
        if (downloadsNavigationTick > 0) {
            navController.navigate(ROUTE_DOWNLOADS)
        }
    }

    LaunchedEffect(openPlayerTick) {
        if (openPlayerTick == 0) return@LaunchedEffect
        if (navController.currentDestination?.route == ROUTE_PLAYER) return@LaunchedEffect
        if (!playbackSession.state.value.active && playlistState == null) return@LaunchedEffect
        playbackSession.markReturnToExisting()
        navController.navigate(ROUTE_PLAYER) {
            launchSingleTop = true
        }
    }

    NavHost(
        navController = navController,
        startDestination = ROUTE_VIDEO_LIST,
    ) {
        composable(ROUTE_VIDEO_LIST) {
            val libraryEntries by libraryViewModel.libraryEntries.collectAsState()
            val isLibraryLoading by libraryViewModel.isLibraryLoading.collectAsState()
            val cacheResolved by libraryViewModel.cacheResolved.collectAsState()
            val isRefreshing by libraryViewModel.isRefreshing.collectAsState()
            VideoListScreen(
                libraryEntries = libraryEntries,
                isLibraryLoading = isLibraryLoading && libraryEntries.isEmpty(),
                cacheResolved = cacheResolved,
                isRefreshing = isRefreshing,
                hasPermission = hasPermission,
                libraryViewModel = libraryViewModel,
                hiddenVideosStore = hiddenVideosStore,
                playbackStore = playbackStore,
                pinnedItemsStore = pinnedItemsStore,
                playCountStore = playCountStore,
                themeStore = themeStore,
                libraryPreferencesStore = libraryPreferencesStore,
                themeState = themeState,
                onRequestPermission = onRequestPermission,
                onPlayVideo = { state ->
                    playlistState = state
                    navController.navigate(ROUTE_PLAYER)
                },
                onOpenSeries = { seriesId ->
                    navController.navigate("series/$seriesId")
                },
                onOpenDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
                onOpenSettings = { navController.navigate(ROUTE_SETTINGS) },
                onVideosRemoved = { uris -> libraryViewModel.removeVideos(uris) },
                onPullRefresh = {
                    libraryViewModel.refresh(fromPull = true)
                },
            )
        }
        composable(
            route = ROUTE_SERIES,
            arguments = listOf(
                navArgument("seriesId") { type = NavType.StringType },
            ),
        ) { backStackEntry ->
            val libraryEntries by libraryViewModel.libraryEntries.collectAsState()
            val seriesId = backStackEntry.arguments?.getString("seriesId")
            val series = libraryEntries
                .filterIsInstance<VideoLibraryEntry.Series>()
                .map { it.series }
                .find { it.id == seriesId }

            if (series == null) {
                navController.popBackStack()
            } else {
                SeriesDetailScreen(
                    series = series,
                    repository = repository,
                    hiddenVideosStore = hiddenVideosStore,
                    playbackStore = playbackStore,
                    playCountStore = playCountStore,
                    onBack = { navController.popBackStack() },
                    onPlayEpisode = { state ->
                        val current = state.items.getOrNull(state.currentIndex)
                        playlistState = if (current != null) {
                            val resolved = libraryViewModel.playlistStateFor(current)
                            if (resolved.items.size > 1) resolved else state
                        } else {
                            state
                        }
                        navController.navigate(ROUTE_PLAYER)
                    },
                    onVideosRemoved = { uris -> libraryViewModel.removeVideos(uris) },
                )
            }
        }
        composable(ROUTE_PLAYER) {
            val state = playlistState ?: playbackSession.playlistState()
            if (state == null) {
                LaunchedEffect(Unit) {
                    navController.popBackStack()
                }
            } else {
                PlayerScreen(
                    playlistState = state,
                    playbackStore = playbackStore,
                    playerPreferencesStore = playerPreferencesStore,
                    pinnedItemsStore = pinnedItemsStore,
                    seriesSkipStore = seriesSkipStore,
                    seriesPlaybackStore = seriesPlaybackStore,
                    bookmarkStore = bookmarkStore,
                    playbackSession = playbackSession,
                    seriesPlaylistFor = { video ->
                        libraryViewModel.playlistStateFor(video)
                    },
                    onReplacePlaylist = { playlistState = it },
                    onDurationKnown = { uri, durationMs ->
                        libraryViewModel.applyKnownDurations(mapOf(uri to durationMs))
                    },
                    onBack = { navController.popBackStack() },
                )
            }
        }
        composable(ROUTE_SETTINGS) {
            SettingsScreen(
                themeStore = themeStore,
                themeState = themeState,
                libraryPreferencesStore = libraryPreferencesStore,
                playerPreferencesStore = playerPreferencesStore,
                folderSourceStore = folderSourceStore,
                hiddenVideosStore = hiddenVideosStore,
                onRestoreHidden = { libraryViewModel.refresh(fromPull = true) },
                onLibraryRefresh = { libraryViewModel.refresh(fromPull = true) },
                onBack = { navController.popBackStack() },
            )
        }
        composable(ROUTE_DOWNLOADS) {
            DownloadManagerScreen(
                manager = downloadManager,
                onOpenCompleted = { task ->
                    val uri = task.outputUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                        ?: return@DownloadManagerScreen
                    scope.launch {
                        val video = libraryViewModel.includeDownloaded(uri) ?: return@launch
                        playlistState = libraryViewModel.playlistStateFor(video)
                        navController.navigate(ROUTE_PLAYER)
                    }
                },
                onBack = { navController.popBackStack() },
            )
        }
    }
}
