package com.zhaoshuo.localvideoplayer.model

import android.net.Uri

data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val dateAdded: Long,
    val thumbnailUri: Uri?,
    val relativePath: String? = null,
    val bucketName: String? = null,
    val seriesKey: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)
