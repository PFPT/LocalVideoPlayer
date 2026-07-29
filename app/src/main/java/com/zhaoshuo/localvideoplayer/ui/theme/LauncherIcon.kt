package com.zhaoshuo.localvideoplayer.ui.theme

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import com.zhaoshuo.localvideoplayer.R

enum class LauncherIcon(
    val id: String,
    val componentSuffix: String,
    @DrawableRes val previewRes: Int,
    @StringRes val labelRes: Int,
) {
    Default(
        id = "default",
        componentSuffix = "MainActivityDefault",
        previewRes = R.drawable.ic_launcher_foreground,
        labelRes = R.string.launcher_icon_default,
    ),
    PlaySquare(
        id = "play_square",
        componentSuffix = "MainActivityPlaySquare",
        previewRes = R.drawable.ic_launcher_play_square_foreground,
        labelRes = R.string.launcher_icon_play_square,
    ),
    FilmPlay(
        id = "film_play",
        componentSuffix = "MainActivityFilmPlay",
        previewRes = R.drawable.ic_launcher_film_play_foreground,
        labelRes = R.string.launcher_icon_film_play,
    ),
    CameraYellow(
        id = "camera_yellow",
        componentSuffix = "MainActivityCameraYellow",
        previewRes = R.drawable.ic_launcher_camera_yellow_foreground,
        labelRes = R.string.launcher_icon_camera_yellow,
    ),
    FolderPlay(
        id = "folder_play",
        componentSuffix = "MainActivityFolderPlay",
        previewRes = R.drawable.ic_launcher_folder_play_foreground,
        labelRes = R.string.launcher_icon_folder_play,
    ),
    MonitorPlay(
        id = "monitor_play",
        componentSuffix = "MainActivityMonitorPlay",
        previewRes = R.drawable.ic_launcher_monitor_play_foreground,
        labelRes = R.string.launcher_icon_monitor_play,
    ),
    ;

    companion object {
        fun fromId(value: String?): LauncherIcon {
            return entries.firstOrNull { it.id == value } ?: Default
        }
    }
}
