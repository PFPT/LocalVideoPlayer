package com.zhaoshuo.localvideoplayer.util

import android.net.Uri
import androidx.media3.common.MediaItem
import androidx.media3.common.MimeTypes
import com.zhaoshuo.localvideoplayer.model.VideoItem

fun VideoItem.toMediaItem(): MediaItem {
    val builder = MediaItem.Builder().setUri(uri)
    if (isM3u8Uri(uri) || isM3u8Title(title)) {
        builder.setMimeType(MimeTypes.APPLICATION_M3U8)
    }
    return builder.build()
}

fun isM3u8Uri(uri: Uri): Boolean {
    return isM3u8Title(uri.lastPathSegment ?: uri.toString())
}

fun isM3u8Title(name: String): Boolean {
    return name.substringBefore('?').substringBefore('#').endsWith(".m3u8", ignoreCase = true)
}
