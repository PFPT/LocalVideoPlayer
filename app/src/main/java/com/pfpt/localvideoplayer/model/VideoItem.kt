package com.pfpt.localvideoplayer.model

import android.net.Uri
import androidx.compose.runtime.Immutable

@Immutable
data class VideoItem(
    val id: Long,
    val uri: Uri,
    val title: String,
    val durationMs: Long,
    val dateAdded: Long,
    val dateModified: Long = 0L,
    val thumbnailUri: Uri?,
    val relativePath: String? = null,
    val bucketName: String? = null,
    val seriesKey: String? = null,
    val season: Int? = null,
    val episode: Int? = null,
)
