package com.zhaoshuo.localvideoplayer

import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.media.MediaMetadataRetriever
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavHostController
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhaoshuo.localvideoplayer.cast.CastManager
import com.zhaoshuo.localvideoplayer.cast.LocalCastManager
import com.zhaoshuo.localvideoplayer.data.LibraryViewModel
import com.zhaoshuo.localvideoplayer.data.PlaybackStore
import com.zhaoshuo.localvideoplayer.data.PlayerPreferencesStore
import com.zhaoshuo.localvideoplayer.data.PinnedItemsStore
import com.zhaoshuo.localvideoplayer.data.PlayCountStore
import com.zhaoshuo.localvideoplayer.data.HiddenVideosStore
import com.zhaoshuo.localvideoplayer.data.SeriesSkipStore
import com.zhaoshuo.localvideoplayer.data.ThemePreferencesStore
import com.zhaoshuo.localvideoplayer.data.VideoRepository
import com.zhaoshuo.localvideoplayer.download.DownloadManager
import com.zhaoshuo.localvideoplayer.download.DownloadTask
import com.zhaoshuo.localvideoplayer.model.PlaybackRecord
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import com.zhaoshuo.localvideoplayer.player.PlaylistState
import com.zhaoshuo.localvideoplayer.ui.PlayerScreen
import com.zhaoshuo.localvideoplayer.ui.DownloadManagerScreen
import com.zhaoshuo.localvideoplayer.ui.SeriesDetailScreen
import com.zhaoshuo.localvideoplayer.ui.VideoListScreen
import com.zhaoshuo.localvideoplayer.ui.theme.LocalVideoPlayerTheme
import com.zhaoshuo.localvideoplayer.ui.theme.ThemeState
import com.zhaoshuo.localvideoplayer.util.LauncherIconSwitcher
import com.zhaoshuo.localvideoplayer.util.Permissions
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.debounce

private const val ROUTE_VIDEO_LIST = "video_list"
private const val ROUTE_SERIES = "series/{seriesId}"
private const val ROUTE_PLAYER = "player"
private const val ROUTE_DOWNLOADS = "downloads"

@Composable
fun LocalVideoPlayerApp(
    sharedDownloadText: String? = null,
    onSharedDownloadConsumed: () -> Unit = {},
    downloadsNavigationTick: Int = 0,
) {
    val context = LocalContext.current
    val navController = rememberNavController()
    val repository = remember { VideoRepository(context) }
    val downloadManager = remember { DownloadManager.get(context) }
    val playbackStore = remember { PlaybackStore(context) }
    val playerPreferencesStore = remember { PlayerPreferencesStore(context) }
    val seriesSkipStore = remember { SeriesSkipStore(context) }
    val hiddenVideosStore = remember { HiddenVideosStore(context) }
    val pinnedItemsStore = remember { PinnedItemsStore(context) }
    val playCountStore = remember { PlayCountStore(context) }
    val themeStore = remember { ThemePreferencesStore(context) }
    val libraryViewModel = remember { LibraryViewModel(context) }
    val castManager = remember { CastManager(context, playbackStore) }
    val themeState by themeStore.themeState.collectAsState(initial = ThemeState())
    val libraryEntries by libraryViewModel.libraryEntries.collectAsState()
    val isLibraryLoading by libraryViewModel.isLibraryLoading.collectAsState()
    val isRefreshing by libraryViewModel.isRefreshing.collectAsState()

    DisposableEffect(castManager) {
        onDispose { castManager.release() }
    }
    DisposableEffect(libraryViewModel) {
        libraryViewModel.startObserving()
        onDispose { libraryViewModel.stopObserving() }
    }

    val activity = context as? ComponentActivity
    val isSystemDark = isSystemInDarkTheme()
    SideEffect {
        val isLight = !themeState.mode.resolveDark(isSystemDark)
        val barStyle = if (isLight) {
            SystemBarStyle.light(AndroidColor.TRANSPARENT, AndroidColor.TRANSPARENT)
        } else {
            SystemBarStyle.dark(AndroidColor.TRANSPARENT)
        }
        activity?.enableEdgeToEdge(
            statusBarStyle = barStyle,
            navigationBarStyle = barStyle,
        )
    }

    var playlistState by remember { mutableStateOf<PlaylistState?>(null) }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Permissions.readVideoPermission,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    var recentRecords by remember { mutableStateOf<List<PlaybackRecord>>(emptyList()) }
    var playbackRecords by remember { mutableStateOf<Map<String, PlaybackRecord>>(emptyMap()) }

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(downloadsNavigationTick) {
        if (downloadsNavigationTick > 0) {
            navController.navigate(ROUTE_DOWNLOADS)
        }
    }

    LaunchedEffect(downloadManager, hasPermission) {
        var knownCompleted = downloadManager.tasks.value
            .filter { it.status.name == "COMPLETED" }
            .map { it.id }
            .toSet()
        downloadManager.tasks.collect { tasks ->
            val completed = tasks.filter { it.status.name == "COMPLETED" }.map { it.id }.toSet()
            if (hasPermission && (completed - knownCompleted).isNotEmpty()) {
                libraryViewModel.refresh()
            }
            knownCompleted = completed
        }
    }

    LaunchedEffect(sharedDownloadText) {
        if (sharedDownloadText != null) navController.navigate(ROUTE_DOWNLOADS)
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Permissions.readVideoPermission)
        }
    }

    LaunchedEffect(themeStore) {
        val savedIcon = themeStore.getLauncherIcon()
        val activeIcon = LauncherIconSwitcher.current(context)
        if (savedIcon != activeIcon) {
            LauncherIconSwitcher.apply(context, savedIcon)
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        runCatching { libraryViewModel.loadFromCache() }
        libraryViewModel.refresh(showLoading = libraryViewModel.libraryEntries.value.isEmpty())
    }

    LaunchedEffect(libraryViewModel) {
        libraryViewModel.refreshError.collect { message ->
            when (message) {
                LibraryViewModel.REFRESH_UP_TO_DATE -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.library_up_to_date),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
                else -> {
                    Toast.makeText(
                        context,
                        context.getString(R.string.library_refresh_failed),
                        Toast.LENGTH_SHORT,
                    ).show()
                }
            }
        }
    }

    LaunchedEffect(playbackStore) {
        playbackStore.recentRecords.collect { records ->
            recentRecords = records
        }
    }

    @OptIn(FlowPreview::class)
    LaunchedEffect(playbackStore) {
        playbackStore.recentRecords
            .debounce(400)
            .collect { records ->
                playbackRecords = records.associateBy { it.uri.toString() }
            }
    }

    LocalVideoPlayerTheme(
        mode = themeState.mode,
        colors = themeState.colors,
    ) {
        CompositionLocalProvider(LocalCastManager provides castManager) {
            Surface(modifier = Modifier.fillMaxSize()) {
                NavHost(
                    navController = navController,
                    startDestination = ROUTE_VIDEO_LIST,
                ) {
                    composable(ROUTE_VIDEO_LIST) {
                        VideoListScreen(
                            libraryEntries = libraryEntries,
                            isLibraryLoading = isLibraryLoading && libraryEntries.isEmpty(),
                            isRefreshing = isRefreshing,
                            hasPermission = hasPermission,
                            recentRecords = recentRecords,
                            playbackRecords = playbackRecords,
                            repository = repository,
                            hiddenVideosStore = hiddenVideosStore,
                            playbackStore = playbackStore,
                            pinnedItemsStore = pinnedItemsStore,
                            playCountStore = playCountStore,
                            themeStore = themeStore,
                            themeState = themeState,
                            onRequestPermission = {
                                permissionLauncher.launch(Permissions.readVideoPermission)
                            },
                            onPlayVideo = { state ->
                                playlistState = state
                                navController.navigate(ROUTE_PLAYER)
                            },
                            onOpenSeries = { seriesId ->
                                navController.navigate("series/$seriesId")
                            },
                            onOpenDownloads = { navController.navigate(ROUTE_DOWNLOADS) },
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
                                playbackRecords = playbackRecords,
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
                        val state = playlistState
                        if (state == null) {
                            navController.popBackStack()
                        } else {
                            PlayerScreen(
                                playlistState = state,
                                playbackStore = playbackStore,
                                playerPreferencesStore = playerPreferencesStore,
                                pinnedItemsStore = pinnedItemsStore,
                                seriesSkipStore = seriesSkipStore,
                                onBack = { navController.popBackStack() },
                            )
                        }
                    }
                    composable(ROUTE_DOWNLOADS) {
                        DownloadManagerScreen(
                            manager = downloadManager,
                            initialShareText = sharedDownloadText,
                            onInitialShareConsumed = onSharedDownloadConsumed,
                            onOpenCompleted = { task ->
                                openCompletedDownload(context, task, navController) { playlistState = it }
                            },
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}

private fun openCompletedDownload(
    context: android.content.Context,
    task: DownloadTask,
    navController: NavHostController,
    onPlaylistReady: (PlaylistState) -> Unit,
) {
    val uri = task.outputUri?.let { runCatching { Uri.parse(it) }.getOrNull() } ?: return
    val durationMs = queryDownloadDurationMs(context, uri)
    val video = VideoItem(
        id = uri.hashCode().toLong(),
        uri = uri,
        title = task.fileName,
        durationMs = durationMs,
        dateAdded = task.createdAt / 1000,
        thumbnailUri = null,
    )
    onPlaylistReady(PlaylistState(items = listOf(video), currentIndex = 0))
    navController.navigate(ROUTE_PLAYER)
}

private fun queryDownloadDurationMs(context: android.content.Context, uri: Uri): Long {
    return runCatching {
        MediaMetadataRetriever().use { retriever ->
            retriever.setDataSource(context, uri)
            retriever.extractMetadata(MediaMetadataRetriever.METADATA_KEY_DURATION)?.toLongOrNull() ?: 0L
        }
    }.getOrDefault(0L)
}
