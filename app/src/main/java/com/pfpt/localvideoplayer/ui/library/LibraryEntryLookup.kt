package com.pfpt.localvideoplayer.ui.library

import android.net.Uri
import com.pfpt.localvideoplayer.model.VideoItem
import com.pfpt.localvideoplayer.model.VideoLibraryEntry
import com.pfpt.localvideoplayer.model.entryKey

internal fun urisForEntry(entry: VideoLibraryEntry): List<Uri> {
    return when (entry) {
        is VideoLibraryEntry.Series -> entry.series.episodes.map { it.uri }
        is VideoLibraryEntry.Single -> listOf(entry.video.uri)
    }
}

internal fun titleForUri(uri: Uri, libraryEntries: List<VideoLibraryEntry>): String {
    val key = uri.toString()
    libraryEntries.forEach { entry ->
        when (entry) {
            is VideoLibraryEntry.Single -> {
                if (entry.video.uri.toString() == key) return entry.video.title
            }
            is VideoLibraryEntry.Series -> {
                entry.series.episodes.firstOrNull { it.uri.toString() == key }?.let { return it.title }
            }
        }
    }
    return ""
}

internal fun titleForUris(uris: List<Uri>, libraryEntries: List<VideoLibraryEntry>): String? {
    if (uris.size != 1) return null
    val target = uris.first().toString()
    libraryEntries.forEach { entry ->
        when (entry) {
            is VideoLibraryEntry.Single -> {
                if (entry.video.uri.toString() == target) return entry.video.title
            }
            is VideoLibraryEntry.Series -> {
                entry.series.episodes.firstOrNull { it.uri.toString() == target }?.let {
                    return it.title
                }
            }
        }
    }
    return null
}

internal fun subtitleForDelete(uris: List<Uri>, libraryEntries: List<VideoLibraryEntry>): String? {
    if (uris.size == 1) return titleForUris(uris, libraryEntries)
    val uriSet = uris.map { it.toString() }.toSet()
    libraryEntries.forEach { entry ->
        if (entry is VideoLibraryEntry.Series) {
            val entryUris = entry.series.episodes.map { it.uri.toString() }.toSet()
            if (entryUris == uriSet) return entry.series.title
        }
    }
    return null
}

internal fun playCountKeyForVideo(video: VideoItem, libraryEntries: List<VideoLibraryEntry>): String {
    val targetUri = video.uri.toString()
    libraryEntries.forEach { entry ->
        when (entry) {
            is VideoLibraryEntry.Single -> {
                if (entry.video.uri.toString() == targetUri) return entryKey(entry)
            }
            is VideoLibraryEntry.Series -> {
                if (entry.series.episodes.any { it.uri.toString() == targetUri }) {
                    return entryKey(entry)
                }
            }
        }
    }
    return "single:${video.uri}"
}
