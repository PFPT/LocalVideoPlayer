package com.zhaoshuo.localvideoplayer.download

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.first

private val Context.downloadTitleDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "download_title_cache",
)

object DownloadTitleCache {
    private fun seriesKey(stemPrefix: String): String = "series:$stemPrefix"

    suspend fun get(context: Context, cdnKey: String): String? {
        if (cdnKey.isBlank()) return null
        return context.downloadTitleDataStore.data.first()[stringPreferencesKey(cdnKey)]
    }

    suspend fun getSeries(context: Context, stemPrefix: String): String? {
        if (stemPrefix.isBlank()) return null
        return context.downloadTitleDataStore.data.first()[stringPreferencesKey(seriesKey(stemPrefix))]
    }

    suspend fun put(context: Context, cdnKey: String, title: String) {
        val trimmed = title.trim()
        if (cdnKey.isBlank() || trimmed.isBlank()) return
        context.downloadTitleDataStore.edit { prefs ->
            prefs[stringPreferencesKey(cdnKey)] = trimmed
        }
    }

    suspend fun putSeries(context: Context, stemPrefix: String, showName: String) {
        val trimmed = showName.trim()
        if (stemPrefix.isBlank() || trimmed.isBlank()) return
        context.downloadTitleDataStore.edit { prefs ->
            prefs[stringPreferencesKey(seriesKey(stemPrefix))] = trimmed
        }
    }
}
