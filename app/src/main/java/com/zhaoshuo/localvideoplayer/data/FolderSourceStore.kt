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

private val Context.folderSourceDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "folder_sources",
)

data class FolderSource(
    val treeUri: String,
    val name: String,
)

class FolderSourceStore(private val context: Context) {

    val sources: Flow<List<FolderSource>> = context.folderSourceDataStore.data.map { prefs ->
        (prefs[SOURCES] ?: emptySet()).mapNotNull(::decode).sortedBy { it.name.lowercase() }
    }

    suspend fun getSources(): List<FolderSource> = sources.first()

    suspend fun add(treeUri: Uri, name: String) {
        val encoded = encode(treeUri.toString(), name.ifBlank { treeUri.lastPathSegment.orEmpty() })
        context.folderSourceDataStore.edit { prefs ->
            val current = prefs[SOURCES] ?: emptySet()
            val without = current.filterNot { decode(it)?.treeUri == treeUri.toString() }.toSet()
            prefs[SOURCES] = without + encoded
        }
    }

    suspend fun remove(treeUri: String) {
        context.folderSourceDataStore.edit { prefs ->
            val current = prefs[SOURCES] ?: emptySet()
            prefs[SOURCES] = current.filterNot { decode(it)?.treeUri == treeUri }.toSet()
        }
    }

    companion object {
        private val SOURCES = stringSetPreferencesKey("sources")
        private const val SEPARATOR = "|||"

        private fun encode(uri: String, name: String): String = "$uri$SEPARATOR$name"

        private fun decode(value: String): FolderSource? {
            val parts = value.split(SEPARATOR, limit = 2)
            if (parts.isEmpty() || parts[0].isBlank()) return null
            val name = parts.getOrNull(1)?.ifBlank { null } ?: parts[0].substringAfterLast('/')
            return FolderSource(treeUri = parts[0], name = name)
        }
    }
}
