package com.zhaoshuo.localvideoplayer.data

import android.content.ContentUris
import android.content.Context
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import com.zhaoshuo.localvideoplayer.model.VideoItem
import com.zhaoshuo.localvideoplayer.model.VideoLibraryEntry
import com.zhaoshuo.localvideoplayer.player.PlaylistState
import com.zhaoshuo.localvideoplayer.util.M3uPlaylistParser
import com.zhaoshuo.localvideoplayer.util.SeriesGrouper
import com.zhaoshuo.localvideoplayer.util.isM3u8Title

class VideoRepository(private val context: Context) {

    fun loadVideos(): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        val seenUris = mutableSetOf<String>()
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            null,
            null,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val bucketColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Video.Media.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: "Unknown"
                val duration = cursor.getLong(durationColumn)
                val dateAdded = cursor.getLong(dateAddedColumn)
                val bucketName = cursor.getString(bucketColumn)
                val relativePath = if (relativePathColumn >= 0) {
                    cursor.getString(relativePathColumn)
                } else {
                    null
                }
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                    id,
                )
                if (!seenUris.add(contentUri.toString())) continue

                videos.add(
                    VideoItem(
                        id = id,
                        uri = contentUri,
                        title = name,
                        durationMs = duration,
                        dateAdded = dateAdded,
                        thumbnailUri = null,
                        relativePath = relativePath,
                        bucketName = bucketName,
                    ),
                )
            }
        }

        return videos
    }

    fun loadM3u8Playlists(existing: List<VideoItem> = emptyList()): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        val seenUris = existing.mapTo(mutableSetOf()) { it.uri.toString() }
        loadM3u8Files(videos, seenUris)
        return videos
    }

    fun loadAllVideos(): List<VideoItem> {
        val videos = loadVideos().toMutableList()
        val seenUris = videos.mapTo(mutableSetOf()) { it.uri.toString() }
        loadM3u8Files(videos, seenUris)
        return videos
    }

    fun loadVideosExcluding(hiddenUris: Set<String>): List<VideoItem> {
        return loadAllVideos().filter { it.uri.toString() !in hiddenUris }
    }

    fun loadLibrary(hiddenUris: Set<String> = emptySet()): List<VideoLibraryEntry> {
        return SeriesGrouper.groupVideos(loadVideosExcluding(hiddenUris))
    }

    fun deleteFromDevice(uri: Uri): Boolean {
        return try {
            val deleted = context.contentResolver.delete(uri, null, null)
            deleted > 0
        } catch (_: SecurityException) {
            false
        } catch (_: UnsupportedOperationException) {
            false
        }
    }

    fun expandPlaylistIfNeeded(item: VideoItem): List<VideoItem> {
        if (!isM3u8Title(item.title) && !isM3u8Title(item.uri.lastPathSegment.orEmpty())) {
            return listOf(item)
        }
        return M3uPlaylistParser.expandPlaylist(context, item.uri, item.title)
    }

    fun playlistStateFor(
        video: VideoItem,
        libraryEntries: List<VideoLibraryEntry>,
    ): PlaylistState {
        val allVideos = libraryEntries.flatMap { entry ->
            when (entry) {
                is VideoLibraryEntry.Single -> listOf(entry.video)
                is VideoLibraryEntry.Series -> entry.series.episodes
            }
        }.distinctBy { it.uri.toString() }

        SeriesGrouper.playlistBySeriesName(video, allVideos)?.let { return it }

        val targetUri = video.uri.toString()
        for (entry in libraryEntries) {
            when (entry) {
                is VideoLibraryEntry.Series -> {
                    val index = entry.series.episodes.indexOfFirst {
                        it.uri.toString() == targetUri
                    }
                    if (index >= 0) {
                        return PlaylistState(
                            items = entry.series.episodes,
                            currentIndex = index,
                        )
                    }
                }
                is VideoLibraryEntry.Single -> {
                    if (entry.video.uri.toString() == targetUri) {
                        return PlaylistState(
                            items = expandPlaylistIfNeeded(entry.video),
                            currentIndex = 0,
                        )
                    }
                }
            }
        }
        return PlaylistState(
            items = expandPlaylistIfNeeded(video),
            currentIndex = 0,
        )
    }

    private fun loadM3u8Files(videos: MutableList<VideoItem>, seenUris: MutableSet<String>) {
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.DATE_ADDED)
            add(MediaStore.Files.FileColumns.RELATIVE_PATH)
        }.toTypedArray()

        val selection = buildString {
            append("(")
            append(MediaStore.Files.FileColumns.DISPLAY_NAME)
            append(" LIKE ? OR ")
            append(MediaStore.Files.FileColumns.MIME_TYPE)
            append(" IN (?, ?))")
        }
        val selectionArgs = arrayOf(
            "%.m3u8",
            "application/vnd.apple.mpegurl",
            "application/x-mpegURL",
        )

        context.contentResolver.query(
            MediaStore.Files.getContentUri("external"),
            projection,
            selection,
            selectionArgs,
            null,
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
            val relativePathColumn = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.RELATIVE_PATH)
            } else {
                -1
            }

            while (cursor.moveToNext()) {
                val id = cursor.getLong(idColumn)
                val name = cursor.getString(nameColumn) ?: continue
                if (!isM3u8Title(name)) continue
                val dateAdded = cursor.getLong(dateAddedColumn)
                val relativePath = if (relativePathColumn >= 0) {
                    cursor.getString(relativePathColumn)
                } else {
                    null
                }
                val contentUri = ContentUris.withAppendedId(
                    MediaStore.Files.getContentUri("external"),
                    id,
                )
                if (!seenUris.add(contentUri.toString())) continue

                videos.add(
                    VideoItem(
                        id = id,
                        uri = contentUri,
                        title = name,
                        durationMs = 0L,
                        dateAdded = dateAdded,
                        thumbnailUri = null,
                        relativePath = relativePath,
                        bucketName = relativePath?.substringBefore('/')?.ifBlank { null },
                    ),
                )
            }
        }
    }

}
