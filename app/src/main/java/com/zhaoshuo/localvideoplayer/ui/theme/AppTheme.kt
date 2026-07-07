package com.zhaoshuo.localvideoplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.zhaoshuo.localvideoplayer.R

val GeistFontFamily = FontFamily(
    Font(R.font.geist_variable, FontWeight.Normal),
    Font(R.font.geist_variable, FontWeight.Medium),
    Font(R.font.geist_variable, FontWeight.SemiBold),
    Font(R.font.geist_variable, FontWeight.Bold),
)

val GeistMonoFontFamily = FontFamily(
    Font(R.font.geist_mono_variable, FontWeight.Normal),
    Font(R.font.geist_mono_variable, FontWeight.Medium),
)

private fun lumenTypography(colors: LumenThemeColors) = Typography(
    titleLarge = TextStyle(
        fontFamily = GeistFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
        color = colors.onBackground,
    ),
    titleMedium = TextStyle(
        fontFamily = GeistFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
        color = colors.onBackground,
    ),
    bodyLarge = TextStyle(
        fontFamily = GeistFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
        color = colors.onBackground,
    ),
    bodyMedium = TextStyle(
        fontFamily = GeistFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
        color = colors.accent,
    ),
    bodySmall = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
        color = colors.onSurfaceMuted,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
        color = colors.onBackground,
    ),
)

private fun lumenColorScheme(colors: LumenThemeColors) = darkColorScheme(
    primary = colors.accent,
    onPrimary = colors.background,
    background = colors.background,
    onBackground = colors.onBackground,
    surface = colors.surface,
    onSurface = colors.onBackground,
    surfaceContainerHigh = colors.surface,
    onSurfaceVariant = colors.onSurfaceMuted,
    outline = LumenSheetBorder,
)

@Composable
fun LocalVideoPlayerTheme(
    colors: LumenThemeColors = LumenThemeColors.default(),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(LocalLumenTheme provides colors) {
        MaterialTheme(
            colorScheme = lumenColorScheme(colors),
            typography = lumenTypography(colors),
            content = content,
        )
    }
}

@Deprecated("Use LumenBackground", ReplaceWith("LumenBackground"))
val IosBackground = LumenBackground

@Deprecated("Use LumenSurface", ReplaceWith("LumenSurface"))
val IosSurface = LumenSurface

@Deprecated("Use LumenSheetBorder", ReplaceWith("LumenSheetBorder"))
val IosDivider = LumenSheetBorder

@Deprecated("Use LumenPrimary", ReplaceWith("LumenPrimary"))
val IosBlue = LumenPrimary

@Deprecated("Use LumenOnSurfaceMuted", ReplaceWith("LumenOnSurfaceMuted"))
val IosSecondaryText = LumenOnSurfaceMuted
