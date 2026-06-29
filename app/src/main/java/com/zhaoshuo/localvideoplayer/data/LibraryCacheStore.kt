package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhaoshuo.localvideoplayer.model.VideoItem
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

private val Context.legacyLibraryCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "library_cache",
)

class LibraryCacheStore(private val context: Context) {

    private val cacheFile: File
        get() = File(context.filesDir, CACHE_FILE_NAME)

    suspend fun loadVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val file = cacheFile
        if (file.exists()) {
            return@withContext runCatching {
                parseVideos(file.readText())
            }.getOrElse { emptyList() }
        }
        migrateLegacyDataStore()
    }

    suspend fun saveVideos(videos: List<VideoItem>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val tempFile = File(context.filesDir, "$CACHE_FILE_NAME.tmp")
                tempFile.writeText(encodeVideos(videos))
                if (tempFile.renameTo(cacheFile)) {
                    return@withContext
                }
                tempFile.copyTo(cacheFile, overwrite = true)
                tempFile.delete()
            }
        }
    }

    suspend fun removeVideos(uris: Collection<Uri>) {
        if (uris.isEmpty()) return
        val uriSet = uris.map { it.toString() }.toSet()
        val current = loadVideos()
        if (current.isEmpty()) return
        saveVideos(current.filter { it.uri.toString() !in uriSet })
    }

    private suspend fun migrateLegacyDataStore(): List<VideoItem> {
        val legacyJson = runCatching {
            val prefs = context.legacyLibraryCacheDataStore.data.first()
            prefs[LEGACY_CACHED_VIDEOS_KEY]
        }.getOrNull().orEmpty()
        if (legacyJson.isBlank()) return emptyList()
        val videos = runCatching { parseVideos(legacyJson) }.getOrElse { emptyList() }
        if (videos.isNotEmpty()) {
            saveVideos(videos)
        }
        return videos
    }

    private fun encodeVideos(videos: List<VideoItem>): String {
        val array = JSONArray()
        videos.forEach { video ->
            array.put(
                JSONObject().apply {
                    put(KEY_ID, video.id)
                    put(KEY_URI, video.uri.toString())
                    put(KEY_TITLE, video.title)
                    put(KEY_DURATION_MS, video.durationMs)
                    put(KEY_DATE_ADDED, video.dateAdded)
                    video.thumbnailUri?.let { put(KEY_THUMBNAIL_URI, it.toString()) }
                    video.relativePath?.let { put(KEY_RELATIVE_PATH, it) }
                    video.bucketName?.let { put(KEY_BUCKET_NAME, it) }
                },
            )
        }
        return array.toString()
    }

    private fun parseVideos(json: String): List<VideoItem> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val objectJson = array.optJSONObject(index) ?: continue
                val uriString = objectJson.optString(KEY_URI)
                if (uriString.isBlank()) continue
                add(
                    VideoItem(
                        id = objectJson.optLong(KEY_ID),
                        uri = Uri.parse(uriString),
                        title = objectJson.optString(KEY_TITLE, "Unknown"),
                        durationMs = objectJson.optLong(KEY_DURATION_MS),
                        dateAdded = objectJson.optLong(KEY_DATE_ADDED),
                        thumbnailUri = objectJson.optString(KEY_THUMBNAIL_URI)
                            .takeIf { it.isNotBlank() }
                            ?.let(Uri::parse),
                        relativePath = objectJson.optString(KEY_RELATIVE_PATH).takeIf { it.isNotBlank() },
                        bucketName = objectJson.optString(KEY_BUCKET_NAME).takeIf { it.isNotBlank() },
                    ),
                )
            }
        }
    }

    companion object {
        private const val CACHE_FILE_NAME = "library_cache.json"
        private val LEGACY_CACHED_VIDEOS_KEY = stringPreferencesKey("cached_videos")
        private const val KEY_ID = "id"
        private const val KEY_URI = "uri"
        private const val KEY_TITLE = "title"
        private const val KEY_DURATION_MS = "durationMs"
        private const val KEY_DATE_ADDED = "dateAdded"
        private const val KEY_THUMBNAIL_URI = "thumbnailUri"
        private const val KEY_RELATIVE_PATH = "relativePath"
        private const val KEY_BUCKET_NAME = "bucketName"
    }
}
