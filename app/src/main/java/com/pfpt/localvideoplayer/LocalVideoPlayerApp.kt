package com.pfpt.localvideoplayer

import android.content.pm.PackageManager
import android.graphics.Color as AndroidColor
import android.net.Uri
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.SideEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.withFrameNanos
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.zIndex
import androidx.compose.ui.platform.LocalContext
import androidx.core.content.ContextCompat
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import com.pfpt.localvideoplayer.cast.CastManager
import com.pfpt.localvideoplayer.cast.CastUiState
import com.pfpt.localvideoplayer.cast.LocalCastManager
import com.pfpt.localvideoplayer.data.BookmarkStore
import com.pfpt.localvideoplayer.data.LibraryPreferencesStore
import com.pfpt.localvideoplayer.data.LibraryViewModel
import com.pfpt.localvideoplayer.data.PlaybackStore
import com.pfpt.localvideoplayer.data.PlayerPreferencesStore
import com.pfpt.localvideoplayer.data.PinnedItemsStore
import com.pfpt.localvideoplayer.data.PlayCountStore
import com.pfpt.localvideoplayer.data.HiddenVideosStore
import com.pfpt.localvideoplayer.data.FolderSourceStore
import com.pfpt.localvideoplayer.data.SeriesPlaybackStore
import com.pfpt.localvideoplayer.data.SeriesSkipStore
import com.pfpt.localvideoplayer.data.ThemePreferencesStore
import com.pfpt.localvideoplayer.data.VideoRepository
import com.pfpt.localvideoplayer.download.DownloadManager
import com.pfpt.localvideoplayer.download.DownloadStatus
import com.pfpt.localvideoplayer.player.PlaybackSessionViewModel
import com.pfpt.localvideoplayer.ui.PlayerFlightOverlay
import com.pfpt.localvideoplayer.ui.PlayerFlightState
import com.pfpt.localvideoplayer.ui.ProvidePlayerFlight
import com.pfpt.localvideoplayer.ui.components.PlaybackMiniBar
import com.pfpt.localvideoplayer.ui.theme.LocalVideoPlayerTheme
import com.pfpt.localvideoplayer.ui.theme.ThemeState
import com.pfpt.localvideoplayer.util.LauncherIconSwitcher
import com.pfpt.localvideoplayer.util.Permissions
import kotlinx.coroutines.launch

@Composable
fun LocalVideoPlayerApp(
    downloadsNavigationTick: Int = 0,
    openPlayerTick: Int = 0,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val navController = rememberNavController()
    val repository = remember { VideoRepository(context) }
    val downloadManager = remember { DownloadManager.get(context) }
    val playbackStore = remember { PlaybackStore(context) }
    val bookmarkStore = remember { BookmarkStore(context) }
    val playerPreferencesStore = remember { PlayerPreferencesStore(context) }
    val seriesSkipStore = remember { SeriesSkipStore(context) }
    val seriesPlaybackStore = remember { SeriesPlaybackStore(context) }
    val hiddenVideosStore = remember { HiddenVideosStore(context) }
    val pinnedItemsStore = remember { PinnedItemsStore(context) }
    val playCountStore = remember { PlayCountStore(context) }
    val themeStore = remember { ThemePreferencesStore(context) }
    val libraryPreferencesStore = remember { LibraryPreferencesStore(context) }
    val folderSourceStore = remember { FolderSourceStore(context) }
    val libraryViewModel = remember { LibraryViewModel(context) }
    val playbackSession = viewModel<PlaybackSessionViewModel>().session
    val flight = remember { PlayerFlightState() }
    val castManager = remember { CastManager(context, playbackStore) }
    val themeState by themeStore.themeState.collectAsState(initial = ThemeState())

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

    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                Permissions.readVideoPermission,
            ) == PackageManager.PERMISSION_GRANTED,
        )
    }
    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission(),
    ) { granted ->
        hasPermission = granted
    }

    LaunchedEffect(downloadManager, hasPermission) {
        var knownCompleted = downloadManager.tasks.value
            .filter { it.status == DownloadStatus.COMPLETED }
            .map { it.id }
            .toSet()
        downloadManager.tasks.collect { tasks ->
            val completed = tasks.filter { it.status == DownloadStatus.COMPLETED }
            if (hasPermission) {
                completed.filter { it.id !in knownCompleted }.forEach { task ->
                    val uri = task.outputUri?.let { runCatching { Uri.parse(it) }.getOrNull() }
                        ?: return@forEach
                    libraryViewModel.includeDownloaded(uri)
                }
            }
            knownCompleted = completed.map { it.id }.toSet()
        }
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

    LocalVideoPlayerTheme(
        mode = themeState.mode,
        colors = themeState.colors,
    ) {
        val navBackStack by navController.currentBackStackEntryAsState()
        val sessionState by playbackSession.state.collectAsState()
        val castUi by castManager.uiState.collectAsState()
        val showPlaybackBar = sessionState.active &&
            !sessionState.onPlayerScreen &&
            navBackStack?.destination?.route != ROUTE_PLAYER &&
            castUi !is CastUiState.Connected
        LaunchedEffect(navBackStack?.destination?.route) {
            val route = navBackStack?.destination?.route
            if (route == null || route == ROUTE_PLAYER) return@LaunchedEffect
            withFrameNanos { }
            flight.settleReturnLayout()
        }
        CompositionLocalProvider(LocalCastManager provides castManager) {
            ProvidePlayerFlight(flight) {
            Surface(modifier = Modifier.fillMaxSize()) {
                Box(modifier = Modifier.fillMaxSize()) {
                            AppNavHost(
                                navController = navController,
                                downloadsNavigationTick = downloadsNavigationTick,
                                openPlayerTick = openPlayerTick,
                                playbackSession = playbackSession,
                                libraryViewModel = libraryViewModel,
                                repository = repository,
                                hiddenVideosStore = hiddenVideosStore,
                                playbackStore = playbackStore,
                                pinnedItemsStore = pinnedItemsStore,
                                playCountStore = playCountStore,
                                themeStore = themeStore,
                                libraryPreferencesStore = libraryPreferencesStore,
                                themeState = themeState,
                                hasPermission = hasPermission,
                                onRequestPermission = {
                                    permissionLauncher.launch(Permissions.readVideoPermission)
                                },
                                playerPreferencesStore = playerPreferencesStore,
                                seriesSkipStore = seriesSkipStore,
                                seriesPlaybackStore = seriesPlaybackStore,
                                bookmarkStore = bookmarkStore,
                                folderSourceStore = folderSourceStore,
                                downloadManager = downloadManager,
                                scope = scope,
                            )
                    PlaybackMiniBar(
                        visible = showPlaybackBar,
                        title = sessionState.title,
                        sleepChoice = sessionState.sleepChoice,
                        sleepRemaining = playbackSession.sleepRemainingMs,
                        isPlaying = sessionState.isPlaying,
                        onOpen = {
                            playbackSession.markReturnToExisting()
                            navController.navigate(ROUTE_PLAYER) {
                                launchSingleTop = true
                            }
                        },
                        onPlayPause = { playbackSession.togglePlayPause() },
                        onClose = { scope.launch { playbackSession.stop() } },
                        modifier = Modifier
                            .zIndex(1f)
                            .align(Alignment.BottomCenter)
                            .navigationBarsPadding(),
                    )
                    PlayerFlightOverlay(
                        flight = flight,
                        modifier = Modifier.zIndex(2f),
                    )
                }
            }
            }
        }
    }
}

