package com.pfpt.localvideoplayer.ui.library

import androidx.compose.animation.animateContentSize
import androidx.compose.animation.core.Spring
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.spring
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.data.PlaybackStore
import com.pfpt.localvideoplayer.model.LibraryViewLayout
import com.pfpt.localvideoplayer.model.PinnedLibrarySections
import com.pfpt.localvideoplayer.model.PlaybackRecord
import com.pfpt.localvideoplayer.model.VideoLibraryEntry
import com.pfpt.localvideoplayer.model.entryKey
import com.pfpt.localvideoplayer.ui.LibraryOpening
import com.pfpt.localvideoplayer.ui.components.LumenEmptyState
import com.pfpt.localvideoplayer.ui.components.PinnedLibraryRow
import com.pfpt.localvideoplayer.ui.components.RecentPlaybackSection
import com.pfpt.localvideoplayer.ui.libraryOpening
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.LumenListHorizontalInset
import com.pfpt.localvideoplayer.ui.theme.LumenListItemGap
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceMd
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceSm
import com.pfpt.localvideoplayer.ui.theme.LumenSpaceXs

private val LibraryRowPlacement = tween<IntOffset>(durationMillis = 200)

@OptIn(ExperimentalFoundationApi::class)
@Composable
internal fun LibraryHomeList(
    listState: LazyListState,
    selectionMode: Boolean,
    isSearching: Boolean,
    hasRecentPlayback: Boolean,
    playbackStore: PlaybackStore,
    onRecordClick: (PlaybackRecord) -> Unit,
    showPinnedRow: Boolean,
    pinnedHomeEntries: List<VideoLibraryEntry>,
    onPinnedClick: (VideoLibraryEntry) -> Unit,
    cacheResolved: Boolean,
    libraryEntryCount: Int,
    isLibraryLoading: Boolean,
    isSortingLibrary: Boolean,
    visibleLibrary: List<VideoLibraryEntry>,
    searchQuery: String,
    viewLayout: LibraryViewLayout,
    flatLibrary: List<VideoLibraryEntry>,
    pinnedSections: PinnedLibrarySections?,
    collapseSingles: Boolean,
    onToggleCollapseSingles: () -> Unit,
    row: @Composable (VideoLibraryEntry, Any) -> Unit,
) {
    val theme = LocalLumenTheme.current
    LazyColumn(
        state = listState,
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(
            start = LumenListHorizontalInset,
            end = LumenListHorizontalInset,
            top = LumenSpaceMd,
            bottom = 88.dp,
        ),
        verticalArrangement = Arrangement.spacedBy(LumenListItemGap),
    ) {
        if (!selectionMode && !isSearching && hasRecentPlayback) {
            item(key = "recent-playback") {
                RecentPlaybackSection(
                    playbackStore = playbackStore,
                    onRecordClick = onRecordClick,
                    modifier = Modifier.animateItem(placementSpec = null),
                )
            }
        }
        if (showPinnedRow) {
            item(key = "pinned-home-row") {
                PinnedLibraryRow(
                    entries = pinnedHomeEntries,
                    playbackStore = playbackStore,
                    onEntryClick = onPinnedClick,
                    modifier = Modifier.animateItem(placementSpec = null),
                )
            }
        }

        when (
            libraryOpening(
                cacheResolved = cacheResolved,
                entryCount = libraryEntryCount,
                isLibraryLoading = isLibraryLoading,
            )
        ) {
            LibraryOpening.Hold, LibraryOpening.List -> Unit
            LibraryOpening.Loading -> {
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
            LibraryOpening.Empty -> {
                if (!hasRecentPlayback) {
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
            }
        }

        if (
            !isLibraryLoading &&
            !isSortingLibrary &&
            visibleLibrary.isEmpty() &&
            libraryEntryCount > 0
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
                val listKey = "flat-${viewLayout.name}-${entryKey(entry)}"
                Box(modifier = Modifier.animateItem(placementSpec = LibraryRowPlacement)) {
                    row(entry, listKey)
                }
            }
        } else {
            val sections = pinnedSections
            if (sections != null) {
                if (sections.pinned.isNotEmpty() && !showPinnedRow) {
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
                        val listKey = "sectioned-${viewLayout.name}-${entryKey(entry)}"
                        Box(modifier = Modifier.animateItem(placementSpec = LibraryRowPlacement)) {
                            row(entry, listKey)
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
                        val listKey = entryKey(entry)
                        Box(modifier = Modifier.animateItem(placementSpec = LibraryRowPlacement)) {
                            row(entry, listKey)
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
                        val listKey = entryKey(entry)
                        Box(modifier = Modifier.animateItem(placementSpec = LibraryRowPlacement)) {
                            row(entry, listKey)
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
                        val listKey = entryKey(entry)
                        Box(modifier = Modifier.animateItem(placementSpec = LibraryRowPlacement)) {
                            row(entry, listKey)
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
                            onClick = onToggleCollapseSingles,
                            modifier = Modifier.animateItem(placementSpec = null),
                        )
                    }
                    if (!collapseSingles) {
                        items(
                            items = sections.singles,
                            key = { entry -> entryKey(entry) },
                            contentType = { 1 },
                        ) { entry ->
                            val listKey = entryKey(entry)
                            Box(modifier = Modifier.animateItem(placementSpec = LibraryRowPlacement)) {
                                row(entry, listKey)
                            }
                        }
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
        modifier = modifier.padding(top = LumenSpaceSm, bottom = LumenSpaceXs),
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
            .padding(top = LumenSpaceSm, bottom = LumenSpaceXs),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(LumenSpaceXs),
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
