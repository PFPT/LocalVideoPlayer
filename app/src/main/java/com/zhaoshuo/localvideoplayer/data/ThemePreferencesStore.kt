package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhaoshuo.localvideoplayer.ui.theme.LauncherIcon
import com.zhaoshuo.localvideoplayer.ui.theme.LumenThemeColors
import com.zhaoshuo.localvideoplayer.ui.theme.ThemeMode
import com.zhaoshuo.localvideoplayer.ui.theme.ThemeState
import com.zhaoshuo.localvideoplayer.ui.theme.toArgbInt
import com.zhaoshuo.localvideoplayer.ui.theme.toComposeColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_preferences",
)

class ThemePreferencesStore(private val context: Context) {

    val themeState: Flow<ThemeState> = context.themeDataStore.data.map { prefs ->
        val mode = ThemeMode.fromStorage(prefs[THEME_MODE_KEY])
        val defaults = LumenThemeColors.defaults(mode)
        ThemeState(
            mode = mode,
            colors = defaults.copy(
                background = (prefs[BACKGROUND_COLOR_KEY] ?: defaults.background.toArgbInt()).toComposeColor(),
                onBackground = (prefs[TEXT_COLOR_KEY] ?: defaults.onBackground.toArgbInt()).toComposeColor(),
                surface = (prefs[LIST_OPTION_COLOR_KEY] ?: defaults.surface.toArgbInt()).toComposeColor(),
            ),
        )
    }

    val themeColors: Flow<LumenThemeColors> = themeState.map { it.colors }

    val launcherIcon: Flow<LauncherIcon> = context.themeDataStore.data.map { prefs ->
        LauncherIcon.fromId(prefs[LAUNCHER_ICON_KEY])
    }

    suspend fun getLauncherIcon(): LauncherIcon = launcherIcon.first()

    suspend fun setLauncherIcon(icon: LauncherIcon) {
        context.themeDataStore.edit { prefs ->
            prefs[LAUNCHER_ICON_KEY] = icon.id
        }
    }

    suspend fun setThemeMode(mode: ThemeMode) {
        val defaults = LumenThemeColors.defaults(mode)
        context.themeDataStore.edit { prefs ->
            prefs[THEME_MODE_KEY] = mode.name
            prefs[BACKGROUND_COLOR_KEY] = defaults.background.toArgbInt()
            prefs[TEXT_COLOR_KEY] = defaults.onBackground.toArgbInt()
            prefs[LIST_OPTION_COLOR_KEY] = defaults.surface.toArgbInt()
        }
    }

    suspend fun setBackgroundColor(color: Color) {
        context.themeDataStore.edit { prefs ->
            prefs[BACKGROUND_COLOR_KEY] = color.toArgbInt()
        }
    }

    suspend fun setTextColor(color: Color) {
        context.themeDataStore.edit { prefs ->
            prefs[TEXT_COLOR_KEY] = color.toArgbInt()
        }
    }

    suspend fun setListOptionColor(color: Color) {
        context.themeDataStore.edit { prefs ->
            prefs[LIST_OPTION_COLOR_KEY] = color.toArgbInt()
        }
    }

    suspend fun saveThemeColors(colors: LumenThemeColors) {
        context.themeDataStore.edit { prefs ->
            prefs[BACKGROUND_COLOR_KEY] = colors.background.toArgbInt()
            prefs[TEXT_COLOR_KEY] = colors.onBackground.toArgbInt()
            prefs[LIST_OPTION_COLOR_KEY] = colors.surface.toArgbInt()
        }
    }

    suspend fun resetAll() {
        context.themeDataStore.edit { prefs ->
            val mode = ThemeMode.fromStorage(prefs[THEME_MODE_KEY])
            val defaults = LumenThemeColors.defaults(mode)
            prefs[BACKGROUND_COLOR_KEY] = defaults.background.toArgbInt()
            prefs[TEXT_COLOR_KEY] = defaults.onBackground.toArgbInt()
            prefs[LIST_OPTION_COLOR_KEY] = defaults.surface.toArgbInt()
        }
    }

    companion object {
        private val THEME_MODE_KEY = stringPreferencesKey("theme_mode")
        private val LAUNCHER_ICON_KEY = stringPreferencesKey("launcher_icon_id")
        private val BACKGROUND_COLOR_KEY = intPreferencesKey("background_color_argb")
        private val TEXT_COLOR_KEY = intPreferencesKey("text_color_argb")
        private val LIST_OPTION_COLOR_KEY = intPreferencesKey("list_option_color_argb")
    }
}
