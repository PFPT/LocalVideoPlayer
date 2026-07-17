package com.zhaoshuo.localvideoplayer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class LumenThemeColors(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
    val glassFill: Color,
    val glassBorder: Color,
    val sheetBg: Color,
    val sheetBorder: Color,
) {
    fun withBackground(color: Color): LumenThemeColors = copy(background = color)

    fun withOnBackground(color: Color): LumenThemeColors = copy(onBackground = color)

    fun withSurface(color: Color): LumenThemeColors = copy(surface = color)

    companion object {
        fun defaults(mode: ThemeMode): LumenThemeColors {
            return when (mode) {
                ThemeMode.Dark -> LumenThemeColors(
                    background = LumenBackground,
                    onBackground = LumenOnBackground,
                    surface = LumenSurface,
                    onSurfaceMuted = LumenOnSurfaceMuted,
                    accent = LumenAccentText,
                    glassFill = LumenGlassFill,
                    glassBorder = LumenGlassBorder,
                    sheetBg = LumenSheetBg,
                    sheetBorder = LumenSheetBorder,
                )
                ThemeMode.Light -> LumenThemeColors(
                    background = LumenLightBackground,
                    onBackground = LumenLightOnBackground,
                    surface = LumenLightSurface,
                    onSurfaceMuted = LumenLightOnSurfaceMuted,
                    accent = LumenLightAccent,
                    glassFill = LumenLightGlassFill,
                    glassBorder = LumenLightGlassBorder,
                    sheetBg = LumenLightSheetBg,
                    sheetBorder = LumenLightSheetBorder,
                )
            }
        }

        fun default(): LumenThemeColors = defaults(ThemeMode.Dark)
    }
}

data class ThemeState(
    val mode: ThemeMode = ThemeMode.Dark,
    val colors: LumenThemeColors = LumenThemeColors.defaults(ThemeMode.Dark),
)

val LocalLumenTheme = staticCompositionLocalOf { LumenThemeColors.default() }
val LocalThemeMode = staticCompositionLocalOf { ThemeMode.Dark }
