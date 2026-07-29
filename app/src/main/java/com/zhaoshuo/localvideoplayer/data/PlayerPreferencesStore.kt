package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playerPreferencesDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "player_preferences",
)

data class PlayerPreferences(
    val softDecode: Boolean = false,
    val skipIntroSeconds: Int = 0,
    val skipOutroSeconds: Int = 0,
    val shuffleEnabled: Boolean = false,
)

class PlayerPreferencesStore(private val context: Context) {

    val preferences: Flow<PlayerPreferences> = context.playerPreferencesDataStore.data.map { prefs ->
        PlayerPreferences(
            softDecode = prefs[SOFT_DECODE] ?: false,
            skipIntroSeconds = (prefs[SKIP_INTRO_SECONDS] ?: 0).coerceIn(0, 600),
            skipOutroSeconds = (prefs[SKIP_OUTRO_SECONDS] ?: 0).coerceIn(0, 600),
            shuffleEnabled = prefs[SHUFFLE_ENABLED] ?: false,
        )
    }

    suspend fun getPreferences(): PlayerPreferences = preferences.first()

    suspend fun setSoftDecode(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[SOFT_DECODE] = enabled }
    }

    suspend fun setSkipIntroSeconds(seconds: Int) {
        context.playerPreferencesDataStore.edit {
            it[SKIP_INTRO_SECONDS] = seconds.coerceIn(0, 600)
        }
    }

    suspend fun setSkipOutroSeconds(seconds: Int) {
        context.playerPreferencesDataStore.edit {
            it[SKIP_OUTRO_SECONDS] = seconds.coerceIn(0, 600)
        }
    }

    suspend fun setShuffleEnabled(enabled: Boolean) {
        context.playerPreferencesDataStore.edit { it[SHUFFLE_ENABLED] = enabled }
    }

    companion object {
        private val SOFT_DECODE = booleanPreferencesKey("soft_decode")
        private val SKIP_INTRO_SECONDS = intPreferencesKey("skip_intro_seconds")
        private val SKIP_OUTRO_SECONDS = intPreferencesKey("skip_outro_seconds")
        private val SHUFFLE_ENABLED = booleanPreferencesKey("shuffle_enabled")
    }
}
