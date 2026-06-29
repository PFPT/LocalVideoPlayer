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

private val Context.hiddenVideosDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "hidden_videos",
)

class HiddenVideosStore(private val context: Context) {

    val hiddenUris: Flow<Set<String>> = context.hiddenVideosDataStore.data.map { prefs ->
        prefs[HIDDEN_URIS_KEY] ?: emptySet()
    }

    suspend fun getHiddenUris(): Set<String> {
        return hiddenUris.first()
    }

    suspend fun hide(uri: Uri) {
        hideAll(listOf(uri))
    }

    suspend fun hideAll(uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        context.hiddenVideosDataStore.edit { prefs ->
            val current = prefs[HIDDEN_URIS_KEY]?.toMutableSet() ?: mutableSetOf()
            uris.forEach { current.add(it.toString()) }
            prefs[HIDDEN_URIS_KEY] = current
        }
    }

    suspend fun unhide(uri: Uri) {
        unhideAll(listOf(uri))
    }

    suspend fun unhideAll(uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        context.hiddenVideosDataStore.edit { prefs ->
            val current = prefs[HIDDEN_URIS_KEY]?.toMutableSet() ?: return@edit
            uris.forEach { current.remove(it.toString()) }
            prefs[HIDDEN_URIS_KEY] = current
        }
    }

    companion object {
        private val HIDDEN_URIS_KEY = stringSetPreferencesKey("hidden_uris")
    }
}
