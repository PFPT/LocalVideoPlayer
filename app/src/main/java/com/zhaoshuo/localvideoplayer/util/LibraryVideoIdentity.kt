package com.zhaoshuo.localvideoplayer.util

import com.zhaoshuo.localvideoplayer.model.VideoItem

fun libraryFileKey(video: VideoItem): String? {
    val title = video.title.trim().lowercase()
    if (title.isEmpty()) return null
    val path = video.relativePath?.trim()?.trim('/')?.lowercase().orEmpty()
    if (path.isEmpty()) return null
    return if (path == title || path.endsWith("/$title")) {
        path
    } else {
        "$path/$title"
    }
}

fun dedupeLibraryVideos(videos: List<VideoItem>): List<VideoItem> {
    if (videos.size < 2) return videos
    val winners = HashMap<String, VideoItem>()
    for (video in videos) {
        val key = libraryFileKey(video) ?: continue
        val current = winners[key]
        if (current == null || preferLibraryCopy(video, current)) {
            winners[key] = video
        }
    }
    if (winners.isEmpty()) return videos
    val emitted = HashSet<String>()
    val result = ArrayList<VideoItem>(videos.size)
    for (video in videos) {
        val key = libraryFileKey(video)
        if (key == null) {
            result.add(video)
        } else if (emitted.add(key)) {
            result.add(winners.getValue(key))
        }
    }
    return result
}

private fun preferLibraryCopy(candidate: VideoItem, current: VideoItem): Boolean {
    val rank = libraryCopyRank(candidate).compareTo(libraryCopyRank(current))
    if (rank != 0) return rank < 0
    return candidate.durationMs > current.durationMs
}

private fun libraryCopyRank(video: VideoItem): Int {
    val uri = video.uri.toString()
    return when {
        uri.contains("/video/media/") -> 0
        uri.contains("/downloads/") -> 1
        uri.contains("/file/") -> 2
        else -> 3
    }
}
