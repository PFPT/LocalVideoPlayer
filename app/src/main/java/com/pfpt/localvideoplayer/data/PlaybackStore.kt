package com.pfpt.localvideoplayer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.pfpt.localvideoplayer.model.PlaybackRecord
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn

private val Context.playbackDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "playback_records",
)

data class PlaybackSnapshotView(
    val recent: List<PlaybackRecord>,
    val records: Map<String, PlaybackRecord>,
)

class PlaybackStore(private val context: Context) {
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    val snapshot: StateFlow<PlaybackSnapshotView> = flow {
        ensureRecentIndex()
        emitAll(
            context.playbackDataStore.data.map { prefs ->
                playbackSnapshotView(recordsFromPrefs(prefs), recentUriOrder(prefs))
            },
        )
    }.stateIn(
        scope,
        SharingStarted.Eagerly,
        PlaybackSnapshotView(emptyList(), emptyMap()),
    )

    val recentRecords: Flow<List<PlaybackRecord>> = snapshot.map { it.recent }

    fun progressOf(uriKey: String): Flow<Float?> {
        return snapshot
            .map { it.records[uriKey]?.progress }
            .distinctUntilChanged()
    }

    fun episodeProgress(uriKeys: List<String>): Flow<List<Float>> {
        return snapshot
            .map { view -> uriKeys.map { key -> view.records[key]?.progress ?: 0f } }
            .distinctUntilChanged()
    }

    suspend fun ensureRecentIndex() {
        context.playbackDataStore.edit { prefs ->
            if (prefs.contains(RECENT_ORDER)) return@edit
            prefs[RECENT_ORDER] = legacyRecentUris(prefs).joinToString("\n")
        }
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
            val order = if (prefs.contains(RECENT_ORDER)) {
                recentUriOrder(prefs)
            } else {
                legacyRecentUris(prefs)
            }
            prefs[RECENT_ORDER] = updateRecentOrder(order, uriString).joinToString("\n")
        }
    }

    suspend fun removeRecord(uri: Uri) {
        val uriString = uri.toString()
        context.playbackDataStore.edit { prefs ->
            removeRecordKeys(prefs, uriString)
            if (prefs.contains(RECENT_ORDER)) {
                val order = recentUriOrder(prefs).filterNot { it == uriString }
                prefs[RECENT_ORDER] = order.joinToString("\n")
            }
        }
    }

    suspend fun removeRecords(uris: Collection<Uri>) {
        uris.forEach { removeRecord(it) }
    }

    suspend fun durationMsByUri(): Map<String, Long> {
        val prefs = context.playbackDataStore.data.first()
        return recordsFromPrefs(prefs)
            .mapValues { it.value.durationMs }
            .filterValues { it > 0L }
    }

    private fun recordsFromPrefs(prefs: Preferences): Map<String, PlaybackRecord> {
        val records = LinkedHashMap<String, PlaybackRecord>()
        prefs.asMap().keys.forEach { key ->
            val name = key.name
            if (!name.startsWith(PREFIX_URI)) return@forEach
            val uriString = name.removePrefix(PREFIX_URI)
            val record = recordFromPrefs(prefs, uriString) ?: return@forEach
            records[uriString] = record
        }
        return records
    }

    private fun recentUriOrder(prefs: Preferences): List<String> {
        val stored = prefs[RECENT_ORDER] ?: return emptyList()
        return stored.split('\n').filter { it.isNotEmpty() }
    }

    private fun legacyRecentUris(prefs: Preferences): List<String> {
        return prefs.asMap().keys
            .mapNotNull { key ->
                val name = key.name
                if (!name.startsWith(PREFIX_URI)) return@mapNotNull null
                name.removePrefix(PREFIX_URI)
            }
            .mapNotNull { uriString ->
                val playedAt = prefs[longPreferencesKey(PREFIX_LAST_PLAYED + uriString)] ?: return@mapNotNull null
                uriString to playedAt
            }
            .sortedByDescending { it.second }
            .take(MAX_RECENT_COUNT)
            .map { it.first }
    }

    private fun recordFromPrefs(prefs: Preferences, uriString: String): PlaybackRecord? {
        val title = prefs[stringPreferencesKey(PREFIX_TITLE + uriString)] ?: return null
        return PlaybackRecord(
            uri = Uri.parse(uriString),
            title = title,
            positionMs = prefs[longPreferencesKey(PREFIX_POSITION + uriString)] ?: 0L,
            durationMs = prefs[longPreferencesKey(PREFIX_DURATION + uriString)] ?: 0L,
            lastPlayedAt = prefs[longPreferencesKey(PREFIX_LAST_PLAYED + uriString)] ?: 0L,
        )
    }

    private fun removeRecordKeys(prefs: androidx.datastore.preferences.core.MutablePreferences, uriString: String) {
        prefs.remove(stringPreferencesKey(PREFIX_URI + uriString))
        prefs.remove(stringPreferencesKey(PREFIX_TITLE + uriString))
        prefs.remove(longPreferencesKey(PREFIX_POSITION + uriString))
        prefs.remove(longPreferencesKey(PREFIX_DURATION + uriString))
        prefs.remove(longPreferencesKey(PREFIX_LAST_PLAYED + uriString))
    }

    companion object {
        private const val PREFIX_URI = "uri_"
        private const val PREFIX_TITLE = "title_"
        private const val PREFIX_POSITION = "pos_"
        private const val PREFIX_DURATION = "dur_"
        private const val PREFIX_LAST_PLAYED = "last_"
        private val RECENT_ORDER = stringPreferencesKey("recent_order")
        private const val MAX_RECENT_COUNT = 15
    }
}

fun playbackSnapshotView(
    records: Map<String, PlaybackRecord>,
    recentOrder: List<String>,
    limit: Int = 15,
): PlaybackSnapshotView {
    val recent = recentOrder.mapNotNull { records[it] }.take(limit)
    return PlaybackSnapshotView(recent = recent, records = records)
}

fun updateRecentOrder(existing: List<String>, uri: String, limit: Int = 15): List<String> {
    return (listOf(uri) + existing.filterNot { it == uri }).take(limit)
}
