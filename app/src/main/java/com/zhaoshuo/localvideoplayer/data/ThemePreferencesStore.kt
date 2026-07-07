package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import androidx.compose.ui.graphics.Color
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhaoshuo.localvideoplayer.ui.theme.LumenBackground
import com.zhaoshuo.localvideoplayer.ui.theme.LumenOnBackground
import com.zhaoshuo.localvideoplayer.ui.theme.LumenOnSurfaceMuted
import com.zhaoshuo.localvideoplayer.ui.theme.LumenSurface
import com.zhaoshuo.localvideoplayer.ui.theme.LumenThemeColors
import com.zhaoshuo.localvideoplayer.ui.theme.toArgbInt
import com.zhaoshuo.localvideoplayer.ui.theme.toComposeColor
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.themeDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "theme_preferences",
)

class ThemePreferencesStore(private val context: Context) {

    val themeColors: Flow<LumenThemeColors> = context.themeDataStore.data.map { prefs ->
        LumenThemeColors(
            background = (prefs[BACKGROUND_COLOR_KEY] ?: DEFAULT_BACKGROUND_ARGB).toComposeColor(),
            onBackground = (prefs[TEXT_COLOR_KEY] ?: DEFAULT_TEXT_ARGB).toComposeColor(),
            surface = (prefs[LIST_OPTION_COLOR_KEY] ?: DEFAULT_LIST_OPTION_ARGB).toComposeColor(),
            onSurfaceMuted = LumenOnSurfaceMuted,
            accent = LumenThemeColors.default().accent,
        )
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
        saveThemeColors(LumenThemeColors.default())
    }

    companion object {
        private val BACKGROUND_COLOR_KEY = intPreferencesKey("background_color_argb")
        private val TEXT_COLOR_KEY = intPreferencesKey("text_color_argb")
        private val LIST_OPTION_COLOR_KEY = intPreferencesKey("list_option_color_argb")

        private val DEFAULT_BACKGROUND_ARGB = LumenBackground.toArgbInt()
        private val DEFAULT_TEXT_ARGB = LumenOnBackground.toArgbInt()
        private val DEFAULT_LIST_OPTION_ARGB = LumenSurface.toArgbInt()
    }
}
