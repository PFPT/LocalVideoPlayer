package com.zhaoshuo.localvideoplayer.util

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.MimeTypes
import com.zhaoshuo.localvideoplayer.model.VideoItem

fun VideoItem.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder()
        .setUri(uri)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(title)
                .setDisplayTitle(title)
                .build(),
        )
    playbackMimeType(uri, title)?.let { builder.setMimeType(it) }
    return builder.build()
}

fun isM3u8Uri(uri: Uri): Boolean {
    return isM3u8Title(uri.lastPathSegment ?: uri.toString())
}

fun isM3u8Title(name: String): Boolean {
    return name.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)
}

private fun isHlsPlayback(uri: Uri, title: String): Boolean {
    val scheme = uri.scheme?.lowercase()
    if (scheme == "file" && (isM3u8Uri(uri) || isM3u8Title(title))) return true
    return (scheme == "http" || scheme == "https") && (isM3u8Uri(uri) || isM3u8Title(title))
}

private fun playbackMimeType(uri: Uri, title: String): String? {
    if (isHlsPlayback(uri, title)) return MimeTypes.APPLICATION_M3U8
    mimeTypeFromName(title)?.let { return it }
    uri.lastPathSegment?.let { mimeTypeFromName(it) }?.let { return it }
    if (isM3u8Title(title) && uri.scheme == "content") {
        return MimeTypes.VIDEO_MP2T
    }
    return null
}

private fun mimeTypeFromName(name: String): String? {
    val base = name.substringBefore('?').substringBefore('#')
    return when {
        base.endsWith(".ts", true) -> MimeTypes.VIDEO_MP2T
        base.endsWith(".mkv", true) -> MimeTypes.VIDEO_MATROSKA
        base.endsWith(".webm", true) -> MimeTypes.VIDEO_WEBM
        base.endsWith(".mp4", true) || base.endsWith(".m4v", true) -> MimeTypes.VIDEO_MP4
        else -> null
    }
}
