package com.zhaoshuo.localvideoplayer.ui

import android.net.Uri
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.LaunchedEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.zhaoshuo.localvideoplayer.R
import com.zhaoshuo.localvideoplayer.data.HiddenVideosStore
import com.zhaoshuo.localvideoplayer.data.PlayCountStore
import com.zhaoshuo.localvideoplayer.data.PlaybackStore
import com.zhaoshuo.localvideoplayer.data.VideoRepository
import com.zhaoshuo.localvideoplayer.model.VideoSeries
import com.zhaoshuo.localvideoplayer.player.PlaylistState
import com.zhaoshuo.localvideoplayer.ui.components.DeleteVideoDialog
import com.zhaoshuo.localvideoplayer.ui.components.EpisodeListItem
import com.zhaoshuo.localvideoplayer.ui.components.LumenListHeader
import com.zhaoshuo.localvideoplayer.ui.components.SelectionTopBar
import com.zhaoshuo.localvideoplayer.ui.components.rememberPlaybackProgress
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun SeriesDetailScreen(
    series: VideoSeries,
    repository: VideoRepository,
    hiddenVideosStore: HiddenVideosStore,
    playbackStore: PlaybackStore,
    playCountStore: PlayCountStore,
    onBack: () -> Unit,
    onPlayEpisode: (PlaylistState) -> Unit,
    onVideosRemoved: (List<Uri>) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scope = rememberCoroutineScope()
    var selectionMode by rememberSaveable { mutableStateOf(false) }
    var selectedUris by remember { mutableStateOf<Set<String>>(emptySet()) }
    var pendingDeleteUris by remember { mutableStateOf<List<Uri>?>(null) }
    var pendingDeleteSubtitle by remember { mutableStateOf<String?>(null) }
    var showDeleteDialog by remember { mutableStateOf(false) }
    var isDeleting by remember { mutableStateOf(false) }

    val hiddenUris by hiddenVideosStore.hiddenUris.collectAsStateWithLifecycle(initialValue = emptySet())
    val visibleEpisodes = remember(series.episodes, hiddenUris) {
        series.episodes.filter { it.uri.toString() !in hiddenUris }
    }

    LaunchedEffect(visibleEpisodes.isEmpty()) {
        if (visibleEpisodes.isEmpty()) {
            onBack()
        }
    }

    fun exitSelectionMode() {
        selectionMode = false
        selectedUris = emptySet()
    }

    fun closeDeleteDialog() {
        showDeleteDialog = false
        isDeleting = false
        pendingDeleteUris = null
        pendingDeleteSubtitle = null
    }

    fun subtitleForDelete(uris: List<Uri>): String? {
        if (uris.size == 1) {
            return visibleEpisodes.find { it.uri == uris.first() }?.title
        }
        val uriSet = uris.map { it.toString() }.toSet()
        val visibleUriSet = visibleEpisodes.map { it.uri.toString() }.toSet()
        if (uriSet == visibleUriSet) return series.title
        return null
    }

    fun openDeleteDialog(uris: List<Uri>) {
        if (uris.isEmpty()) return
        pendingDeleteUris = uris
        pendingDeleteSubtitle = subtitleForDelete(uris)
        showDeleteDialog = true
    }

    fun titleForUri(uri: Uri): String {
        return series.episodes.firstOrNull { it.uri.toString() == uri.toString() }?.title.orEmpty()
    }

    val deleteFromDevice = rememberVideoDeleter(
        playbackStore = playbackStore,
        hiddenVideosStore = hiddenVideosStore,
        playCountStore = playCountStore,
        titleForUri = ::titleForUri,
        onVideosRemoved = onVideosRemoved,
        onDeleteStarted = { isDeleting = true },
        onDeleteFinished = {
            isDeleting = false
            exitSelectionMode()
            closeDeleteDialog()
        },
    )

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

    BackHandler {
        if (selectionMode) {
            exitSelectionMode()
        } else {
            onBack()
        }
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

    Column(
        modifier = modifier
            .fillMaxSize()
            .windowInsetsPadding(WindowInsets.safeDrawing)
            .padding(bottom = 8.dp),
    ) {
        AnimatedContent(
            targetState = selectionMode,
            transitionSpec = {
                fadeIn(animationSpec = tween(180)) togetherWith
                    fadeOut(animationSpec = tween(180))
            },
            label = "series-detail-top-bar",
        ) { selecting ->
            if (selecting) {
                SelectionTopBar(
                    selectedCount = selectedUris.size,
                    totalCount = visibleEpisodes.size,
                    onSelectAll = {
                        selectedUris = visibleEpisodes.map { it.uri.toString() }.toSet()
                    },
                    onDelete = {
                        val uris = visibleEpisodes
                            .filter { it.uri.toString() in selectedUris }
                            .map { it.uri }
                        openDeleteDialog(uris)
                    },
                    onCancel = ::exitSelectionMode,
                )
            } else {
                LumenListHeader(
                    title = series.title,
                    onBack = onBack,
                    applyStatusBarPadding = false,
                )
            }
        }

        val listState = rememberLazyListState()
        val isListScrolling by remember {
            derivedStateOf { listState.isScrollInProgress }
        }

        AnimatedContent(
            targetState = series.id,
            transitionSpec = {
                fadeIn(animationSpec = tween(200)) togetherWith
                    fadeOut(animationSpec = tween(120))
            },
            label = "series-detail-list",
            modifier = Modifier.fillMaxSize(),
        ) {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                state = listState,
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = 12.dp,
                    bottom = 24.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                itemsIndexed(
                    items = visibleEpisodes,
                    key = { _, episode -> episode.uri.toString() },
                    contentType = { _, _ -> 1 },
                ) { index, episode ->
                    val uriKey = episode.uri.toString()
                    val isSelected = uriKey in selectedUris
                    val progress = rememberPlaybackProgress(uriKey, playbackStore)

                    Box(modifier = Modifier.animateItem()) {
                        EpisodeListItem(
                            episode = episode,
                            episodeNumber = episode.episode,
                            progress = progress,
                            selectionMode = selectionMode,
                            selected = isSelected,
                            deferThumbnailLoad = isListScrolling,
                            onClick = {
                                if (selectionMode) {
                                    selectedUris = if (isSelected) {
                                        selectedUris - uriKey
                                    } else {
                                        selectedUris + uriKey
                                    }
                                } else {
                                    scope.launch(Dispatchers.IO) {
                                        playCountStore.increment("series:${series.id}")
                                    }
                                    onPlayEpisode(
                                        PlaylistState(
                                            items = visibleEpisodes,
                                            currentIndex = index,
                                        ),
                                    )
                                }
                            },
                            onLongClick = {
                                selectionMode = true
                                selectedUris = setOf(uriKey)
                            },
                            onSwipeDelete = {
                                openDeleteDialog(listOf(episode.uri))
                            },
                        )
                    }
                }
            }
        }
    }
}
