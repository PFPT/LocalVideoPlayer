package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.bookmarkDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "video_bookmarks",
)

const val BOOKMARK_LIMIT = 20

fun updateBookmarks(existing: List<Long>, positionMs: Long, limit: Int = BOOKMARK_LIMIT): List<Long> {
    val position = positionMs.coerceAtLeast(0L)
    val merged = existing.filter { it != position } + position
    if (merged.size <= limit) return merged.sorted()
    val kept = merged.filter { it != position }.sorted().takeLast(limit - 1)
    return (kept + position).sorted()
}

fun decodeBookmarks(raw: String?): List<Long> {
    if (raw.isNullOrBlank()) return emptyList()
    return raw.split(',').mapNotNull { it.toLongOrNull() }.distinct().sorted()
}

fun encodeBookmarks(positions: List<Long>): String {
    return positions.joinToString(",")
}

class BookmarkStore(private val context: Context) {
    fun bookmarks(uri: Uri): Flow<List<Long>> {
        val key = bookmarkKey(uri.toString())
        return context.bookmarkDataStore.data.map { prefs -> decodeBookmarks(prefs[key]) }
    }

    suspend fun add(uri: Uri, positionMs: Long) {
        val key = bookmarkKey(uri.toString())
        context.bookmarkDataStore.edit { prefs ->
            prefs[key] = encodeBookmarks(updateBookmarks(decodeBookmarks(prefs[key]), positionMs))
        }
    }

    suspend fun remove(uri: Uri, positionMs: Long) {
        val key = bookmarkKey(uri.toString())
        context.bookmarkDataStore.edit { prefs ->
            val next = decodeBookmarks(prefs[key]).filter { it != positionMs }
            if (next.isEmpty()) prefs.remove(key) else prefs[key] = encodeBookmarks(next)
        }
    }

    private fun bookmarkKey(uri: String) = stringPreferencesKey("bm_$uri")
}
