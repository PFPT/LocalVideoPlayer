package com.zhaoshuo.localvideoplayer.model

import android.net.Uri

const val APP_DOWNLOAD_BUCKET = "LocalVideoPlayer"
const val APP_DOWNLOAD_FOLDER_KEY = "bucket:$APP_DOWNLOAD_BUCKET"

sealed class VideoLibraryEntry {
    data class Series(val series: VideoSeries) : VideoLibraryEntry()
    data class Single(val video: VideoItem) : VideoLibraryEntry()
}

fun entryKey(entry: VideoLibraryEntry): String {
    return when (entry) {
        is VideoLibraryEntry.Series -> "series:${entry.series.id}"
        is VideoLibraryEntry.Single -> "single:${entry.video.uri}"
    }
}

fun isAppDownloadFolder(entry: VideoLibraryEntry): Boolean {
    if (entry !is VideoLibraryEntry.Series) return false
    if (entry.series.groupKind != SeriesGroupKind.BUCKET) return false
    if (entry.series.title.equals(APP_DOWNLOAD_BUCKET, ignoreCase = true)) return true
    return entry.series.episodes.any { it.seriesKey == APP_DOWNLOAD_FOLDER_KEY }
}

fun isAppDownloadLocation(video: VideoItem): Boolean {
    val bucket = video.bucketName?.trim().orEmpty()
    if (bucket.equals(APP_DOWNLOAD_BUCKET, ignoreCase = true)) return true
    val path = video.relativePath.orEmpty()
    return path.contains(APP_DOWNLOAD_BUCKET, ignoreCase = true)
}

data class LibrarySections(
    val episodes: List<VideoLibraryEntry.Series>,
    val folders: List<VideoLibraryEntry.Series>,
    val shortClips: List<VideoLibraryEntry.Series>,
    val singles: List<VideoLibraryEntry.Single>,
)

fun partitionLibrary(entries: List<VideoLibraryEntry>): LibrarySections {
    val episodes = mutableListOf<VideoLibraryEntry.Series>()
    val folders = mutableListOf<VideoLibraryEntry.Series>()
    val shortClips = mutableListOf<VideoLibraryEntry.Series>()
    val singles = mutableListOf<VideoLibraryEntry.Single>()
    for (entry in entries) {
        when (entry) {
            is VideoLibraryEntry.Single -> singles.add(entry)
            is VideoLibraryEntry.Series -> when (entry.series.groupKind) {
                SeriesGroupKind.EPISODE -> episodes.add(entry)
                SeriesGroupKind.DIRECTORY, SeriesGroupKind.BUCKET -> folders.add(entry)
                SeriesGroupKind.SHORT_CLIPS -> shortClips.add(entry)
            }
        }
    }
    return LibrarySections(episodes, folders, shortClips, singles)
}

fun List<VideoLibraryEntry>.withoutUris(uris: Collection<Uri>): List<VideoLibraryEntry> {
    if (uris.isEmpty()) return this
    val uriSet = uris.map { it.toString() }.toSet()
    return mapNotNull { entry ->
        when (entry) {
            is VideoLibraryEntry.Single -> {
                if (entry.video.uri.toString() in uriSet) null else entry
            }
            is VideoLibraryEntry.Series -> {
                val remaining = entry.series.episodes.filter { it.uri.toString() !in uriSet }
                when {
                    remaining.isEmpty() -> null
                    remaining.size == entry.series.episodes.size -> entry
                    else -> VideoLibraryEntry.Series(entry.series.copy(episodes = remaining))
                }
            }
        }
    }
}
