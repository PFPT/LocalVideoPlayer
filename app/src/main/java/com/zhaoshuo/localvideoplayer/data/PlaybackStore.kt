package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhaoshuo.localvideoplayer.model.PlaybackRecord
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback_records",
)

class PlaybackStore(private val context: Context) {

    val recentRecords: Flow<List<PlaybackRecord>> = context.playbackDataStore.data.map { prefs ->
        prefs.asMap()
            .entries
            .mapNotNull { (key, value) ->
                if (!key.name.startsWith(PREFIX_URI)) return@mapNotNull null
                val uriString = key.name.removePrefix(PREFIX_URI)
                val title = prefs[stringPreferencesKey(PREFIX_TITLE + uriString)] ?: return@mapNotNull null
                val positionMs = prefs[longPreferencesKey(PREFIX_POSITION + uriString)] ?: 0L
                val durationMs = prefs[longPreferencesKey(PREFIX_DURATION + uriString)] ?: 0L
                val lastPlayedAt = prefs[longPreferencesKey(PREFIX_LAST_PLAYED + uriString)] ?: 0L
                PlaybackRecord(
                    uri = Uri.parse(uriString),
                    title = title,
                    positionMs = positionMs,
                    durationMs = durationMs,
                    lastPlayedAt = lastPlayedAt,
                )
            }
            .sortedByDescending { it.lastPlayedAt }
            .take(MAX_RECENT_COUNT)
    }

    suspend fun getRecord(uri: Uri): PlaybackRecord? {
        val uriString = uri.toString()
        val prefs = context.playbackDataStore.data.first()
        val title = prefs[stringPreferencesKey(PREFIX_TITLE + uriString)] ?: return null
        return PlaybackRecord(
            uri = uri,
            title = title,
            positionMs = prefs[longPreferencesKey(PREFIX_POSITION + uriString)] ?: 0L,
            durationMs = prefs[longPreferencesKey(PREFIX_DURATION + uriString)] ?: 0L,
            lastPlayedAt = prefs[longPreferencesKey(PREFIX_LAST_PLAYED + uriString)] ?: 0L,
        )
    }

    suspend fun getResumePositionMs(uri: Uri, durationMs: Long = 0L): Long {
        val record = getRecord(uri) ?: return 0L
        val effectiveDuration = if (durationMs > 0) durationMs else record.durationMs
        if (effectiveDuration > 0) {
            val updated = record.copy(durationMs = effectiveDuration)
            return if (updated.isResumable) updated.positionMs else 0L
        }
        return if (record.isResumable) record.positionMs else 0L
    }

    suspend fun saveProgress(
        uri: Uri,
        title: String,
        positionMs: Long,
        durationMs: Long,
    ) {
        val uriString = uri.toString()
        val now = System.currentTimeMillis()
        context.playbackDataStore.edit { prefs ->
            prefs[stringPreferencesKey(PREFIX_URI + uriString)] = uriString
            prefs[stringPreferencesKey(PREFIX_TITLE + uriString)] = title
            prefs[longPreferencesKey(PREFIX_POSITION + uriString)] = positionMs.coerceAtLeast(0L)
            prefs[longPreferencesKey(PREFIX_DURATION + uriString)] = durationMs.coerceAtLeast(0L)
            prefs[longPreferencesKey(PREFIX_LAST_PLAYED + uriString)] = now
        }
    }

    suspend fun removeRecord(uri: Uri) {
        val uriString = uri.toString()
        context.playbackDataStore.edit { prefs ->
            prefs.remove(stringPreferencesKey(PREFIX_URI + uriString))
            prefs.remove(stringPreferencesKey(PREFIX_TITLE + uriString))
            prefs.remove(longPreferencesKey(PREFIX_POSITION + uriString))
            prefs.remove(longPreferencesKey(PREFIX_DURATION + uriString))
            prefs.remove(longPreferencesKey(PREFIX_LAST_PLAYED + uriString))
        }
    }

    suspend fun removeRecords(uris: Collection<Uri>) {
        uris.forEach { removeRecord(it) }
    }

    companion object {
        private const val PREFIX_URI = "uri_"
        private const val PREFIX_TITLE = "title_"
        private const val PREFIX_POSITION = "pos_"
        private const val PREFIX_DURATION = "dur_"
        private const val PREFIX_LAST_PLAYED = "last_"
        private const val MAX_RECENT_COUNT = 15
    }
}
