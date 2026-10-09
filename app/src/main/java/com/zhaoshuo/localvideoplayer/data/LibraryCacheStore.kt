package com.zhaoshuo.localvideoplayer.data

import android.content.Context
import android.net.Uri
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.zhaoshuo.localvideoplayer.model.SeriesGroupKind
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import com.zhaoshuo.localvideoplayer.model.VideoSeries
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

    private val libraryEntriesFile: File
        get() = File(context.filesDir, LIBRARY_ENTRIES_FILE_NAME)

    suspend fun loadVideos(): List<VideoItem> = withContext(Dispatchers.IO) {
        val file = cacheFile
        if (file.exists()) {
            return@withContext runCatching {
                parseVideos(file.readText())
            }.getOrElse { emptyList() }
        }
        migrateLegacyDataStore()
    }

    suspend fun loadLibraryEntries(): List<VideoLibraryEntry> = withContext(Dispatchers.IO) {
        val file = libraryEntriesFile
        if (!file.exists()) return@withContext emptyList()
        runCatching { parseLibraryEntries(file.readText()) }.getOrElse { emptyList() }
    }

    suspend fun saveLibraryEntries(entries: List<VideoLibraryEntry>) {
        withContext(Dispatchers.IO) {
            runCatching {
                val tempFile = File(context.filesDir, "$LIBRARY_ENTRIES_FILE_NAME.tmp")
                tempFile.writeText(encodeLibraryEntries(entries))
                if (!tempFile.renameTo(libraryEntriesFile)) {
                    tempFile.copyTo(libraryEntriesFile, overwrite = true)
                    tempFile.delete()
                }
            }
        }
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
            array.put(encodeVideoObject(video))
        }
        return array.toString()
    }

    private fun parseVideos(json: String): List<VideoItem> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val objectJson = array.optJSONObject(index) ?: continue
                parseVideoObject(objectJson)?.let(::add)
            }
        }
    }

    private fun parseVideoObject(objectJson: JSONObject): VideoItem? {
        val uriString = objectJson.optString(KEY_URI)
        if (uriString.isBlank()) return null
        return VideoItem(
            id = objectJson.optLong(KEY_ID),
            uri = Uri.parse(uriString),
            title = objectJson.optString(KEY_TITLE, "Unknown"),
            durationMs = objectJson.optLong(KEY_DURATION_MS),
            dateAdded = objectJson.optLong(KEY_DATE_ADDED),
            dateModified = objectJson.optLong(KEY_DATE_MODIFIED),
            thumbnailUri = objectJson.optString(KEY_THUMBNAIL_URI)
                .takeIf { it.isNotBlank() }
                ?.let(Uri::parse),
            relativePath = objectJson.optString(KEY_RELATIVE_PATH).takeIf { it.isNotBlank() },
            bucketName = objectJson.optString(KEY_BUCKET_NAME).takeIf { it.isNotBlank() },
            seriesKey = objectJson.optString(KEY_SERIES_KEY).takeIf { it.isNotBlank() },
            season = objectJson.optInt(KEY_SEASON).takeIf { objectJson.has(KEY_SEASON) && it > 0 },
            episode = objectJson.optInt(KEY_EPISODE).takeIf { objectJson.has(KEY_EPISODE) && it > 0 },
        )
    }

    private fun encodeVideoObject(video: VideoItem): JSONObject {
        return JSONObject().apply {
            put(KEY_ID, video.id)
            put(KEY_URI, video.uri.toString())
            put(KEY_TITLE, video.title)
            put(KEY_DURATION_MS, video.durationMs)
            put(KEY_DATE_ADDED, video.dateAdded)
            put(KEY_DATE_MODIFIED, video.dateModified)
            video.thumbnailUri?.let { put(KEY_THUMBNAIL_URI, it.toString()) }
            video.relativePath?.let { put(KEY_RELATIVE_PATH, it) }
            video.bucketName?.let { put(KEY_BUCKET_NAME, it) }
            video.seriesKey?.let { put(KEY_SERIES_KEY, it) }
            video.season?.let { put(KEY_SEASON, it) }
            video.episode?.let { put(KEY_EPISODE, it) }
        }
    }

    private fun encodeLibraryEntries(entries: List<VideoLibraryEntry>): String {
        val array = JSONArray()
        entries.forEach { entry ->
            array.put(
                when (entry) {
                    is VideoLibraryEntry.Single -> JSONObject().apply {
                        put(KEY_TYPE, TYPE_SINGLE)
                        put(KEY_VIDEO, encodeVideoObject(entry.video))
                    }
                    is VideoLibraryEntry.Series -> JSONObject().apply {
                        put(KEY_TYPE, TYPE_SERIES)
                        put(KEY_SERIES_ID, entry.series.id)
                        put(KEY_SERIES_TITLE, entry.series.title)
                        put(KEY_GROUP_KIND, entry.series.groupKind.name)
                        put(
                            KEY_EPISODES,
                            JSONArray().apply {
                                entry.series.episodes.forEach { put(encodeVideoObject(it)) }
                            },
                        )
                    }
                },
            )
        }
        return array.toString()
    }

    private fun parseLibraryEntries(json: String): List<VideoLibraryEntry> {
        if (json.isBlank()) return emptyList()
        val array = JSONArray(json)
        return buildList {
            for (index in 0 until array.length()) {
                val obj = array.optJSONObject(index) ?: continue
                when (obj.optString(KEY_TYPE)) {
                    TYPE_SINGLE -> {
                        val video = obj.optJSONObject(KEY_VIDEO)?.let(::parseVideoObject) ?: continue
                        add(VideoLibraryEntry.Single(video))
                    }
                    TYPE_SERIES -> {
                        val episodesJson = obj.optJSONArray(KEY_EPISODES) ?: continue
                        val episodes = buildList {
                            for (i in 0 until episodesJson.length()) {
                                val episodeObj = episodesJson.optJSONObject(i) ?: continue
                                parseVideoObject(episodeObj)?.let(::add)
                            }
                        }
                        if (episodes.isEmpty()) continue
                        val kind = runCatching {
                            SeriesGroupKind.valueOf(obj.optString(KEY_GROUP_KIND, SeriesGroupKind.EPISODE.name))
                        }.getOrDefault(SeriesGroupKind.EPISODE)
                        add(
                            VideoLibraryEntry.Series(
                                VideoSeries(
                                    id = obj.optString(KEY_SERIES_ID),
                                    title = obj.optString(KEY_SERIES_TITLE, "Series"),
                                    episodes = episodes,
                                    groupKind = kind,
                                ),
                            ),
                        )
                    }
                }
            }
        }
    }

    companion object {
        private const val CACHE_FILE_NAME = "library_cache.json"
        private const val LIBRARY_ENTRIES_FILE_NAME = "library_entries.json"
        private val LEGACY_CACHED_VIDEOS_KEY = stringPreferencesKey("cached_videos")
        private const val KEY_ID = "id"
        private const val KEY_URI = "uri"
        private const val KEY_TITLE = "title"
        private const val KEY_DURATION_MS = "durationMs"
        private const val KEY_DATE_ADDED = "dateAdded"
        private const val KEY_DATE_MODIFIED = "dateModified"
        private const val KEY_THUMBNAIL_URI = "thumbnailUri"
        private const val KEY_RELATIVE_PATH = "relativePath"
        private const val KEY_BUCKET_NAME = "bucketName"
        private const val KEY_SERIES_KEY = "seriesKey"
        private const val KEY_SEASON = "season"
        private const val KEY_EPISODE = "episode"
        private const val KEY_TYPE = "type"
        private const val KEY_VIDEO = "video"
        private const val KEY_SERIES_ID = "seriesId"
        private const val KEY_SERIES_TITLE = "seriesTitle"
        private const val KEY_GROUP_KIND = "groupKind"
        private const val KEY_EPISODES = "episodes"
        private const val TYPE_SINGLE = "single"
        private const val TYPE_SERIES = "series"
    }
}
