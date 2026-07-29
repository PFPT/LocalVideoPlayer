package com.zhaoshuo.localvideoplayer.ui.theme

import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
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

private val lumenTypography = Typography(
    titleLarge = TextStyle(
        fontFamily = GeistFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 17.sp,
    ),
    titleMedium = TextStyle(
        fontFamily = GeistFontFamily,
        fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp,
    ),
    bodyLarge = TextStyle(
        fontFamily = GeistFontFamily,
        fontWeight = FontWeight.Medium,
        fontSize = 13.sp,
    ),
    bodyMedium = TextStyle(
        fontFamily = GeistFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 13.sp,
    ),
    bodySmall = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 12.sp,
    ),
    labelSmall = TextStyle(
        fontFamily = GeistMonoFontFamily,
        fontWeight = FontWeight.Normal,
        fontSize = 10.sp,
    ),
)

private fun contrastingOnColor(background: Color): Color {
    val luminance = 0.2126f * background.red + 0.7152f * background.green + 0.0722f * background.blue
    return if (luminance > 0.5f) Color.Black else Color.White
}

private fun lumenColorScheme(mode: ThemeMode, colors: LumenThemeColors) = when (mode) {
    ThemeMode.Dark -> darkColorScheme(
        primary = colors.accent,
        onPrimary = contrastingOnColor(colors.accent),
        primaryContainer = colors.accent.copy(alpha = 0.18f),
        onPrimaryContainer = colors.accent,
        secondaryContainer = colors.surface,
        onSecondaryContainer = colors.onBackground,
        tertiaryContainer = colors.accent.copy(alpha = 0.12f),
        onTertiaryContainer = colors.accent,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = colors.surface,
        onSurface = colors.onBackground,
        surfaceVariant = colors.surface,
        onSurfaceVariant = colors.onSurfaceMuted,
        surfaceContainerHigh = colors.surface,
        outline = colors.sheetBorder,
        error = LumenDelete,
        errorContainer = LumenDelete.copy(alpha = 0.18f),
        onErrorContainer = LumenDelete,
    )
    ThemeMode.Light -> lightColorScheme(
        primary = colors.accent,
        onPrimary = contrastingOnColor(colors.accent),
        primaryContainer = colors.accent.copy(alpha = 0.12f),
        onPrimaryContainer = colors.accent,
        secondaryContainer = colors.glassFill,
        onSecondaryContainer = colors.onBackground,
        tertiaryContainer = colors.accent.copy(alpha = 0.08f),
        onTertiaryContainer = colors.accent,
        background = colors.background,
        onBackground = colors.onBackground,
        surface = colors.surface,
        onSurface = colors.onBackground,
        surfaceVariant = colors.glassFill,
        onSurfaceVariant = colors.onSurfaceMuted,
        surfaceContainerHigh = colors.surface,
        outline = colors.sheetBorder,
        error = LumenDelete,
        errorContainer = LumenDelete.copy(alpha = 0.12f),
        onErrorContainer = LumenDelete,
    )
}

@Composable
fun LocalVideoPlayerTheme(
    mode: ThemeMode = ThemeMode.Dark,
    colors: LumenThemeColors = LumenThemeColors.defaults(mode),
    content: @Composable () -> Unit,
) {
    CompositionLocalProvider(
        LocalLumenTheme provides colors,
        LocalThemeMode provides mode,
    ) {
        MaterialTheme(
            colorScheme = lumenColorScheme(mode, colors),
            typography = lumenTypography,
            content = content,
        )
    }
}
