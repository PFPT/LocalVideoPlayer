package com.zhaoshuo.localvideoplayer.model

import android.net.Uri

sealed class VideoLibraryEntry {
    data class Series(val series: VideoSeries) : VideoLibraryEntry()
    data class Single(val video: VideoItem) : VideoLibraryEntry()
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
