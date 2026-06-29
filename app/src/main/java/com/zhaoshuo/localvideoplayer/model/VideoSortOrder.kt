package com.zhaoshuo.localvideoplayer.model

enum class VideoSortOrder {
    DATE_DESC,
    DATE_ASC,
    NAME_ASC,
    NAME_DESC,
    DURATION_DESC,
    DURATION_ASC,
}

fun sortVideos(videos: List<VideoItem>, order: VideoSortOrder): List<VideoItem> {
    return when (order) {
        VideoSortOrder.DATE_DESC -> videos.sortedByDescending { it.dateAdded }
        VideoSortOrder.DATE_ASC -> videos.sortedBy { it.dateAdded }
        VideoSortOrder.NAME_ASC -> videos.sortedBy { it.title.lowercase() }
        VideoSortOrder.NAME_DESC -> videos.sortedByDescending { it.title.lowercase() }
        VideoSortOrder.DURATION_DESC -> videos.sortedByDescending { it.durationMs }
        VideoSortOrder.DURATION_ASC -> videos.sortedBy { it.durationMs }
    }
}

fun sortLibrary(entries: List<VideoLibraryEntry>, order: VideoSortOrder): List<VideoLibraryEntry> {
    return when (order) {
        VideoSortOrder.DATE_DESC -> entries.sortedByDescending { entrySortDate(it) }
        VideoSortOrder.DATE_ASC -> entries.sortedBy { entrySortDate(it) }
        VideoSortOrder.NAME_ASC -> entries.sortedBy { entrySortName(it).lowercase() }
        VideoSortOrder.NAME_DESC -> entries.sortedByDescending { entrySortName(it).lowercase() }
        VideoSortOrder.DURATION_DESC -> entries.sortedByDescending { entrySortDuration(it) }
        VideoSortOrder.DURATION_ASC -> entries.sortedBy { entrySortDuration(it) }
    }
}

private fun entrySortDate(entry: VideoLibraryEntry): Long = when (entry) {
    is VideoLibraryEntry.Series -> entry.series.episodes.maxOfOrNull { it.dateAdded } ?: 0L
    is VideoLibraryEntry.Single -> entry.video.dateAdded
}

private fun entrySortName(entry: VideoLibraryEntry): String = when (entry) {
    is VideoLibraryEntry.Series -> entry.series.title
    is VideoLibraryEntry.Single -> entry.video.title
}

private fun entrySortDuration(entry: VideoLibraryEntry): Long = when (entry) {
    is VideoLibraryEntry.Series -> entry.series.episodes.sumOf { it.durationMs }
    is VideoLibraryEntry.Single -> entry.video.durationMs
}
