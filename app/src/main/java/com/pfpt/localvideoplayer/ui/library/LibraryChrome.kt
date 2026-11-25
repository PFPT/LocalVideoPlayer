package com.pfpt.localvideoplayer.ui.library

import androidx.compose.animation.AnimatedContent
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.togetherWith
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Sort
import androidx.compose.material.icons.filled.BrightnessAuto
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.DarkMode
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.LightMode
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.cast.CastManager
import com.pfpt.localvideoplayer.cast.CastUiState
import com.pfpt.localvideoplayer.ui.components.CastButton
import com.pfpt.localvideoplayer.ui.components.DeleteVideoDialog
import com.pfpt.localvideoplayer.ui.components.LumenListHeader
import com.pfpt.localvideoplayer.ui.components.SelectionTopBar
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.ThemeMode

@Composable
internal fun LibraryScreenChrome(
    selectionMode: Boolean,
    isSearching: Boolean,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onExitSearch: () -> Unit,
    onStartSearch: () -> Unit,
    selectedCount: Int,
    totalCount: Int,
    onSelectAll: () -> Unit,
    onDeleteSelection: () -> Unit,
    onExitSelection: () -> Unit,
    castManager: CastManager?,
    castState: CastUiState,
    onOpenCast: () -> Unit,
    onOpenDownloads: () -> Unit,
    isMarkedTabActive: Boolean,
    onToggleMarked: () -> Unit,
    themeMode: ThemeMode,
    onCycleTheme: () -> Unit,
    onOpenSettings: () -> Unit,
    onOpenOptions: () -> Unit,
    hasActiveOptions: Boolean,
    showDeleteDialog: Boolean,
    deleteCount: Int,
    deleteSubtitle: String?,
    isDeleting: Boolean,
    onDismissDelete: () -> Unit,
    onHideFromList: () -> Unit,
    onDeleteFromDevice: () -> Unit,
) {
    val theme = LocalLumenTheme.current
    if (showDeleteDialog && deleteCount > 0) {
        DeleteVideoDialog(
            count = deleteCount,
            subtitle = deleteSubtitle,
            isDeleting = isDeleting,
            onDismiss = onDismissDelete,
            onHideFromList = onHideFromList,
            onDeleteFromDevice = onDeleteFromDevice,
        )
    }
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
                selectedCount = selectedCount,
                totalCount = totalCount,
                onSelectAll = onSelectAll,
                onDelete = onDeleteSelection,
                onCancel = onExitSelection,
            )
        } else if (isSearching) {
            LumenListHeader(
                title = stringResource(R.string.search),
                onBack = onExitSearch,
                applyStatusBarPadding = false,
                titleContent = {
                    BasicTextField(
                        value = searchQuery,
                        onValueChange = onSearchQueryChange,
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
                            onClick = { onSearchQueryChange("") },
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
                            onClick = onOpenCast,
                            modifier = Modifier.size(40.dp),
                        )
                    }
                    HeaderIconButton(
                        onClick = onStartSearch,
                        imageVector = Icons.Filled.Search,
                        contentDescription = stringResource(R.string.search),
                    )
                    HeaderIconButton(
                        onClick = onOpenDownloads,
                        imageVector = Icons.Filled.Download,
                        contentDescription = stringResource(R.string.downloads_title),
                    )
                    HeaderIconButton(
                        onClick = onToggleMarked,
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
                        onClick = onCycleTheme,
                        imageVector = when (themeMode) {
                            ThemeMode.System -> Icons.Default.BrightnessAuto
                            ThemeMode.Dark -> Icons.Default.LightMode
                            ThemeMode.Light -> Icons.Default.DarkMode
                        },
                        contentDescription = when (themeMode) {
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
                            onClick = onOpenOptions,
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
