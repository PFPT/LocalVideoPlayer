package com.zhaoshuo.localvideoplayer.ui.theme

import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

data class LumenThemeColors(
    val background: Color,
    val onBackground: Color,
    val surface: Color,
    val onSurfaceMuted: Color,
    val accent: Color,
) {
    fun withBackground(color: Color): LumenThemeColors = copy(background = color)

    fun withOnBackground(color: Color): LumenThemeColors = copy(onBackground = color)

    fun withSurface(color: Color): LumenThemeColors = copy(surface = color)

    companion object {
        fun default(): LumenThemeColors {
            return LumenThemeColors(
                background = LumenBackground,
                onBackground = LumenOnBackground,
                surface = LumenSurface,
                onSurfaceMuted = LumenOnSurfaceMuted,
                accent = LumenAccentText,
            )
        }
    }
}

val LocalLumenTheme = staticCompositionLocalOf { LumenThemeColors.default() }
