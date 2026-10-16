package com.pfpt.localvideoplayer.util

import android.content.Context
import android.net.Uri
import com.pfpt.localvideoplayer.model.VideoItem

object M3uPlaylistParser {

    fun expandPlaylist(context: Context, playlistUri: Uri, fallbackTitle: String): List<VideoItem> {
        val content = context.contentResolver.openInputStream(playlistUri)
            ?.bufferedReader()
            ?.use { it.readText() }
            ?.trim()
            .orEmpty()
        if (content.isEmpty()) {
            return listOf(singleItem(playlistUri, fallbackTitle))
        }
        if (isHlsPlaylist(content)) {
            return listOf(singleItem(playlistUri, fallbackTitle))
        }

        val entries = parseEntries(content, playlistUri)
        if (entries.size <= 1) {
            return listOf(singleItem(playlistUri, fallbackTitle))
        }

        val now = System.currentTimeMillis() / 1000
        return entries.mapIndexed { index, entry ->
            VideoItem(
                id = (playlistUri.toString() + index).hashCode().toLong(),
                uri = entry.uri,
                title = entry.title,
                durationMs = 0L,
                dateAdded = now,
                thumbnailUri = null,
            )
        }
    }

    private fun isHlsPlaylist(content: String): Boolean {
        return content.contains("#EXT-X-", ignoreCase = true)
    }

    private fun parseEntries(content: String, baseUri: Uri): List<PlaylistEntry> {
        val entries = mutableListOf<PlaylistEntry>()
        var pendingTitle: String? = null

        content.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() && !it.startsWith("#EXTM3U", ignoreCase = true) }
            .forEach { line ->
                if (line.startsWith("#")) {
                    if (line.startsWith("#EXTINF:", ignoreCase = true)) {
                        pendingTitle = line.substringAfter(",").trim().ifBlank { null }
                    }
                    return@forEach
                }
                val title = pendingTitle ?: line.substringAfterLast('/')
                entries.add(
                    PlaylistEntry(
                        title = title,
                        uri = resolveEntryUri(baseUri, line),
                    ),
                )
                pendingTitle = null
            }

        return entries
    }

    private fun resolveEntryUri(baseUri: Uri, entry: String): Uri {
        val trimmed = entry.trim()
        if (trimmed.startsWith("http://", ignoreCase = true) ||
            trimmed.startsWith("https://", ignoreCase = true) ||
            trimmed.startsWith("file://", ignoreCase = true)
        ) {
            return Uri.parse(trimmed)
        }
        val base = baseUri.toString()
        val separator = base.lastIndexOf('/')
        if (separator >= 0) {
            return Uri.parse(base.substring(0, separator + 1) + trimmed.trimStart('/'))
        }
        return Uri.parse(trimmed)
    }

    private fun singleItem(uri: Uri, title: String): VideoItem {
        return VideoItem(
            id = uri.hashCode().toLong(),
            uri = uri,
            title = title,
            durationMs = 0L,
            dateAdded = System.currentTimeMillis() / 1000,
            thumbnailUri = null,
        )
    }

    private data class PlaylistEntry(
        val title: String,
        val uri: Uri,
    )
}
