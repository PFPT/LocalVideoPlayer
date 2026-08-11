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
        previewRes = R.drawable.ic_launcher_film_reel_foreground,
        labelRes = R.string.launcher_icon_film_reel,
    ),
    FilmPlay(
        id = "film_play",
        componentSuffix = "MainActivityFilmPlay",
        previewRes = R.drawable.ic_launcher_clapper_play_foreground,
        labelRes = R.string.launcher_icon_clapper_play,
    ),
    CameraYellow(
        id = "camera_yellow",
        componentSuffix = "MainActivityCameraYellow",
        previewRes = R.drawable.ic_launcher_tv_play_foreground,
        labelRes = R.string.launcher_icon_tv_play,
    ),
    FolderPlay(
        id = "folder_play",
        componentSuffix = "MainActivityFolderPlay",
        previewRes = R.drawable.ic_launcher_filmstrip_foreground,
        labelRes = R.string.launcher_icon_filmstrip,
    ),
    MonitorPlay(
        id = "monitor_play",
        componentSuffix = "MainActivityMonitorPlay",
        previewRes = R.drawable.ic_launcher_clapper_open_foreground,
        labelRes = R.string.launcher_icon_clapper_open,
    ),
    CamDuo(
        id = "cam_duo",
        componentSuffix = "MainActivityCamDuo",
        previewRes = R.drawable.ic_launcher_cam_duo_foreground,
        labelRes = R.string.launcher_icon_cam_duo,
    ),
    LetterPlay(
        id = "letter_play",
        componentSuffix = "MainActivityLetterPlay",
        previewRes = R.drawable.ic_launcher_letter_play_foreground,
        labelRes = R.string.launcher_icon_letter_play,
    ),
    ClapperGreen(
        id = "clapper_green",
        componentSuffix = "MainActivityClapperGreen",
        previewRes = R.drawable.ic_launcher_clapper_green_foreground,
        labelRes = R.string.launcher_icon_clapper_green,
    ),
    CatCam(
        id = "cat_cam",
        componentSuffix = "MainActivityCatCam",
        previewRes = R.drawable.ic_launcher_cat_cam_foreground,
        labelRes = R.string.launcher_icon_cat_cam,
    ),
    CamOutline(
        id = "cam_outline",
        componentSuffix = "MainActivityCamOutline",
        previewRes = R.drawable.ic_launcher_cam_outline_foreground,
        labelRes = R.string.launcher_icon_cam_outline,
    ),
    CamSimple(
        id = "cam_simple",
        componentSuffix = "MainActivityCamSimple",
        previewRes = R.drawable.ic_launcher_cam_simple_foreground,
        labelRes = R.string.launcher_icon_cam_simple,
    ),
    CamAccent(
        id = "cam_accent",
        componentSuffix = "MainActivityCamAccent",
        previewRes = R.drawable.ic_launcher_cam_accent_foreground,
        labelRes = R.string.launcher_icon_cam_accent,
    ),
    ;

    companion object {
        fun fromId(value: String?): LauncherIcon {
            return entries.firstOrNull { it.id == value } ?: Default
        }
    }
}
