package com.pfpt.localvideoplayer.ui

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.DocumentsContract
import android.provider.Settings
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawing
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.pfpt.localvideoplayer.R
import com.pfpt.localvideoplayer.data.FolderSourceStore
import com.pfpt.localvideoplayer.data.HiddenVideosStore
import com.pfpt.localvideoplayer.data.hiddenDisplayLabel
import com.pfpt.localvideoplayer.data.LibraryPreferencesStore
import com.pfpt.localvideoplayer.data.PlayerPreferencesStore
import com.pfpt.localvideoplayer.data.ThemePreferencesStore
import com.pfpt.localvideoplayer.model.AspectRatioMode
import com.pfpt.localvideoplayer.model.LibraryViewLayout
import com.pfpt.localvideoplayer.model.VideoSortOrder
import com.pfpt.localvideoplayer.ui.components.AspectRatioSheet
import com.pfpt.localvideoplayer.ui.components.LumenListHeader
import com.pfpt.localvideoplayer.ui.components.SpeedSelectorSheet
import com.pfpt.localvideoplayer.ui.components.ThemeColorBottomSheet
import com.pfpt.localvideoplayer.ui.components.labelRes
import com.pfpt.localvideoplayer.ui.theme.LauncherIcon
import com.pfpt.localvideoplayer.ui.theme.LocalLumenTheme
import com.pfpt.localvideoplayer.ui.theme.ThemeState
import com.pfpt.localvideoplayer.util.LauncherIconSwitcher
import kotlinx.coroutines.launch

@Composable
fun SettingsScreen(
    themeStore: ThemePreferencesStore,
    themeState: ThemeState,
    libraryPreferencesStore: LibraryPreferencesStore,
    playerPreferencesStore: PlayerPreferencesStore,
    folderSourceStore: FolderSourceStore,
    hiddenVideosStore: HiddenVideosStore,
    onRestoreHidden: () -> Unit,
    onLibraryRefresh: () -> Unit = {},
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val theme = LocalLumenTheme.current
    val lifecycleOwner = LocalLifecycleOwner.current
    val libraryPrefs by libraryPreferencesStore.preferences.collectAsStateWithLifecycle(
        initialValue = com.pfpt.localvideoplayer.data.LibraryPreferences(),
    )
    val playerPrefs by playerPreferencesStore.preferences.collectAsStateWithLifecycle(
        initialValue = com.pfpt.localvideoplayer.data.PlayerPreferences(),
    )
    val folderSources by folderSourceStore.sources.collectAsStateWithLifecycle(initialValue = emptyList())
    val hiddenEntries by hiddenVideosStore.hiddenEntries.collectAsStateWithLifecycle(initialValue = emptyList())
    val selectedLauncherIcon by themeStore.launcherIcon.collectAsStateWithLifecycle(initialValue = LauncherIcon.Default)
    var showThemeSheet by remember { mutableStateOf(false) }
    var showSpeedSheet by remember { mutableStateOf(false) }
    var showAspectSheet by remember { mutableStateOf(false) }
    var hasAllFilesAccess by remember {
        mutableStateOf(Environment.isExternalStorageManager())
    }
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                val granted = Environment.isExternalStorageManager()
                if (granted != hasAllFilesAccess) {
                    hasAllFilesAccess = granted
                    if (granted) onLibraryRefresh()
                } else {
                    hasAllFilesAccess = granted
                }
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val folderPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocumentTree(),
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        val flags = Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION
        val granted = runCatching {
            context.contentResolver.takePersistableUriPermission(uri, flags)
            true
        }.recoverCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION,
            )
            true
        }.getOrDefault(false)
        if (!granted) {
            Toast.makeText(context, R.string.settings_folder_permission_failed, Toast.LENGTH_SHORT).show()
            return@rememberLauncherForActivityResult
        }
        val name = folderDisplayName(context, uri)
        scope.launch { folderSourceStore.add(uri, name) }
        Toast.makeText(context, R.string.settings_folder_added, Toast.LENGTH_SHORT).show()
    }

    if (showThemeSheet) {
        ThemeColorBottomSheet(
            themeMode = themeState.mode,
            initialColors = themeState.colors,
            selectedLauncherIcon = selectedLauncherIcon,
            onThemeModeChange = { mode ->
                scope.launch { themeStore.setThemeMode(mode) }
            },
            onApplyLauncherIcon = { icon ->
                themeStore.setLauncherIcon(icon)
                LauncherIconSwitcher.apply(context, icon)
            },
            onSave = { colors, icon ->
                scope.launch {
                    themeStore.saveThemeColors(colors)
                    themeStore.setLauncherIcon(icon)
                    if (icon != selectedLauncherIcon) {
                        LauncherIconSwitcher.apply(context, icon)
                    }
                }
            },
            onDismiss = { showThemeSheet = false },
        )
    }
    if (showSpeedSheet) {
        SpeedSelectorSheet(
            currentSpeed = playerPrefs.defaultSpeed,
            onSpeedSelected = { speed ->
                scope.launch { playerPreferencesStore.setDefaultSpeed(speed) }
            },
            onDismiss = { showSpeedSheet = false },
        )
    }
    if (showAspectSheet) {
        AspectRatioSheet(
            currentMode = playerPrefs.aspectRatioMode,
            onModeSelected = { mode ->
                scope.launch { playerPreferencesStore.setAspectRatioMode(mode) }
            },
            onDismiss = { showAspectSheet = false },
        )
    }

    Scaffold(
        modifier = modifier.fillMaxSize(),
        contentWindowInsets = WindowInsets.safeDrawing,
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            LumenListHeader(
                title = stringResource(R.string.settings),
                onBack = onBack,
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .padding(innerPadding)
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(bottom = 24.dp),
        ) {
            SettingsSectionTitle(stringResource(R.string.settings_appearance))
            SettingsClickRow(
                title = stringResource(R.string.theme_color),
                subtitle = stringResource(
                    when (themeState.mode) {
                        com.pfpt.localvideoplayer.ui.theme.ThemeMode.System -> R.string.theme_mode_system
                        com.pfpt.localvideoplayer.ui.theme.ThemeMode.Light -> R.string.theme_mode_light
                        com.pfpt.localvideoplayer.ui.theme.ThemeMode.Dark -> R.string.theme_mode_dark
                    },
                ),
                onClick = { showThemeSheet = true },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = theme.surface.copy(alpha = 0.4f))

            SettingsSectionTitle(stringResource(R.string.settings_library))
            SettingsClickRow(
                title = stringResource(R.string.sort),
                subtitle = stringResource(sortLabel(libraryPrefs.sortOrder)),
                onClick = {
                    val next = nextSort(libraryPrefs.sortOrder)
                    scope.launch { libraryPreferencesStore.setSortOrder(next) }
                },
            )
            SettingsClickRow(
                title = stringResource(R.string.view_layout),
                subtitle = stringResource(
                    if (libraryPrefs.viewLayout == LibraryViewLayout.SECTIONED) {
                        R.string.view_sectioned
                    } else {
                        R.string.view_flat
                    },
                ),
                onClick = {
                    val next = if (libraryPrefs.viewLayout == LibraryViewLayout.SECTIONED) {
                        LibraryViewLayout.FLAT
                    } else {
                        LibraryViewLayout.SECTIONED
                    }
                    scope.launch { libraryPreferencesStore.setViewLayout(next) }
                },
            )
            SettingsSectionTitle(stringResource(R.string.settings_folder_sources))
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                SettingsClickRow(
                    title = stringResource(R.string.settings_all_files_access),
                    subtitle = stringResource(
                        if (hasAllFilesAccess) {
                            R.string.settings_all_files_access_on
                        } else {
                            R.string.settings_all_files_access_off
                        },
                    ),
                    onClick = {
                        val intent = Intent(
                            Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION,
                            Uri.parse("package:${context.packageName}"),
                        )
                        runCatching { context.startActivity(intent) }.onFailure {
                            context.startActivity(Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION))
                        }
                    },
                )
            }
            if (folderSources.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_folders),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                folderSources.forEach { source ->
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = source.name,
                            style = MaterialTheme.typography.bodyLarge,
                            color = theme.onBackground,
                            modifier = Modifier.weight(1f),
                        )
                        IconButton(
                            onClick = {
                                runCatching {
                                    context.contentResolver.releasePersistableUriPermission(
                                        Uri.parse(source.treeUri),
                                        Intent.FLAG_GRANT_READ_URI_PERMISSION or
                                            Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
                                    )
                                }
                                scope.launch { folderSourceStore.remove(source.treeUri) }
                            },
                        ) {
                            Icon(
                                imageVector = Icons.Default.Delete,
                                contentDescription = stringResource(R.string.settings_remove_folder),
                                tint = theme.onSurfaceMuted,
                            )
                        }
                    }
                }
            }
            TextButton(
                onClick = { folderPicker.launch(null) },
                modifier = Modifier.padding(horizontal = 12.dp),
            ) {
                Icon(imageVector = Icons.Default.Add, contentDescription = null)
                Text(
                    text = stringResource(R.string.settings_add_folder),
                    modifier = Modifier.padding(start = 8.dp),
                )
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = theme.surface.copy(alpha = 0.4f))

            SettingsSectionTitle(stringResource(R.string.settings_hidden_videos))
            if (hiddenEntries.isEmpty()) {
                Text(
                    text = stringResource(R.string.settings_no_hidden_videos),
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceMuted,
                    modifier = Modifier.padding(horizontal = 24.dp, vertical = 8.dp),
                )
            } else {
                hiddenEntries.forEach { entry ->
                    SettingsClickRow(
                        title = hiddenDisplayLabel(entry),
                        subtitle = stringResource(R.string.settings_restore_hidden),
                        onClick = {
                            scope.launch {
                                hiddenVideosStore.unhide(Uri.parse(entry.uri))
                                onRestoreHidden()
                            }
                        },
                    )
                }
            }
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = theme.surface.copy(alpha = 0.4f))

            SettingsSectionTitle(stringResource(R.string.settings_playback))
            SettingsClickRow(
                title = stringResource(R.string.settings_default_speed),
                subtitle = stringResource(R.string.speed_format, formatSpeed(playerPrefs.defaultSpeed)),
                onClick = { showSpeedSheet = true },
            )
            SettingsClickRow(
                title = stringResource(R.string.settings_aspect_ratio),
                subtitle = stringResource(playerPrefs.aspectRatioMode.labelRes()),
                onClick = { showAspectSheet = true },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_repeat_one),
                checked = playerPrefs.repeatOne,
                onCheckedChange = { scope.launch { playerPreferencesStore.setRepeatOne(it) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_use_texture_view),
                subtitle = stringResource(R.string.settings_use_texture_view_hint),
                checked = playerPrefs.useTextureView,
                onCheckedChange = { scope.launch { playerPreferencesStore.setUseTextureView(it) } },
            )
            HorizontalDivider(modifier = Modifier.padding(horizontal = 24.dp), color = theme.surface.copy(alpha = 0.4f))

            SettingsSectionTitle(stringResource(R.string.settings_gestures))
            SettingsSwitchRow(
                title = stringResource(R.string.settings_gesture_brightness),
                checked = playerPrefs.gestureBrightness,
                onCheckedChange = { scope.launch { playerPreferencesStore.setGestureBrightness(it) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_gesture_volume),
                checked = playerPrefs.gestureVolume,
                onCheckedChange = { scope.launch { playerPreferencesStore.setGestureVolume(it) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_gesture_seek),
                checked = playerPrefs.gestureSeek,
                onCheckedChange = { scope.launch { playerPreferencesStore.setGestureSeek(it) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_gesture_double_tap),
                checked = playerPrefs.gestureDoubleTapSeek,
                onCheckedChange = { scope.launch { playerPreferencesStore.setGestureDoubleTapSeek(it) } },
            )
            SettingsSwitchRow(
                title = stringResource(R.string.settings_gesture_pinch),
                checked = playerPrefs.gesturePinchZoom,
                onCheckedChange = { scope.launch { playerPreferencesStore.setGesturePinchZoom(it) } },
            )
        }
    }
}

@Composable
private fun SettingsSectionTitle(title: String) {
    val theme = LocalLumenTheme.current
    Text(
        text = title,
        style = MaterialTheme.typography.titleSmall,
        color = theme.onSurfaceMuted,
        modifier = Modifier.padding(horizontal = 24.dp, vertical = 12.dp),
    )
}

@Composable
private fun SettingsClickRow(
    title: String,
    subtitle: String,
    onClick: () -> Unit,
) {
    val theme = LocalLumenTheme.current
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 24.dp, vertical = 12.dp),
        verticalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        Text(text = title, style = MaterialTheme.typography.bodyLarge, color = theme.onBackground)
        Text(text = subtitle, style = MaterialTheme.typography.bodyMedium, color = theme.onSurfaceMuted)
    }
}

@Composable
private fun SettingsSwitchRow(
    title: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    subtitle: String? = null,
) {
    val theme = LocalLumenTheme.current
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 24.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = title,
                style = MaterialTheme.typography.bodyLarge,
                color = theme.onBackground,
            )
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = theme.onSurfaceMuted,
                )
            }
        }
        Switch(
            checked = checked,
            onCheckedChange = onCheckedChange,
            colors = SwitchDefaults.colors(checkedTrackColor = theme.accent),
        )
    }
}

private fun sortLabel(order: VideoSortOrder): Int {
    return when (order) {
        VideoSortOrder.DATE_DESC -> R.string.sort_date_desc
        VideoSortOrder.DATE_ASC -> R.string.sort_date_asc
        VideoSortOrder.NAME_ASC -> R.string.sort_name_asc
        VideoSortOrder.NAME_DESC -> R.string.sort_name_desc
        VideoSortOrder.DURATION_DESC -> R.string.sort_duration_desc
        VideoSortOrder.DURATION_ASC -> R.string.sort_duration_asc
        VideoSortOrder.POPULARITY_DESC -> R.string.sort_popularity_desc
    }
}

private fun nextSort(order: VideoSortOrder): VideoSortOrder {
    val values = VideoSortOrder.entries
    val index = values.indexOf(order)
    return values[(index + 1) % values.size]
}

private fun formatSpeed(speed: Float): String {
    return if (speed == speed.toLong().toFloat()) {
        speed.toLong().toString()
    } else {
        speed.toString()
    }
}

private fun folderDisplayName(context: android.content.Context, treeUri: Uri): String {
    val docId = runCatching { DocumentsContract.getTreeDocumentId(treeUri) }.getOrNull()
    if (docId != null) {
        val documentUri = DocumentsContract.buildDocumentUriUsingTree(treeUri, docId)
        val name = runCatching {
            context.contentResolver.query(
                documentUri,
                arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { cursor ->
                if (cursor.moveToFirst()) cursor.getString(0) else null
            }
        }.getOrNull()
        if (!name.isNullOrBlank()) return name
    }
    return treeUri.lastPathSegment?.substringAfterLast(':') ?: treeUri.toString()
}
