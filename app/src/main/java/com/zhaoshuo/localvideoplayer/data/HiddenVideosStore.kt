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

data class HiddenVideoEntry(
    val uri: String,
    val title: String,
)

private const val HIDDEN_FIELD_SEPARATOR = "\u0001"

fun encodeHiddenEntry(uri: String, title: String): String {
    val safeTitle = title.replace(HIDDEN_FIELD_SEPARATOR, " ").replace("\n", " ").trim()
    return uri + HIDDEN_FIELD_SEPARATOR + safeTitle
}

fun decodeHiddenEntry(raw: String): HiddenVideoEntry {
    val separator = raw.indexOf(HIDDEN_FIELD_SEPARATOR)
    if (separator < 0) return HiddenVideoEntry(raw, "")
    return HiddenVideoEntry(raw.substring(0, separator), raw.substring(separator + 1))
}

fun hiddenDisplayLabel(entry: HiddenVideoEntry): String {
    val title = entry.title.trim()
    if (title.isNotEmpty()) return title
    val tail = entry.uri.substringAfterLast('/').substringAfterLast(':')
    return tail.ifBlank { entry.uri }
}

class HiddenVideosStore(private val context: Context) {

    val hiddenEntries: Flow<List<HiddenVideoEntry>> = context.hiddenVideosDataStore.data.map { prefs ->
        (prefs[HIDDEN_URIS_KEY] ?: emptySet())
            .map(::decodeHiddenEntry)
            .sortedBy { hiddenDisplayLabel(it).lowercase() }
    }

    val hiddenUris: Flow<Set<String>> = hiddenEntries.map { entries ->
        entries.map { it.uri }.toSet()
    }

    suspend fun getHiddenUris(): Set<String> {
        return hiddenUris.first()
    }

    suspend fun hide(uri: Uri, title: String = "") {
        hideAll(listOf(uri), mapOf(uri.toString() to title))
    }

    suspend fun hideAll(uris: Collection<Uri>, titles: Map<String, String> = emptyMap()) {
        if (uris.isEmpty()) return
        context.hiddenVideosDataStore.edit { prefs ->
            val current = prefs[HIDDEN_URIS_KEY]?.toMutableSet() ?: mutableSetOf()
            uris.forEach { uri ->
                val key = uri.toString()
                current.removeAll { decodeHiddenEntry(it).uri == key }
                current.add(encodeHiddenEntry(key, titles[key].orEmpty()))
            }
            prefs[HIDDEN_URIS_KEY] = current
        }
    }

    suspend fun unhide(uri: Uri) {
        unhideAll(listOf(uri))
    }

    suspend fun unhideAll(uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        val remove = uris.map { it.toString() }.toSet()
        context.hiddenVideosDataStore.edit { prefs ->
            val current = prefs[HIDDEN_URIS_KEY] ?: return@edit
            prefs[HIDDEN_URIS_KEY] = current.filterNot { decodeHiddenEntry(it).uri in remove }.toSet()
        }
    }

    companion object {
        private val HIDDEN_URIS_KEY = stringSetPreferencesKey("hidden_uris")
    }
}
