package com.pfpt.localvideoplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.seriesSkipDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "series_skip",
)

data class SeriesSkipSettings(
    val introSeconds: Int? = null,
    val outroSeconds: Int? = null,
) {
    val hasAny: Boolean get() = introSeconds != null || outroSeconds != null
}

class SeriesSkipStore(private val context: Context) {

    fun observe(seriesKey: String?): Flow<SeriesSkipSettings> {
        return context.seriesSkipDataStore.data.map { prefs ->
            if (seriesKey.isNullOrBlank()) {
                SeriesSkipSettings()
            } else {
                prefs.toSettings(seriesKey)
            }
        }
    }

    suspend fun get(seriesKey: String): SeriesSkipSettings {
        return context.seriesSkipDataStore.data.first().toSettings(seriesKey)
    }

    suspend fun setIntroSeconds(seriesKey: String, seconds: Int) {
        context.seriesSkipDataStore.edit { prefs ->
            prefs[introKey(seriesKey)] = seconds.coerceIn(0, 600)
        }
    }

    suspend fun setOutroSeconds(seriesKey: String, seconds: Int) {
        context.seriesSkipDataStore.edit { prefs ->
            prefs[outroKey(seriesKey)] = seconds.coerceIn(0, 600)
        }
    }

    suspend fun clearIntro(seriesKey: String) {
        context.seriesSkipDataStore.edit { prefs ->
            prefs.remove(introKey(seriesKey))
        }
    }

    suspend fun clearOutro(seriesKey: String) {
        context.seriesSkipDataStore.edit { prefs ->
            prefs.remove(outroKey(seriesKey))
        }
    }

    suspend fun clear(seriesKey: String) {
        context.seriesSkipDataStore.edit { prefs ->
            prefs.remove(introKey(seriesKey))
            prefs.remove(outroKey(seriesKey))
        }
    }

    private fun Preferences.toSettings(seriesKey: String): SeriesSkipSettings {
        return SeriesSkipSettings(
            introSeconds = this[introKey(seriesKey)],
            outroSeconds = this[outroKey(seriesKey)],
        )
    }

    companion object {
        private const val INTRO_PREFIX = "ssi:"
        private const val OUTRO_PREFIX = "sso:"

        private fun introKey(seriesKey: String) = intPreferencesKey(INTRO_PREFIX + seriesKey)
        private fun outroKey(seriesKey: String) = intPreferencesKey(OUTRO_PREFIX + seriesKey)
    }
}
