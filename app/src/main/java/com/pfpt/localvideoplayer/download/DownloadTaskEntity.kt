package com.pfpt.localvideoplayer.download

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "download_tasks")
data class DownloadTaskEntity(
    @PrimaryKey val id: String,
    val url: String,
    val fileName: String,
    val encryptedHeaders: String,
    val saveToMovies: Boolean,
    val status: String,
    val downloadedBytes: Long,
    val totalBytes: Long,
    val partialPath: String?,
    val hlsCompletedSegments: Int,
    val hlsTotalSegments: Int,
    val downloadSpeedBps: Long,
    val isMerging: Boolean,
    val hlsPlaylistUrl: String?,
    val hlsPlaylistSignature: String?,
    val outputUri: String?,
    val retryCount: Int,
    val error: String?,
    val createdAt: Long,
)
