package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.playCountDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "play_counts",
)

class PlayCountStore(private val context: Context) {

    val playCounts: Flow<Map<String, Int>> = context.playCountDataStore.data.map { prefs ->
        prefs.asMap().mapNotNull { (key, value) ->
            if (!key.name.startsWith(PREFIX)) return@mapNotNull null
            val entryKey = key.name.removePrefix(PREFIX)
            if (value is Int) entryKey to value else null
        }.toMap()
    }

    suspend fun increment(key: String) {
        context.playCountDataStore.edit { prefs ->
            val prefKey = intPreferencesKey(PREFIX + key)
            prefs[prefKey] = (prefs[prefKey] ?: 0) + 1
        }
    }

    suspend fun removeForUris(uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        context.playCountDataStore.edit { prefs ->
            uris.forEach { uri ->
                prefs.remove(intPreferencesKey(PREFIX + "single:$uri"))
            }
        }
    }

    suspend fun removeKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        context.playCountDataStore.edit { prefs ->
            keys.forEach { key ->
                prefs.remove(intPreferencesKey(PREFIX + key))
            }
        }
    }

    companion object {
        private const val PREFIX = "pc:"
    }
}
