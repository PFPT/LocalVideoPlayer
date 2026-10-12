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
import com.zhaoshuo.localvideoplayer.util.dedupeLibraryVideos
import com.zhaoshuo.localvideoplayer.util.isM3u8Title

class VideoRepository(private val context: Context) {

    fun loadVideos(): List<VideoItem> {
        val fromVideo = queryVideos(selection = null, selectionArgs = null)
        val seen = fromVideo.mapTo(mutableSetOf()) { it.uri.toString() }
        val fromDownloads = queryDownloadVideos(seen)
        val fromFiles = queryOrphanVideosByExtension(seen)
        return dedupeLibraryVideos(fromVideo + fromDownloads + fromFiles)
    }

    fun loadVideoIds(): Set<Long>? {
        return try {
            val ids = mutableSetOf<Long>()
            val cursor = context.contentResolver.query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                arrayOf(MediaStore.Video.Media._ID),
                null,
                null,
                null,
            ) ?: return null
            cursor.use {
                val idColumn = it.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
                while (it.moveToNext()) {
                    ids.add(it.getLong(idColumn))
                }
            }
            ids += queryDownloadVideoIds()
            ids += queryOrphanVideoFileIds()
            ids
        } catch (_: Exception) {
            null
        }
    }

    fun loadVideosAddedAfter(minDateAdded: Long, minId: Long): List<VideoItem> {
        val selection = "(${MediaStore.Video.Media.DATE_ADDED} > ?) OR " +
            "(${MediaStore.Video.Media.DATE_ADDED} = ? AND ${MediaStore.Video.Media._ID} > ?)"
        val selectionArgs = arrayOf(
            minDateAdded.toString(),
            minDateAdded.toString(),
            minId.toString(),
        )
        val fromVideo = queryVideos(selection, selectionArgs)
        val seen = fromVideo.mapTo(mutableSetOf()) { it.uri.toString() }
        val fromDownloads = queryDownloadVideos(seen, minDateAdded, minId)
        val fromFiles = queryOrphanVideosByExtension(
            seenUris = seen,
            minDateAdded = minDateAdded,
            minId = minId,
        )
        return dedupeLibraryVideos(fromVideo + fromDownloads + fromFiles)
    }

    private fun queryVideos(selection: String?, selectionArgs: Array<String>?): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        val seenUris = mutableSetOf<String>()
        val projection = buildList {
            add(MediaStore.Video.Media._ID)
            add(MediaStore.Video.Media.DISPLAY_NAME)
            add(MediaStore.Video.Media.DURATION)
            add(MediaStore.Video.Media.DATE_ADDED)
            add(MediaStore.Video.Media.DATE_MODIFIED)
            add(MediaStore.Video.Media.BUCKET_DISPLAY_NAME)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Video.Media.RELATIVE_PATH)
            }
        }.toTypedArray()

        context.contentResolver.query(
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
            projection,
            selection,
            selectionArgs,
            "${MediaStore.Video.Media.DATE_ADDED} ASC, ${MediaStore.Video.Media._ID} ASC",
        )?.use { cursor ->
            val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DISPLAY_NAME)
            val durationColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DURATION)
            val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_ADDED)
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATE_MODIFIED)
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
                val dateModified = cursor.getLong(dateModifiedColumn)
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
                        dateModified = dateModified,
                        thumbnailUri = null,
                        relativePath = relativePath,
                        bucketName = bucketName,
                    ),
                )
            }
        }

        return videos
    }

    private fun queryDownloadVideos(
        seenUris: MutableSet<String>,
        minDateAdded: Long? = null,
        minId: Long? = null,
    ): List<VideoItem> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptyList()
        val videos = mutableListOf<VideoItem>()
        val incremental = minDateAdded != null && minId != null
        val projection = arrayOf(
            MediaStore.Downloads._ID,
            MediaStore.Downloads.DISPLAY_NAME,
            MediaStore.Downloads.MIME_TYPE,
            MediaStore.Downloads.DATE_ADDED,
            MediaStore.Downloads.DATE_MODIFIED,
            MediaStore.Downloads.RELATIVE_PATH,
            MediaStore.MediaColumns.IS_PENDING,
        )
        val selectionArgs = buildList {
            addAll(VIDEO_EXTENSIONS.map { "%.$it" })
            if (incremental) {
                add(minDateAdded.toString())
                add(minDateAdded.toString())
                add(minId.toString())
            }
        }.toTypedArray()
        try {
            context.contentResolver.query(
                MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                projection,
                downloadVideoSelection(incremental),
                selectionArgs,
                "${MediaStore.Downloads.DATE_ADDED} ASC, ${MediaStore.Downloads._ID} ASC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DISPLAY_NAME)
                val mimeColumn = cursor.getColumnIndex(MediaStore.Downloads.MIME_TYPE)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Downloads.DATE_MODIFIED)
                val relativePathColumn = cursor.getColumnIndex(MediaStore.Downloads.RELATIVE_PATH)
                val pendingColumn = cursor.getColumnIndex(MediaStore.MediaColumns.IS_PENDING)
                while (cursor.moveToNext()) {
                    if (pendingColumn >= 0 && cursor.getInt(pendingColumn) == 1) continue
                    val name = cursor.getString(nameColumn) ?: continue
                    val mime = if (mimeColumn >= 0) cursor.getString(mimeColumn).orEmpty() else ""
                    if (!FolderVideoScanner.isVideoFile(name, mime)) continue
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Downloads.EXTERNAL_CONTENT_URI,
                        id,
                    )
                    if (!seenUris.add(contentUri.toString())) continue
                    val relativePath = if (relativePathColumn >= 0) {
                        cursor.getString(relativePathColumn)
                    } else {
                        null
                    }
                    videos.add(
                        VideoItem(
                            id = id,
                            uri = contentUri,
                            title = name,
                            durationMs = 0L,
                            dateAdded = cursor.getLong(dateAddedColumn),
                            dateModified = cursor.getLong(dateModifiedColumn),
                            thumbnailUri = null,
                            relativePath = relativePath,
                            bucketName = leafBucketName(relativePath),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return videos
    }

    private fun queryDownloadVideoIds(): Set<Long> {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return emptySet()
        return queryDownloadVideos(mutableSetOf()).mapTo(mutableSetOf()) { it.id }
    }

    private fun downloadVideoSelection(incremental: Boolean): String {
        val extensionClauses = VIDEO_EXTENSIONS.joinToString(" OR ") {
            "${MediaStore.Downloads.DISPLAY_NAME} LIKE ?"
        }
        return buildString {
            append("((")
            append(MediaStore.Downloads.MIME_TYPE)
            append(" LIKE 'video/%') OR ")
            append(extensionClauses)
            append(") AND (")
            append(MediaStore.MediaColumns.IS_PENDING)
            append(" = 0 OR ")
            append(MediaStore.MediaColumns.IS_PENDING)
            append(" IS NULL)")
            if (incremental) {
                append(" AND ((")
                append(MediaStore.Downloads.DATE_ADDED)
                append(" > ?) OR (")
                append(MediaStore.Downloads.DATE_ADDED)
                append(" = ? AND ")
                append(MediaStore.Downloads._ID)
                append(" > ?))")
            }
        }
    }

    private fun queryOrphanVideosByExtension(
        seenUris: MutableSet<String>,
        minDateAdded: Long? = null,
        minId: Long? = null,
    ): List<VideoItem> {
        val videos = mutableListOf<VideoItem>()
        val projection = buildList {
            add(MediaStore.Files.FileColumns._ID)
            add(MediaStore.Files.FileColumns.DISPLAY_NAME)
            add(MediaStore.Files.FileColumns.DATE_ADDED)
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
            add(MediaStore.Files.FileColumns.MEDIA_TYPE)
            add(MediaStore.Files.FileColumns.MIME_TYPE)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                add(MediaStore.Files.FileColumns.RELATIVE_PATH)
                add(MediaStore.Files.FileColumns.DURATION)
                add(MediaStore.Files.FileColumns.IS_PENDING)
            }
        }.toTypedArray()
        val selection = buildOrphanVideoSelection(minDateAdded != null && minId != null)
        val selectionArgs = buildList {
            addAll(ORPHAN_EXTENSIONS.map { "%.$it" })
            if (minDateAdded != null && minId != null) {
                add(minDateAdded.toString())
                add(minDateAdded.toString())
                add(minId.toString())
            }
        }.toTypedArray()
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                projection,
                selection,
                selectionArgs,
                "${MediaStore.Files.FileColumns.DATE_ADDED} ASC, ${MediaStore.Files.FileColumns._ID} ASC",
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                val dateAddedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_ADDED)
                val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
                val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val relativePathColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.RELATIVE_PATH)
                val durationColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.DURATION)
                val pendingColumn = cursor.getColumnIndex(MediaStore.Files.FileColumns.IS_PENDING)
                while (cursor.moveToNext()) {
                    if (pendingColumn >= 0 && cursor.getInt(pendingColumn) == 1) continue
                    if (cursor.getInt(mediaTypeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) continue
                    val name = cursor.getString(nameColumn) ?: continue
                    if (!FolderVideoScanner.isVideoFile(name, mime = "")) continue
                    val id = cursor.getLong(idColumn)
                    val contentUri = ContentUris.withAppendedId(
                        MediaStore.Files.getContentUri("external"),
                        id,
                    )
                    if (!seenUris.add(contentUri.toString())) continue
                    val relativePath = if (relativePathColumn >= 0) {
                        cursor.getString(relativePathColumn)
                    } else {
                        null
                    }
                    val duration = if (durationColumn >= 0) cursor.getLong(durationColumn) else 0L
                    videos.add(
                        VideoItem(
                            id = id,
                            uri = contentUri,
                            title = name,
                            durationMs = duration.coerceAtLeast(0L),
                            dateAdded = cursor.getLong(dateAddedColumn),
                            dateModified = cursor.getLong(dateModifiedColumn),
                            thumbnailUri = null,
                            relativePath = relativePath,
                            bucketName = leafBucketName(relativePath),
                        ),
                    )
                }
            }
        } catch (_: Exception) {
            return emptyList()
        }
        return videos
    }

    private fun queryOrphanVideoFileIds(): Set<Long> {
        val ids = mutableSetOf<Long>()
        val selection = buildOrphanVideoSelection()
        val selectionArgs = ORPHAN_EXTENSIONS.map { "%.$it" }.toTypedArray()
        try {
            context.contentResolver.query(
                MediaStore.Files.getContentUri("external"),
                arrayOf(
                    MediaStore.Files.FileColumns._ID,
                    MediaStore.Files.FileColumns.MEDIA_TYPE,
                    MediaStore.Files.FileColumns.DISPLAY_NAME,
                ),
                selection,
                selectionArgs,
                null,
            )?.use { cursor ->
                val idColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns._ID)
                val mediaTypeColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.MEDIA_TYPE)
                val nameColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DISPLAY_NAME)
                while (cursor.moveToNext()) {
                    if (cursor.getInt(mediaTypeColumn) == MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO) continue
                    val name = cursor.getString(nameColumn) ?: continue
                    if (!FolderVideoScanner.isVideoFile(name, mime = "")) continue
                    ids.add(cursor.getLong(idColumn))
                }
            }
        } catch (_: Exception) {
            return emptySet()
        }
        return ids
    }

    private fun buildOrphanVideoSelection(incremental: Boolean = false): String {
        val nameClauses = ORPHAN_EXTENSIONS.joinToString(" OR ") {
            "${MediaStore.Files.FileColumns.DISPLAY_NAME} LIKE ?"
        }
        return buildString {
            append('(')
            append(nameClauses)
            append(')')
            append(" AND ")
            append(MediaStore.Files.FileColumns.MEDIA_TYPE)
            append(" != ")
            append(MediaStore.Files.FileColumns.MEDIA_TYPE_VIDEO)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                append(" AND (")
                append(MediaStore.MediaColumns.IS_PENDING)
                append(" = 0 OR ")
                append(MediaStore.MediaColumns.IS_PENDING)
                append(" IS NULL)")
            }
            if (incremental) {
                append(" AND ((")
                append(MediaStore.Files.FileColumns.DATE_ADDED)
                append(" > ?) OR (")
                append(MediaStore.Files.FileColumns.DATE_ADDED)
                append(" = ? AND ")
                append(MediaStore.Files.FileColumns._ID)
                append(" > ?))")
            }
        }
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
            add(MediaStore.Files.FileColumns.DATE_MODIFIED)
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
            val dateModifiedColumn = cursor.getColumnIndexOrThrow(MediaStore.Files.FileColumns.DATE_MODIFIED)
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
                val dateModified = cursor.getLong(dateModifiedColumn)
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
                        dateModified = dateModified,
                        thumbnailUri = null,
                        relativePath = relativePath,
                        bucketName = leafBucketName(relativePath),
                    ),
                )
            }
        }
    }

    companion object {
        private val VIDEO_EXTENSIONS = listOf(
            "mp4", "mkv", "avi", "webm", "mov", "m4v", "ts", "flv", "wmv", "mpeg", "mpg",
        )
        private val ORPHAN_EXTENSIONS = listOf("mp4", "mkv", "ts", "avi", "mov", "m4v")

        fun leafBucketName(relativePath: String?): String? {
            val trimmed = relativePath?.trim()?.trim('/') ?: return null
            if (trimmed.isEmpty()) return null
            return trimmed.substringAfterLast('/').ifBlank { null }
        }
    }
}
