package com.zhaoshuo.localvideoplayer.ui.theme

import android.os.Build
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.ColorScheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Typography
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
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

private fun staticLumenColorScheme(isDark: Boolean, colors: LumenThemeColors): ColorScheme {
    return if (isDark) {
        darkColorScheme(
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
    } else {
        lightColorScheme(
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
}

private fun Color.toArgbComparable(): Int {
    return android.graphics.Color.argb(
        (alpha * 255).toInt().coerceIn(0, 255),
        (red * 255).toInt().coerceIn(0, 255),
        (green * 255).toInt().coerceIn(0, 255),
        (blue * 255).toInt().coerceIn(0, 255),
    )
}

private fun hasCustomColors(colors: LumenThemeColors, defaults: LumenThemeColors): Boolean {
    return colors.background.toArgbComparable() != defaults.background.toArgbComparable() ||
        colors.onBackground.toArgbComparable() != defaults.onBackground.toArgbComparable() ||
        colors.surface.toArgbComparable() != defaults.surface.toArgbComparable()
}

private fun lumenFromScheme(scheme: ColorScheme, base: LumenThemeColors): LumenThemeColors {
    return base.copy(
        background = scheme.background,
        onBackground = scheme.onBackground,
        surface = scheme.surface,
        onSurfaceMuted = scheme.onSurfaceVariant,
        accent = scheme.primary,
        glassFill = scheme.surfaceVariant.copy(alpha = base.glassFill.alpha),
        glassBorder = scheme.outline.copy(alpha = base.glassBorder.alpha),
        sheetBg = scheme.surfaceContainerHigh,
        sheetBorder = scheme.outline.copy(alpha = base.sheetBorder.alpha),
    )
}

@Composable
fun LocalVideoPlayerTheme(
    mode: ThemeMode = ThemeMode.System,
    colors: LumenThemeColors = LumenThemeColors.defaults(mode),
    content: @Composable () -> Unit,
) {
    val context = LocalContext.current
    val isSystemDark = isSystemInDarkTheme()
    val isDark = mode.resolveDark(isSystemDark)
    val appearanceDefaults = LumenThemeColors.defaultsForDark(isDark)
    val customized = hasCustomColors(colors, appearanceDefaults)

    val baseScheme = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        if (isDark) dynamicDarkColorScheme(context) else dynamicLightColorScheme(context)
    } else {
        staticLumenColorScheme(isDark, appearanceDefaults)
    }

    val effectiveLumen = if (customized) {
        colors.copy(
            accent = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                baseScheme.primary
            } else {
                colors.accent
            },
            onSurfaceMuted = appearanceDefaults.onSurfaceMuted,
            glassFill = appearanceDefaults.glassFill,
            glassBorder = appearanceDefaults.glassBorder,
            sheetBg = appearanceDefaults.sheetBg,
            sheetBorder = appearanceDefaults.sheetBorder,
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        lumenFromScheme(baseScheme, appearanceDefaults)
    } else {
        appearanceDefaults
    }

    val colorScheme = if (customized) {
        baseScheme.copy(
            primary = effectiveLumen.accent,
            onPrimary = contrastingOnColor(effectiveLumen.accent),
            background = effectiveLumen.background,
            onBackground = effectiveLumen.onBackground,
            surface = effectiveLumen.surface,
            onSurface = effectiveLumen.onBackground,
            surfaceVariant = effectiveLumen.surface,
            onSurfaceVariant = effectiveLumen.onSurfaceMuted,
            surfaceContainerHigh = effectiveLumen.surface,
            outline = effectiveLumen.sheetBorder,
        )
    } else if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
        baseScheme
    } else {
        staticLumenColorScheme(isDark, effectiveLumen)
    }

    CompositionLocalProvider(
        LocalLumenTheme provides effectiveLumen,
        LocalThemeMode provides mode,
    ) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = lumenTypography,
            content = content,
        )
    }
}
