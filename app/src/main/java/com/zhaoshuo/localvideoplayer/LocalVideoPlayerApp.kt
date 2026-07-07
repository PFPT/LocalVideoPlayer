package com.zhaoshuo.localvideoplayer

import android.content.pm.PackageManager
import android.net.Uri
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.navigation.NavType
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import androidx.navigation.navArgument
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.data.HiddenVideosStore
import com.zhaoshuo.localvideoplayer.data.PinnedItemsStore
import com.zhaoshuo.localvideoplayer.data.PlayCountStore
import com.zhaoshuo.localvideoplayer.data.LibraryCacheStore
import com.zhaoshuo.localvideoplayer.data.PlaybackStore
import com.zhaoshuo.localvideoplayer.data.ThemePreferencesStore
import com.zhaoshuo.localvideoplayer.data.VideoRepository
import com.zhaoshuo.localvideoplayer.model.PlaybackRecord
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import com.zhaoshuo.localvideoplayer.model.withoutUris
import com.zhaoshuo.localvideoplayer.player.PlaylistState
import com.zhaoshuo.localvideoplayer.ui.PlayerScreen
import com.zhaoshuo.localvideoplayer.ui.SeriesDetailScreen
import com.zhaoshuo.localvideoplayer.ui.VideoListScreen
import com.zhaoshuo.localvideoplayer.ui.theme.LocalVideoPlayerTheme
import com.zhaoshuo.localvideoplayer.ui.theme.LumenThemeColors
import com.zhaoshuo.localvideoplayer.util.Permissions
import com.zhaoshuo.localvideoplayer.util.SeriesGrouper
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

private const val ROUTE_VIDEO_LIST = "video_list"
private const val ROUTE_SERIES = "series/{seriesId}"
private const val ROUTE_PLAYER = "player"

@Composable
fun LocalVideoPlayerApp() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val repository = remember { VideoRepository(context) }
    val playbackStore = remember { PlaybackStore(context) }
    val hiddenVideosStore = remember { HiddenVideosStore(context) }
    val pinnedItemsStore = remember { PinnedItemsStore(context) }
    val playCountStore = remember { PlayCountStore(context) }
    val libraryCacheStore = remember { LibraryCacheStore(context) }
    val themeStore = remember { ThemePreferencesStore(context) }
    val savedThemeColors by themeStore.themeColors.collectAsState(initial = LumenThemeColors.default())

    var playlistState by remember { mutableStateOf<PlaylistState?>(null) }
    var libraryEntries by remember { mutableStateOf<List<VideoLibraryEntry>>(emptyList()) }
    var isLibraryLoading by remember { mutableStateOf(false) }
    var isRefreshing by remember { mutableStateOf(false) }
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

    suspend fun scanVideosWithRetry(): List<VideoItem> {
        var scanned = withContext(Dispatchers.IO) {
            repository.loadVideos()
        }
        if (scanned.isEmpty()) {
            delay(400)
            scanned = withContext(Dispatchers.IO) {
                repository.loadVideos()
            }
        }
        return scanned
    }

    fun librarySignature(entries: List<VideoLibraryEntry>): List<String> {
        return entries.map { entry ->
            when (entry) {
                is VideoLibraryEntry.Series -> "series:${entry.series.id}:${entry.series.episodes.size}"
                is VideoLibraryEntry.Single -> "single:${entry.video.uri}"
            }
        }
    }

    suspend fun applyVideos(videos: List<VideoItem>, hiddenUris: Set<String>): List<VideoLibraryEntry> {
        val entries = withContext(Dispatchers.Default) {
            SeriesGrouper.groupVideos(
                videos.filter { it.uri.toString() !in hiddenUris },
            )
        }
        libraryEntries = entries
        return entries
    }

    suspend fun loadLibraryFromCache() {
        coroutineScope {
            val hiddenUrisDeferred = async(Dispatchers.IO) {
                hiddenVideosStore.getHiddenUris()
            }
            val cachedVideosDeferred = async(Dispatchers.IO) {
                libraryCacheStore.loadVideos()
            }
            val hiddenUris = hiddenUrisDeferred.await()
            val videos = cachedVideosDeferred.await().filter { video ->
                video.uri.toString() !in hiddenUris
            }
            if (videos.isEmpty()) return@coroutineScope

            libraryEntries = videos.map { VideoLibraryEntry.Single(it) }
            libraryEntries = withContext(Dispatchers.Default) {
                SeriesGrouper.groupVideos(videos)
            }
        }
    }

    fun removeVideosFromLibrary(uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        val uriSet = uris.map { it.toString() }.toSet()
        val removedSeriesIds = libraryEntries.mapNotNull { entry ->
            when (entry) {
                is VideoLibraryEntry.Series -> {
                    val remaining = entry.series.episodes.filter {
                        it.uri.toString() !in uriSet
                    }
                    if (remaining.isEmpty()) entry.series.id else null
                }
                is VideoLibraryEntry.Single -> null
            }
        }
        libraryEntries = libraryEntries.withoutUris(uris)
        scope.launch(Dispatchers.IO) {
            libraryCacheStore.removeVideos(uris)
            pinnedItemsStore.removeKeysForUris(uris)
            pinnedItemsStore.removeSeriesKeys(removedSeriesIds)
            playCountStore.removeForUris(uris)
            playCountStore.removeKeys(removedSeriesIds.map { "series:$it" })
        }
    }

    suspend fun performLibraryRefresh(showLoading: Boolean = false, fromPull: Boolean = false) {
        val previousSignature = if (fromPull) librarySignature(libraryEntries) else emptyList()
        if (fromPull) isRefreshing = true
        else if (showLoading) isLibraryLoading = true
        try {
            val hiddenUris = hiddenVideosStore.getHiddenUris()
            val scanned = scanVideosWithRetry()
            applyVideos(scanned, hiddenUris)
            if (scanned.isNotEmpty() || fromPull) {
                withContext(Dispatchers.IO) {
                    runCatching {
                        libraryCacheStore.saveVideos(scanned)
                    }
                }
            }
            if (fromPull && librarySignature(libraryEntries) == previousSignature) {
                Toast.makeText(
                    context,
                    context.getString(R.string.library_up_to_date),
                    Toast.LENGTH_SHORT,
                ).show()
            }
        } catch (_: Exception) {
        } finally {
            isRefreshing = false
            isLibraryLoading = false
        }
    }

    fun refreshLibrary(showLoading: Boolean = false, fromPull: Boolean = false) {
        if (!hasPermission) return
        scope.launch {
            performLibraryRefresh(showLoading = showLoading, fromPull = fromPull)
        }
    }

    LaunchedEffect(Unit) {
        if (!hasPermission) {
            permissionLauncher.launch(Permissions.readVideoPermission)
        }
    }

    LaunchedEffect(hasPermission) {
        if (!hasPermission) return@LaunchedEffect
        try {
            loadLibraryFromCache()
        } catch (_: Exception) {
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

    LocalVideoPlayerTheme(savedThemeColors) {
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
                        savedThemeColors = savedThemeColors,
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
                        onVideosRemoved = { uris -> removeVideosFromLibrary(uris) },
                        onPullRefresh = { refreshLibrary(fromPull = true) },
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
                                playlistState = state
                                navController.navigate(ROUTE_PLAYER)
                            },
                            onVideosRemoved = { uris -> removeVideosFromLibrary(uris) },
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
                            onBack = { navController.popBackStack() },
                        )
                    }
                }
            }
        }
    }
}
