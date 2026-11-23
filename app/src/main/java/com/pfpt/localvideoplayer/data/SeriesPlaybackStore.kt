package com.pfpt.localvideoplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.floatPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pfpt.localvideoplayer.player.SERIES_TEXT_OFF
import com.pfpt.localvideoplayer.player.SeriesPlaybackSettings
import com.pfpt.localvideoplayer.player.normalizeSeriesSpeed
import com.pfpt.localvideoplayer.player.textMemoryFromStorage
import com.pfpt.localvideoplayer.player.usableTrackLanguage
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.seriesPlaybackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "series_playback",
)

class SeriesPlaybackStore(private val context: Context) {

    fun observe(seriesKey: String?): Flow<SeriesPlaybackSettings> {
        return context.seriesPlaybackDataStore.data.map { prefs ->
            if (seriesKey.isNullOrBlank()) {
                SeriesPlaybackSettings()
            } else {
                prefs.toSettings(seriesKey)
            }
        }
    }

    suspend fun setAudioLanguage(seriesKey: String, language: String) {
        val code = usableTrackLanguage(language) ?: return
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs[audioKey(seriesKey)] = code
        }
    }

    suspend fun clearAudioLanguage(seriesKey: String) {
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs.remove(audioKey(seriesKey))
        }
    }

    suspend fun setTextLanguage(seriesKey: String, language: String) {
        val code = usableTrackLanguage(language) ?: return
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs[textKey(seriesKey)] = code
        }
    }

    suspend fun setTextOff(seriesKey: String) {
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs[textKey(seriesKey)] = SERIES_TEXT_OFF
        }
    }

    suspend fun clearText(seriesKey: String) {
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs.remove(textKey(seriesKey))
        }
    }

    suspend fun setSpeed(seriesKey: String, speed: Float) {
        if (seriesKey.isBlank()) return
        context.seriesPlaybackDataStore.edit { prefs ->
            prefs[speedKey(seriesKey)] = normalizeSeriesSpeed(speed)
        }
    }

    private fun Preferences.toSettings(seriesKey: String): SeriesPlaybackSettings {
        return SeriesPlaybackSettings(
            audioLanguage = usableTrackLanguage(this[audioKey(seriesKey)]),
            text = textMemoryFromStorage(this[textKey(seriesKey)]),
            speed = this[speedKey(seriesKey)]?.let(::normalizeSeriesSpeed),
        )
    }

    companion object {
        private const val AUDIO_PREFIX = "spa:"
        private const val TEXT_PREFIX = "spt:"
        private const val SPEED_PREFIX = "sps:"

        private fun audioKey(seriesKey: String) = stringPreferencesKey(AUDIO_PREFIX + seriesKey)
        private fun textKey(seriesKey: String) = stringPreferencesKey(TEXT_PREFIX + seriesKey)
        private fun speedKey(seriesKey: String) = floatPreferencesKey(SPEED_PREFIX + seriesKey)
    }
}