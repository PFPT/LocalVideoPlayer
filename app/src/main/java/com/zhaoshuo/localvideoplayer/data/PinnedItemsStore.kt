package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringSetPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.pinnedItemsDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "pinned_items",
)

class PinnedItemsStore(private val context: Context) {

    val pinnedKeys: Flow<Set<String>> = context.pinnedItemsDataStore.data.map { prefs ->
        prefs[PINNED_KEYS] ?: emptySet()
    }

    suspend fun getPinnedKeys(): Set<String> {
        return pinnedKeys.first()
    }

    suspend fun toggle(key: String) {
        context.pinnedItemsDataStore.edit { prefs ->
            val current = prefs[PINNED_KEYS]?.toMutableSet() ?: mutableSetOf()
            if (!current.add(key)) {
                current.remove(key)
            }
            prefs[PINNED_KEYS] = current
        }
    }

    suspend fun removeKeys(keys: Collection<String>) {
        if (keys.isEmpty()) return
        context.pinnedItemsDataStore.edit { prefs ->
            val current = prefs[PINNED_KEYS]?.toMutableSet() ?: return@edit
            keys.forEach { current.remove(it) }
            prefs[PINNED_KEYS] = current
        }
    }

    suspend fun removeKeysForUris(uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        val uriKeys = uris.map { uri -> "single:$uri" }.toSet()
        removeKeys(uriKeys)
    }

    suspend fun removeSeriesKeys(seriesIds: Collection<String>) {
        if (seriesIds.isEmpty()) return
        removeKeys(seriesIds.map { "series:$it" })
    }

    companion object {
        private val PINNED_KEYS = stringSetPreferencesKey("pinned_keys")
    }
}
