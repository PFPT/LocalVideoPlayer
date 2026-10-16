package com.zhaoshuo.localvideoplayer.download

import java.util.UUID

enum class DownloadStatus {
    QUEUED,
    RUNNING,
    PAUSED,
    CANCELED,
    COMPLETED,
    FAILED,
}

data class DownloadTask(
    val id: String = UUID.randomUUID().toString(),
    val url: String,
    val fileName: String,
    val headers: Map<String, String> = emptyMap(),
    val saveToMovies: Boolean = false,
    val status: DownloadStatus = DownloadStatus.QUEUED,
    val downloadedBytes: Long = 0,
    val totalBytes: Long = -1,
    val partialPath: String? = null,
    val hlsCompletedSegments: Int = 0,
    val hlsTotalSegments: Int = 0,
    val downloadSpeedBps: Long = 0,
    val isMerging: Boolean = false,
    val hlsPlaylistUrl: String? = null,
    val hlsPlaylistSignature: String? = null,
    val outputUri: String? = null,
    val retryCount: Int = 0,
    val error: String? = null,
    val createdAt: Long = System.currentTimeMillis(),
)
